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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.ThreadSafe;
import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.MultiValueAttribute;
import com.googlecode.cqengine.attribute.SimpleAttribute;
import com.googlecode.cqengine.index.hash.HashIndex;
import com.googlecode.cqengine.index.unique.UniqueIndex;
import com.googlecode.cqengine.query.Query;
import static com.googlecode.cqengine.query.QueryFactory.ascending;
import static com.googlecode.cqengine.query.QueryFactory.greaterThan;
import static com.googlecode.cqengine.query.QueryFactory.greaterThanOrEqualTo;
import static com.googlecode.cqengine.query.QueryFactory.in;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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
 * In-memory event file for a single event type + topic.
 *
 * <p>
 * An EfsFile stores events (instances of
 * {@link org.efs.event.IEfsEvent}) and assigns each posted
 * event:
 * </p>
 * <ul>
 *   <li>
 *     a publish timestamp (an {@link java.time.Instant} obtained
 *     from the system clock), and
 *   </li>
 *   <li>
 *     a monotonic, continuous row index (a long) that is unique
 *     within the file.
 *   </li>
 * </ul>
 * <p>
 * The file keeps events in a CQEngine indexed collection so
 * callers can query historical events (by timestamp or row
 * index) and optionally subscribe to future events that match an
 * interval and a CQEngine {@code Query}.
 * </p>
 *
 * <h2>Threading and postRow</h2>
 * <p>
 * {@code EfsFile} is thread-safe. All public operations that
 * mutate or query file state are handed off to the file's
 * associated dispatcher (see
 * {@link org.efs.dispatcher.EfsDispatcher}) so that event
 * delivery and request completion happen on the dispatcher's
 * thread. This provides a virtual single-threaded processing
 * model for agents interacting with a particular
 * {@code EfsFile}.
 * </p>
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li>
 *     Create with
 *     {@link #createEventFile(org.efs.event.EfsTopicKey, String)}.
 *     The dispatcher name must correspond to a dispatcher
 *     created/started by your application.
 *   </li>
 *   <li>
 *     Obtain a connection using
 *     {@link #connect(AccessMode, org.efs.dispatcher.IEfsAgent)}.
 *     The agent must be registered with the dispatcher before
 *     calling {@code connect}.
 *   </li>
 *   <li>
 *     Use the returned {@link EfsFileConnection} to add events
 *     or to
 *     {@link EfsFileConnection#retrieve(org.efs.io.EfsInterval, com.googlecode.cqengine.query.Query, java.util.function.Consumer, java.util.function.Consumer)}
 *     historical and/or future events.
 *   </li>
 *   <li>
 *     Close
 *     {@link EfsFileConnection}s when finished; doClose the file with {@link #close()}
 *     to remove it from the global registry.
 *   </li>
 * </ul>
 *
 * <h2>Ordering and guarantees</h2>
 * <ul>
 *   <li>
 *     Row index is strictly monotonic and unique and can be used
 *     as an ordered key for retrieval.
 *   </li>
 *   <li>
 *     Publish timestamp is non-decreasing (monotonic as events
 *     are posted) but is not guaranteed unique.
 *   </li>
 *   <li>
 *     Retrieval requests may deliver matching historical rows
 *     immediately and, optionally, continue delivering matching
 *     future rows until the retrieval ends or the request is
 *     cancelled.
 *   </li>
 * </ul>
 *
 * <h2>Querying</h2>
 * <p>
 * EfsFile exposes a small set of CQEngine attributes and
 * provides event-field attributes:
 * </p>
 * <ul>
 *   <li>
 *     {@link #ROW_INDEX_ATTRIBUTE} — row index attribute name.
 *   </li>
 *   <li>
 *     {@link #PUBLISH_TIMESTAMP_ATTRIBUTE} — publish timestamp
 *     attribute name.
 *   </li>
 *   <li>
 *     Use
 *     {@link #attribute(String)} to obtain a CQEngine {@code Attribute<EfsRow<E>,?>}
 *     for event fields extracted from the event class layout;
 *     that attribute can be used in CQEngine queries.
 *   </li>
 * </ul>
 * <p>
 * A CQEngine {@code Query<EfsRow<E>>} is required when calling
 * {@link org.efs.io.EfsFileConnection#retrieve(org.efs.io.EfsInterval, com.googlecode.cqengine.query.Query, java.util.function.Consumer, java.util.function.Consumer) EfsFileConnection.retrieve(...)}.
 * For simple retrieval of all rows in an interval, combine
 * CQEngine interval queries (by index or timestamp) with a user
 * query (or a query that matches all rows).
 * </p>
 *
 * <h2>Errors and exceptional states</h2>
 * <ul>
 *   <li>
 *     Passing a {@code null} topic key, dispatcher, access mode,
 *     or agent will throw {@link NullPointerException}.
 *   </li>
 *   <li>
 *     Creating a file for a key that already exists will throw
 *     {@link IllegalStateException}.
 *   </li>
 *   <li>
 *     Connecting with an agent that is not registered with a
 *     dispatcher will throw {@link IllegalStateException}.
 *   </li>
 *   <li>
 *     Attempting to add events on a connection opened without
 *     write permission or retrieving on a read-only connection
 *     will throw {@link IllegalStateException}.
 *   </li>
 * </ul>
 *
 * <h2>Example (simplified)</h2>
 * <pre>{@code // Create or obtain a dispatcher named "mainDispatcher" before calling createEventFile.
// (See org.efs.dispatcher.EfsDispatcher.Builder for creating dispatchers.)

// Define the topic key for an event type
EfsTopicKey<MyEvent> key = EfsTopicKey.getKey(MyEvent.class, "myTopic");

// Create the file (registers it with the given dispatcher)
EfsFile<MyEvent> file = EfsFile.createEventFile(key, "mainDispatcher");

// Assuming 'agent' is an IEfsAgent instance that has been registered with the same dispatcher:
EfsFileConnection<MyEvent> conn = file.connect(AccessMode.READ_WRITE, agent);

// Append an event (returns the publish Instant)
Instant publishedAt = conn.add(new MyEvent(...));

// Build an interval (example: from 1 hour ago until now, inclusive)
EfsInterval interval = EfsInterval.builder()
    .beginning(EfsDurationEndpoint.builder().timeOffset(Duration.ofHours(-1), EfsIntervalEndpoint.Clusivity.INCLUSIVE).build())
    .ending(EfsDurationEndpoint.builder().now(EfsIntervalEndpoint.Clusivity.INCLUSIVE).build())
    .build();

// Create a CQEngine query that matches the events you want (user-provided).
// For example, you can use attributes returned by file.attribute("fieldName").
Query<EfsRow<MyEvent>> query = /* create your CQEngine query for EfsRow<MyEvent> * /;

// Retrieve matching events and subscribe to completion callback
EfsFileConnection.Retrieval<MyEvent> r =
    conn.retrieve(interval,
                  query,
                  row -> {
                      // event callback: row.getEvent() or row.getRowIndex()/getPublishTimestamp()
                  },
                  completionEvent -> {
                      // completion callback: check completionEvent.completionType()
                  });

// When finished, cancel retrieval (AutoCloseable)
r.doClose();
conn.doClose();
file.doClose();
}</pre>
 *
 * <p>
 * Note: the example is illustrative and omits dispatcher and
 * agent setup details. See the dispatcher package documentation
 * for how to create and register dispatchers and agents.
 * </p>
 * <p style="background-color:#ffcccc;padding:5px;border: 2px solid darkred;">
 * TODO: This class is in-progress.
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
         * compatible with this connect mode and {@code false}
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

    /**
     * Empty immutable set of user defined tags.
     */
    public static final Set<Integer> NO_TAGS =
        ImmutableSet.of();

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
     * {@link EfsRow#getTags()} attribute named {@value}.
     */
    public static final String TAGS_ATTRIBUTE = "tags";

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
    private static final AtomicReference<Clock> sClock =
        new AtomicReference<>(Clock.systemUTC());

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
     * {@link EfsRow#getTags()} user-defined tabs index.
     */
    private final Attribute<EfsRow<E>, Integer> mTagIndex;

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
    private final Map<Integer, Retrieval<E>> mActiveRequests;

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
        mActiveRequests = new HashMap<>();

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
        mTagIndex =
            new MultiValueAttribute<EfsRow<E>, Integer>(TAGS_ATTRIBUTE)
            {
                @Override
                public Iterable<Integer> getValues(final EfsRow<E> row,
                                                   final QueryOptions qo)
                {
                    return (row.getTags());
                } // end of getValues(EfsRow, QueryOptions)
            };
        mOrderByOpts =
            queryOptions(orderBy(ascending(mRowIndex)));

        // Index row and publish timestamp attributes.
        mTable.addIndex(UniqueIndex.onAttribute(mRowIndex));
        mTable.addIndex(HashIndex.onAttribute(mPubTimeIndex));
        mTable.addIndex(HashIndex.onAttribute(mTagIndex));

        mNextRowIndex = new AtomicLong();
        mLatestRow =
            new EfsRow<>(
                (sClock.get()).instant(), 0, NO_TAGS, null);
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

        // If this field does not have an associated attribute,
        // then returns null.
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
        return ((sClock.get()).instant());
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
        return (sClock.get());
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
     * <p style="background-color:#ffcccc;padding:5px;border: 2px solid darkred;">
     * Note: setting this system clock affects all active
     * {@code EfsFile} instances. This method is primarily
     * provided for unit tests which need to control wall clock
     * time for consistent test results. If used, that means that
     * tests updating the system clock may <em>not</em> execute
     * in parallel as these tests will impact the other and
     * prevent consistent test results. Therefore when using this
     * method in testing, parallel execution is discouraged.
     * </p>
     * <p>
     * This method may also be used to replace the default
     * {@code Clock.systemUTC()} with a proprietary clock. This
     * should be during application start up.
     * </p>
     * @param clock replaces current system clock.
     * @return replaced system clock.
     * @throws NullPointerException
     * if {@code clock} is {@code null}.
     */
    @VisibleForTesting
    public static Clock setSystemClock(final Clock clock)
    {
        Objects.requireNonNull(clock, NULL_CLOCK);

        return (sClock.getAndSet(clock));
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

        synchronized (mConnections)
        {
            retval =
                new EfsFileConnection<>(this, agent, accessMode);
            mConnections.add(retval);
        }

        return (retval);
    } // end of connect(AccessMode, IEfsAgent)

    /**
     * Closes this efs event file asynchronously on dispatcher
     * thread. All stored events are lost. Does nothing if
     * already closed.
     */
    public void close()
    {
        synchronized (sFiles)
        {
            // 1. Is this file open?
            if (mOpenFlag.compareAndSet(true, false))
            {
                // 2. Yes. Remove file from global map.
                sFiles.remove(mTopicKey);

                // 3. Dispatch asynchronous doClose handler.
                EfsDispatcher.dispatch(
                    this::onClose,
                    new CloseInternalEvent(), this);
            }
        }
    } // end of doClose()

    /**
     * Returns a newly created efs event file for given
     * type+topic key and assigning the file to the given
     * dispatcher.
     * <p>
     * Example
     * </p>
     * <pre>{@code
     * // 1) Create and start a dispatcher named "fileDispatcher".
     * EfsDispatcher.builder("fileDispatcher")
     *     .threadType(org.efs.dispatcher.config.ThreadType.BLOCKING)
     *     .numThreads(1)
     *     .priority(Thread.NORM_PRIORITY)
     *     .dispatcherType(org.efs.dispatcher.EfsDispatcher.DispatcherType.EFS)
     *     .eventQueueCapacity(128)
     *     .runQueueCapacity(4)
     *     .maxEvents(128)
     *     .build();
     *
     * // 2) Create a topic key for your event type.
     * EfsTopicKey<MyEvent> key = EfsTopicKey.getKey(MyEvent.class, "myTopic");
     *
     * // 3) Create the EfsFile and assign it to the existing dispatcher.
     * //    The dispatcher named "fileDispatcher" must already exist (see step 1).
     * EfsFile<MyEvent> file = EfsFile.createEventFile(key, "fileDispatcher");
     *
     * // 4) Register an agent with the same dispatcher before connecting.
     * //    The agent must be registered with the dispatcher; otherwise connect() will throw.
     * IEfsAgent agent = new MyAgent("myAgent"); // implements IEfsAgent
     * EfsDispatcher.register(agent, "fileDispatcher");
     *
     * // 5) Connect to the file (now allowed because the dispatcher exists and the agent is registered).
     * EfsFileConnection<MyEvent> conn = file.connect(EfsFile.AccessMode.READ_WRITE, agent);
     * }</pre>
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

        // Everything checks out. Clear to create new event file.
        synchronized (sFiles)
        {
            retval =
                new EfsFile<>(key, layout.fields(), attributes);
            EfsDispatcher.register(retval, dispatcher);
            sFiles.put(key, retval);
        }

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
        final EfsFile<E> retval;

        Objects.requireNonNull(key, NULL_TOPIC_KEY);

        synchronized (sFiles)
        {
            if (!sFiles.containsKey(key))
            {
                throw (
                    new IllegalStateException(
                        String.format(NO_SUCH_FILE, key)));
            }

            retval = (EfsFile<E>) sFiles.get(key);
        }

        return (retval);
    } // end of getEventFile(EfsTopicKey)

    /**
     * Handles an internal add-event by creating a new
     * {@link EfsRow} and forwarding it to any active retrievals
     * that match.
     * <p>
     * This method is intended to be invoked on the file's
     * dispatcher thread (via
     * {@link org.efs.dispatcher.EfsDispatcher#dispatch}) and is
     * package-private for that reason. It performs the following
     * steps:
     * </p>
     * <ol>
     *   <li>
     *     If the file is closed, the method returns immediately
     *     (no-op).
     *   </li>
     *   <li>
     *       Extracts the publish timestamp and event from the
     *       supplied {@link AddInternalEvent}.
     *   </li>
     *   <li>
     *     Creates a new {@link EfsRow} using the publish
     *     timestamp and a monotonic row index produced by
     *     {@code mNextRowIndex.getAndIncrement()}, then adds
     *     that row to the internal CQEngine table updating
     *     {@code mLatestRow}.
     *   </li>
     *   <li>
     *     Iterates the list of active
     *     {@link EfsFileConnection.Retrieval} requests:
     *     <ul>
     *       <li>
     *         If a retrieval is still active and its query
     *         matches the new row, the row is dispatched to
     *         agent's retrieval callback.
     *       </li>
     *       <li>
     *         If dispatching to an agent fails with
     *         {@code IllegalStateException} (for example, agent
     *         event queue full), a warning is logged and
     *         processing continues.
     *       </li>
     *       <li>
     *         If a retrieval has reached its end as a result of
     *         this new row, it is removed from
     *         {@code mActiveRequests} and this completion is
     *         reported to agent's retrieval completed callback.
     *       </li>
     *       <li>
     *         Retrievals that have been cancelled by the user
     *         are not removed here; cancellation is handled by
     *         {@link #onCancel}.
     *       </li>
     *     </ul>
     *   </li>
     * </ol>
     * <p>
Note: this method does not throw checked exceptions and
performs minimal per-retrieval exception handling so a
single failing retrieval postRow does not prevent other
retrievals from receiving the row.
</p>
     * @param addEvent internal add event containing publish
     * timestamp and the event to append (must be
     * non-{@code null}).
     *
     * @see AddInternalEvent
     * @see #onRetrieve(RetrievalInternalEvent)
     * @see #onCancel(CancelInternalEvent)
     */
    /* package */ void onAdd(final AddInternalEvent<E> addEvent)
    {
        // Note: since this is a package-private method, caller
        // is guaranteed to pass a non-null addEvent.

        // Is this file open?
        // Note: it is possible that this efs event file was
        // closed *after* addEvent posted to this file's event
        // queue and *before* addEvent is processed.
        if (!mOpenFlag.get())
        {
            // No. Do nothing.
            return;
        }

        final Instant pubTime = addEvent.publishTimestamp();
        final Set<Integer> tags = addEvent.tags();
        final EfsRow<E> row =
            new EfsRow<>(pubTime,
                         mNextRowIndex.getAndIncrement(),
                         tags,
                         addEvent.event());

        mTable.add(row);
        mLatestRow = row;

        // Forward event row to agents whose request matches this
        // row.
        final Iterator<Retrieval<E>> rIt =
            (mActiveRequests.values()).iterator();
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
                    request.postRow(row);
                }

                // Has the retrieval request reached its end?
                if (request.isAtEnd(row))
                {
                    // Yes. Mark request as completed, telling
                    // agent about retrieval completion.
                    // Note: this removal is thread safe due to
                    // it being performed in the file's
                    // dispatcher thread.
                    request.doClose(
                        pubTime,
                        CompletionType.RETRIEVAL_COMPLETED);
                }
            }
            // No, request was canceled by user.
            // Do not remove from active request lists. That
            // will be done in onCancel method.
        }
    } // end of onAdd(Instant, E)

    /**
     * Handles an internal retrieval request by evaluating the
     * retrieval's interval and query against the file's stored
     * rows and (optionally) registering the retrieval for future
     * matching rows.
     * <p>
     * This method is intended to be invoked on the file's
     * dispatcher thread (via
     * {@link EfsDispatcher#dispatch}). It
     * performs the following operations:
     * </p>
     * <ol>
     *   <li>
     *     If the file is closed, the retrieval is immediately
     *     completed with
     *     {@link RetrievalCompleteEvent.CompletionType#FILE_CLOSED}
     *     and the method returns.
     *   </li>
     *   <li>
     *     If the retrieval was canceled before hand-off to the
     *     dispatcher thread
     *     ({@link EfsFileConnection.Retrieval#isCompleted()}),
     *     the method does nothing.
     *   </li>
     *   <li>
     *     If the file currently contains no rows:
     *     <ul>
     *       <li>
     *         If the retrieval interval represents a future
     *         interval (see
     *         {@link EfsInterval#isFutureInterval(Instant)}),
     *         the retrieval's CQEngine query is prepared and the
     *         retrieval is stored in {@code mActiveRequests} so
     *         it can receive matching future rows.
     *       </li>
     *       <li>
     *         Otherwise, the retrieval is immediately completed
     *         with
     *         {@link RetrievalCompleteEvent.CompletionType#RETRIEVAL_COMPLETED}.
     *       </li>
     *     </ul>
     *   </li>
     *   <li>
     *     When the file contains rows:
     *     <ul>
     *       <li>
     *         Builds a CQEngine row query for the retrieval by
     *         calling {@link #generateRowQuery(Retrieval)} which
     *         composes beginning and ending interval queries
     *         combined (ANDed) with the user's query.
     *       </li>
     *       <li>
     *         Executes query against internal CQEngine table
     *         (ordered by row index) and dispatches each
     *         matching {@link EfsRow} to retrieval's event
        callback using the retrieval's postRow logic.
      </li>
     *       <li>
     *         If the retrieval's interval is not yet at its end
     *         (it also includes future rows), the retrieval is
     *         added to {@code mActiveRequests} so future
     *         inserted rows will be matched and dispatched.
     *       </li>
     *       <li>
     *         If the retrieval reached its end after delivering
     *         historical rows, the retrieval is closed with
     *         {@link RetrievalCompleteEvent.CompletionType#RETRIEVAL_COMPLETED}.
     *       </li>
     *     </ul>
     *   </li>
     * </ol>
     * <p>
     * Implementation notes and side effects:
     * </p>
     * <ul>
     *   <li>
     *     The method uses
     *     {@code mTable.retrieve(rowQuery, mOrderByOpts)} with a
     *     try-with-resources {@link ResultSet} to iterate
     *     results in ascending row index order.
     *   </li>
     *   <li>
    Dispatch to retrievals may log warnings (and silently
    continue) when postRow fails (for example, if an
    agent's event queue is full).
  </li>
     *   <li>
     *     Retrieval registration for future events stores
     *     retrieval in {@code mActiveRequests}; cancelled
     *     retrievals remain until
     *     {@link #onCancel(CancelInternalEvent)} removes them.
     *   </li>
     *   <li>
    All retrieval completion notifications are delivered
    via the retrieval's completion callback (through
    postRow machinery) on the retrieval's associated
    agent.
  </li>
     * </ul>
     * <p>
Because this method runs on the dispatcher's thread it
must not block for long periods; callbacks invoked via
postRow are scheduled onto agent event queues rather than
executed inline here.
</p>
     * @param retrieveEvent nternal event wrapping
     * {@link EfsFileConnection.Retrieval} request to be
     * processed; must not be {@code null}.
     *
     * @see #generateRowQuery(EfsFileConnection.Retrieval)
     * @see EfsFileConnection.Retrieval
     * @see #onCancel(CancelInternalEvent)
     * @see EfsDispatcher#dispatch(Consumer, IEfsEvent, IEfsAgent)
     */
    /* package */ void onRetrieve(final RetrievalInternalEvent<E> retrieveEvent)
    {
        final Instant now = (sClock.get()).instant();
        final Retrieval<E> retrieval = retrieveEvent.request();
        final EfsInterval interval = retrieval.interval();

        // Is this file open?
        if (!mOpenFlag.get())
        {
            // No. Inform agent that retrieval is completed due
            // to event file being closed.
            retrieval.doClose(now, CompletionType.FILE_CLOSED);
        }
        // Was retrieval request canceled during hand-off to
        // dispatcher thread?
        else if (retrieval.isCompleted())
        {
            // Yes, no-op.
        }
        // Is this file empty?
        else if (mNextRowIndex.get() == 0)
        {
            // Yes. Does this retrieval for future events?
            if (interval.isFutureInterval(now))
            {
                // Yes again. Store request away while waiting
                // for those future events.
                generateRowQuery(retrieval);
                mActiveRequests.put(retrieval.id(), retrieval);
            }
            else
            {
                // No. Then this retrieval is completed because
                // there are no historical events to retrieve.
                retrieval.doClose(
                    now, CompletionType.RETRIEVAL_COMPLETED);
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
                    retrieval.postRow(r);
                }
            }

            // Does this request also include future events?
            if (!retrieval.isAtEnd(mLatestRow))
            {
                // Yes. Store request away so it can be matched
                // against those future events.
                mActiveRequests.put(retrieval.id(), retrieval);
            }
            // Request is for past events only. Let the agent
            // know this fact.
            else
            {
                retrieval.doClose(
                    now, CompletionType.RETRIEVAL_COMPLETED);
            }
        }
    } // end of onRetrieve(RetrieveEvent)

    /**
     * TODO
     * @param retrieveEvent contains user-defined event tag used
     * to retrieve events.
     */
    /* package */ void onRetrieve(final TagRetrieveInternalEvent<E> retrieveEvent)
    {
        final Instant now = (sClock.get()).instant();

        // Is this file open?
        if (!mOpenFlag.get())
        {
            // No. Inform agent that retrieval is completed due
            // to event file being closed.
            retrieveEvent.postCompletion(
                now, CompletionType.FILE_CLOSED);
        }
        // Is this file empty?
        else if (mNextRowIndex.get() == 0)
        {
            // Yes. This retrieval is completed because there are
            // no historical events to retrieve.
            retrieveEvent.postCompletion(
                now, CompletionType.RETRIEVAL_COMPLETED);
        }
        else
        {
            // Retrieve events with given user-defined event tag.
            final int tag = retrieveEvent.tag();
            final Query<EfsRow<E>> rowQuery = in(mTagIndex, tag);

            // Retrieve events in ascending row index order.
            try (ResultSet<EfsRow<E>> results =
                     mTable.retrieve(rowQuery, mOrderByOpts))
            {
                // Dispatch matching rows to user.
                for (EfsRow<E> r : results)
                {
                    retrieveEvent.postRow(r);
                }
            }

            retrieveEvent.postCompletion(
                now, CompletionType.RETRIEVAL_COMPLETED);
        }
    } // end of onRetrieve(TagRetrieveInternalEvent)

    /**
     * Removes specified retrieval request from active requests
     * map.
     * @param cancelEvent contains canceled {@code Retrieval}
     * instance.
     */
    /* package */ void onCancel(final CancelInternalEvent<E> cancelEvent)
    {
        final Retrieval<E> r = cancelEvent.request();

        mActiveRequests.remove(r.id());
    } // end of onCancel(Instant, Retrieval)

    /**
     * Removes a now disconnected connection from connections
     * set.
     * @param connection remove this connection from connections
     * set.
     */
    /* package */ void onDisconnect(final EfsFileConnection<E> connection)
    {
        synchronized (mConnections)
        {
            mConnections.remove(connection);
        }
    } // end of onDisconnect(EfsFileConnection)

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
        final List<EfsFileConnection<E>> connections;
        final Instant now = (sClock.get()).instant();

        synchronized (mConnections)
        {
            connections = ImmutableList.copyOf(mConnections);
            mConnections.clear();
        }

        // Clear out collected events.
        mTable.clear();
        mNextRowIndex.set(0L);

        // Report all requests as canceled due file closure.
        for (Retrieval<E> r : mActiveRequests.values())
        {
            r.doClose(now, CompletionType.FILE_CLOSED);
        }

        mActiveRequests.clear();

        // Disconnect all active connections.
        for (EfsFileConnection<E> c : connections)
        {
            c.markClosed();
        }

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
                final Instant endTime =
                    (mLatestRow.getPublishTimestamp())
                        .plus(dep.timeOffset());

                retval =
                    switch (clusivity)
                    {
                        case INCLUSIVE ->
                            lessThanOrEqualTo(
                                mPubTimeIndex, endTime);

                        // EXCLUSIVE
                        default ->
                            lessThan(mPubTimeIndex, endTime);
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
