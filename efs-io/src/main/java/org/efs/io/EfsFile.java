//
// Copyright 2026 Charles W. Rapp
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package org.efs.io;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.errorprone.annotations.ThreadSafe;
import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.SimpleAttribute;
import com.googlecode.cqengine.index.hash.HashIndex;
import com.googlecode.cqengine.index.unique.UniqueIndex;
import com.googlecode.cqengine.query.Query;
import static com.googlecode.cqengine.query.QueryFactory.and;
import static com.googlecode.cqengine.query.QueryFactory.ascending;
import static com.googlecode.cqengine.query.QueryFactory.greaterThan;
import static com.googlecode.cqengine.query.QueryFactory.greaterThanOrEqualTo;
import static com.googlecode.cqengine.query.QueryFactory.lessThan;
import static com.googlecode.cqengine.query.QueryFactory.lessThanOrEqualTo;
import static com.googlecode.cqengine.query.QueryFactory.orderBy;
import static com.googlecode.cqengine.query.QueryFactory.queryOptions;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.googlecode.cqengine.resultset.ResultSet;
import jakarta.annotation.Nonnull;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;
import org.efs.event.EfsTopicKey;
import org.efs.event.IEfsEvent;
import org.efs.io.EfsIntervalEndpoint.Clusivity;
import static org.efs.io.EfsIntervalEndpoint.Clusivity.INCLUSIVE;
import static org.efs.io.EfsIntervalEndpoint.EndpointType.TIME_OFFSET;
import org.efs.io.RetrievalCompleteEvent.CompletionType;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * Stores efs events indexed by publish timestamp and row index
 * where row index is unique and monotonically increases. Publish
 * timestamp may not be unique but does increase.
 * <p>
 * This class is in-progress.
 * </p>
 *
 * @param <E> efs event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@ThreadSafe
