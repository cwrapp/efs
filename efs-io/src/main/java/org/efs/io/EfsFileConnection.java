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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.cqengine.query.Query;
import static com.googlecode.cqengine.query.QueryFactory.and;
import com.googlecode.cqengine.query.option.QueryOptions;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 * Provides read and/or write access to events in an
 * {@link EfsFile}.
 *
 * <p>
 * An {@code EfsFileConnection} is obtained by calling
 * {@link EfsFile#connect(EfsFile.AccessMode, IEfsAgent)} and
 * represents a single agent's connection to the file. This
 * connection enforces access permissions (read-only, write-only,
 * or read-write) and manages the lifecycle of event additions
 * and retrieval subscriptions for that agent.
 * </p>
 *
 * <h2>Core Operations</h2>
 * <ul>
 *   <li>
 *     <strong>Add events:</strong> Use {@link #add(IEfsEvent)}
 *     to append a new event to the file (requires write
 *     permission). Returns the publish {@link Instant timestamp}
 *     assigned by the file.
 *   </li>
 *   <li>
 *     <strong>Retrieve events:</strong> Use
 *     {@link #retrieve(EfsInterval, Query, Consumer, Consumer)}
 *     to subscribe to events matching an interval and CQEngine
 *     query. Optionally continues delivering future matching
 *     events until the interval ends or the subscription is
 *     cancelled.
 *   </li>
 *   <li>
 *     <strong>Close:</strong> Use {@link #close()} to doClose
    connection and cancel all active retrieval subscriptions.
  </li>
 * </ul>
 *
 * <h2>Threading and Dispatch</h2>
 * <p>
 * All file operations ({@code add}, {@code retrieve}, event
 * delivery, and completion notifications) are handed off to the
 * file's dispatcher thread via {@link EfsDispatcher#dispatch}.
 * This ensures that all activity on the file appears to be
 * single-threaded from the file's perspective, regardless of the
 * number of connections or agents.
 * </p>
 *
 * <h2>Access Control</h2>
 * <p>
 * The connection's {@link EfsFile.AccessMode} determines what
 * operations are allowed:
 * </p>
 * <ul>
 *   <li>
 *     {@code READ_ONLY}: only {@code retrieve} is allowed;
 *     {@code add} throws {@link IllegalStateException}.
 *   </li>
 *   <li>
 *     {@code WRITE_ONLY}: only {@code add} is allowed;
 *     {@code retrieve} throws {@link IllegalStateException}.
 *   </li>
 *   <li>
 *     {@code READ_WRITE}: both {@code add} and {@code retrieve}
 *     are allowed.
 *   </li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * import org.efs.event.EfsTopicKey;
import org.efs.io.EfsFile;
import org.efs.io.EfsFileConnection;
import org.efs.io.EfsInterval;
import org.efs.io.EfsDurationEndpoint;
import org.efs.io.EfsIntervalEndpoint.Clusivity;
import com.googlecode.cqengine.query.Query;
import static com.googlecode.cqengine.query.QueryFactory.all;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

public class TradeEventExample {
    // 1) Assume dispatcher "tradeDispatcher" exists and is started.
    //    (See EfsDispatcher.Builder example)

    public static void main(String[] args) throws Exception {
        // 2) Create or retrieve topic key for TradeEvent type
        EfsTopicKey<TradeEvent> key = EfsTopicKey.getKey(TradeEvent.class, "trades");

        // 3) Create the event file and assign it to the dispatcher
        EfsFile<TradeEvent> file = EfsFile.createEventFile(key, "tradeDispatcher");

        // 4) Create and register a writer agent
        WriterAgent writerAgent = new WriterAgent("tradeWriter");
        EfsDispatcher.register(writerAgent, "tradeDispatcher");

        // 5) Create and register a reader agent
        ReaderAgent readerAgent = new ReaderAgent("tradeReader");
        EfsDispatcher.register(readerAgent, "tradeDispatcher");

        // 6) Writer agent connects with write access and adds trades
        EfsFileConnection<TradeEvent> writeConn =
            file.connect(EfsFile.AccessMode.WRITE_ONLY, writerAgent);

        // Simulate adding trades
        for (int i = 0; i < 10; i++) {
            TradeEvent trade = new TradeEvent("AAPL", 150.0 + i, 1000L);
            Instant publishedAt = writeConn.add(trade);
            System.out.println("Added trade at " + publishedAt);
        }

        // 7) Reader agent connects with read access and retrieves trades
        EfsFileConnection<TradeEvent> readConn =
            file.connect(EfsFile.AccessMode.READ_ONLY, readerAgent);

        // 8) Build retrieval interval (last 1 hour)
        EfsInterval interval = EfsInterval.builder()
            .beginning(EfsDurationEndpoint.builder()
                .timeOffset(Duration.ofHours(-1), Clusivity.INCLUSIVE)
                .build())
            .ending(EfsDurationEndpoint.builder()
                .now(Clusivity.INCLUSIVE)
                .build())
            .build();

        // 9) Create a CQEngine query to match all trades (or customize to filter)
        Query<EfsRow<TradeEvent>> query = all(TradeEvent.class);

        // 10) Set up latches for synchronization
        CountDownLatch eventLatch = new CountDownLatch(10);
        CountDownLatch completionLatch = new CountDownLatch(1);

        // 11) Retrieve and subscribe to matching trades
        EfsFileConnection.Retrieval<TradeEvent> retrieval = readConn.retrieve(
            interval,
            query,
            row -> {
                // Event callback: handle each matching trade
                TradeEvent trade = row.getEvent();
                System.out.println("Retrieved trade at row " + row.getRowIndex() +
                    ": " + trade.symbol() + " @ $" + trade.price());
                eventLatch.countDown();
            },
            completion -> {
                // Completion callback: handle end of retrieval
                System.out.println("Retrieval completed: " +
                    completion.completionType());
                completionLatch.countDown();
            }
        );

        // 12) Wait for all events to be delivered and retrieval to complete
        eventLatch.await();
        completionLatch.await();
        System.out.println("All trades retrieved and processed.");

        // 13) Cancel retrieval and doClose connections
        retrieval.doClose();  // Cancels if still active; idempotent
        readConn.doClose();
        writeConn.doClose();
        file.doClose();
    }
}

// Simple trade event POJO
static class TradeEvent implements org.efs.event.IEfsEvent {
    private final String symbol;
    private final double price;
    private final long quantity;

    TradeEvent(String symbol, double price, long quantity) {
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
    }

    public String symbol() { return symbol; }
    public double price() { return price; }
    public long quantity() { return quantity; }
}

// Writer agent that publishes trades
static class WriterAgent implements org.efs.dispatcher.IEfsAgent {
    private final String mName;

    WriterAgent(String name) { mName = name; }

    @Override
    public String name() { return mName; }
}

// Reader agent that consumes trades
static class ReaderAgent implements org.efs.dispatcher.IEfsAgent {
    private final String mName;

    ReaderAgent(String name) { mName = name; }

    @Override
    public String name() { return mName; }
}
}</pre>
 *
 * <h2>Key Points</h2>
 * <ul>
 *   <li>
 *     All callbacks (event and completion) are dispatched to the
 *     agent on its dispatcher thread, not invoked synchronously.
 *   </li>
 *   <li>
 *     Retrieval subscriptions are cancellable via
 *     {@link Retrieval#close()} and implement
 *     {@link java.lang.AutoCloseable} for try-with-resources.
 *   </li>
 *   <li>
 *     Multiple connections (from different agents) may be open
 *     on the same {@code EfsFile} simultaneously; the file
 *     ensures all their operations appear single-threaded.
 *   </li>
 *   <li>
    Closing a connection marks it as closed but does not
    immediately doClose the file; the file remains open until
    {@link EfsFile#close()} is called.
 *   </li>
 * </ul>
 *
 * <h2>Exception Handling</h2>
 * <ul>
 *   <li>
 *     {@link NullPointerException} if any required argument is
 *     {@code null}.
 *   </li>
 *   <li>
 *     {@link IllegalStateException} if attempting to add on a
 *     read-only connection, retrieve on a write-only connection,
 *     or use a closed connection.
 *   </li>
 * </ul>
 *
 * @param <E> the event type stored and retrieved (must implement
 * {@link IEfsEvent}).
 *
 * @see EfsFile
 * @see EfsFile.AccessMode
 * @see Retrieval
 * @see org.efs.dispatcher.EfsDispatcher
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
     * A {@code null} events tags set results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_TAGS = "tags is null";

    /**
     * An event tags set containing a {@code null} value results
     * in an {@code IllegalArgumentException} with message
     * {@value}.
     */
    public static final String TAGS_CONTAINS_NULL =
        "tags contains a null value";

    /**
     * If agent attempts to add an event with read-only connect
     * results in a {@code IllegalStateException} with message
     * {@value}.
     */
    public static final String READ_ONLY_ACCESS =
        "{} opened with read-only access, cannot add events";

    /**
     * If agent attempts to retrieve events with write-only
     * connect results in a {@code IllegalStateException} with
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
     * Attempting to use event file connection when closed
     * results in an {@code IllegalArgumentException} with
     * message {@value}.
     */
    public static final String CLOSED_CONNECTION =
        "\"%s\" event file connection is closed";

    /**
     * Attempting to connect event file when closed results in an
     * {@code IllegalArgumentException} with message {@value}.
     */
    public static final String CLOSED_FILE =
        "\"%s\" event file is closed";

    /**
     * If attempt to postRow event to an {@code EfsFile}, then
     * an {@code IllegalStateException} is thrown with message
     * {@value}.
     */
    public static final String DISPATCH_FAILURE =
       "failed to dispatch %s event to \"%s\"";

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
     * Retrieval identifier is used as key.
     * This data member is only accessed within the dispatcher
     * thread, so it does not need to be a concurrent map.
     */
    private final Map<Integer, Retrieval<E>> mActiveRequests;

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
     * Creates a new efs event file connection to given event
     * file, agent, and access mode.
     * @param eventFile connected to this event file.
     * @param agent agent connecting to event file.
     * @param accessMode connection access mode.
     */
    /* package */ EfsFileConnection(final EfsFile<E> eventFile,
                                    final IEfsAgent agent,
                                    final AccessMode accessMode)
    {
        mEventFile = eventFile;
        mAgent = agent;
        mAccessMode = accessMode;
        mActiveRequests = new HashMap<>();
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
     * Marks this connection as closed. Invoked by
     * {@link EfsFile#onClose()} when underlying file is closing.
     * Does not cancel active retrievals; that is handled
     * separately by {@link EfsFile}.
     *
     * @see EfsFile#close()
     */
    /* package */ void markClosed()
    {
        mOpenFlag.set(false);
    } // end of markClosed()

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    /**
     * Asynchronously appends given event to underlying
     * {@link EfsFile}, assigning it a publish
     * {@link Instant timestamp} and a monotonic row index.
     * <p>
     * This append is performed as follows:
     * </p>
     * <ol>
     *   <li>
     *     Validates that the event is not {@code null}.
     *   </li>
     *   <li>
     *     Checks that this connection's
     *     {@link EfsFile.AccessMode access mode} is compatible
     *     with {@code WRITE_ONLY} (i.e., {@code WRITE_ONLY} or
     *     {@code READ_WRITE}); if not, throws
     *     {@link IllegalStateException}.
     *   </li>
     *   <li>
     *     Checks that both this connection and underlying file
     *     are still open; if not, throws
     *    {@code IllegalStateException}.
     *   </li>
     *   <li>
     *     Obtains the current system time from the underlying
     *     file's clock.
     *   </li>
     *   <li>
     *     Wraps the event and timestamp in an
     *     {@link AddInternalEvent} and dispatches it to the
     *     file's dispatcher thread via
     *     {@link EfsDispatcher#dispatch}, passing the file's
     *     {@link EfsFile#onAdd(AddInternalEvent)} handler.
     *   </li>
     *   <li>
     *     Returns the publish {@link Instant timestamp} assigned
     *     to event.
     *   </li>
     *   <li>
     *     No user-defined integer tags are associated with this
     *     event.
     *   </li>
     * </ol>
     * <br>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Threading Model</strong></span></dt>
     * </dl>
     * <p>
     * Although this method returns immediately with the publish
     * timestamp, the actual row insertion into the file occurs
     * asynchronously on the file's dispatcher thread. This
     * means:
     * </p>
     * <ul>
     *   <li>
     *     Multiple calls to {@code add} from the same or
     *     different agents will be processed serially on the
     *     dispatcher thread in the order posted to event file's
     *     event queue.
     *   </li>
     *   <li>
     *     The returned publish timestamp is not guaranteed to be
     *     unique; events added in quick succession may receive
     *     the same timestamp (the file's clock resolution may be
     *     coarser than call frequency).
     *   </li>
     *   <li>
     *     Row indices are guaranteed to be strictly monotonic
     *     and unique.
     *   </li>
     *   <li>
     *     Active retrieval subscriptions (see {@link #retrieve})
     *     will receive matching rows as soon as they are
     *     inserted, without blocking the caller of this method.
     *   </li>
     * </ul>
     * <br>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Event Ordering</strong></span></dt>
     * </dl>
     * <p>
     * Events added via {@code add} are guaranteed to maintain
     * call order in the file: event <em>E0</em> added before
     * event <em>E1</em> will receive a smaller row index and
     * (generally) an equal or earlier publish timestamp.
     * </p>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Access Control</strong></span></dt>
     * </dl>
     * <p>
     * This method requires the connection to have been opened
     * with write access ({@code WRITE_ONLY} or
     * {@code READ_WRITE}). Attempting to add on a
     * {@code READ_ONLY} connection throws
     * {@code IllegalStateException}.
     * </p>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Error Handling</strong></span></dt>
     * </dl>
     * <p>
     * Event insertion failures on the dispatcher thread (for
     * example, failure to allocate a row or retrieve time) are
     * not reported back to the caller and do not throw
     * exceptions from this method. The method assumes the
     * dispatcher will log such errors via the file's logger.
     * </p>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Example</strong></span></dt>
     * </dl>
     * <pre>{@code
     * // Assuming 'connection' is an open EfsFileConnection<TradeEvent> with write access
     * TradeEvent trade = new TradeEvent("AAPL", 150.25, 1000L);
     *
     * try {
     *     Instant publishedAt = connection.add(trade);
     *     System.out.println("Trade added at " + publishedAt);
     * } catch (IllegalStateException e) {
     *     System.err.println("Cannot add: " + e.getMessage());
     * } catch (NullPointerException e) {
     *     System.err.println("Trade must not be null");
     * }
     * }</pre>
     *
     * @param event the event to append to the file; must not be
     * {@code null} and must be an instance of type {@code E}.
     * @return publish {@link Instant timestamp} assigned to
     * event by event file's clock.
     * @throws NullPointerException
     * if {@code event} is {@code null}.
     * @throws IllegalStateException
     * if this connection does not have write access (opened with
     * {@code READ_ONLY} mode), or if this connection is closed,
     * or if underlying event file is closed. Also thrown if
     * attempt to postRow retrieval request to {@code EfsFile}
     * fails. In all cases, event is <em>not</em> added to event
     * file.
     *
     * @see EfsFile.AccessMode
     * @see #retrieve(EfsInterval, Query, Consumer, Consumer)
     * @see EfsFile#onAdd(AddInternalEvent)
     * @see EfsDispatcher#dispatch
     */
    @Nonnull
    public Instant add(@Nonnull final E event)
    {
        return (add(EfsFile.NO_TAGS, event));
    } // end of add(E)

    /**
     * Asynchronously appends given event to underlying
     * {@link EfsFile}, assigning it a publish
     * {@link Instant timestamp} and a monotonic row index.
     * <p>
     * This append is performed as follows:
     * </p>
     * <ol>
     *   <li>
     *     Validates that the event are tags are not
     *     {@code null} or that tags contains no {@code null}
     *     values.
     *   </li>
     *   <li>
     *     Checks that this connection's
     *     {@link EfsFile.AccessMode access mode} is compatible
     *     with {@code WRITE_ONLY} (i.e., {@code WRITE_ONLY} or
     *     {@code READ_WRITE}); if not, throws
     *     {@link IllegalStateException}.
     *   </li>
     *   <li>
     *     Checks that both this connection and underlying file
     *     are still open; if not, throws
     *    {@code IllegalStateException}.
     *   </li>
     *   <li>
     *     Obtains the current system time from the underlying
     *     file's clock.
     *   </li>
     *   <li>
     *     Wraps the event and timestamp in an
     *     {@link AddInternalEvent} and dispatches it to the
     *     file's dispatcher thread via
     *     {@link EfsDispatcher#dispatch}, passing the file's
     *     {@link EfsFile#onAdd(AddInternalEvent)} handler.
     *   </li>
     *   <li>
     *     Returns the publish {@link Instant timestamp} assigned
     *     to event.
     *   </li>
     * </ol>
     * <br>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Threading Model</strong></span></dt>
     * </dl>
     * <p>
     * Although this method returns immediately with the publish
     * timestamp, the actual row insertion into the file occurs
     * asynchronously on the file's dispatcher thread. This
     * means:
     * </p>
     * <ul>
     *   <li>
     *     Multiple calls to {@code add} from the same or
     *     different agents will be processed serially on the
     *     dispatcher thread in the order posted to event file's
     *     event queue.
     *   </li>
     *   <li>
     *     The returned publish timestamp is not guaranteed to be
     *     unique; events added in quick succession may receive
     *     the same timestamp (the file's clock resolution may be
     *     coarser than call frequency).
     *   </li>
     *   <li>
     *     Row indices are guaranteed to be strictly monotonic
     *     and unique.
     *   </li>
     *   <li>
     *     Active retrieval subscriptions (see {@link #retrieve})
     *     will receive matching rows as soon as they are
     *     inserted, without blocking the caller of this method.
     *   </li>
     * </ul>
     * <br>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Event Ordering</strong></span></dt>
     * </dl>
     * <p>
     * Events added via {@code add} are guaranteed to maintain
     * call order in the file: event <em>E0</em> added before
     * event <em>E1</em> will receive a smaller row index and
     * (generally) an equal or earlier publish timestamp.
     * </p>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Access Control</strong></span></dt>
     * </dl>
     * <p>
     * This method requires the connection to have been opened
     * with write access ({@code WRITE_ONLY} or
     * {@code READ_WRITE}). Attempting to add on a
     * {@code READ_ONLY} connection throws
     * {@code IllegalStateException}.
     * </p>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Error Handling</strong></span></dt>
     * </dl>
     * <p>
     * Event insertion failures on the dispatcher thread (for
     * example, failure to allocate a row or retrieve time) are
     * not reported back to the caller and do not throw
     * exceptions from this method. The method assumes the
     * dispatcher will log such errors via the file's logger.
     * </p>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Example</strong></span></dt>
     * </dl>
     * <pre>{@code
     * // Assuming 'connection' is an open EfsFileConnection<TradeEvent> with write access
     * Set<Integer> tags = new TreeSet<>(List.of(FIRST_TRADE_OF_THE_DAY));
     * TradeEvent trade = new TradeEvent("AAPL", 150.25, 1000L);
     *
     * try {
     *     Instant publishedAt = connection.add(tags, trade);
     *     System.out.println("Trade added at " + publishedAt);
     * } catch (IllegalStateException e) {
     *     System.err.println("Cannot add: " + e.getMessage());
     * } catch (NullPointerException e) {
     *     System.err.println("Trade must not be null");
     * }
     * }</pre>
     *
     * @param tags user-defined tags associated with event; must
     * not be {@code null} or contain {@code null} values. The
     * reason tags are integers and not strings is for faster
     * value comparison.
     * @param event the event to append to the file; must not be
     * {@code null} and must be an instance of type {@code E}.
     * @return publish {@link Instant timestamp} assigned to
     * event by event file's clock.
     * @throws NullPointerException
     * if either {@code tags} or {@code event} is {@code null}.
     * @throws IllegalStateException
     * if this connection does not have write access (opened with
     * {@code READ_ONLY} mode), or if this connection is closed,
     * or if underlying event file is closed. Also thrown if
     * attempt to postRow retrieval request to {@code EfsFile}
     * fails. In all cases, event is <em>not</em> added to event
     * file.
     *
     * @see EfsFile.AccessMode
     * @see #retrieve(EfsInterval, Query, Consumer, Consumer)
     * @see EfsFile#onAdd(AddInternalEvent)
     * @see EfsDispatcher#dispatch
     */
    @Nonnull
    public Instant add(@Nonnull final Set<Integer> tags,
                       @Nonnull final E event)
    {
        Objects.requireNonNull(tags, NULL_TAGS);
        Objects.requireNonNull(event, NULL_EVENT);

        // Validate that agent has proper access and that
        // connection and file are open.
        validateState(AccessMode.WRITE_ONLY, READ_ONLY_ACCESS);

        final Instant pubTime = mEventFile.instant();
        final Set<Integer> tagscopy = ImmutableSet.copyOf(tags);
        final AddInternalEvent<E> addEvent =
            new AddInternalEvent<>(pubTime, tagscopy, event);

        try
        {
            EfsDispatcher.dispatch(
                mEventFile::onAdd, addEvent, mEventFile);
        }
        catch (IllegalStateException statex)
        {
            throw (
                new IllegalStateException(
                    String.format(
                        DISPATCH_FAILURE,
                        "add",
                        mEventFile.name())));
        }

        return (pubTime);
    } // end of add(Set<>, E)

    /**
     * Initiates a retrieval of events from the underlying
     * {@link EfsFile} matching the specified interval and
     * CQEngine query, with optional subscription to future
     * matching events.
     * <p>
     * This method performs the following:
     * </p>
     * <ol>
     *   <li>
     *     Validates that all arguments ({@code interval},
     *     {@code query}, {@code eventCB}, {@code completionCB})
     *     are not {@code null}; throws
     *     {@code NullPointerException} if any is {@code null}.
     *   </li>
     *   <li>
     *     Checks that this connection's
     *     {@link EfsFile.AccessMode access mode} is compatible
     *     with {@code READ_ONLY} (i.e., {@code READ_ONLY} or
     *     {@code READ_WRITE}); if not, throws
     *     {@code IllegalStateException}.
     *   </li>
     *   <li>
     *     Checks that both this connection and the underlying
     *     file are still open; if not, throws
     *     {@code IllegalStateException}.
     *   </li>
     *   <li>
     *     Creates a new {@link Retrieval} instance encapsulating
     *     interval, query, callbacks, and agent.
     *   </li>
     *   <li>
     *     Wraps the {@code Retrieval} in a
     *     {@link RetrievalInternalEvent} and dispatches it to
     *     event file's dispatcher thread via
     *    {@link EfsDispatcher#dispatch}, passing the file's
     *    {@link EfsFile#onRetrieve(RetrievalInternalEvent)}
     *    handler.
     *   </li>
     *   <li>
     *     Adds the {@code Retrieval} to this connection's active
     *     retrieval list for cancellation tracking.
     *   </li>
     *   <li>
     *     Returns {@code Retrieval} instance to the caller so
     *     events and completion can be received via callbacks,
     *     and the retrieval can be cancelled via
     *     {@link Retrieval#close()}.
     *   </li>
     * </ol>
     * <br>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Threading Model</strong></span></dt>
     * </dl>
     * <p>
     * Although this method returns immediately with a
     * {@code Retrieval} subscription, the actual query execution
     * and event matching occur asynchronously on the file's
     * dispatcher thread. This means:
     * </p>
     * <ul>
     *   <li>
     *     The {@code eventCB} and {@code completionCB} callbacks
     *     are invoked on the retrieval's agent's dispatcher
     *     thread, <em>not</em> from the caller's thread.
     *   </li>
     *   <li>
     *     Events matching the interval and query are delivered
     *     to {@code eventCB} serially and in ascending row-index
     *     order.
     *   </li>
     *   <li>
     *     If the interval includes future events, the retrieval
     *     remains active and continues to receive newly inserted
     *     events that match the query.
     *   </li>
     *   <li>
     *     When the interval's ending is reached (or retrieval is
     *     cancelled), {@code completionCB} is invoked exactly
     *     once with a {@link RetrievalCompleteEvent} describing
     *     when the retrieval completed and completion reason.
     *   </li>
     * </ul>
     * <br>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Interval and Query Semantics</strong></span></dt>
     * </dl>
     * <p>
     * The retrieval matches rows from the file as follows:
     * </p>
     * <ul>
     *   <li>
     *     <strong>Interval:</strong> Defines the begin and end
     *     points for row retrieval. Points may be specified by
     *     row index, fixed timestamp, or duration offset from
     *     current time. See {@link EfsInterval} and
     *     {@link EfsIntervalEndpoint} for endpoint types and
     *     clusivity (inclusive/exclusive).
     *   </li>
     *   <li>
     *     <strong>Query:</strong> A CQEngine {@link Query} that
     *     further filters rows. Construct queries using
     *     CQEngine's
     *     {@link com.googlecode.cqengine.query.QueryFactory}
     *     methods and obtain
     *     field attributes via {@link EfsFile#attribute(String)}
     *     for the event's fields. A query matching all rows can
     *     be created with {@code QueryFactory.all(eventClass)}.
     *   </li>
     *   <li>
     *     <strong>Combined effect:</strong> Only rows falling
     *     within the interval <em>and</em> matching the query
     *     are delivered to {@code eventCB}.
     *   </li>
     * </ul>
     * <br>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Retrieval Lifecycle</strong></span></dt>
     * </dl>
     * <ul>
     *   <li>
     *     <strong>Historical events:</strong> Rows which exist
     *     at the time of retrieval and match the interval and
     *     query are delivered first, in increasing row index
     *     order.
     *   </li>
     *   <li>
     *     <strong>Future events:</strong> If the interval
     *     extends beyond the current time (see
     *     {@link EfsInterval#isFutureInterval}), the retrieval
     *     is registered with the file in order to receive newly
     *     added matching rows as they arrive.
     *   </li>
     *   <li>
     *     <strong>Completion:</strong> The retrieval completes
     *     when:
     *     <ul>
     *       <li>
     *         All historical rows within the interval have been
     *         delivered and the interval does not extend into
     *         the future.
     *       </li>
     *       <li>
     *         The retrieval interval's ending point is reached
     *         (current time moves past the interval's end).
     *       </li>
     *       <li>
     *         The retrieval is cancelled by the caller via
     *         {@link Retrieval#close()}.
     *       </li>
     *       <li>
     *         The underlying file is closed.
     *       </li>
     *       <li>
     *         This connection is closed.
     *       </li>
     *     </ul>
     *   </li>
     *   <li>
     *     <strong>Completion notification:</strong> Upon
     *     completion, {@code completionCB} is invoked exactly
     *     once with a {@link RetrievalCompleteEvent} specifying
     *     completion time and  completion type (e.g.,
     *     {@link RetrievalCompleteEvent.CompletionType#RETRIEVAL_COMPLETED},
     *     {@link RetrievalCompleteEvent.CompletionType#USER_CANCEL},
     *     {@link RetrievalCompleteEvent.CompletionType#FILE_CLOSED},
     *     or
     *     {@link RetrievalCompleteEvent.CompletionType#CONNECTION_CLOSED}).
     *   </li>
     * </ul>
     * <br>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Access Control</strong></span></dt>
     * </dl>
     * <p>
     * This method requires the connection to have been opened
     * with read access ({@code READ_ONLY} or {@code READ_WRITE}).
     * Attempting to retrieve on a {@code WRITE_ONLY} connection
     * throws {@link IllegalStateException}.
     * </p>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Callback Guarantees</strong></span></dt>
     * </dl>
     * <ul>
     *   <li>
     *     If {@code eventCB} throws an exception, the exception
     *     is logged but does not interrupt the retrieval; other
     *     matching events continue to be processed.
     *   </li>
     *   <li>
     *     If {@code completionCB} throws an exception, the
     *     exception is logged but does not prevent the retrieval
     *     from closing.
     *   </li>
     *   <li>
     *     Both callbacks are guaranteed to be invoked on agent's
     *     dispatcher thread in a single-threaded manner (no
     *     concurrent invocations for the same agent).
     *   </li>
     * </ul>
     * <br>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Cancellation</strong></span></dt>
     * </dl>
     * <p>
     * The returned {@code Retrieval} is {@link AutoCloseable},
     * allowing try-with-resources usage. Call
     * {@link Retrieval#close()} or use try-with-resources to
     * cancel an active retrieval. Cancellation is idempotent:
     * calling {@code doClose()} on an already-completed retrieval
     * has no effect.
     * </p>
     * <p style="background-color:#ffcccc;padding:5px;border: 2px solid darkred;">
     * <strong>WARNING!</strong> Because events are retrieved
     * asynchronously, placing a {@code Retrieval} instance
     * within a try-with-resources requires the current thread to
     * block until the retrieval completes (effectively turning
     * the asynchronous retrieval into synchronous). Failure to
     * do this results in the retrieval request being closed
     * before the request is processed by the event file.
     * </p>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Example: Retrieve Historical Events</strong></span></dt>
     * </dl>
     * <pre>{@code // Retrieve all trades from the last hour
EfsInterval interval = EfsInterval.builder()
    .beginning(EfsDurationEndpoint.builder()
        .timeOffset(Duration.ofHours(-1), Clusivity.INCLUSIVE)
        .build())
    .ending(EfsDurationEndpoint.builder()
        .now(Clusivity.INCLUSIVE)
        .build())
    .build();

// Create a query that matches trades with price > 150
Query<EfsRow<TradeEvent>> query =
    QueryFactory.greaterThan(file.attribute("price"), 150.0);

try (EfsFileConnection.Retrieval<TradeEvent> retrieval =
    connection.retrieve(
        interval,
        query,
        row -> {
            // Event callback
            TradeEvent trade = row.getEvent();
            System.out.println("Trade: " + trade.symbol() + " @ $" + trade.price());
        },
        completion -> {
            // Completion callback
            System.out.println("Retrieval done: " + completion.completionType());
        }
    )) {
    // Retrieval is active here; callbacks may still be in flight.
    // Try-with-resources will cancel the retrieval when exiting the block.
    // Therefore  a lock-and-condition must be used to prevent retrieval
    // from being canceled.
}
}</pre>
     *
     * <dl>
     * <dt><span style="font-size: 14px"><strong>Example: Subscribe to Future Events</strong></span></dt>
     * </dl>
     * <pre>{@code // Subscribe to all trades starting now and ongoing indefinitely
EfsInterval interval = EfsInterval.builder()
    .beginning(EfsDurationEndpoint.builder()
        .now(Clusivity.INCLUSIVE)
        .build())
    .ending(EfsDurationEndpoint.builder()
        .endNever()  // Interval never ends
        .build())
    .build();

Query<EfsRow<TradeEvent>> query = QueryFactory.all(TradeEvent.class);

EfsFileConnection.Retrieval<TradeEvent> subscription =
    connection.retrieve(
        interval,
        query,
        row -> System.out.println("New trade: " + row.getEvent()),
        completion -> System.out.println("Subscription ended: " + completion.completionType())
    );

// Subscription is now active and will receive all new trades.
// Later, cancel when done:
subscription.doClose();
}</pre>
     *
     * @param interval the time or index interval defining the
     * range of rows to retrieve; must not be {@code null}. See
     * {@link EfsInterval} for details on building intervals.
     * @param query a CQEngine {@link Query} that further filters
     * which rows are delivered; must not be {@code null}. Use
     * {@link com.googlecode.cqengine.query.QueryFactory} methods
     * and {@link EfsFile#attribute(String)} to construct
     * queries.
     * @param eventCB a {@link Consumer} invoked on the agent's
     * dispatcher thread for each matching {@link EfsRow}; must
     * not be {@code null}. The callback receives rows in
     * ascending row-index order. Exceptions thrown by this
     * callback are logged but do not interrupt retrieval.
     * @param completionCB a {@link Consumer} invoked exactly
     * once when retrieval completes (either successfully,
     * cancelled, or due to file/connection closure); must not be
     * {@code null}. Receives a {@link RetrievalCompleteEvent}
     * providing completion time and reason. Invoked on agent's
     * dispatcher thread. Exceptions thrown by this callback are
     * logged but do not prevent the retrieval from closing.
     * @return a {@link Retrieval} subscription that can be
     * queried for status or cancelled via
     * {@link Retrieval#close()}; never {@code null}.
     * @throws NullPointerException
     * if any of {@code interval}, {@code query}, {@code eventCB},
     * or {@code completionCB} is {@code null}.
     * @throws IllegalStateException
     * if this connection does not have read access
     * (opened with {@code WRITE_ONLY} mode), if this connection
     * is closed, or if underlying file is closed. Also thrown
     * if attempt to postRow retrieval request to
     * {@code EfsFile} fails. In all cases, no events are
     * retrieved from event file.
     *
     * @see EfsInterval
     * @see EfsIntervalEndpoint
     * @see com.googlecode.cqengine.query.QueryFactory
     * @see EfsFile#attribute(String)
     * @see Retrieval
     * @see RetrievalCompleteEvent
     * @see EfsFile#onRetrieve(RetrievalInternalEvent)
     * @see EfsDispatcher#dispatch
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

        // Validate that agent has proper access and that
        // connection and file are open.
        validateState(AccessMode.READ_ONLY, WRITE_ONLY_ACCESS);

        retval = new Retrieval<>(sRetrieveIds.getAndIncrement(),
                                 this,
                                 mAgent,
                                 interval,
                                 query,
                                 eventCB,
                                 completionCB);

        sLogger.debug(
            "{}: {} retrieving events over interval {}, query {}.",
            mEventFile.name(),
            mAgent.name(),
            interval,
            query);

        // Do actual row retrieval on dispatcher thread.
        try
        {
            EfsDispatcher.dispatch(
                mEventFile::onRetrieve,
                new RetrievalInternalEvent<>(retval),
                mEventFile);
            mActiveRequests.put(retval.id(), retval);
        }
        catch (Exception jex)
        {
            throw (
                new IllegalStateException(
                    String.format(
                        DISPATCH_FAILURE,
                        "retrieve",
                        mEventFile.name())));
        }

        return (retval);
    } // end of retrieve(...)

    /**
     * TODO
     * @param tag retrieve events with this user-defined event
     * tag.
     * @param eventCB a {@link Consumer} invoked on the agent's
     * dispatcher thread for each matching {@link EfsRow}; must
     * not be {@code null}. The callback receives rows in
     * ascending row-index order. Exceptions thrown by this
     * callback are logged but do not interrupt retrieval.
     * @param completionCB a {@link Consumer} invoked exactly
     * once when retrieval completes (either successfully,
     * cancelled, or due to file/connection closure); must not be
     * {@code null}. Receives a {@link RetrievalCompleteEvent}
     * providing completion time and reason. Invoked on agent's
     * dispatcher thread. Exceptions thrown by this callback are
     * logged but do not prevent the retrieval from closing.
     * @throws NullPointerException
     * if either {@code eventCB} or {@code completionCB} is
     * {@code null}.
     * @throws IllegalStateException
     * if this connection does not have read access
     * (opened with {@code WRITE_ONLY} mode), if this connection
is closed, or if underlying file is closed. Also thrown
if attempt to postRow retrieval request to
{@code EfsFile} fails. In all cases, no events are
     * retrieved from event file.
     */
    public void retrieve(final int tag,
                         @Nonnull final Consumer<EfsRow<E>> eventCB,
                         @Nonnull final Consumer<RetrievalCompleteEvent<E>> completionCB)
    {
        // Validate arguments.
        Objects.requireNonNull(eventCB, NULL_EVENT_CALLBACK);
        Objects.requireNonNull(completionCB, NULL_DONE_CALLBACK);

        // Validate that agent has proper access and that
        // connection and file are open.
        validateState(AccessMode.READ_ONLY, WRITE_ONLY_ACCESS);

        sLogger.debug("{}: {} retrieving events with {} tag.",
                      mEventFile.name(),
                      mAccessMode.name(),
                      tag);

        try
        {
            final TagRetrieveInternalEvent<E> event =
                new TagRetrieveInternalEvent<>(tag,
                                               mAgent,
                                               eventCB,
                                               completionCB);

            EfsDispatcher.dispatch(
                mEventFile::onRetrieve, event, mEventFile);
        }
        catch (Exception jex)
        {
            throw (
                new IllegalStateException(
                    String.format(
                        DISPATCH_FAILURE,
                        "retrieve",
                        mEventFile.name())));
        }
    } // end of retrieve(int, Consumer, Consumer)

    /**
     * Closes all active retrieval requests. If this event file
     * is already closed, then does nothing (idempotent).
     * <p>
     * This method is asynchronous: it marks the connection as
     * closed and dispatches cancellation events to the file.
     * After this call, no new event adds or retrievals may be
     * created via this connection, but in-flight callbacks may
     * still be processed. Completion callbacks for active
     * retrievals are invoked on the agent's dispatcher thread.
     * </p>
     */
    public void close()
    {
        if (mOpenFlag.compareAndSet(true, false))
        {
            final List<Retrieval<E>> copy =
                ImmutableList.copyOf(mActiveRequests.values());

            mActiveRequests.clear();

            // Close retrievals on event file dispatcher thread.
            EfsDispatcher.dispatch(() ->
                {
                    for (Retrieval<E> r : copy)
                    {
                        try
                        {
                            r.doClose(mEventFile.instant(),
                                    CompletionType.CONNECTION_CLOSED);
                        }
                        catch (Exception jex)
                        {
                            sLogger.warn(
                                "Error closing retrieval {}",
                                r.id(),
                                jex);
                        }
                    }
                },
                mEventFile);

            // Since connections are added to the efs event
            // file's set on the callers thread and not on the
            // event file's dispatcher thread, remove connection
            // on the caller's thread as well.
            mEventFile.onDisconnect(this);
        }
    } // end of doClose()

    /**
     * Removes a completed retrieval from active requests list.
     * Invoked by {@link Retrieval#markCompleted(Instant)} or
     * {@link Retrieval#doClose(Instant, CompletionType)} when a
     * retrieval transitions to the completed state.
     * @param retrieval remove this request from active requests.
     *
     * @see Retrieval#markCompleted(Instant)
     * @see Retrieval#doClose(Instant, CompletionType)
     */
    /* package */ void retrievalComplete(final Retrieval<E> retrieval)
    {
        final int rId = retrieval.id();

        if (!mActiveRequests.containsKey(rId))
        {
            sLogger.warn(
                "{} connection: retrieval {} not found in active requests.",
                mEventFile.name(),
                rId);
        }
        else
        {
            mActiveRequests.remove(rId);
        }
    } // end of retrievalComplete(Retrieval)

    /**
     * Validates that connect mode allows for specified action
     * and that {@code this} connection and underlying event file
     * are open.
     * <p>
     * This method is called for effect only.
     * </p>
     * @param mode required access mode.
     * @param format exception message format for invalid access
     * mode.
     * @throws IllegalStateException
     * if {@code mode} is not compatible with connection mode,
     * {@code this} connection is closed, or underlying event
     * file is closed.
     */
    private void validateState(final AccessMode mode,
                               final String format)
    {
        // Is this agent able to retrieve events from efs file?
        if (!mAccessMode.isCompatible(mode))
        {
            throw (
                new IllegalStateException(
                    String.format(format, mEventFile.name())));
        }

        // Is this file connection open?
        if (!mOpenFlag.get())
        {
            throw (
                new IllegalStateException(
                    String.format(
                        CLOSED_CONNECTION, mEventFile.name())));
        }

        // NOTE: Closing an event file results in all extant
        // connections being closed. So if the underlying event
        // file is closed, then this connection is closed.
    } // end of validateState(final AccessMode mode)

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Represents an active or completed event retrieval
     * subscription from an {@link EfsFileConnection}.
     * <p>
     * A {@code Retrieval} encapsulates a single agent's
     * subscription to events within a specified
     * {@link EfsInterval} and matching a CQEngine {@link Query}.
     * It manages the delivery of matching historical events
     * and/or future events, along with completion notification.
     * </p>
     * <p>
     * Instances are created internally by
     * {@link EfsFileConnection#retrieve} and can not be
     * instantiated directly.
     * </p>
     * <h2>Lifecycle</h2>
     * <p>
     * A {@code Retrieval} progresses through the following
     * states:
     * </p>
     * <ul>
     *   <li>
     *     <strong>Active:</strong> Created by
     *     {@code retrieve()}, the subscription is waiting for or
     *     is receiving events. Query {@link #isCompleted()} to
     *     check if still active.
     *   </li>
     *   <li>
     *     <strong>Completed:</strong> Reached when:
     *     <ul>
     *       <li>
     *         All historical events within the interval have
     *         been delivered and the interval does not extend
     *         into the future.
     *       </li>
     *       <li>
     *         Current time moves past the interval's ending
     *         point.
     *       </li>
     *       <li>
     *         Retrieval is cancelled by the caller via
     *         {@link #close()}.
     *       </li>
     *       <li>
     *         Underlying {@link EfsFileConnection} is closed.
     *       </li>
     *       <li>
     *         Underlying {@link EfsFile} is closed.
     *       </li>
     *     </ul>
     *   </li>
     *   <li>
     *     <strong>Completion notification:</strong> Upon
     *     transition to completed, the completion callback
     *     provided to {@code retrieve()} is invoked exactly once
     *     on the agent's dispatcher thread with a
     *     {@link RetrievalCompleteEvent} specifying
     *     completion time and type (e.g.,
     *     {@link RetrievalCompleteEvent.CompletionType#RETRIEVAL_COMPLETED},
     *     {@link RetrievalCompleteEvent.CompletionType#USER_CANCEL},
     *     {@link RetrievalCompleteEvent.CompletionType#FILE_CLOSED}, or
     *     {@link RetrievalCompleteEvent.CompletionType#CONNECTION_CLOSED}).
     *   </li>
     * </ul>
     *
     * <h2>Event Delivery</h2>
     * <p>
     * Matching events are delivered in ascending row-index order
     * via the event callback provided to
     * {@link EfsFileConnection#retrieve}. All delivery occurs
     * asynchronously on the agent's dispatcher thread, not on
     * the caller's thread. The callback receives {@link EfsRow}
     * instances; access the stored event via
     * {@link EfsRow#getEvent()}, the publish timestamp via
     * {@link EfsRow#getPublishTimestamp()}, and the row index via
     * {@link EfsRow#getRowIndex()}.
     * </p>
     *
     * <h2>Cancellation</h2>
     * <p>
     * Call {@link #close()} to cancel an active retrieval. The
     * method implements {@link AutoCloseable}, enabling
     * try-with-resources usage:
     * <pre>{@code
     * try (Retrieval<MyEvent> sub = connection.retrieve(...)) {
    // Subscription is active
}  // doClose() is automatically called, cancelling the subscription
}</pre>
     * <p>
     * Cancellation is idempotent: calling {@code doClose()} on an
     * already-completed retrieval has no effect. The completion
     * callback is invoked with
     * {@link RetrievalCompleteEvent.CompletionType#USER_CANCEL}
     * if cancellation succeeds.
     * </p>
     * <p style="background-color:#ffcccc;padding:5px;border: 2px solid darkred;">
     * <strong>WARNING!</strong> Because events are retrieved
     * asynchronously, placing a {@code Retrieval} instance
     * within a try-with-resources requires the current thread to
     * block until the retrieval completes (effectively turning
     * the asynchronous retrieval into synchronous). Failure to
     * do this results in the retrieval request being closed
     * before the request is processed by the event file.
     * </p>
     *
     * <h2>Status and Information</h2>
     * <ul>
     *   <li>
     *     {@link #isCompleted()}: Returns {@code true} if the
     *     retrieval has reached completion (either naturally or
     *     via cancellation).
     *   </li>
     *   <li>
     *     {@link #completionType()}: Returns the
     *     {@link RetrievalCompleteEvent.CompletionType} if
     *     completed; {@code null} if still active.
     *   </li>
     *   <li>
     *     {@link #id()}: Returns a unique JVM-wide retrieval
     *     identifier.
     *   </li>
     *   <li>
     *     {@link #agent()}: Returns the {@link IEfsAgent} owning
     *     this retrieval.
     *   </li>
     *   <li>
     *     {@link #interval()}: Returns the {@link EfsInterval}
     *     specified when the retrieval was created.
     *   </li>
     *   <li>
     *     {@link #toString()}: Returns a human-readable summary
     *     of the retrieval including ID, agent name, interval,
     *     and query.
     *   </li>
     * </ul>
     *
     * <h2>Threading Model</h2>
     * <p>
     * All {@code Retrieval} operations are thread-safe. Status
     * queries ({@link #isCompleted()},
     * {@link #completionType()}, etc.) can be called from any
     * thread and return atomic snapshots. Event delivery and
     * cancellation are coordinated by the underlying file's
     * dispatcher thread, ensuring consistent state transitions.
     * </p>
     *
     * <h2>Integration with EfsFile</h2>
     * <p>
     * Active retrievals are registered with the underlying
     * {@link EfsFile} in order to receive matching new rows as
     * they are added (if the interval extends into the future).
     * The file invokes package-private methods
     * ({@link #matches(EfsRow)}, {@link #isAtEnd(EfsRow)},
     * {@link #postRow(EfsRow)},
     * {@link #doClose(Instant, CompletionType)}) to match
     * incoming events and notify the retrieval of completion.
     * </p>
     *
     * <h2>Example: Historical Retrieval with Cancellation</h2>
     * <pre>{@code
     * // Retrieve trades from the last 30 minutes
EfsInterval interval = EfsInterval.builder()
    .beginning(EfsDurationEndpoint.builder()
        .timeOffset(Duration.ofMinutes(-30), Clusivity.INCLUSIVE)
        .build())
    .ending(EfsDurationEndpoint.builder()
        .now(Clusivity.INCLUSIVE)
        .build())
    .build();

Query<EfsRow<TradeEvent>> query = QueryFactory.all(TradeEvent.class);

Retrieval<TradeEvent> retrieval = connection.retrieve(
    interval,
    query,
    row -> {
        System.out.println("Trade: " + row.getEvent().symbol()
            + " at row " + row.getRowIndex());
    },
    completion -> {
        System.out.println("Retrieval complete: " + completion.completionType());
    }
);

// Check status
if (!retrieval.isCompleted()) {
    System.out.println("Retrieval " + retrieval.id() + " is active");
}

// Later, cancel if still active
if (!retrieval.isCompleted()) {
    retrieval.doClose();
}
}</pre>
     *
     * <h3>Example: Future Subscription</h3>
     * <pre>{@code
     * // Subscribe to all future trades
EfsInterval interval = EfsInterval.builder()
    .beginning(EfsDurationEndpoint.builder()
        .now(Clusivity.INCLUSIVE)
        .build())
    .ending(EfsDurationEndpoint.builder()
        .endNever()  // No end time
        .build())
    .build();

// Filter for high-value trades
Query<EfsRow<TradeEvent>> query = QueryFactory.greaterThan(
    file.attribute("quantity"), 5000L);

try (Retrieval<TradeEvent> subscription = connection.retrieve(
    interval,
    query,
    row -> System.out.println("Large trade: " + row.getEvent()),
    completion -> System.out.println("Subscription ended")
)) {
    // Subscription is active and will receive all matching future trades
    Thread.sleep(60_000);  // Run for 1 minute
}  // doClose() is automatically called
}</pre>
     *
     * <h2>Error Handling</h2>
     * <ul>
     *   <li>
     *     Exceptions thrown by the event callback are logged but
     *     do not interrupt the retrieval or prevent other events
     *     from being delivered.
     *   </li>
     *   <li>
     *     Exceptions thrown by the completion callback are
     *     logged but do not prevent the retrieval from
     *     transitioning to completed.
     *   </li>
     *   <li>
     *     If {@link #close()} is called on an already-completed
     *     retrieval, it simply returns without error
     *     (idempotent).
     *   </li>
     * </ul>
     *
     * <h2>Package-Private Implementation Details</h2>
     * <p>
     * The following package-private methods are invoked by
     * {@link EfsFile} on the dispatcher thread and should not be
     * called directly:
     * </p>
     * <ul>
     *   <li>
     *     {@link #matches(EfsRow)}: Evaluates whether a newly
     *     inserted row satisfies both the interval and user
     *     query.
     *   </li>
     *   <li>
     *     {@link #isAtEnd(EfsRow)}: Checks if a row has moved
     *     past the interval's ending point.
     *   </li>
     *   <li>
     *     {@link #postRow(EfsRow)}: Dispatches a matching row
     *     to the event callback on the agent's thread.
     *   </li>
     *   <li>
     *     {@link #doClose(Instant, CompletionType)}: Marks
     *     retrieval as completed (if not already) and invokes
     *     completion callback.
     *   </li>
     *   <li>
     *     {@link #intervalQuery(Query, Query)}: Combines
     *      beginning, ending, and user queries into a final
     *      composite query used for event matching.
     *   </li>
     * </ul>
     *
     * @param <E> the event type retrieved (must implement
     * {@link IEfsEvent})
     *
     * @see EfsFileConnection
     * @see EfsFileConnection#retrieve
     * @see EfsFile
     * @see EfsInterval
     * @see RetrievalCompleteEvent
     * @see org.efs.dispatcher.EfsDispatcher
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
        @VisibleForTesting
        /* package */ Retrieval(final int id,
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
            if (doClose(mEventFile.instant(),
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
        } // end of doClose()

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
                    "[id=%d, agent=%s, status=%s, interval=%s, query=%s]",
                    mId,
                    mAgent.name(),
                    (isCompleted() ?
                     "completed (" + mCompletionType + ")" :
                     "active"),
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
        /* package */ Query<EfsRow<E>> intervalQuery(final Query<EfsRow<E>> beginQuery,
                                                     final Query<EfsRow<E>> endQuery)
        {
            mIntervalEndQuery = endQuery;

            mRowQuery = and(beginQuery, endQuery, mUserQuery);

            return (mRowQuery);
        } // end of intervalQuery(Query, Query)

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
         * @param row postRow this row to agent.
         */
        /* package */ void postRow(final EfsRow<E> row)
        {
            try
            {
                EfsDispatcher.dispatch(mEventCB, row, mAgent);
            }
            catch (Exception jex)
            {
                        sLogger.warn(
                            "{}: attempt to post row {} to agent {} failed; event queue full.",
                            (mEventFile.mEventFile).topicKey(),
                            row,
                            mAgent.name());
            }
        } // end of postRow(EfsRow)

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
        /* package */ boolean doClose(final Instant timestamp,
                                      final CompletionType completionType)
        {
            final boolean retcode =
                mCompletionFlag.compareAndSet(false, true);

            // Is this retrieval request completed?
            if (retcode)
            {
                sLogger.debug("Retrieval {}: completed, {}",
                              mId,
                              completionType);

                // No, mark this request as completed and have
                // the event file cancel this request.
                mCompletionType.set(completionType);

                // Remove this retrieval from the agent's list.
                mEventFile.retrievalComplete(this);

                // Let the agent know this retrieval is
                // completed.
                try
                {
                    EfsDispatcher.dispatch(
                        mCompletionCB,
                        new RetrievalCompleteEvent(
                            completionType, timestamp, this),
                        mAgent);
                }
                catch (Exception jex)
                {
                    sLogger.warn(
                        "Failed to dispatch retrieval completion to agent {}",
                        mAgent.name());
                }
            }

            return (retcode);
        } // end of doClose(Instant, CompletionType)
    } // end of class Retrieval
} // end of class EfsFileConnection
