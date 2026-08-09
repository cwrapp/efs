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
import com.googlecode.cqengine.index.navigable.NavigableIndex;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import java.util.function.Supplier;
import net.sf.eBus.util.Validator;
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
 *     Create with {@link EfsFile.Builder}. A {@code Builder}
 *     instance is acquired from
 *     {@link #builder(EfsTopicKey)}. {@code Builder} requires
 *     only that a dispatcher name is set (since {@code EfsFile}
 *     is itself an agent). This dispatcher name must correspond
 *     to a dispatcher created/started by your application.
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
 * <h2>Ordering and Consistency Guarantees</h2>
 * <ul>
 *   <li>
 *     <strong>Row Index</strong>: Strictly increasing, unique,
 *     and  assigned in the order events are posted. Can be used
 *     as a linearization point.
 *   </li>
 *   <li>
 *     <strong>Publish Timestamp</strong>: Non-decreasing
 *     (monotonic)  but not guaranteed unique. Events posted in
 *     quick succession  may share the same timestamp. When two
 *     rows have the same timestamp, they are ordered by row
 *     index in ascending order.
 *   </li>
 *   <li>
 *     <strong>Query Results</strong>: Always returned in
 *     ascending row index order, ensuring a consistent
 *     linearization across  historical and future retrievals.
 *   </li>
 *   <li>
 *     <strong>Retrieval Continuity</strong>: A retrieval that
 *     spans  both historical and future events will never skip
 *     or duplicate  a row, even if the historical query and
 *     future subscriptions are processed asynchronously.
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
     * Default maximum concurrent {@link EfsFileConnection}s is
     * {@value}. This value may be overridden in
     * {@link Builder#maxConnections(int)}.
     */
    public static final int DEFAULT_MAX_CONNECTIONS = 100;

    /**
     * Default maximum concurrent, active retrievals is {@value}.
     */
    public static final int DEFAULT_MAX_ACTIVE_RETRIEVALS =
        1_000;

    /**
     * Empty immutable set of user defined tags.
     */
    public static final Set<Integer> NO_TAGS =
        ImmutableSet.of();

    /**
     * Default event file connection policy allows all agents
     * to connect using any access mode.
     */
    public static final IConnectionPolicy DEFAULT_CONNECTION_POLICY =
        (agent, accessMode) -> true;

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

    /**
     * Attempt to set maximum concurrent efs event file
     * connection or active retrieval limit to a value &le; zero
     * results in an {@code IllegalArgumentException} with
     * message {@value}.
     */
    public static final String INVALID_LIMIT =
        "limit <= zero";

    /**
     * Attempt to set table initializer to {@code null} results
     * in an {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_INITIALIZER =
        "initializer is null";

    /**
     * Attempt to set table exhaust callback to {@code null}
     * results in an {@code NullPointerException} with message
     * {@value}.
     */
    public static final String NULL_EXHAUST_CB =
        "exhaustCB is null";

    /**
     * Attempt to set table exhaust agent to {@code null}
     * results in an {@code NullPointerException} with message
     * {@value}.
     */
    public static final String NULL_EXHAUST_AGENT =
        "exhaustAgent is null";

    /**
     * Attempt to create an efs event file connection which
     * exceeds maximum concurrent connection allowed results in
     * an {@code IllegalStateException} with message {@value}.
     */
    public static final String CONNECTION_LIMIT_REACHED =
        "connection limit (%,d) exceeded for file %s";

    /**
     * A {@code null} policy results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_POLICY = "policy is null";

    /**
     * Attempt to create an efs event file connection by
     * specified agent and access mode pair without necessary
     * permission results in an {@code IllegalStateException}
     * with message {@value}.
     */
    public static final String ACCESS_DENIED =
        "%s may not connect to %s with %s access mode";

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
     * Default system clock used by efs file instances.
     */
    private static final Clock sClock = Clock.systemUTC();

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
     * Defines which agents, access mode pairs are allowed to
     * access this event file. Defaults to
     * {@link #DEFAULT_CONNECTION_POLICY}.
     */
    private final IConnectionPolicy mConnectionPolicy;

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
     * If not {@code null}, then post newly added event row to
     * this consumer so it can store the row in persistent
     * memory.
     */
    @Nullable
    private final Consumer<EfsRow<E>> mExhaustCB;

    /**
     * Agent associated with {@link #mExhaustCB}.
     */
    @Nullable
    private final IEfsAgent mExhaustAgent;

    /**
     * Method used to forward row to exhaust agent.
     */
    private final Consumer<EfsRow<E>> mForwardExhaust;

    /**
     * Clock used by this efs file instance. Defaults to
     * {@code Clock.systemUTC()}. This value may be changed for
     * testing purposes.
     *
     * @see #setSystemClock(Clock)
     */
    private final AtomicReference<Clock> mClock;

    /**
     * Total number of efs event file connections in place at
     * any one time.
     */
    private final int mMaxConnections;

    /**
     * Total number of event file retrievals active at any
     * one time.
     */
    private final int mMaxActiveRetrievals;

    /**
     * Tracks this event file's performance metrics.
     */
    private final Metrics<E> mMetrics;

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
     * Creates a new efs event file instance based on builder
     * settings. These settings are validated to be correct.
     * @param builder contains event file settings.
     */
    private EfsFile(final Builder<E> builder)
    {
        mTopicKey = builder.mTopicKey;
        mFileName = mTopicKey.toString();
        mFields = builder.mFields;
        mAttributes = builder.mAttributes;
        mOpenFlag = new AtomicBoolean(true);
        mConnectionPolicy = builder.mConnectionPolicy;
        mTable = builder.mTable;
        mConnections = new ArrayList<>();
        mActiveRequests = new HashMap<>();
        mExhaustCB = builder.mExhaustCB;
        mExhaustAgent = builder.mExhaustAgent;
        mForwardExhaust = (mExhaustAgent == null ?
                           this::doNoExhaust :
                           this::doExhaust);
        mClock = new AtomicReference<>(builder.mClock);
        mMetrics =
            new Metrics<>(mTopicKey, (mClock.get()).instant());
        mMaxConnections = builder.mMaxConnections;
        mMaxActiveRetrievals = builder.mMaxActiveRetrievals;

        mRowIndex = builder.mRowIndex;
        mPubTimeIndex = builder.mPubTimeIndex;
        mTagIndex = builder.mTagIndex;
        mOrderByOpts = builder.mOrderByOpts;

        mNextRowIndex = new AtomicLong(builder.mNextRowIndex);
        mLatestRow = builder.mLatestRow;
    } // end of EfsFile(Builder)

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
        return ((mClock.get()).instant());
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
    public Clock getSystemClock()
    {
        return (mClock.get());
    } // end of getSystemClock()

    /**
     * Returns this event file's metrics.
     * @return metrics for this event file.
     */
    public Metrics<E> metrics()
    {
        return (mMetrics);
    } // end of metrics()

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
    public Clock setSystemClock(final Clock clock)
    {
        Objects.requireNonNull(clock, NULL_CLOCK);

        return (mClock.getAndSet(clock));
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
     * if:
     * <ul>
     *   <li>
     *     {@code agent} is not registered with a dispatcher,
     *   </li>
     *   <li>
     *     this file is closed,
     *   </li>
     *   <li>
     *     {@code agent} is not allowed to connect with this
     *     file using {@code accessMode}, or
     *   </li>
     *   <li>
     *     this file's connection limit is at maximum and no
     *     new connections are allowed until an existing
     *     connection closes.
     *   </li>
     * </ul>
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

        // Is agent allowed to connect to this event file using
        // the specified access mode?
        if (!mConnectionPolicy.isAllowed(agent, accessMode))
        {
            // No.
            throw (
                new IllegalStateException(
                    String.format(
                        ACCESS_DENIED,
                        agent.name(),
                        mFileName,
                        accessMode)));
        }

        sLogger.debug(
            "{}: creating connection for agent {}, access mode {}.",
            mTopicKey,
            agent.name(),
            accessMode);

        synchronized (mConnections)
        {
            // Is the connection limit at maximum allowed?
            if (mConnections.size() == mMaxConnections)
            {
                // Yes, can't exceed that value.
                throw (
                    new IllegalStateException(
                        String.format(
                            CONNECTION_LIMIT_REACHED,
                            mMaxConnections,
                            mTopicKey)));
            }

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
                final CloseInternalEvent event =
                    new CloseInternalEvent();

                sLogger.info(
                    "{}: closing event file.", mTopicKey);

                // 2. Yes. Remove file from global map.
                sFiles.remove(mTopicKey);

                // 3. Dispatch asynchronous doClose handler.
                try
                {
                    EfsDispatcher.dispatch(
                        this::onClose, event, this);
                }
                catch (Exception jex)
                {
                    sLogger.warn(
                        "{}: failed to dispatch close event, running on this thread.",
                        mTopicKey,
                        jex);

                    mMetrics.incrementDispatchFailure();

                    onClose(event);
                }
            }
        }
    } // end of doClose()

    /**
     * Returns a new {@code EfsFile} builder for given topic key.
     * <p>
     * <strong>Note:</strong> this method does <em>not</em>
     * check if an event file currently exists for given key.
     * This check is performed when {@link Builder#build()} is
     * called.
     * </p>
     * <p>
     * Please read {@link Builder} class documentation required
     * building event files.
     * </p>
     * @param <E> efs event type.
     * @param key event file topic key.
     * @return efs event file builder.
     * @throws NullPointerException
     * if {@code key} is {@code null}.
     *
     * @see #getEventFile(EfsTopicKey)
     */
    public static <E extends IEfsEvent>  Builder<E> builder(final EfsTopicKey<E> key)
    {
        Objects.requireNonNull(key, NULL_TOPIC_KEY);

        return (new Builder<>(key));
    } // end of builder(EfsTopicKey<>)

    /**
     * Returns a previously created efs event file.
     * @param <E> efs event type.
     * @param key efs event class and topic key.
     * @return efs event file.
     * @throws IllegalStateException
     * if there is no event file for {@code key}.
     *
     * @see #builder(EfsTopicKey)
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

        // NOTE: there is *no* need to check if this event file
        // is open prior to performing add.
        // Why?
        // Consider this scenario: a close event is posted to
        // this event file's event queue immediately followed
        // by an add event. When this event file closes, it
        // deregisters from its EfsDispatcher. This means the add
        // event will never be delivered to this event file.
        // Ergo: if we are processing a event, that means this
        //       event file is open by definition.

        final Instant pubTime = addEvent.publishTimestamp();
        final Set<Integer> tags = addEvent.tags();
        final EfsRow<E> row =
            EfsRow.createRow(pubTime,
                             mNextRowIndex.getAndIncrement(),
                             tags,
                             addEvent.event());

        sLogger.debug("{}: adding row {}.", mTopicKey, row);

        mTable.add(row);
        mLatestRow = row;
        mMetrics.incrementEventAdd();

        // Forward event row to agents whose request matches this
        // row.
        final Iterator<Retrieval<E>> rIt =
            (mActiveRequests.values()).iterator();
        Retrieval<E> request;

        while (rIt.hasNext())
        {
            request = rIt.next();

            // Is this request still active?
            if (request.isCompleted())
            {
                // That is strange. It should have been removed
                // from the list before this. Remove it now.
                sLogger.warn(
                    "{}: removing defunct retrieval ({}).",
                    mTopicKey,
                    request);

                rIt.remove();
            }
            else
            {
                // Yes, request is active.
                // Does this row satisfy the request?
                if (request.matches(row))
                {
                    // Yes. Forward row to agent.
                    request.postRow(row, mMetrics);
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
                    mMetrics.retrievalCompleted();
                }
            }
            // No, request was canceled by user.
            // Do not remove from active request lists. That
            // will be done in onCancel method.
        }

        // Dispatch event row to exhaust agent.
        mForwardExhaust.accept(row);
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
     * @param retrieveEvent internal event wrapping
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
        final Instant now = (mClock.get()).instant();
        final Retrieval<E> retrieval = retrieveEvent.request();
        final EfsInterval interval = retrieval.interval();

        // NOTE: there is *no* need to check if this event file
        // is open prior to performing retrieval.
        // Why?
        // Consider this scenario: a close event is posted to
        // this event file's event queue immediately followed
        // by a retrieval event. When this event file closes,
        // it deregisters from its EfsDispatcher. This means the
        // retrieval event will never be delivered to this event
        // file.
        // Ergo: if we are processing a retrieval event, that
        //       means this event file is open by definition.

        // Was retrieval request canceled during hand-off to
        // dispatcher thread?
        if (!retrieval.isCompleted())
        {
            // No. Perform the retrieval.
            doRetrieve(now, retrieval, interval);
        }
        // Yes, request is cancled. Nothing else to do.
    } // end of onRetrieve(RetrieveEvent)

    /**
     * Handles a tag-based retrieval request for this file.
     * <p>
     * If the file has no rows, the request is completed
     * immediately with a retrieval-completed status. Otherwise,
     * the method looks up rows whose tags include the tag
     * carried by the request, dispatches the matching rows to
     * the retrieval event in ascending row-index order, and then
     * completes the request.
     * </p>
     * @param retrieveEvent contains the user-defined tag used to
     * select matching events and the callbacks used to deliver
     * those rows and completion notification.
     */
    /* package */ void onRetrieve(final TagRetrieveInternalEvent<E> retrieveEvent)
    {
        final Instant now = (mClock.get()).instant();

        // NOTE: there is *no* need to check if this event file
        // is open prior to performing retrieval.
        // Why?
        // Consider this scenario: a close event is posted to
        // this event file's event queue immediately followed
        // by a retrieval event. When this event file closes,
        // it deregisters from its EfsDispatcher. This means the
        // retrieval event will never be delivered to this event
        // file.
        // Ergo: if we are processing a retrieval event, that
        //       means this event file is open by definition.

        // Is this file empty?
        if (mNextRowIndex.get() == 0)
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
            EfsRow<E> row = null;

            // Retrieve events in ascending row index order.
            try (ResultSet<EfsRow<E>> results =
                     mTable.retrieve(rowQuery, mOrderByOpts))
            {
                // Dispatch matching rows to user.
                for (EfsRow<E> r : results)
                {
                    row = r;
                    retrieveEvent.postRow(r);
                }
            }
            catch (Exception jex)
            {
                // Row posting failure means that target agent's
                // event queue is full, therefore there is no
                // reason to continue posting rows to the agent.
                sLogger.warn(
                    "{}: attempt to post row {} to agent {} failed; event queue full; retrieval terminated.",
                    mTopicKey,
                    row,
                    (retrieveEvent.agent()).name(),
                    jex);
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
    @VisibleForTesting
    /* package */ void onClose(final CloseInternalEvent event)
    {
        final List<EfsFileConnection<E>> connections;
        final Instant now = (mClock.get()).instant();

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
     * Performs actual work of matching retrieval against past
     * and future events based on the given interval.
     * @param currentTime time retrieval was posted.
     * @param retrieval event retrieval.
     * @param interval retrieve events over this interval.
     */
    private void doRetrieve(final Instant currentTime,
                            final Retrieval<E> retrieval,
                            final EfsInterval interval)
    {
        sLogger.debug(
            "{}: retrieving events {}.",
            mTopicKey,
            retrieval);

        mMetrics.retrievalStarted();

        if (mNextRowIndex.get() == 0)
        {
            // Yes. Does this retrieval for future events?
            if (interval.isFutureInterval(mNextRowIndex.get(),
                                          currentTime))
            {
                // Yes again. Store request away while waiting
                // for those future events.
                generateRowQuery(retrieval);
                storeRetrieval(currentTime, retrieval);
            }
            else
            {
                // No. Then this retrieval is completed because
                // there are no historical events to retrieve.
                retrieval.doClose(
                    currentTime,
                    CompletionType.RETRIEVAL_COMPLETED);
                mMetrics.retrievalCompleted();
            }
        }
        else
        {
            // No, request is still active. Generate CQEngine
            // query based on request interval and current time
            // and row index.
            final Query<EfsRow<E>> rowQuery =
                generateRowQuery(retrieval);
            EfsRow<E> row = null;

            // Retrieve events as per request. If request is for
            // future events, then store retrieval.
            try (ResultSet<EfsRow<E>> results =
                     mTable.retrieve(rowQuery, mOrderByOpts))
            {
                // Dispatch matching rows to user.
                for (EfsRow<E> r : results)
                {
                    row = r;
                    retrieval.postRow(r, mMetrics);
                }
            }
            catch (Exception jex)
            {
                // Row posting failure means that target agent's
                // event queue is full, therefore there is no
                // reason to continue posting rows to the agent.
                sLogger.warn(
                    "{}: attempt to post row {} to agent {} failed; event queue full; retrieval terminated.",
                    mTopicKey,
                    row,
                    (retrieval.agent()).name(),
                    jex);
            }

            // Does this request also include future events?
            if (!retrieval.isAtEnd(mLatestRow))
            {
                storeRetrieval(currentTime, retrieval);
            }
            // Request is for past events only. Let the agent
            // know this fact.
            else
            {
                retrieval.doClose(
                    currentTime,
                    CompletionType.RETRIEVAL_COMPLETED);
                mMetrics.retrievalCompleted();
            }
        }
    } // end of doRetrieve(Instant, Retrieval, EfsInterval)

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

            case FIXED_INDEX:
                final EfsIndexFixedEndpoint fep =
                    (EfsIndexFixedEndpoint) beginning;

                retval =
                    switch (clusivity)
                    {
                        case INCLUSIVE ->
                            greaterThanOrEqualTo(
                                mRowIndex, fep.fixedIndex());

                        // EXCLUSIVE
                        default ->
                            greaterThan(
                                mRowIndex, fep.fixedIndex());
                    };
                break;

            // INDEX_OFFSET
            default:
                final EfsIndexOffsetEndpoint iep =
                    (EfsIndexOffsetEndpoint) beginning;
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

            case FIXED_INDEX:
                final EfsIndexFixedEndpoint fep =
                    (EfsIndexFixedEndpoint) ending;

                retval =
                    switch (clusivity)
                    {
                        case INCLUSIVE ->
                            lessThanOrEqualTo(
                                mRowIndex, fep.fixedIndex());

                        // EXCLUSIVE
                        default ->
                            lessThan(
                                mRowIndex, fep.fixedIndex());
                    };
                break;

            // INDEX_OFFSET
            default:
                final EfsIndexOffsetEndpoint iep =
                    (EfsIndexOffsetEndpoint) ending;
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

    /**
     * Stores given retrieval in active requests map after
     * verifying that maximum allowed concurrent retrieval
     * requests is not exceeded. If exceeded, then request is
     * closed.
     * @param currentTime retrieval placement timestamp.
     * @param retrieval retrieval request.
     */
    private void storeRetrieval(final Instant currentTime,
                                final Retrieval<E> retrieval)
    {
        // Has retrieval limit been reached?
        if (mActiveRequests.size() == mMaxActiveRetrievals)
        {
            // Yes, cannot add another retrieval.
            retrieval.doClose(currentTime,
                              CompletionType.RESOURCE_EXHAUSTED);
            mMetrics.retrievalCompleted();

        }
        else
        {
            sLogger.debug(
                "{}: storing retrieval {} for future events.",
                mTopicKey,
                retrieval);

            // Yes. Store request away so it can be matched
            // against those future events.
            mActiveRequests.put(retrieval.id(), retrieval);
        }
    } // end of storeRetrieval(Instant, Retrieval)

    /**
     * There is no exhaust agent configured, so do nothing.
     * @param row exhaust this row.
     */
    private void doNoExhaust(final EfsRow<E> row)
    {}

    /**
     * Forwards row to exhaust agent for persistent storage.
     * @param row write this row to persistent store.
     */
    private void doExhaust(final EfsRow<E> row)
    {
        try
        {
            EfsDispatcher.dispatch(
                mExhaustCB, row, mExhaustAgent);
        }
        catch (Exception jex)
        {
            sLogger.warn(
                "{}: failed to dispatch event to exhaust agent {}.",
                mTopicKey,
                mExhaustAgent.name(),
                jex);

            mMetrics.incrementDispatchFailure();
        }
    } // end of doExhaust(EfsRow)

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Fluent builder for creating and, optionally, populating an
     * {@link EfsFile}.
     * <p>
     * Use {@link EfsFile#builder(EfsTopicKey)} to obtain a
     * builder for a specific event type and topic, configure the
     * file's dispatcher and limits, and then call
     * {@link #build()} to create the file. The builder also sets
     * up the CQEngine indexes required for row retrieval and can
     * preload the table from a supplied initializer when the file
     * represents persisted history.
     * </p>
     * <p>
     * Typical usage is:
     * </p>
     * <pre>{@code
     * EfsFile<MyEvent> file =
     *     EfsFile.<MyEvent>builder(key)
     *         .dispatcher("mainDispatcher")
     *         .maxConnections(50)
     *         .maxRetrievals(250)
     *         .build();
     * }</pre>
     * <p>
     * Builders may also be used to attach an exhaust callback for
     * persistence, provide an initialization supplier for existing
     * rows, or override the clock for testing.
     * </p>
     *
     * @param <E> efs event type.
     */
    public static final class Builder<E extends IEfsEvent>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Unique topic key defining this efs event file layout
         * and topic.
         */
        private final EfsTopicKey<E> mTopicKey;

        /**
         * Event file table indexed by row index.
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
         * Connection policy defining which agents using which
         * access modes may connect to event file. Defaults to
         * {@link #DEFAULT_CONNECTION_POLICY}.
         */
        private IConnectionPolicy mConnectionPolicy;

        /**
         * Event file dispatcher. This data member must be
         * set.
         */
        private String mDispatcher;

        /**
         * Total number of efs event file connections in place at
         * any one time.
         */
        private int mMaxConnections;

        /**
         * Total number of event file retrievals active at any
         * one time.
         */
        private int mMaxActiveRetrievals;

        /**
         * If not {@code null}, then used to initialize efs event
         * file rows; otherwise an empty event file is created.
         */
        @Nullable private Supplier<Iterator<EfsRow<E>>> mInitializer;

        /**
         * If not {@code null}, then newly added rows are
         * forwarded to this consumer callback for persistence.
         *
         * @see #mExhaustAgent
         */
        @Nullable private Consumer<EfsRow<E>> mExhaustCB;

        /**
         * Agent responsible for persisting newly added rows.
         *
         * @see #mExhaustCB
         */
        @Nullable private IEfsAgent mExhaustAgent;

        /**
         * Index used for next row added to table. This value
         * should be used and then incremented.
         */
        private long mNextRowIndex;

        /**
         * Clock used to acquire current instant. Initialized to
         * {@link #sClock}. Should be overridden only for testing
         * purposes.
         */
        private Clock mClock;

        /**
         * The latest row in the efs event table.
         */
        private EfsRow<E> mLatestRow;

        /**
         * Event field names in lexicographic sorted order.
         */
        private SortedSet<String> mFields;

        /**
         * Event field attributes generated from field
         * {@code CQAttribute} annotations.
         */
        private Map<String, Attribute<EfsRow<E>, ?>> mAttributes;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates a new {@link EfsFile} builder for given
         * event type + topic key. Sets data members to default
         * values.
         * @param key defines file event type and topic.
         */
        private Builder(final EfsTopicKey<E> key)
        {
            mTopicKey = key;
            mTable = new ConcurrentIndexedCollection<>();

            mConnectionPolicy = DEFAULT_CONNECTION_POLICY;
            mMaxConnections = DEFAULT_MAX_CONNECTIONS;
            mMaxActiveRetrievals = DEFAULT_MAX_ACTIVE_RETRIEVALS;
            mNextRowIndex = 0L;
            mClock = sClock;

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
            mTable.addIndex(
                NavigableIndex.onAttribute(mPubTimeIndex));
            mTable.addIndex(HashIndex.onAttribute(mTagIndex));
        } // end of Builder(EfsTopicKey)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets efs event file's connection policy.
         * @param policy connection policy defining which agents
         * may connect to event file using which access modes.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code policy} is {@code null}.
         */
        public Builder<E> connectionPolicy(final IConnectionPolicy policy)
        {
            mConnectionPolicy =
                Objects.requireNonNull(policy, NULL_POLICY);

            return (this);
        } // end of connectionPolicy(IConnectionPolicy)

        /**
         * Sets efs event file's dispatcher.
         * @param dispatcher register event file with this
         * dispatcher.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code dispatcher} is either {@code null}, an empty
         * string, or blank or if there is no such
         * {@link EfsDispatcher} named {@code dispatcher}.
         */
        public Builder<E> dispatcher(final String dispatcher)
        {
            if (Strings.isNullOrEmpty(dispatcher) ||
                dispatcher.isBlank())
            {
                throw (
                    new IllegalArgumentException(
                        INVALID_DISPATCHER));
            }

            if (!EfsDispatcher.isDispatcher(dispatcher))
            {
                throw (
                    new IllegalArgumentException(
                        String.format(
                            UNKNOWN_DISPATCHER,
                            dispatcher)));
            }

            mDispatcher = dispatcher;

            return (this);
        } // end of dispatcher(String)

        /**
         * Sets maximum concurrent connection limit to given
         * value.
         * @param limit maximum concurrent connection limit.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code limit} &le; zero.
         */
        public Builder<E> maxConnections(final int limit)
        {
            if (limit <= 0)
            {
                throw (
                    new IllegalArgumentException(INVALID_LIMIT));
            }

            mMaxConnections = limit;

            return (this);
        } // end of maxConnections(int)

        /**
         * Sets maximum concurrent, active retrieval limit to
         * given value.
         * @param limit maximum concurrent, active retrievals
         * limit.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code limit} &le; zero.
         */
        public Builder<E> maxRetrievals(final int limit)
        {
            if (limit <= 0)
            {
                throw (
                    new IllegalArgumentException(INVALID_LIMIT));
            }

            mMaxActiveRetrievals = limit;

            return (this);
        } // end of maxRetrievals(int)

        /**
         * Sets efs event file initializer. This
         * supplier provides an event row {@code Iterator} used
         * to place initial rows into the event file starting
         * with row zero and up to the latest row.
         * <p>
         * The initializer provides an {@code Iterator} which,
         * in turn, returns {@link EfsRow}s in row index
         * ascending order. These indices are required to be
         * sequential and ascending.
         * </p>
         * @param initializer initializes efs event file rows.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code initializer} is {@code null}.
         */
        public Builder<E> tableInitializer(final Supplier<Iterator<EfsRow<E>>> initializer)
        {
            mInitializer =
                Objects.requireNonNull(
                    initializer, NULL_INITIALIZER);

            return (this);
        } // end of tableInitializer(Supplier<>)

        /**
         * Sets efs event file exhaust callback and agent to
         * given values. This pair is used to exhaust newly
         * added event rows to persistent store.
         * @param exhaustCB method used to persist given row.
         * @param exhaustAgent agent performing event row
         * persistence.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if either {@code exhaustCB} or {@code exhaustAgent} is
         * {@code null}.
         */
        public Builder<E> tableExhaust(final Consumer<EfsRow<E>> exhaustCB,
                                       final IEfsAgent exhaustAgent)
        {
            Objects.requireNonNull(exhaustCB, NULL_EXHAUST_CB);
            Objects.requireNonNull(
                exhaustAgent, NULL_EXHAUST_AGENT);

            mExhaustCB = exhaustCB;
            mExhaustAgent = exhaustAgent;

            return (this);
        } // end of tableExhaust(Consumer<>, IEfsAgent)

        /**
         * Sets clock used by efs event file. Provided for unit
         * testing purposes only.
         * @param clock event file clock providing current
         * {@code Instant}.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code clock} is {@code null}.
         */
        @VisibleForTesting
        public Builder<E> clock(final Clock clock)
        {
            mClock = Objects.requireNonNull(clock, NULL_CLOCK);

            return (this);
        } // end of clock(Clock)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         *
         * <p>
         * <strong>Note:</strong> this method is synchronous.
         * If an initializer is set, then this method call
         * blocks until initialization completes. This could be
         * a lengthy process depending on how event row
         * retrieval from persistent store is implemented.
         * This method also synchronizes on the global efs event
         * file map, so building event files is done one at a
         * time in arrival order. Therefore, it is
         * <em>strongly</em> recommended that efs event file
         * creation is performed on application start.
         * </p>
         * @return new {@code EfsFile} instance.
         * @throws IllegalStateException
         * if event file for topic key already exists.
         * @throws EfsFileInitializationException
         * if efs event file initialization fails due to:
         * <ul>
         *   <li>
         *     returned row is {@code null},
         *   </li>
         *   <li>
         *     returned row has index &lt; latest row index, or
         *   </li>
         *   <li>
         *     returned row has publish timestamp &lt; latest row
         *     publish timestamp.
         *   </li>
         * </ul>
         */
        @SuppressWarnings({"java:S1067", "unchecked"})
        public EfsFile<E> build()
            throws EfsFileInitializationException
        {
            final Validator problems = new Validator();
            final EfsFile<E> retval;

            // Make sure dispatcher is set.
            problems.requireNotNull(mDispatcher, "dispatcher")
                    .throwException(EfsFile.class);

            synchronized (sFiles)
            {
                // Is there an efs event file for this topic key
                // already?
                if (sFiles.containsKey(mTopicKey))
                {
                    throw (
                        new IllegalStateException(
                            String.format(
                                FILE_PREVIOUSLY_CREATED,
                                mTopicKey)));
                }

                // If initializer is provided, then insert
                // initial rows into table.
                if (mInitializer == null)
                {
                    // No initializer means empty event file.
                    // Create a default latest row.
                    mLatestRow =
                        EfsRow.createRow(mClock.instant());
                }
                else
                {
                    initializeEvents();
                }

                final EfsEventLayout<E> layout;

                try
                {
                    layout =
                        EfsEventLayout.getLayout(
                            mTopicKey.eventClass());
                    mFields = layout.fields();
                    mAttributes =
                        CQAttributeGenerator.createAttributeMap(
                            layout);
                }
                catch (Exception jex)
                {
                    throw (
                        new EfsFileInitializationException(
                            String.format(
                                "%s attribute creation failed",
                                mTopicKey),
                            jex));
                }

                // Create the event file, register with
                // dispatcher, and store in map.
                retval = new EfsFile<>(this);
                EfsDispatcher.register(retval, mDispatcher);
                sFiles.put(mTopicKey, retval);
            }

            return (retval);
        } // end of build()

        /**
         * Initializes new efs event file using provided
         * initializer.
         * @throws EfsFileInitializationException
         * if efs event file initialization fails due to:
         * <ul>
         *   <li>
         *     returned row is {@code null},
         *   </li>
         *   <li>
         *     returned row has index &lt; latest row index, or
         *   </li>
         *   <li>
         *     returned row has publish timestamp &lt; latest row
         *     publish timestamp.
         *   </li>
         * </ul>
         */
        private void initializeEvents()
            throws EfsFileInitializationException
        {
            final Iterator<EfsRow<E>> rIt;
            EfsRow<E> row;
            Instant prevPubTime = Instant.MIN;
            long rowIndex;
            Instant rowPubTime;

            sLogger.info("{}: initializing event file.",
                         mTopicKey);

            try
            {
                rIt = mInitializer.get();
            }
            catch (Throwable tex)
            {
                throw (
                    new EfsFileInitializationException(
                        String.format(
                            "%s initialization failed: initializer exception",
                            mTopicKey),
                        tex));
            }

            while (rIt.hasNext())
            {
                row = rIt.next();

                // Was a null row returned?
                if (row == null)
                {
                    throw (
                        new EfsFileInitializationException(
                            String.format(
                                "%s initialization failed: null row returned",
                                mTopicKey)));
                }

                rowIndex = row.getRowIndex();
                rowPubTime = row.getPublishTimestamp();

                // Is row index in ascending order?
                if (rowIndex < mNextRowIndex)
                {
                    // No. Fail initialization.
                    throw (
                        new EfsFileInitializationException(
                            String.format(
                                "%s initialization failed: row index %,d < expected index %,d",
                                mTopicKey,
                                rowIndex,
                                mNextRowIndex)));
                }

                // Is row index in sequential order?
                if (rowIndex > mNextRowIndex)
                {
                    // No. Fail initialization.
                    throw (
                        new EfsFileInitializationException(
                            String.format(
                                "%s initialization failed: row index %,d > expected index %,d",
                                mTopicKey,
                                rowIndex,
                                mNextRowIndex)));
                }

                // Is the row publish timestamp in
                // non-descending order?
                if (rowPubTime.compareTo(prevPubTime) < 0)
                {
                    // No. Fail initialization.
                    throw (
                        new EfsFileInitializationException(
                            String.format(
                                "%s initialization failed: row publish timestamp %s < previous timestamp %s",
                                mTopicKey,
                                rowPubTime,
                                prevPubTime)));
                }

                // All checks out. Add the row.
                mTable.add(row);
                mLatestRow = row;
                mNextRowIndex = (rowIndex + 1);
                prevPubTime = rowPubTime;
            }
        } // end of initializeEvents()
    } // end of class Builder

    /**
     * Run time metrics for an {@link EfsFile} instance. These
     * metrics track:
     * <ul>
     *   <li>
     *     number of events added to the file,
     *   </li>
     *   <li>
     *     number of event retrievals started,
     *   </li>
     *   <li>
     *     number of event retrievals completed,
     *   </li>
     *   <li>
     *     number of event retrievals in progress (retrievals
     *     started - retrievals completed), and
     *   </li>
     *   <li>
     *     event dispatch failures observed.
     *   </li>
     * </ul>
     * <p>
     * Please note that above counts may change while being
     * accessed. Any count retrieval is a snapshot of event
     * file's current state.
     * </p>
     * <p>
     * Metrics are maintained after event file is closed allowing
     * further examination.
     * </p>
     *
     * @param <E> event type managed by owning file
     */
    public static final class Metrics<E extends IEfsEvent>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Constants.
        //

        /**
         * Open timestamp is formatted at {@value}.
         */
        private static final String TIMESTAMP_FORMAT =
            "yyyy-MM-dd HH:mm:ss.SSS";

        /**
         * Format open timestamp in GMT.
         */
        private static final ZoneId GMT = ZoneId.of("GMT");

        //-------------------------------------------------------
        // Statics.
        //

        /**
         * Open timestamp formatter.
         */
        private static final DateTimeFormatter sTimeFormatter =
            DateTimeFormatter.
                ofPattern(TIMESTAMP_FORMAT).withZone(GMT);

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Event file topic key.
         */
        private final EfsTopicKey<E> mTopicKey;

        /**
         * {@code EfsFile} opened at this time.
         */
        private final Instant mOpenTime;

        /**
         * Tracks number of events added to efs event file since
         * opening.
         */
        private final AtomicLong mEventsAdded;

        /**
         * Tracks number of event retrievals started. Subtract
         * {@link #mRetrievalsCompleted} to get number of
         * in-progress retrievals.
         *
         * @see #mRetrievalsCompleted
         */
        private final AtomicLong mRetrievalsStarted;

        /**
         * Tracks number of event retrievals completed. Subtract
         * this value from {@link #mRetrievalsStarted} to get
         * number of in-progress retrievals.
         *
         * @see #mRetrievalsStarted
         */
        private final AtomicLong mRetrievalsCompleted;

        /**
         * Tracks number of calls to
         * {@link EfsDispatcher#dispatch(Consumer, IEfsEvent, IEfsAgent) EfsDispatcher.dispatch}
         * resulting in an exception and dispatch failure. Such
         * failures are most likely due to the target agent's
         * event queue being full.
         */
        private final AtomicLong mDispatchFailures;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /* package */ Metrics(final EfsTopicKey<E> topicKey,
                              final Instant openTime)
        {
            mTopicKey = topicKey;
            mOpenTime = openTime;
            mEventsAdded = new AtomicLong();
            mRetrievalsStarted = new AtomicLong();
            mRetrievalsCompleted = new AtomicLong();
            mDispatchFailures = new AtomicLong();
        } // end of Metrics(EfsTopicKey<>, Instant)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        /**
         * Returns efs event file metrics as text.
         * @return efs event file metrics as text.
         */
        @Override
        public String toString()
        {
            return (
                String.format(
                    "[key=%s, open time=%s, added=%,d, retrievals started=%,d, retrievals completed=%,d, dispatch failures=%,d]",
                    mTopicKey,
                    sTimeFormatter.format(mOpenTime),
                    mEventsAdded.get(),
                    mRetrievalsStarted.get(),
                    mRetrievalsCompleted.get(),
                    mDispatchFailures.get()));
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns efs event file topic key.
         * @return efs event file topic key.
         */
        public EfsTopicKey<E> topicKey()
        {
            return (mTopicKey);
        } // end of topicKey()

        /**
         * Returns efs event file opening timestamp.
         * @return efs event file opening timestamp.
         */
        public Instant openTime()
        {
            return (mOpenTime);
        } // end of openTime()

        /**
         * Returns number of events added to efs event file at
         * this time.
         * @return event add count.
         */
        public long eventsAdded()
        {
            return (mEventsAdded.get());
        } // end of eventsAdded()

        /**
         * Returns number of event retrievals started at this
         * time.
         * @return event retrieval start count.
         */
        public long retrievalsStarted()
        {
            return (mRetrievalsStarted.get());
        } // end of retrievalsStarted()

        /**
         * Returns number of event retrievals completed at this
         * time.
         * @return event retrieval completion count.
         */
        public long retrievalsCompleted()
        {
            return (mRetrievalsCompleted.get());
        } // end of retrievalsCompleted()

        /**
         * Returns number of in-progress event retrievals at this
         * time.
         * @return event retrievals in-progress count.
         */
        public long retrievalsInProgress()
        {
            return (mRetrievalsStarted.get() -
                    mRetrievalsCompleted.get());
        } // end of retrievalsInProgress()

        /**
         * Returns number of event dispatch failures at this
         * time.
         * @return event dispatch failure count.
         */
        public long dispatchFailures()
        {
            return (mDispatchFailures.get());
        } // end of dispatchFailures()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Increments event add count.
         */
        /* package */ void incrementEventAdd()
        {
            mEventsAdded.incrementAndGet();
        } // end of incrementEventAdd()

        /**
         * Increments retrieval started count.
         */
        /* package */ void retrievalStarted()
        {
            mRetrievalsStarted.incrementAndGet();
        } // end of retrievalStarted()

        /**
         * Increments retrieval completed count.
         */
        /* package */ void retrievalCompleted()
        {
            mRetrievalsCompleted.incrementAndGet();
        } // end of retrievalCompleted()

        /**
         * Increments dispatch failure count.
         */
        /* package */ void incrementDispatchFailure()
        {
            mDispatchFailures.incrementAndGet();
        } // end of incrementDispatchFailure()

        //
        // end of Set Methods.
        //-------------------------------------------------------
    } // end of class Metrics
} // end of class EfsFile
