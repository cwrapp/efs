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
import static com.googlecode.cqengine.query.QueryFactory.ascending;
import static com.googlecode.cqengine.query.QueryFactory.greaterThan;
import static com.googlecode.cqengine.query.QueryFactory.greaterThanOrEqualTo;
import static com.googlecode.cqengine.query.QueryFactory.lessThan;
import static com.googlecode.cqengine.query.QueryFactory.lessThanOrEqualTo;
import static com.googlecode.cqengine.query.QueryFactory.orderBy;
import static com.googlecode.cqengine.query.QueryFactory.queryOptions;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.googlecode.cqengine.resultset.ResultSet;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;
import org.efs.event.EfsTopicKey;
import org.efs.event.IEfsEvent;
import org.efs.io.EfsFileConnection.Retrieval;
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
// Member enums.
//

    /**
     * Defines how an agent may connect an {@link EfsFile}:
     * read-only, write-only, or read+write.
     */
    public enum AccessMode
    {
        /**
         * Used to access efs event file in read-only mode.
         * When used only row retrieval is supported.
         */
        READ_ONLY (0x1),

        /**
         * Used to access efs event file in write-only mode.
         * When used only row addition is supported.
         */
        WRITE_ONLY (0x2),

        /**
         * Used to access efs event file in both read and write
         * modes. When used rows may be both added and retrieved.
         */
        READ_WRITE (0x3);

    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Defines what actions agent may perform on this file.
         */
        private final int mAccessMask;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates an efs event file connect mode for given mask.
         * @param mask connect mode mask.
         */
        private AccessMode(final int mask)
        {
            mAccessMask = mask;
        } // end of AccessMode(int)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns connect mode mask.
         * @return connect mode mask.
         */
        public int accessMask()
        {
            return (mAccessMask);
        } // end of accessMask()

        /**
         * Returns {@code true} if given connect mode is
compatible with this connect mode and {@code false}
         * otherwise.
         * <p>
         * For example, if {@code this} mode is
         * {@code READ_WRITE} and argument is {@code READ}, then
         * {@code true} is returned. If {@code this} mode is
         * {@code READ} and argument is {@code WRITE}, then
         * {@code false} is returned.
         * </p>
         * @param mode compared with {@code this} connect mode.
         * @return {@code true} if {@code mode} is compatible
         * with {@code this AccessMode}.
         */
        public boolean isCompatible(final AccessMode mode)
        {
            return ((mAccessMask & mode.mAccessMask) != 0);
        } // end of isCompatible(AccessMode)

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of enum AccessMode

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
     * A {@code null} connect mode results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_ACCESS_MODE =
        "accessMode is null";

    /**
     * A {@code null} agent results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_AGENT = "agent is null";

    /**
     * A {@code null} clock results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_CLOCK = "clock is null";

    /**
     * A field name which is either {@code null}, an empty
     * string, or blanks only results in
     * an {@code IllegalArgumentException} with message {@value}.
     */
    public static final String INVALID_FIELD =
        "field is either null, an empty string, or blanks";

    /**
     * A field name which is not in the event layout results in
     * an {@code IllegalArgumentException} with message {@value}.
     */
    public static final String UNKNOWN_FIELD =
        "\"%s\" is an unknown event field";

    /**
     * Attempt to create an already existing efs event file
     * results in an {@code IllegalStateException} with message
     * {@value}.
     */
    public static final String FILE_PREVIOUSLY_CREATED =
        "\"%s\" previously created";

    /**
     * Attempt to obtain an efs event file that does not exist
     * results in an {@code IllegalStateException} with message
     * {@value}.
     */
    public static final String NO_SUCH_FILE =
        "\"%s\" does not exist";

    /**
     * Attempting to connect to event file when closed results in
     * an {@code IllegalArgumentException} with message {@value}.
     */
    public static final String CLOSED_FILE = "\"%s\" is closed";

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
     * Event field names in sorted order.
     */
    private final SortedSet<String> mFields;

    /**
     * Maps event field name to its {@code EfsRow} attribute.
     */
    private final Map<String, Attribute<EfsRow<E>, ?>> mAttributes;

    /**
     * Unique file name identifying this efs event file.
     * Generated from {@link EfsTopicKey#toString()}.
     */
    private final String mFileName;

    /**
     * Set to {@code true} if this event file is open and
     * {@code false} if not. Initialized to {@code true}
     */
    private final AtomicBoolean mOpenFlag;

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
     * When adding an event to file, use this value as row index
     * and then increment.
     */
    private final AtomicLong mNextRowIndex;

    /**
     * Currently open file connections.
     */
    private final List<EfsFileConnection<E>> mConnections;

    /**
     * Active retrieval requests looking to match future events.
     * This data member is only accessed within the dispatcher
     * thread, so it does not need to be a concurrent list.
     */
    private final List<Retrieval<E>> mActiveRequests;

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
     * Creates a new efs file instance for the given event type
     * and topic key and attributes map.
     * @param key event type and topic key.
     * @param attributes event attributes map.
     */
    private EfsFile(final EfsTopicKey<E> key,
                    final SortedSet<String> fields,
                    final Map<String, Attribute<EfsRow<E>, ?>> attributes)
    {
        mTopicKey = key;
        mFields = fields;
        mAttributes = attributes;
        mFileName = key.toString();
        mOpenFlag = new AtomicBoolean(true);
        mTable = new ConcurrentIndexedCollection<>();
        mConnections = new ArrayList<>();
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
        mLatestRow = new EfsRow<>(sClock.instant(), 0, null);
    } // end of EfsFile(EfsTopicKey, Map<>)

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
     * Returns cqengine attribute associated with given field
     * name. Returns {@code null} if field does not have an
     * associated attribute defined.
     * @param field event field name.
     * @return cqengine attribute or {@code null} if there is
     * no attribute for event field.
     * @throws IllegalArgumentException
     * if {@code field} is either {@code null}, an empty string,
     * or blank or if event has no such field.
     */
    @Nullable
    public Attribute<EfsRow<E>, ?> attribute(final String field)
    {
        if (Strings.isNullOrEmpty(field) || field.isBlank())
        {
            throw (new IllegalArgumentException(INVALID_FIELD));
        }

        if (!mFields.contains(field))
        {
            throw (
                new IllegalArgumentException(
                    String.format(UNKNOWN_FIELD, field)));
        }

        return (mAttributes.get(field));
    } // end of attribute(String)

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
     * Returns current instant as per the current {@code Clock}.
     * @return clock's current instant.
     */
    public Instant instant()
    {
        return (sClock.instant());
    } // end of instant()

    /**
     * Returns {@code true} if this efs event file is open and
     * {@code false} otherwise.
     * @return {@code true} if this efs event file is open.
     */
    public boolean isOpen()
    {
        return (mOpenFlag.get());
    } // end of isOpen()

    /**
     * Returns {@code true} if efs event file exists for given
     * topic key and {@code false} otherwise.
     * @param <E> efs event type.
     * @param key event file topic key.
     * @return {@code true} if there is an efs event file for
     * {@code key}.
     */
    public static <E extends IEfsEvent> boolean exists(final EfsTopicKey<E> key)
    {
        return (sFiles.containsKey(key));
    } // end of exists(EfsTopicKey)

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
     * Returns an {@link EfsFileConnection} instance for given
     * connection mode and agent.
     * @param accessMode event file connect mode.
     * @param agent agent accessing event file.
     * @return efs event file connect instance.
     * @throws NullPointerException
     * if either {@code accessMode} or {@code agent} is
     * {@code null}.
     * @throws IllegalStateException
     * if {@code agent} is not registered with a dispatcher or
     * this file is closed.
     */
    public EfsFileConnection<E> connect(final AccessMode accessMode,
                                        final IEfsAgent agent)
    {
        final EfsFileConnection<E> retval;

        Objects.requireNonNull(accessMode, NULL_ACCESS_MODE);
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

        // Is this file closed?
        if (!mOpenFlag.get())
        {
            throw (
                new IllegalStateException(
                    String.format(CLOSED_FILE, mFileName)));
        }

        // TODO: determine agent's connect rights.

        retval = new EfsFileConnection<>(this, agent, accessMode);
        mConnections.add(retval);

        return (retval);
    } // end of connect(AccessMode, IEfsAgent)

    /**
     * Closes this efs event file asynchronously on dispatcher
     * thread. All stored events are lost. Does nothing if
     * already closed.
     */
    public void close()
    {
        if (mOpenFlag.compareAndSet(true, false))
        {
            sFiles.remove(mTopicKey);
            EfsDispatcher.dispatch(
                this::onClose, new CloseInternalEvent(), this);
        }
    } // end of close()

    /**
     * Returns a newly created efs event file for given
     * type+topic key and assigning the file to the given
     * dispatcher.
     * @param <E> efs event type.
     * @param key efs event class and topic key.
     * @param dispatcher efs file is associated with this
     * dispatcher.
     * @return efs event file.
     * @throws NullPointerException
     * if {@code key} is {@code null}.
     * @throws IllegalArgumentException
     * if {@code dispatcher} is either a {@code null}, empty, or
     * blank or is not a known dispatcher.
     * @throws NullPointerException
     * if {@code key} is {@code null}.
     * @throws IllegalArgumentException
     * {@code dispatcher} is either {@code null}, an empty
     * string, or blanks or does not reference a known
     * dispatcher.
     * @throws IllegalStateException
     * if event file for {@code key} already exists.
     * @throws IOException
     * if attempt to open efs event file fails.
     *
     * @see #getEventFile(EfsTopicKey)
     */
    @SuppressWarnings ("unchecked")
    public static <E extends IEfsEvent> EfsFile<E> createEventFile(final EfsTopicKey<E> key,
                                                                   final String dispatcher)
        throws IOException
    {
        final EfsEventLayout<E> layout;
        final Map<String, Attribute<EfsRow<E>, ?>> attributes;
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

        // Does this event file already exist?
        if (sFiles.containsKey(key))
        {
            throw (
                new IllegalStateException(
                    String.format(
                        FILE_PREVIOUSLY_CREATED, key)));
        }

        layout =
            EfsEventLayout.getLayout(
                (Class<E>) key.eventClass());

        try
        {
            attributes =
                CQAttributeGenerator.createAttributeMap(layout);
        }
        catch (Exception jex)
        {
            throw (
                new IOException(
                    String.format(
                        "attempt to open %s event file failed",
                        key),
                    jex));
        }

        retval = new EfsFile<>(key, layout.fields(), attributes);
        EfsDispatcher.register(retval, dispatcher);
        sFiles.put(key, retval);

        return (retval);
    } // end of createEventFile(EfsTopicKey, String)

    /**
     * Returns a previously created efs event file.
     * @param <E> efs event type.
     * @param key efs event class and topic key.
     * @return efs event file.
     * @throws IllegalStateException
     * if there is no event file for {@code key}.
     *
     * @see #createEventFile(EfsTopicKey, String)
     */
    @SuppressWarnings ("unchecked")
    public static <E extends IEfsEvent> EfsFile<E> getEventFile(final EfsTopicKey<E> key)
    {
        Objects.requireNonNull(key, NULL_TOPIC_KEY);

        if (!sFiles.containsKey(key))
        {
            throw (
                new IllegalStateException(
                    String.format(NO_SUCH_FILE, key)));
        }

        return ((EfsFile<E>) sFiles.get(key));
    } // end of getEventFile(EfsTopicKey)

    /* package */ void onAdd(final AddInternalEvent<E> addEvent)
    {
        // Is this file open?
        if (!mOpenFlag.get())
        {
            // No. Do nothing.
            return;
        }

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
                    request.markCompleted(pubTime);
                }
            }
            // No, request was canceled by user.
            // Do not remove from active request lists. That
            // will be done in onCancel method.
        }
    } // end of onAdd(Instant, E)

    /* package */ void onRetrieve(final RetrievalInternalEvent<E> retrieveEvent)
    {
        final Retrieval<E> retrieval = retrieveEvent.request();
        final EfsInterval interval = retrieval.interval();

        // Is this file open?
        if (!mOpenFlag.get())
        {
            // No. Inform agent that retrieval is completed due
            // do event file being closed.
            retrieval.close(sClock.instant(),
                            CompletionType.FILE_CLOSED);
            return;
        }

        // Was retrieval request canceled during hand-off to
        // dispatcher thread?
        if (retrieval.isCompleted())
        {
            // Yes, no-op.
        }
        // Is this file empty?
        else if (mNextRowIndex.get() == 0)
        {
            // Yes. Does this retrieval for future events?
            if (interval.isFutureInterval(sClock.instant()))
            {
                // Yes again. Store request away while waiting
                // for those future events.
                generateRowQuery(retrieval);
                mActiveRequests.add(retrieval);
            }
            else
            {
                // No. Then this retrieval is completed because
                // there are no historical events to retrieve.
                retrieval.close(
                    sClock.instant(),
                    CompletionType.RETRIEVAL_COMPLETED);
            }
        }
        else
        {
            // No, request is still active. Generate CQEngine
            // query based on request interval and current time
            // and row index.
            final Query<EfsRow<E>> rowQuery =
                generateRowQuery(retrieval);

            // Retrieve events as per request. If request is for
            // future events, then store retrieval.
            try (ResultSet<EfsRow<E>> results =
                     mTable.retrieve(rowQuery, mOrderByOpts))
            {
                // Dispatch matching rows to user.
                for (EfsRow<E> r : results)
                {
                    retrieval.dispatch(r);
                }
            }

            // Does this request also include future events?
            if (!retrieval.isAtEnd(mLatestRow))
            {
                // Yes. Store request away so it can be matched
                // against those future events.
                mActiveRequests.add(retrieval);
            }
            // Request is for past events only. Let the agent
            // know this fact.
            else
            {
                retrieval.close(
                    sClock.instant(),
                    CompletionType.RETRIEVAL_COMPLETED);
            }
        }
    } // end of onRetrieve(RetrieveEvent)

    /**
     * Removes specified retrieval request from active requests
     * map.
     * @param cancelEvent contains cancel
     */
    /* package */ void onCancel(final CancelInternalEvent<E> cancelEvent)
    {
        mActiveRequests.remove(cancelEvent.request());
    } // end of onCancel(Instant, Retrieval)

    /**
     * Removes a now disconnected connection from connections
     * list.
     * @param connection remove this connection from connections
     * list.
     */
    /* package */ void onDisconnect(final DisconnectInternalEvent<E> disconnectEvent)
    {
        mConnections.remove(disconnectEvent.connection());
    } // end of onDisconnect(DisconnectInternalEvent)

    /**
     * Closes event file by clearing out event table, reporting
     * all retrievals as completed due to file closing, and
     * marks all connections as disconnected.
     * @param event event used to process file closure on
     * dispatcher thread.
     */
    @SuppressWarnings ({"unused"})
    private void onClose(final CloseInternalEvent event)
    {
        final Instant now = sClock.instant();

        // Clear out collected events.
        mTable.clear();
        mNextRowIndex.set(0L);

        // Report all requests as canceled due file closure.
        for (Retrieval<E> r : mActiveRequests)
        {
            r.close(now, CompletionType.FILE_CLOSED);
        }

        mActiveRequests.clear();

        // Disconnect all active connections.
        for (EfsFileConnection<E> c : mConnections)
        {
            c.markClosed();
        }

        mConnections.clear();

        // De-register this file from the dispatcher.
        EfsDispatcher.deregister(this);
    } // end of onClose(CloseInternalEvent)

    //
    // end of Event Handlers.
    //-----------------------------------------------------------

    /**
     * Returns efs row query for given retrieval request interval
     * and user query.
     * @param retrieval event retrieval request.
     * @return efs row query.
     */
    private Query<EfsRow<E>> generateRowQuery(final Retrieval<E> retrieval)
    {
        final EfsInterval interval = retrieval.interval();
        final Query<EfsRow<E>> beginQuery =
            generateBeginQuery(interval.beginning());
        final Query<EfsRow<E>> endQuery =
            generateEndQuery(interval.ending());

        return (retrieval.intervalQuery(beginQuery, endQuery));
    } // end of generateRowQuery(Retrieval)

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
} // end of class EfsFile
