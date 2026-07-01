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

import com.google.common.collect.ImmutableList;
import com.googlecode.cqengine.query.Query;
import static com.googlecode.cqengine.query.QueryFactory.and;
import com.googlecode.cqengine.query.option.QueryOptions;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;
import org.efs.event.IEfsEvent;
import org.efs.io.EfsFile.AccessMode;
import org.efs.io.RetrievalCompleteEvent.CompletionType;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * TODO
 *
 * @param <E> efs event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsFileConnection<E extends IEfsEvent>
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * When testing user-provided query use an empty query
     * options instance.
     */
    private static final QueryOptions NO_OPTS =
        new QueryOptions();

    // Exception messages.

    /**
     * A {@code null} event results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_EVENT = "event is null";

    /**
     * If agent attempts to add an event with read-only connect
results in a {@code IllegalStateException} with message
     * {@value}.
     */
    public static final String READ_ONLY_ACCESS =
        "{} opened with read-only access, cannot add events";

    /**
     * If agent attempts to retrieve events with write-only
connect results in a {@code IllegalStateException} with
     * message {@value}.
     */
    public static final String WRITE_ONLY_ACCESS =
        "{} opened with write-only access, cannot retrieve events";

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
    public static final String NULL_QUERY = "query is null";

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
     * Attempting to connect event file when closed results in an
{@code IllegalArgumentException} with message {@value}.
     */
    public static final String CLOSED_FILE = "\"%s\" is closed";

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Used to generate unique {@link Retrieval} identifiers
     * unique within the JVM.
     */
    private static final AtomicInteger sRetrieveIds =
        new AtomicInteger();

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(EfsFileConnection.class);

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Underlying efs file.
     */
    private final EfsFile<E> mEventFile;

    /**
     * Agent accessing underlying efs file.
     */
    private final IEfsAgent mAgent;

    /**
     * Agent connect mode.
     */
    private final AccessMode mAccessMode;

    /**
     * Active retrieval requests looking to match future events.
     * This data member is only accessed within the dispatcher
     * thread, so it does not need to be a concurrent list.
     */
    private final List<Retrieval<E>> mActiveRequests;

    /**
     * Set to {@code true} if this event file connection is open
     * and {@code false} if not. Initialized to {@code true}
     */
    private final AtomicBoolean mOpenFlag;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new efs event file instance
     */
    /* package */ EfsFileConnection(final EfsFile<E> eventFile,
                                    final IEfsAgent agent,
                                    final AccessMode accessMode)
    {
        mEventFile = eventFile;
        mAgent = agent;
        mAccessMode = accessMode;
        mActiveRequests = new ArrayList<>();
        mOpenFlag = new AtomicBoolean(true);
    } // end of EfsFileConnection(EfsFile, IEfsAgent, AccessMode)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns underlying efs event file.
     * @return underlying efs event file.
     */
    public EfsFile<E> eventFile()
    {
        return (mEventFile);
    } // end of eventFile()

    /**
     * Returns {@code true} if efs event file connection is open
     * and {@code closed} if not.
     * @return {@code true} if connection is open.
     */
    public boolean isOpen()
    {
        return (mOpenFlag.get());
    } // end of isOpen()

    /**
     * Returns underlying agent owning this connection.
     * @return underlying agent.
     */
    public IEfsAgent agent()
    {
        return (mAgent);
    } // end of agent()

    /**
     * Returns connect mode for this event file.
     * @return event file connect mode.
     */
    public AccessMode accessMode()
    {
        return (mAccessMode);
    } // end of accessMode()

    /**
     * Returns current instant as per the current {@code Clock}.
     * @return clock's current instant.
     */
    public Instant instant()
    {
        return (mEventFile.instant());
    } // end of instant()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Marks this connection as closed. Method called by
     * {@code EfsFile} when file is closed.
     */
    /* package */ void markClosed()
    {
        mOpenFlag.set(false);
    } // end of markClosed()

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    /**
     * Appends given event to event file.
     * @param event event added to efs file.
     * @return efs event retrieval subscription.
     * @throws NullPointerException
     * if {@code event} is {@code null}.
     * @throws IllegalStateException
     * if this event file is not open for writing or event file
     * is closed.
     *
     * @see #retrieve(EfsInterval, Query, Consumer, Consumer)
     */
    @Nonnull
    public Instant add(@Nonnull final E event)
    {
        Objects.requireNonNull(event, NULL_EVENT);

        // Is this agent able to add events to efs file?
        if (!mAccessMode.isCompatible(AccessMode.WRITE_ONLY))
        {
            // No, not allowed to write.
            throw (
                new IllegalStateException(
                    String.format(
                        READ_ONLY_ACCESS, mEventFile.name())));
        }

        // Is this file connection open?
        // Is event file open?
        if (!mOpenFlag.get() || !mEventFile.isOpen())
        {
            // No.
            throw (
                new IllegalStateException(
                    String.format(
                        CLOSED_FILE, mEventFile.name())));
        }

        final Instant pubTime = mEventFile.instant();
        final AddInternalEvent<E> addEvent =
            new AddInternalEvent<>(pubTime, event);

        EfsDispatcher.dispatch(
            mEventFile::onAdd, addEvent, mEventFile);

        return (pubTime);
    } // end of add(E)

    /**
     * TODO
     * @param interval forward only those events within this
     * interval.
     * @param query forward only those events which match
     * this condition.
     * @param eventCB forward matching events to this callback.
     * @param completionCB when retrieval is complete, post
     * {@link RetrievalCompleteEvent} to this callback.
     * @return returns an event retrieval subscription.
     * @throws NullPointerException
     * if any of the arguments is {@code null}.
     * @throws IllegalStateException
     * if this event file is not open for reading or event file
     * is closed.
     *
     * @see #add(IEfsEvent)
     */
    @Nonnull
    public Retrieval<E> retrieve(@Nonnull final EfsInterval interval,
                                 @Nonnull final Query<EfsRow<E>> query,
                                 @Nonnull final Consumer<EfsRow<E>> eventCB,
                                 @Nonnull final Consumer<RetrievalCompleteEvent<E>> completionCB)
    {
        final Retrieval<E> retval;

        // Validate arguments.
        Objects.requireNonNull(interval, NULL_INTERVAL);
        Objects.requireNonNull(query, NULL_QUERY);
        Objects.requireNonNull(eventCB, NULL_EVENT_CALLBACK);
        Objects.requireNonNull(completionCB, NULL_DONE_CALLBACK);

        // Is this agent able to retrieve events from efs file?
        if (!mAccessMode.isCompatible(AccessMode.READ_ONLY))
        {
            throw (
                new IllegalStateException(
                    String.format(
                        WRITE_ONLY_ACCESS, mEventFile.name())));
        }

        // Is this file connection open?
        // Is event file open?
        if (!mOpenFlag.get() || !mEventFile.isOpen())
        {
            throw (
                new IllegalStateException(
                    String.format(
                        CLOSED_FILE, mEventFile.name())));
        }

        retval = new Retrieval<>(sRetrieveIds.getAndIncrement(),
                                 this,
                                 mAgent,
                                 interval,
                                 query,
                                 eventCB,
                                 completionCB);

        sLogger.info(
            "{}: {} retrieving events over interval {}, query {}.",
            mEventFile.name(),
            mAgent.name(),
            interval,
            query);

        // Do actual row retrieval on dispatcher thread.
        EfsDispatcher.dispatch(
            mEventFile::onRetrieve,
            new RetrievalInternalEvent<>(retval),
            mEventFile);
        mActiveRequests.add(retval);

        return (retval);
    } // end of retrieve(...)

    /**
     * Closes all active retrieval requests. If this event file
     * is already closed, then does nothing.
     */
    public void close()
    {
        if (mOpenFlag.compareAndSet(true, false))
        {
            final List<Retrieval<E>> copy =
                ImmutableList.copyOf(mActiveRequests);

            for (Retrieval<E> r : copy)
            {
                try
                {
                    r.close(mEventFile.instant(),
                            CompletionType.CONNECTION_CLOSED);
                }
                catch (Exception jex)
                {
                    // Ignore.
                }
            }

            EfsDispatcher.dispatch(
                mEventFile::onDisconnect,
                new DisconnectInternalEvent<>(this),
                mEventFile);
        }
    } // end of close()

    /**
     * Removes a completed retrieval from active requests
     * list.
     * @param retrieval remove this request from active requests.
     */
    /* package */ void retrievalComplete(final Retrieval<E> retrieval)
    {
        mActiveRequests.remove(retrieval);
    } // end of retrievalComplete(Retrieval)

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
        // Locals.
        //

        /**
         * Retrieval identifier. Unique within JVM.
         */
        private final int mId;

        /**
         * Retrieval is for this event file.
         */
        @Nonnull
        private final EfsFileConnection<E> mEventFile;

        /**
         * Dispatch retrieved rows to this agent.
         */
        @Nonnull
        private final IEfsAgent mAgent;

        /**
         * Retrieve rows within this interval.
         */
        @Nonnull
        private final EfsInterval mInterval;

        /**
         * User event condition.
         */
        @Nonnull
        private final Query<EfsRow<E>> mUserQuery;

        /**
         * Used to retrieve events based on end interval point.
         * This data member is not final because it is generated
         * after the retrieval instance is created and when the
         * retrieval is performed on the dispatcher thread.
         */
        private Query<EfsRow<E>> mIntervalEndQuery;

        /**
         * Complete efs row query combining interval beginning,
         * interval ending, and user event query.
         */
        private Query<EfsRow<E>> mRowQuery;

        /**
         * Post events to this agent callback.
         */
        @Nonnull
        private final Consumer<EfsRow<E>> mEventCB;

        /**
         * Post {@link RetrievalCompleteEvent} to this callback
         * method.
         */
        @Nonnull
        private final Consumer<RetrievalCompleteEvent<E>> mCompletionCB;

        /**
         * Set to {@code true} when retrieval request reaches
         * completion. This may be due to all requested rows
         * being retrieved or user cancellation. Initialized to
         * {@code false}.
         */
        private final AtomicBoolean mCompletionFlag;

        /**
         * If {@link #mCompletionFlag} is {@code true}, then set
         * to completion reason; otherwise is {@code null}.
         */
        private final AtomicReference<CompletionType> mCompletionType;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates an agent retrieval request.
         * @param id unique retrieval identifier.
         * @param file efs event file connection.
         * @param agent efs agent requesting retrieval.
         * @param interval event retrieval interval.
         * @param userQuery user retrieval query.
         * @param eventCB forward retrieved events to this
         * callback.
         * @param completionCB report retrieval completion on
         * this callback.
         */
        private Retrieval(final int id,
                          final EfsFileConnection<E> file,
                          final IEfsAgent agent,
                          final EfsInterval interval,
                          final Query<EfsRow<E>> userQuery,
                          final Consumer<EfsRow<E>> eventCB,
                          final Consumer<RetrievalCompleteEvent<E>> completionCB)
        {
            mId = id;
            mEventFile = file;
            mAgent = agent;
            mInterval = interval;
            mUserQuery = userQuery;
            mEventCB = eventCB;
            mCompletionCB = completionCB;
            mCompletionFlag = new AtomicBoolean();
            mCompletionType = new AtomicReference<>();
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
            // Is this file connection open?
            if (close((mEventFile.mEventFile).instant(),
                      CompletionType.USER_CANCEL))
            {
                final EfsFile<E> file = mEventFile.eventFile();

                // Yes, now it is closed. Inform the event file
                // of this fact so it can remove this now defunt
                // retrieval.
                EfsDispatcher.dispatch(
                    file::onCancel,
                    new CancelInternalEvent<>(
                        mEventFile.instant(),
                        CompletionType.USER_CANCEL,
                        this),
                    mAgent);
            }
        } // end of close()

        //
        // end of AutoCloseable Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        @Override
        public String toString()
        {
            return (
                String.format(
                    "[id=%d, agent=%s, interval=%s, query=%s]",
                    mId,
                    mAgent.name(),
                    mInterval,
                    mUserQuery));
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns {@code true} if retrieval request has reached
         * completion and should now be removed from request
         * list; {@code false} if request is still active.
         * @return {@code true} if retrieval request is
         * completed.
         *
         * @see #completionType()
         */
        public boolean isCompleted()
        {
            return (mCompletionFlag.get());
        } // end of isCompleted()

        /**
         * Returns retrieval's completion type. Will be
         * {@code null} if retrieval is not yet completed.
         * @return retrieval completion type.
         *
         * @see #isCompleted()
         */
        @Nullable
        public CompletionType completionType()
        {
            return (mCompletionType.get());
        } // end of completionType()

        /**
         * Returns unique retrieval identifier. Uniqueness is
         * within JVM.
         * @return JVM-wide unique retrieval identifier.
         */
        public int id()
        {
            return (mId);
        } // end of id()

        /**
         * Returns agent retrieving rows.
         * @return retrieving agent.
         */
        public IEfsAgent agent()
        {
            return (mAgent);
        } // end of agent()

        /**
         * Returns retrieval interval.
         * @return retrieval interval.
         */
        public EfsInterval interval()
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
        /* package */ boolean isAtEnd(final EfsRow<E> row)
        {
            return (!mIntervalEndQuery.matches(row, NO_OPTS));
        } // end of isAtEnd(Instant, long)

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets interval query for a retrieval request for future
         * events, creates the overall efs row query, and returns
         * row query.
         * @param beginQuery beginning interval query.
         * @param endQuery ending interval query.
         */
        /* package */ Query<EfsRow<E>>intervalQuery(final Query<EfsRow<E>> beginQuery,
                                                    final Query<EfsRow<E>> endQuery)
        {
            mIntervalEndQuery = endQuery;

            mRowQuery = and(beginQuery, endQuery, mUserQuery);

            return (mRowQuery);
        } // end of intervalQuery(Query, Query)

        /**
         * Returns {@code true} if retrieval request was not
         * previously canceled and so is now completed due to all
         * requested rows being retrieved.
         * @param timestamp time retrieval reached completion.
         * @return {@code true} if retrieval request successfully
         * reached completion.
         */
        /* package */ boolean markCompleted(final Instant timestamp)
        {
            final boolean retcode =
                close(timestamp,
                      CompletionType.RETRIEVAL_COMPLETED);

            // Is this retrieval now completed?
            if (retcode)
            {
                // Yes. Remove from agent's list.
                mEventFile.retrievalComplete(this);
            }

            return (retcode);
        } // end of markCompleted(Instant)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Returns {@code true} if given row and encapsulated
         * event satisfies both the interval beginning, interval
         * ending, and user queries; otherwise returns
         * {@code false}.
         * @param row compare this efs table row against both
         * interval and user queries.
         * @return {@code true} if row and event satisfy interval
         * and user event queries.
         */
        /* package */ boolean matches(final EfsRow<E> row)
        {
            return (mRowQuery.matches(row, NO_OPTS));
        } // end of matches(EfsRow)

        /**
         * Dispatches event row to retrieval agent.
         * @param row dispatch this row to agent.
         */
        /* package */ void dispatch(final EfsRow<E> row)
        {
            EfsDispatcher.dispatch(mEventCB, row, mAgent);
        } // end of dispatch(EfsRow)

        /**
         * Returns {@code true} if this retrieval was closed and
         * {@code false} if already closed.
         * @param timestamp time retrieval reached completion.
         * @param completionType reason retrieval is being
         * closed.
         * @return {@code true} if retrieval was open and
         * subsequently closed.
         */
        @SuppressWarnings ("unchecked")
        /* package */ boolean close(final Instant timestamp,
                                    final CompletionType completionType)
        {
            final boolean retcode =
                mCompletionFlag.compareAndSet(false, true);

            // Is this retrieval request completed?
            if (retcode)
            {
                sLogger.info("Retrieval {}: completed, {}",
                             mId,
                             completionType);

                // No, mark this request as completed and have
                // the event file cancel this request.
                mCompletionType.set(completionType);

                // Remove this retrieval from the agent's list.
                mEventFile.retrievalComplete(this);

                // Let the agent know this retrieval is
                // completed.
                EfsDispatcher.dispatch(
                    mCompletionCB,
                    new RetrievalCompleteEvent(
                        completionType, timestamp, this),
                    mAgent);
            }

            return (retcode);
        } // end of close(Instant, CompletionType)
    } // end of class Retrieval
} // end of class EfsFileConnection