public final class EfsFile<E extends IEfsEvent>
    implements IEfsAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    // CQEngine attributes.

    /**
     * {@link EfsRow#getRowIndex()} attribute named
     * {@value}.
     */
    public static final String ROW_INDEX_ATTRIBUTE = "rowIndex";

    /**
     * {@link EfsRow#getPublishTimestamp()} attribute named
     * {@value}.
     */
    public static final String PUBLISH_TIMESTAMP_ATTRIBUTE =
        "publishTimestamp";

    /**
     * When testing user-provided query use an empty query
     * options instance.
     */
    private static final QueryOptions NO_OPTS =
        new QueryOptions();

    // Exception messages.

    /**
     * A {@code null EfsTopicKey} argument results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_TOPIC_KEY = "key is null";

    /**
     * An {@code EfsDispatcher} name which is either
     * {@code null}, an empty string, or blanks only results in
     * an {@code IllegalArgumentException} with message {@value}.
     */
    public static final String INVALID_DISPATCHER =
        "dispatcher is either null, an empty string, or blanks";

    /**
     * A dispatcher name which does not reference a known
     * dispatcher results in an {@code IllegalArgumentException}
     * with message {@value}.
     */
    public static final String UNKNOWN_DISPATCHER =
        "\"%s\" is an unknown dispatcher";

    /**
     * An agent which is not registered with a dispatcher results
     * in an {@code IllegalStateException} with message {@value}.
     */
    public static final String UNREGISTERED_AGENT =
        "\"%s\" is not registered with a dispatcher";

    /**
     * A {@code null} interval results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_INTERVAL =
        "interval is null";

    /**
     * A {@code null} retrieval condition results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_CONDITION =
        "condition is null";

    /**
     * A {@code null} event retrieval callback results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_EVENT_CALLBACK =
        "event callback is null";

    /**
     * A {@code null} retrieval complete callback results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_DONE_CALLBACK =
        "completion callback is null";

    /**
     * A {@code null} agent results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_AGENT = "agent is null";

    /**
     * A {@code null} event results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_EVENT = "event is null";

    /**
     * A {@code null} clock results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_CLOCK = "clock is null";

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Maps unique topic keys to their associated efs file.
     */
    private static final Map<EfsTopicKey<?>, EfsFile<?>> sFiles =
        new ConcurrentHashMap<>();

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(EfsFile.class);

    /**
     * Use this clock to obtain {@code Instant} values. This
     * data member is not final to allow for unit tests to use
     * fixed clocks.
     *
     * @see #setSystemClock(Clock)
     */
    private static Clock sClock = Clock.systemUTC();

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Key defining event file topic and stored event type.
     */
    private final EfsTopicKey<E> mTopicKey;

    /**
     * Unique file name identifying this efs event file.
     * Generated from {@link EfsTopicKey#toString()}.
     */
    private final String mFileName;

    /**
     * Table indexed by row index.
     */
    private final IndexedCollection<EfsRow<E>> mTable;

    /**
     * {@link EfsRow#getRowIndex()} row index.
     */
    private final Attribute<EfsRow<E>, Long> mRowIndex;

    /**
     * {@link EfsRow#getPublishTimestamp()} publish time
     * index.
     */
    private final Attribute<EfsRow<E>, Instant> mPubTimeIndex;

    /**
     * Order retrieved events by ascending row index.
     */
    private final QueryOptions mOrderByOpts;

    /**
     * Active retrieval requests looking to match future events.
     * This data member is only accessed within the dispatcher
     * thread, so it does not need to be a concurrent list.
     */
    private final List<Retrieval<E>> mActiveRequests;

    /**
     * When adding an event to file, use this value as row index
     * and then increment.
     */
    private final AtomicLong mNextRowIndex;

    /**
     * Latest row to be added to table. On start-up initialized
     * to current time, zero row index, and {@code null} event.
     */
    private EfsRow<E> mLatestRow;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of EfsFile.
     */
    private EfsFile(final EfsTopicKey<E> key)
    {
        mTopicKey = key;
        mFileName = key.toString();
        mTable = new ConcurrentIndexedCollection<>();
        mActiveRequests = new ArrayList<>();

        mRowIndex =
            new SimpleAttribute<EfsRow<E>, Long>(ROW_INDEX_ATTRIBUTE)
            {
                @Override
                public Long getValue(final EfsRow<E> row,
                                     final QueryOptions qo)
                {
                    return (row.getRowIndex());
                }
            };
        mPubTimeIndex =
            new SimpleAttribute<EfsRow<E>, Instant>(PUBLISH_TIMESTAMP_ATTRIBUTE)
            {
                @Override
                public Instant getValue(final EfsRow<E> row,
                                        final QueryOptions qo)
                {
                    return (row.getPublishTimestamp());
                }
            };
        mOrderByOpts =
            queryOptions(orderBy(ascending(mRowIndex)));

        // Index row and publish timestamp attributes.
        UniqueIndex.onAttribute(mRowIndex);
        HashIndex.onAttribute(mPubTimeIndex);

        mNextRowIndex = new AtomicLong();
        mLatestRow = new EfsRow<>(sClock.instant(), 0L, null);
    } // end of EfsFile(EfsTopicKey)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // IEfsAgent Interface Implementation.
    //

    /**
     * Returns uniquely identifying file name.
     * @return efs file name.
     */
    @Override
    public String name()
    {
        return (mFileName);
    } // end of name()

    //
    // end of IEfsAgent Interface Implementation.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns file topic key.
     * @return topic key.
     */
    public EfsTopicKey<E> topicKey()
    {
        return (mTopicKey);
    } // end of topicKey()

    /**
     * Returns <em>approximate</em> number of rows in efs file.
     * These reason this value is approximate is due to rows
     * are added asynchronously to file. It is possible that at
     * the time of this call, new rows are being added.
     * @return approximate row count.
     */
    public long rowCount()
    {
        return (mNextRowIndex.get());
    } // end of rowCount()

    /**
     * Returns currently configured system clock.
     * @return current system clock.
     */
    public static Clock getSystemClock()
    {
        return (sClock);
    } // end of getSystemClock()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets system clock used to obtain {@code Instant} values,
     * returning replaced system clock. This method is mainly
     * used by unit tests to put a fixed clock in place. This
     * allows for tests to always use the same time.
     * @param clock replaces current system clock.
     * @return replaced system clock.
     * @throws NullPointerException
     * if {@code clock} is {@code null}.
     */
    @VisibleForTesting
    public static Clock setSystemClock(final Clock clock)
    {
        final Clock retval = sClock;

        sClock = Objects.requireNonNull(clock, NULL_CLOCK);

        return (retval);
    } // end of setSystemClock(Clock)

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    /**
     * TODO
     * @param event event added to efs file.
     * @return efs event retrieval subscription.
     *
     * @see #retrieve(EfsInterval, Query, Consumer, Consumer, IEfsAgent)
     */
    public Instant add(final E event)
    {
        final Instant pubTime = sClock.instant();
        final AddInternalEvent<E> addEvent =
            new AddInternalEvent<>(pubTime, event);

        Objects.requireNonNull(event, NULL_EVENT);

        EfsDispatcher.dispatch(this::onAdd, addEvent, this);

        return (pubTime);
    } // end of add(E)

    /**
     * TODO
     * @param interval forward only those events within this
     * interval.
     * @param condition forward only those events which match
     * this condition.
     * @param eventCB forward matching events to this callback.
     * @param completionCB when retrieval is complete, post
     * {@link RetrievalCompleteEvent} to this callback.
     * @param agent forward matching events to this agent.
     * @return returns an event retrieval subscription.
     * @throws NullPointerException
     * if any of the arguments is {@code null}.
     * @throws IllegalStateException
     * if {@code agent} is not registered with a dispatcher.
     *
     * @see #add(IEfsEvent)
     */
    @Nonnull
    public Retrieval<E> retrieve(@Nonnull final EfsInterval interval,
                                 @Nonnull final Query<E> condition,
                                 @Nonnull final Consumer<EfsRow<E>> eventCB,
                                 @Nonnull final Consumer<RetrievalCompleteEvent<E>> completionCB,
                                 @Nonnull final IEfsAgent agent)
    {
        final Retrieval<E> retval;

        // Validate arguments.
        Objects.requireNonNull(interval, NULL_INTERVAL);
        Objects.requireNonNull(condition, NULL_CONDITION);
        Objects.requireNonNull(eventCB, NULL_EVENT_CALLBACK);
        Objects.requireNonNull(completionCB, NULL_DONE_CALLBACK);
        Objects.requireNonNull(agent, NULL_AGENT);

        // Is agent registered with a dispatcher?
        if (!EfsDispatcher.isRegistered(agent))
        {
            // No, and that is wrong.
            throw (
                new IllegalStateException(
                    String.format(
                        UNREGISTERED_AGENT, agent.name())));
        }

        retval = new Retrieval<>(this,
                                 agent,
                                 interval,
                                 condition,
                                 eventCB,
                                 completionCB);

        // Do actual row retrieval on dispatcher thread.
        EfsDispatcher.dispatch(
            this::onRetrieve,
            new RetrievalInternalEvent<>(retval),
            this);

        return (retval);
    } // end of retrieve(...)

    /**
     * TODO
     * @param <E> efs event type.
     * @param key efs event class and topic key.
     * @param dispatcher efs file is associated with this
     * dispatcher.
     * @return open efs file.
     * @throws NullPointerException
     * if {@code key} is {@code null}.
     * @throws IllegalArgumentException
     * if {@code dispatcher} is either a {@code null}, empty, or
     * blank or is not a known dispatcher.
     */
    @SuppressWarnings ("unchecked")
    public static <E extends IEfsEvent> EfsFile<E> openEventFile(final EfsTopicKey<E> key,
                                                                 final String dispatcher)
    {
        final EfsFile<E> retval;

        // Validate arguments.
        Objects.requireNonNull(key, NULL_TOPIC_KEY);

        if (Strings.isNullOrEmpty(dispatcher) ||
            dispatcher.isBlank())
        {
            throw (
                new IllegalArgumentException(
                    INVALID_DISPATCHER));
        }

        // Is this a known dispatcher.
        if (!EfsDispatcher.isDispatcher(dispatcher))
        {
            throw (
                new IllegalArgumentException(
                    String.format(
                        UNKNOWN_DISPATCHER, dispatcher)));
        }

        retval =
            (EfsFile<E>)
                sFiles.computeIfAbsent(
                    key,
                    k -> {
                        final EfsFile<E> ef =
                            new EfsFile<>(key);

                        // Register efs file agent with its
                        // dispatcher before returning.
                        EfsDispatcher.register(ef, dispatcher);

                        return (ef);
                    });

        return (retval);
    } // end of openEventFile(EfsTopicKey, String)

    //-----------------------------------------------------------
    // Event Handlers.
    //

    private void onAdd(final AddInternalEvent<E> addEvent)
    {
        final Instant pubTime = addEvent.publishTimestamp();
        final EfsRow<E> row =
            new EfsRow<>(pubTime,
                         mNextRowIndex.getAndIncrement(),
                         addEvent.event());

        mTable.add(row);
        mLatestRow = row;

        // Forward event row to agents whose request matches this
        // row.
        final Iterator<Retrieval<E>> rIt =
            mActiveRequests.iterator();
        Retrieval<E> request;

        while (rIt.hasNext())
        {
            request = rIt.next();

            // Is this request still active?
            if (!request.isCompleted())
            {
                // Yes, request is active.
                // Does this row satisfy the request?
                if (request.matches(row))
                {
                    // Yes. Forward row to agent.
                    try
                    {
                        request.dispatch(row);
                    }
                    catch (IllegalStateException statex)
                    {
                        sLogger.warn(
                            "{}: attempt to post row {} to agent {} failed; event queue full.",
                            mTopicKey,
                            row,
                            (request.agent()).name());
                    }
                }

                // Has the retrieval request reached its end?
                if (request.isAtEnd(row))
                {
                    // Yes. Remove retrieval request from active
                    // requests list, mark request as completed
                    // and then tell agent about retrieval
                    // completion.
                    rIt.remove();
                    request.markCompleted();

                    request.dispatch(
                        new RetrievalCompleteEvent<>(
                            CompletionType.RETRIEVAL_COMPLETED,
                        pubTime,
                        request));
                }
            }
            // No, request was canceled by user.
            // Do not remove from active request lists. That
            // will be done in onCancel method.
        }
    } // end of onAdd(Instant, E)

    private void onRetrieve(final RetrievalInternalEvent<E> retrieveEvent)
    {
        final Retrieval<E> retrieval= retrieveEvent.request();

        // Was retrieval request canceled during hand-off to
        // dispatcher thread?
        if (!retrieval.isCompleted())
        {
            // No, request is still active. Generate CQEngine
            // query based on request interval and current time
            // and row index.
            final EfsInterval interval = retrieval.interval();
            final Query<EfsRow<E>> beginQuery =
                generateBeginQuery(interval.beginning());
            final Query<EfsRow<E>> endQuery =
                generateEndQuery(interval.ending());
            final Query<EfsRow<E>> intervalQuery =
                and(beginQuery, endQuery);

            // Store interval queries into retrieval request.
            retrieval.intervalQuery(beginQuery, endQuery);

            // Retrieve events as per request. If request is for
            // future events, then store retrieval.
            try (ResultSet<EfsRow<E>> results =
                     mTable.retrieve(intervalQuery, mOrderByOpts))
            {
                E event;

                for (EfsRow<E> r : results)
                {
                    event = r.getEvent();

                    // Does row event match user query?
                    if (retrieval.matches(event))
                    {
                        // Yes. Dispatch this row to user.
                        retrieval.dispatch(r);
                    }
                }
            }

            // Were any rows returned? No rows
            // Is this request also include future events?
            if (mLatestRow == null ||
                !retrieval.isAtEnd(mLatestRow))
            {
                // Yes. Store request away so it can be matched
                // against those future events.
                mActiveRequests.add(retrieval);
            }
            // Request is for past events only. Let the agent
            // know this fact.
            else
            {
                retrieval.dispatch(
                    new RetrievalCompleteEvent<>(
                        CompletionType.RETRIEVAL_COMPLETED,
                        sClock.instant(),
                        retrieval));
            }
        }
    } // end of onRetrieve(Instant, Retrieval)

    private void onCancel(final CancelInternalEvent<E> cancelEvent)
    {
        final Retrieval<E> retrieval = cancelEvent.request();

        // Make sure retrieval request is in list.
        if (mActiveRequests.remove(retrieval))
        {
            retrieval.dispatch(
                new RetrievalCompleteEvent<>(
                    CompletionType.USER_CANCEL,
                    cancelEvent.cancelTimestamp(),
                    retrieval));
        }
    } // end of onCancel(Instant, Retrieval)

    //
    // end of Event Handlers.
    //-----------------------------------------------------------

    /**
     * Returns a CQEngine query based on the interval's
     * beginning.
     * @param beginning interval beginning.
     * @return CQEngine query for interval beginning.
     */
    private Query<EfsRow<E>> generateBeginQuery(final EfsIntervalEndpoint beginning)
    {
        final Clusivity clusivity = beginning.clusivity();
        final Query<EfsRow<E>> retval;

        switch (beginning.endpointType())
        {
            case FIXED_TIME:
                final EfsTimeEndpoint tep =
                    (EfsTimeEndpoint) beginning;

                retval =
                    switch (clusivity)
                    {
                        case INCLUSIVE ->
                            greaterThanOrEqualTo(
                                mPubTimeIndex, tep.time());

                         // EXCLUSIVE
                        default ->
                            greaterThan(
                                mPubTimeIndex, tep.time());
                    };
                break;

            case TIME_OFFSET:
                final EfsDurationEndpoint dep =
                    (EfsDurationEndpoint) beginning;
                final Instant beginTime =
                    (mLatestRow.getPublishTimestamp())
                        .plus(dep.timeOffset());

                retval =
                    switch (clusivity)
                    {
                        case INCLUSIVE ->
                            greaterThanOrEqualTo(
                                mPubTimeIndex, beginTime);

                        // EXCLUSIVE
                        default ->
                            greaterThan(
                                mPubTimeIndex, beginTime);
                    };
                break;

            // INDEX_OFFSET
            default:
                final EfsIndexEndpoint iep =
                    (EfsIndexEndpoint) beginning;
                final long beginRowIndex =
                    ((mLatestRow.getRowIndex()) +
                     iep.indexOffset());

                retval =
                    switch (clusivity)
                    {
                        case INCLUSIVE ->
                            greaterThanOrEqualTo(
                                mRowIndex, beginRowIndex);

                        // EXCLUSIVE
                        default ->
                            greaterThan(
                                mRowIndex, beginRowIndex);
                    };
        }

        return (retval);
    } // end of generateBeginQuery(...)

    /**
     * Returns a CQEngine query on interval's ending.
     * @param ending interval ending.
     * @return CQEngine query for interval ending.
     */
    private Query<EfsRow<E>> generateEndQuery(final EfsIntervalEndpoint ending)
    {
        final Clusivity clusivity = ending.clusivity();
        final Query<EfsRow<E>> retval;

        switch (ending.endpointType())
        {
            case FIXED_TIME:
                final EfsTimeEndpoint tep =
                    (EfsTimeEndpoint) ending;

                retval =
                    switch (clusivity)
                    {
                        case INCLUSIVE ->
                            lessThanOrEqualTo(
                                mPubTimeIndex, tep.time());

                         // EXCLUSIVE
                        default ->
                            lessThan(mPubTimeIndex, tep.time());
                    };
                break;

            case TIME_OFFSET:
                final EfsDurationEndpoint dep =
                    (EfsDurationEndpoint) ending;
                final Instant beginTime =
                    (mLatestRow.getPublishTimestamp())
                        .plus(dep.timeOffset());

                retval =
                    switch (clusivity)
                    {
                        case INCLUSIVE ->
                            lessThanOrEqualTo(
                                mPubTimeIndex, beginTime);

                        // EXCLUSIVE
                        default ->
                            lessThan(mPubTimeIndex, beginTime);
                    };
                break;

            // INDEX_OFFSET
            default:
                final EfsIndexEndpoint iep =
                    (EfsIndexEndpoint) ending;
                final long beginRowIndex =
                    (mLatestRow.getRowIndex() +
                     iep.indexOffset());

                retval =
                    switch (clusivity)
                    {
                        case INCLUSIVE ->
                            lessThanOrEqualTo(
                                mRowIndex, beginRowIndex);

                        // EXCLUSIVE
                        default ->
                            lessThan(
                                mRowIndex, beginRowIndex);
                    };
        }

        return (retval);
    } // end of generateEndQuery(...)

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Acts as a subscription to future events added to ef file.
     * TODO
     * @param <E> efs event type.
     */
    public static final class Retrieval<E extends IEfsEvent>
        implements AutoCloseable
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Constants.
        //
        //-------------------------------------------------------
        // Statics.
        //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Retrieval is for events stored in this file.
         */
        private final EfsFile<E> mFile;

        /**
         * Dispatch retrieved rows to this agent.
         */
        private final IEfsAgent mAgent;

        /**
         * Retrieve rows within this interval.
         */
        private final EfsInterval mInterval;

        /**
         * User event condition.
         */
        private final Query<E> mUserQuery;

        /**
         * Used to retrieve events based on begin interval
         * point. This data member is not final because it is
         * generated after the retrieval instance is created and
         * when the retrieval is performed on the dispatcher
         * thread.
         */
        private Query<EfsRow<E>> mIntervalBeginQuery;

        /**
         * Used to retrieve events based on end interval point.
         * This data member is not final because it is generated
         * after the retrieval instance is created and when the
         * retrieval is performed on the dispatcher thread.
         */
        private Query<EfsRow<E>> mIntervalEndQuery;

        /**
         * Post events to this agent callback.
         */
        private final Consumer<EfsRow<E>> mEventCB;

        /**
         * Post {@link RetrievalCompleteEvent} to this callback
         * method.
         */
        private final Consumer<RetrievalCompleteEvent<E>> mCompletionCB;

        /**
         * Set to {@code true} when retrieval request reaches
         * completion. This may be due to all requested rows
         * being retrieved or user cancellation. Initialized to
         * {@code false}.
         */
        private final AtomicBoolean mCompletionFlag;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Retrieval(final EfsFile<E> file,
                          final IEfsAgent agent,
                          final EfsInterval interval,
                          final Query<E> userCondition,
                          final Consumer<EfsRow<E>> eventCB,
                          final Consumer<RetrievalCompleteEvent<E>> completionCB)
        {
            mFile = file;
            mAgent = agent;
            mInterval = interval;
            mUserQuery = userCondition;
            mEventCB = eventCB;
            mCompletionCB = completionCB;
            mCompletionFlag = new AtomicBoolean();
        } // end of Retrieval(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // AutoCloseable Interface Implementation.
        //

        /**
         * Reports to {@link EfsFile} that this retrieval request
         * is now canceled and should be removed from request
         * list. Does nothing if this request is marked
         * completed.
         * @throws Exception
         * if an error occurs canceling this retrieval request.
         */
        @Override
        public void close()
            throws Exception
        {
            // Is this retrieval request completed?
            if (mCompletionFlag.compareAndSet(false, true))
            {
                // No, mark this request as completed and have
                // the event file cancel this request.
                EfsDispatcher.dispatch(
                    mFile::onCancel,
                    new CancelInternalEvent<>(
                        sClock.instant(), this),
                    mAgent);
            }
        } // end of close()

        //
        // end of AutoCloseable Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //
        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns agent retrieving rows.
         * @return retrieving agent.
         */
        private IEfsAgent agent()
        {
            return (mAgent);
        } // end of agent()

        /**
         * Returns retrieval interval.
         * @return retrieval interval.
         */
        private EfsInterval interval()
        {
            return (mInterval);
        } // end of interval()

        /**
         * Returns {@code true} if given row is beyond interval
         * ending and {@code false} if not. Returning
         * {@code true} means that the retrieval request is
         * completed.
         * @param row latest event row.
         * @return {@code true} if retrieval request has reached
         * completion.
         */
        private boolean isAtEnd(final EfsRow<E> row)
        {
            return (!mIntervalEndQuery.matches(row, NO_OPTS));
        } // end of isAtEnd(Instant, long)

        /**
         * Returns {@code true} if retrieval request has reached
         * completion and should now be removed from request
         * list; {@code false} if request is still active.
         * @return {@code true} if retrieval request is
         * completed.
         */
        private boolean isCompleted()
        {
            return (mCompletionFlag.get());
        } // end of isCompleted()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets interval query for a retrieval request for future
         * events.
         * @param beginQuery beginning interval query.
         * @param endQuery ending interval query.
         */
        private void intervalQuery(final Query<EfsRow<E>> beginQuery,
                                   final Query<EfsRow<E>> endQuery)
        {
            mIntervalBeginQuery = beginQuery;
            mIntervalEndQuery = endQuery;
        } // end of intervalQuery(Query, Query)

        /**
         * Returns {@code true} if retrieval request was not
         * previously canceled and so is now completed due to all
         * requested rows being retrieved.
         * @return {@code true} if retrieval request successfully
         * reached completion.
         */
        private boolean markCompleted()
        {
            return (mCompletionFlag.compareAndSet(false, true));
        } // end of markCompleted()

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Returns {@code true} if given event matches
         * user-provided query; otherwise returns {@code false}.
         * @param event apply user query to this event.
         * @return {@code true} if event matches user query.
         */
        private boolean matches(final E event)
        {
            return (mUserQuery.matches(event, NO_OPTS));
        } // end of matches(E)

        /**
         * Returns {@code true} if given row and encapsulated
         * event satisfies both the interval query and user event
         * query; otherwise returns {@code false}.
         * @param row compare this efs table row against both
         * interval and user queries.
         * @return {@code true} if row and event satisfy interval
         * and user event queries.
         */
        private boolean matches(final EfsRow<E> row)
        {
            return (mIntervalBeginQuery.matches(row, NO_OPTS) &&
                    mIntervalEndQuery.matches(row, NO_OPTS) &&
                    mUserQuery.matches(row.getEvent(), NO_OPTS));
        } // end of matches(EfsRow)

        /**
         * Dispatches event row to retrieval agent.
         * @param row dispatch this row to agent.
         */
        private void dispatch(final EfsRow<E> row)
        {
            EfsDispatcher.dispatch(mEventCB, row, mAgent);
        } // end of dispatch(EfsRow)

        /**
         * Dispatches retrieval completed event to agent.
         * @param event dispatch this event to agent.
         */
        private void dispatch(final RetrievalCompleteEvent<E> event)
        {
            EfsDispatcher.dispatch(mCompletionCB, event, mAgent);
        } // end of dispatch(RetrievalCompleteEvent)
    } // end of class Retrieval
} // end of class EfsFile
