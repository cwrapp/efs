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

import com.google.common.collect.ImmutableSet;
import com.googlecode.cqengine.query.Query;
import static com.googlecode.cqengine.query.QueryFactory.all;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import org.decimal4j.api.Decimal;
import org.decimal4j.immutable.Decimal2f;
import org.decimal4j.scale.Scale2f;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;
import org.efs.dispatcher.IEfsDispatcher.DispatcherType;
import org.efs.dispatcher.config.ThreadType;
import org.efs.event.EfsTopicKey;
import org.efs.event.IEfsEvent;
import org.efs.io.EfsFile.AccessMode;
import org.efs.io.EfsFileConnection.Retrieval;
import org.efs.io.EfsIntervalEndpoint.Clusivity;
import org.efs.io.RetrievalCompleteEvent.CompletionType;
import static org.efs.io.RetrievalCompleteEvent.CompletionType.RETRIEVAL_COMPLETED;
import org.efs.io.TradeEvent.PriceTrend;
import org.efs.logging.AsyncLoggerFactory;
import org.efs.util.DelayedExecution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsFileTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Fixed test timestamp.
     */
    /* package */ static final String TEST_TIME =
        "2026-05-26T11:10:45.000Z";

    /**
     * Use Greenwich Mean Time for testing.
     */
    /* package */ static final ZoneId GMT = ZoneId.of("GMT");

    /* package */ static final String EXCHANGE =
        "/exchange/acme/equity/";

    /**
     * Test trading symbol.
     */
    /* package */ static final String SYMBOL = "ACME-";

    /**
     * {@link EfsFile} policy is
     * {@link EfsFile#DEFAULT_CONNECTION_POLICY}.
     */
    /* package */ static final IConnectionPolicy POLICY =
        EfsFile.DEFAULT_CONNECTION_POLICY;

    /**
     * {@link EfsFile} dispatcher is named {@value}.
     */
    /* package */ static final String FILE_DISPATCHER =
        "file-dispatcher";

    /**
     * Agents use dispatcher named {@value}.
     */
    /* package */ static final String AGENT_DISPATCHER =
        "agent-dispatcher";

    /**
     * Trade retrieval agent name.
     */
    private static final String RETRIEVER_NAME =
        "test-retriever";

    /**
     * Trade publisher agent name is {@value}.
     */
    private static final String PUBLISHER_NAME =
        "test-publisher";

    /**
     * Test agent name is {@value}.
     */
    private static final String AGENT_NAME = "test-agent";

    /**
     * Agent used to post and retrieve tagged trade events is
     * named {@value}.
     */
    private static final String TAG_AGENT_NAME = "tag-agent";

    /**
     * Unregistered agent name is {@value}.
     */
    private static final String UNREGISERED_NAME =
        "test-unregistered";

    /**
     * Exhaust agent name.
     */
    private static final String TEST_EXHAUST = "test-exhaust";

    /**
     * Event queue sizes are {@value}.
     */
    /* package */ static final int EVENT_QUEUE_SIZE = 1_024;

    /**
     * Maximum number of simultaneous connections allowed.
     */
    private static final int MAX_CONNECTIONS = 128;

    /**
     * Maximum number of simultaneeous retrievals allowed.
     */
    private static final int MAX_RETRIEVALS = 512;

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Clock used for testing purposes.
     */
    private static Clock sTestClock;

    /**
     * Interval used for retrieval failure tests.
     */
    private static EfsInterval sInterval;

    /**
     * Query to retrieve all rows.
     */
    private static Query sAllQuery;

    /**
     * Used to generate a unique symbol for each test.
     */
    private static AtomicInteger sSymbolIndex;

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(EfsFileTest.class);

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Trade event file.
     */
    private EfsFile<TradeEvent> mTradeFile;

    /**
     * Agent publishes trades to file.
     */
    private TestPublisher mPublisher;

    /**
     * Agent retrieves trades from file.
     */
    private TestRetriever mRetriever;

    /**
     * Used for efs event file closed tests.
     */
    private TestAgent mTestAgent;

    /**
     * Latest trade symbol.
     */
    private String mSymbol;

    /**
     * Latest trade key for this test.
     */
    private EfsTopicKey<TradeEvent> mTradeKey;

//---------------------------------------------------------------
// Member methods.
//

    /**
     * Main method needed to run performance test.
     * @param args empty arguments list.
     */
    public static void main(final String[] args)
    {}

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void setUpClass()
        throws IOException
    {
        EfsDispatcher.Builder builder;

        if (!EfsDispatcher.isDispatcher(FILE_DISPATCHER))
        {
            builder = EfsDispatcher.builder(FILE_DISPATCHER);

            // Create efs file dispatcher.
            builder.threadType(ThreadType.SPINPARK)
                   .numThreads(1)
                   .priority(10)
                   .spinLimit(2_500_000L)
                   .parkTime(Duration.ofNanos(500L))
                   .dispatcherType(DispatcherType.EFS)
                   .eventQueueCapacity(EVENT_QUEUE_SIZE)
                   .runQueueCapacity(4)
                   .maxEvents(EVENT_QUEUE_SIZE)
                   .build();
        }

        // Create agent dispatcher.
        if (!EfsDispatcher.isDispatcher(AGENT_DISPATCHER))
        {
            builder = EfsDispatcher.builder(AGENT_DISPATCHER);
            builder.threadType(ThreadType.SPINPARK)
                   .numThreads(2)
                   .priority(10)
                   .spinLimit(2_500_000L)
                   .parkTime(Duration.ofNanos(500L))
                   .dispatcherType(DispatcherType.EFS)
                   .eventQueueCapacity(EVENT_QUEUE_SIZE)
                   .runQueueCapacity(4)
                   .maxEvents(EVENT_QUEUE_SIZE)
                   .build();
        }

        sTestClock = Clock.fixed(Instant.parse(TEST_TIME), GMT);

        final EfsIntervalEndpoint beginEndpoint =
            (EfsIndexOffsetEndpoint.builder())
                .indexOffset(0L, Clusivity.INCLUSIVE)
                .build();
        final EfsIntervalEndpoint endEndpoint =
            (EfsIndexOffsetEndpoint.builder())
                .indexOffset(1_000L, Clusivity.EXCLUSIVE)
                .build();

        sInterval =
            (EfsInterval.builder()).beginning(beginEndpoint)
                                   .ending(endEndpoint)
                                   .build();

        sAllQuery = all(TradeEvent.class);

        sSymbolIndex = new AtomicInteger();
    } // end of setUpClass()

    @BeforeEach
    public void setUp()
        throws EfsFileInitializationException
    {
        final String topic;
        final IConnectionPolicy policy =
            (agent, accessMode) ->
            {
                final String agentName = agent.name();
                final boolean retcode;

                retcode =
                    switch (agentName)
                    {
                        case PUBLISHER_NAME ->
                            accessMode == AccessMode.WRITE_ONLY;
                        case RETRIEVER_NAME ->
                            accessMode == AccessMode.READ_ONLY;
                        case AGENT_NAME -> true;
                        case TAG_AGENT_NAME -> true;
                        default -> false;
                    };

                return (retcode);
            };

        mSymbol = SYMBOL + sSymbolIndex.getAndIncrement();
        topic = EXCHANGE + mSymbol;

        mTradeKey = EfsTopicKey.getKey(TradeEvent.class, topic);
        mTradeFile =
            createEventFile(mTradeKey,
                            policy,
                            FILE_DISPATCHER,
                            MAX_CONNECTIONS,
                            MAX_RETRIEVALS);
        mPublisher =
            new TestPublisher(
                PUBLISHER_NAME, mTradeFile, sTestClock);
        mRetriever = new TestRetriever(RETRIEVER_NAME, mTradeFile);
        mTestAgent = new TestAgent(AGENT_NAME,
                                   AccessMode.READ_WRITE,
                                   mTradeFile);

        EfsDispatcher.register(mPublisher, AGENT_DISPATCHER);
        EfsDispatcher.register(mRetriever, AGENT_DISPATCHER);
        EfsDispatcher.register(mTestAgent, AGENT_DISPATCHER);

        mPublisher.open();
        mRetriever.open();
        mTestAgent.open();

        mPublisher.reset(sTestClock);
        mTradeFile.setSystemClock(sTestClock);
    } // end of setUp()

    @AfterEach
    public void tearDown()
    {
        mPublisher.close();
        mRetriever.close();
        mTestAgent.close();

        EfsDispatcher.deregister(mPublisher);
        EfsDispatcher.deregister(mRetriever);
        EfsDispatcher.deregister(mTestAgent);

        mTradeFile.close();
    } // end of tearDown()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    // Failure Tests.

    @Nested
    @DisplayName("EfsFile failure tests")
    public final class EfsFailureTests
    {
        @Test
        @DisplayName("Create event file with null key")
        public void nullTopicKeyTest()
            throws EfsFileInitializationException
        {
            final EfsTopicKey<TradeEvent> key = null;

            assertThatThrownBy(
                () -> EfsFile.builder(key))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsFile.NULL_TOPIC_KEY);
        } // end of nullTopicKeyTest()

        @Test
        @DisplayName("Create event file with null connection policy")
        public void nullConnectionPolicyTest()
        {
            final String dispatcher = FILE_DISPATCHER;
            final IConnectionPolicy policy = null;
            final int maxConnection = MAX_CONNECTIONS;
            final int maxRetrievals = MAX_RETRIEVALS;

            assertThatThrownBy(
                () -> createEventFile(mTradeKey,
                                      policy,
                                      dispatcher,
                                      maxConnection,
                                      maxRetrievals))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsFile.NULL_POLICY);
        } // end of nullConnectionPolicyTest()

        @Test
        @DisplayName("Create event file with null dispatcher")
        public void nullDispatcherTest()
        {
            final IConnectionPolicy policy = POLICY;
            final String dispatcher = null;
            final int maxConnection = MAX_CONNECTIONS;
            final int maxRetrievals = MAX_RETRIEVALS;

            assertThatThrownBy(
                () -> createEventFile(mTradeKey,
                                      policy,
                                      dispatcher,
                                      maxConnection,
                                      maxRetrievals))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsFile.INVALID_DISPATCHER);
        } // end of nullDispatcherTest()

        @Test
        @DisplayName("Create event file with empty dispatcher")
        public void emptyDispatcherTest()
        {
            final IConnectionPolicy policy = POLICY;
            final String dispatcher = "";
            final int maxConnections = MAX_CONNECTIONS;
            final int maxRetrievals = MAX_RETRIEVALS;

            assertThatThrownBy(
                () -> createEventFile(mTradeKey,
                                      policy,
                                      dispatcher,
                                      maxConnections,
                                      maxRetrievals))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsFile.INVALID_DISPATCHER);
        } // end of emptyDispatcherTest()

        @Test
        @DisplayName("Create event file with blank dispatcher")
        public void blankDispatcherTest()
        {
            final IConnectionPolicy policy = POLICY;
            final String dispatcher = "\t";
            final int maxConnections = MAX_CONNECTIONS;
            final int maxRetrievals = MAX_RETRIEVALS;

            assertThatThrownBy(
                () -> createEventFile(mTradeKey,
                                      policy,
                                      dispatcher,
                                      maxConnections,
                                      maxRetrievals))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsFile.INVALID_DISPATCHER);
        } // end of blankDispatcherTest()

        @Test
        @DisplayName("Create event file with unknown dispatcher")
        public void unknownDispatcherTest()
        {
            final IConnectionPolicy policy = POLICY;
            final String dispatcher = "snafu";
            final int maxConnections = MAX_CONNECTIONS;
            final int maxRetrievals = MAX_RETRIEVALS;
            final String message =
                String.format(
                    EfsFile.UNKNOWN_DISPATCHER, dispatcher);

            assertThatThrownBy(
                () -> createEventFile(mTradeKey,
                                      policy,
                                      dispatcher,
                                      maxConnections,
                                      maxRetrievals))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
        } // end of unknownDispatcherTest()

        @Test
        @DisplayName("Create event file with zero conneciton limit")
        public void zeroConnectionLimitTest()
        {
            final IConnectionPolicy policy = POLICY;
            final String dispatcher = FILE_DISPATCHER;
            final int maxConnections = 0;
            final int maxRetrievals = 1_024;

            assertThatThrownBy(
                () -> createEventFile(mTradeKey,
                                      policy,
                                      dispatcher,
                                      maxConnections,
                                      maxRetrievals))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsFile.INVALID_LIMIT);
        } // end of zeroConnectionLimitTest()

        @Test
        @DisplayName("Create event file with zero retrieval limit")
        public void zeroRetrievalLimitTest()
        {
            final IConnectionPolicy policy = POLICY;
            final String dispatcher = FILE_DISPATCHER;
            final int maxConnections = 128;
            final int maxRetrievals = 0;

            assertThatThrownBy(
                () -> createEventFile(mTradeKey,
                                      policy,
                                      dispatcher,
                                      maxConnections,
                                      maxRetrievals))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsFile.INVALID_LIMIT);
        } // end of zeroRetrievalLimitTest()

        @Test
        @DisplayName("Duplicate event file creation")
        public void duplicateEventFileCreationTest()
        {
            final String message =
                String.format(EfsFile.FILE_PREVIOUSLY_CREATED,
                              mTradeKey);

            assertThatThrownBy(
                () -> createEventFile(mTradeKey,
                                      POLICY,
                                      FILE_DISPATCHER,
                                      MAX_CONNECTIONS,
                                      MAX_RETRIEVALS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(message);
        } // end of duplicateEventFileCreationTest()

        @Test
        @DisplayName ("get file with null topic key")
        public void getNullTopicKey()
        {
            final EfsTopicKey<TradeEvent> key = null;

            assertThatThrownBy(() -> EfsFile.getEventFile(key))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsFile.NULL_TOPIC_KEY);
        } // end of getNullTopicKey()

        @Test
        @DisplayName ("get file with unknown key")
        public void getUnknownTopicKey()
        {
            final String topic = "/foo/bar";
            final EfsTopicKey<TradeEvent> key =
                EfsTopicKey.getKey(TradeEvent.class, topic);
            final String text =
                String.format(EfsFile.NO_SUCH_FILE, key);

            assertThatThrownBy(() -> EfsFile.getEventFile(key))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(text);
        } // end of getUnknownTopicKey()

        @Test
        public void connectNullMode()
        {
            final AccessMode mode = null;
            final IEfsAgent agent = mPublisher;

            assertThatThrownBy(() -> mTradeFile.connect(mode, agent))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsFile.NULL_ACCESS_MODE);
        } // end of connectNullMode()

        @Test
        public void connectNullAgent()
        {
            final AccessMode mode = AccessMode.READ_WRITE;
            final IEfsAgent agent = null;

            assertThatThrownBy(() -> mTradeFile.connect(mode, agent))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsFile.NULL_AGENT);
        } // end of connectNullAgent()

        @Test
        public void connectUnregisteredAgent()
        {
            final AccessMode mode = AccessMode.READ_WRITE;
            final IEfsAgent agent =
                new AbstractTestAgent(
                    UNREGISERED_NAME, mode, mTradeFile)
                {};
            final String message =
                String.format(EfsFile.UNREGISTERED_AGENT,
                              UNREGISERED_NAME);

            assertThatThrownBy(() -> mTradeFile.connect(mode, agent))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(message);
        } // end of connectUnregisteredAgent()

        @Test
        @DisplayName("Agent, access mode connection not allowed")
        public void connectPolicyFailure()
            throws EfsFileInitializationException
        {
            final String dispatcher = FILE_DISPATCHER;
            final IConnectionPolicy policy =
                (agent, accessMode) -> false;
            final int maxConnection = MAX_CONNECTIONS;
            final int maxRetrievals = MAX_RETRIEVALS;
            final String agentName = "snafu";
            final AccessMode accessMode = AccessMode.READ_ONLY;
            final TestAgent agent =
                new TestAgent(agentName, accessMode, mTradeFile);
            final String text =
                String.format(
                    EfsFile.ACCESS_DENIED,
                    agent.name(),
                    mTradeKey,
                    accessMode);

            EfsDispatcher.register(agent, AGENT_DISPATCHER);

            assertThatThrownBy(() -> agent.open())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(text);

            EfsDispatcher.deregister(agent);
        } // end of connectPolicyFailure()

        @Test
        @DisplayName ("connect to closed filed")
        public void connectClosedFile()
            throws EfsFileInitializationException
        {
            final AbstractTestAgent agent = mPublisher;
            final AccessMode mode = AccessMode.READ_WRITE;
            final String symbol = "EQTC";
            final String topic = EXCHANGE + symbol;
            final EfsTopicKey<TradeEvent> key =
                EfsTopicKey.getKey(TradeEvent.class, topic);
            final EfsFile<TradeEvent> file =
                createEventFile(key,
                                POLICY,
                                FILE_DISPATCHER,
                                MAX_CONNECTIONS,
                                MAX_RETRIEVALS);
            final String text =
                String.format(EfsFile.CLOSED_FILE, key.toString());

            file.close();

            assertThat(file.isOpen()).isFalse();

            assertThatThrownBy(() -> file.connect(mode, agent))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(text);
        } // end of connectClosedFile()

        @Test
        public void addNullEventTest()
        {
            final TradeEvent event = null;

            assertThatThrownBy(
                () -> mPublisher.add(event))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsFileConnection.NULL_EVENT);
        } // end of addNullEventTest()

        @Test
        public void addInvalidAccess()
        {
            final TradeEvent trade =
                (TradeEvent.builder()).symbol(mSymbol)
                                      .price(Decimal2f.valueOf(1.23d))
                                      .size(1_000)
                                      .priceTrend(PriceTrend.DOWN)
                                      .volume(4_300_000)
                                      .build();
            final String message =
                String.format(EfsFileConnection.READ_ONLY_ACCESS,
                              mTradeFile.name());

            assertThatThrownBy(
                () -> mRetriever.add(trade))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(message);
        } // end of addInvalidAccess()

        @Test
        public void addAccessClosed()
        {
            final AbstractTestAgent agent = mTestAgent;
            final AccessMode mode = agent.accessMode();
            final TradeEvent trade =
                (TradeEvent.builder()).symbol(mSymbol)
                                      .price(Decimal2f.valueOf(1.23d))
                                      .size(1_000)
                                      .priceTrend(PriceTrend.DOWN)
                                      .volume(4_300_000)
                                      .build();
            final EfsFileConnection<TradeEvent> eventFile =
                mTradeFile.connect(mode, agent);
            final String message =
                String.format(
                    EfsFileConnection.CLOSED_CONNECTION,
                    mTradeFile.name());

            eventFile.close();

            assertThatThrownBy(() -> eventFile.add(trade))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(message);
        } // end of addAccessClosed()

        @Test
        @SuppressWarnings ("unchecked")
        public void retrieveNullIntervalTest()
        {
            final EfsInterval interval = null;
            final Query query = all(TradeEvent.class);
            final Consumer<EfsRow<TradeEvent>> eventCB =
                mRetriever::onEvent;
            final Consumer<RetrievalCompleteEvent<TradeEvent>> doneCB =
                mRetriever::onDone;

            assertThatThrownBy(
                () -> mRetriever.retrieve(interval,
                                           query,
                                           eventCB,
                                           doneCB))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsFileConnection.NULL_INTERVAL);
        } // end of retrieveNullIntervalTest()

        @Test
        @SuppressWarnings ("unchecked")
        public void retrieveNullQueryTest()
        {
            final EfsInterval interval = sInterval;
            final Query query = null;
            final Consumer<EfsRow<TradeEvent>> eventCB =
                mRetriever::onEvent;
            final Consumer<RetrievalCompleteEvent<TradeEvent>> doneCB =
                mRetriever::onDone;

            assertThatThrownBy(
                () -> mRetriever.retrieve(interval,
                                           query,
                                           eventCB,
                                           doneCB))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsFileConnection.NULL_QUERY);
        } // end of retrieveNullQueryTest()

        @Test
        @SuppressWarnings ("unchecked")
        public void retrieveNullEventCBTest()
        {
            final EfsInterval interval = sInterval;
            final Query query = all(TradeEvent.class);
            final Consumer<EfsRow<TradeEvent>> eventCB = null;
            final Consumer<RetrievalCompleteEvent<TradeEvent>> doneCB =
                mRetriever::onDone;

            assertThatThrownBy(
                () -> mRetriever.retrieve(interval,
                                           query,
                                           eventCB,
                                           doneCB))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsFileConnection.NULL_EVENT_CALLBACK);
        } // end of retrieveNullEventCBTest()

        @Test
        @SuppressWarnings ("unchecked")
        public void retrieveNullCompletionCBTest()
        {
            final EfsInterval interval = sInterval;
            final Query query = all(TradeEvent.class);
            final Consumer<EfsRow<TradeEvent>> eventCB =
                mRetriever::onEvent;
            final Consumer<RetrievalCompleteEvent<TradeEvent>> doneCB =
                null;

            assertThatThrownBy(
                () -> mRetriever.retrieve(interval,
                                           query,
                                           eventCB,
                                           doneCB))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsFileConnection.NULL_DONE_CALLBACK);
        } // end of retrieveNullCompletionCBTest()

        @Test
        @SuppressWarnings ("unchecked")
        public void retrieveInvalidAccess()
        {
            final EfsInterval interval = sInterval;
            final Query query = all(TradeEvent.class);
            final Consumer<EfsRow<TradeEvent>> eventCB =
                mRetriever::onEvent;
            final Consumer<RetrievalCompleteEvent<TradeEvent>> doneCB =
                mRetriever::onDone;
            final String message =
                String.format(EfsFileConnection.WRITE_ONLY_ACCESS,
                              mTradeFile.name());

            assertThatThrownBy(
                () -> mPublisher.retrieve(interval,
                                          query,
                                          eventCB,
                                          doneCB))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(message);
        } // end of retrieveInvalidAccess()

        @Test
        @SuppressWarnings ("unchecked")
        public void retrieveAccessClosed()
        {
            final AbstractTestAgent agent = mTestAgent;
            final AccessMode mode = agent.accessMode();
            final EfsInterval interval = sInterval;
            final Query query = all(TradeEvent.class);
            final Consumer<EfsRow<TradeEvent>> eventCB =
                mRetriever::onEvent;
            final Consumer<RetrievalCompleteEvent<TradeEvent>> doneCB =
                mRetriever::onDone;
            final EfsFileConnection<TradeEvent> eventFile =
                mTradeFile.connect(mode, agent);
            final String message =
                String.format(
                    EfsFileConnection.CLOSED_CONNECTION,
                    mTradeFile.name());

            eventFile.close();

            assertThatThrownBy(
                () -> eventFile.retrieve(interval,
                                         query,
                                         eventCB,
                                         doneCB))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(message);
        } // end of retrieveAccessClosed()

        @Test
        @DisplayName ("null field name")
        public void fieldNameNull()
        {
            final String field = null;

            assertThatThrownBy(() -> mTradeFile.attribute(field))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsFile.INVALID_FIELD);
        } // end of fieldNameNull()

        @Test
        @DisplayName ("empty field name")
        public void fieldNameEmpty()
        {
            final String field = "";

            assertThatThrownBy(() -> mTradeFile.attribute(field))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsFile.INVALID_FIELD);
        } // end of fieldNameEmpty()

        @Test
        @DisplayName ("blank field name")
        public void fieldNameBlank()
        {
            final String field = "\t";

            assertThatThrownBy(() -> mTradeFile.attribute(field))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsFile.INVALID_FIELD);
        } // end of fieldNameBlank()

        @Test
        @DisplayName ("unknown field name")
        public void fieldNameUnknown()
        {
            final String field = "fubar";
            final String text =
                String.format(EfsFile.UNKNOWN_FIELD, field);

            assertThatThrownBy(() -> mTradeFile.attribute(field))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(text);
        } // end of fieldNameUnknown()

        @Test
        @DisplayName ("add on closed file")
        public void addOnClosedFile()
        {
            final TradeEvent.Builder tradeBuilder =
                TradeEvent.builder();
            final Decimal2f price =
                Decimal2f.valueOfUnscaled(4321, 2);
            final int size = 200;
            final TradeEvent trade =
                tradeBuilder.symbol(mSymbol)
                            .price(price)
                            .size(size)
                            .priceTrend(PriceTrend.DOWN)
                            .volume(103_700)
                            .build();

            // Close file from underneath publishing agent.
            mTradeFile.close();

            // Note: this test will always fail with an
            // IllegalStateException but the reason may be
            // either that event file is closed or no longer
            // registered with dispatcher. So exception message
            // cannot be checked.
            assertThatThrownBy(
                () -> mPublisher.add(trade))
                .isInstanceOf(IllegalStateException.class);
        } // end of addOnClosedFile()

        @Test
        @DisplayName("event and completion dispatch failure")
        @SuppressWarnings({"unchecked"})
        public void eventCompletionDispatchFailure()
        {
            final EfsFile.Metrics metrics =
                new EfsFile.Metrics<>(
                    mTradeKey, sTestClock.instant());
            final Decimal2f price =
                Decimal2f.valueOfUnscaled(321, 2);
            final TradeEvent trade =
                (TradeEvent.builder()).symbol(mSymbol)
                                      .price(price)
                                      .size(500)
                                      .priceTrend(PriceTrend.ZERO_MINUS)
                                      .volume(123_700)
                                      .build();
            final Set<Integer> tags = ImmutableSet.of();
            final int requestId = 12345;
            final Retrieval<TradeEvent> request =
                new Retrieval<>(requestId,
                                mTestAgent.connection(),
                                mTestAgent,
                                sInterval,
                                sAllQuery,
                                mTestAgent::onEvent,
                                mTestAgent::onDone);
            final long timeDelta = 10L;
            final int numEvents = (EVENT_QUEUE_SIZE + 2);
            final CountDownLatch continueSignal =
                new CountDownLatch(1);
            final CountDownLatch doneSignal = new CountDownLatch(1);
            int index;
            Instant timestamp = sTestClock.instant();
            EfsRow<TradeEvent> row;

            mTestAgent.setContinueSignal(continueSignal);
            mTestAgent.setDoneSignal(doneSignal);

            try
            {
            for (index = 0; index < numEvents; ++index)
            {
                row =
                    EfsRow.createRow(
                        timestamp, index, tags, trade);

                request.postRow(row, metrics);

                timestamp = timestamp.plusMillis(timeDelta);
            }
            }
            catch (IllegalStateException statex)
            {
                // Ignore. Event queue overflow is expected.
            }

            request.doClose(timestamp,
                            CompletionType.RETRIEVAL_COMPLETED);

            continueSignal.countDown();

            assertThat(metrics.dispatchFailures())
                .isGreaterThan(0L);
        } // end of eventCompletionDispatchFailure()
    } // end of class EfsFailureTests

    // Success Tests.

    @Nested
    @DisplayName("EfsFile open, close tests")
    public final class EfsFileOpenCloseTests
    {
        @Test
        @DisplayName("access mask test")
        public void accessMaskTest()
        {
            AccessMode mode;

            mode = AccessMode.READ_ONLY;
            assertThat(mode.accessMask()).isNotZero();
            assertThat(mode.isCompatible(AccessMode.WRITE_ONLY)).isFalse();
            assertThat(mode.isCompatible(AccessMode.READ_WRITE)).isTrue();

            mode = AccessMode.WRITE_ONLY;
            assertThat(mode.accessMask()).isNotZero();
            assertThat(mode.isCompatible(AccessMode.READ_ONLY)).isFalse();
            assertThat(mode.isCompatible(AccessMode.READ_WRITE)).isTrue();

            mode = AccessMode.READ_WRITE;
            assertThat(mode.accessMask()).isNotZero();
            assertThat(mode.isCompatible(AccessMode.READ_ONLY)).isTrue();
            assertThat(mode.isCompatible(AccessMode.WRITE_ONLY)).isTrue();
        } // end of accessMaskTest()

        @Test
        @DisplayName("open, examine, close EfsFileConnection")
        @SuppressWarnings ("unchecked")
        public void openCloseFileConnection()
            throws Exception
        {
            final TestAgent agent = mTestAgent;
            final EfsInterval pastInterval =
                (EfsInterval.builder())
                    .beginning(
                        (EfsIndexOffsetEndpoint.builder())
                            .indexOffset(-20, Clusivity.EXCLUSIVE)
                            .build())
                    .ending(
                        (EfsIndexOffsetEndpoint.builder())
                            .indexOffset(-10, Clusivity.EXCLUSIVE)
                            .build())
                    .build();
            final EfsInterval interval = sInterval;
            final Query query = sAllQuery;
            final Retrieval<TradeEvent> retrieval;
            final CountDownLatch continueSignal =
                new CountDownLatch(1);
            final CountDownLatch doneSignal = new CountDownLatch(1);
            final EfsFileConnection<TradeEvent> connection =
                agent.connection();

            assertThat(connection.isOpen()).isTrue();
            assertThat(connection.agent()).isSameAs(agent);
            assertThat(connection.accessMode())
                .isEqualTo(agent.accessMode());

            // Retrieve from empty event file first.
            agent.setContinueSignal(continueSignal);
            agent.setDoneSignal(doneSignal);
            agent.retrieve(pastInterval, query);

            // Wait here for test agent to be informed that retrieval
            // request is completed.
            try
            {
                doneSignal.await(10L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}

            // Now add trades to file.
            postTrades();

            agent.setContinueSignal(continueSignal);
            agent.setDoneSignal(doneSignal);
            retrieval = agent.retrieve(interval, query);

            // Create retrieval, examine, and doClose.
            final String text =
                String.format(
                    "[id=%d, agent=%s, status=active, interval=%s, query=%s]",
                    retrieval.id(),
                    agent.name(),
                    interval,
                    query);

            assertThat(retrieval).isNotNull();
            assertThat(retrieval.isCompleted()).isFalse();
            assertThat(retrieval.completionType()).isNull();
            assertThat(retrieval.agent()).isSameAs(agent);
            assertThat(retrieval.interval()).isEqualTo(interval);
            assertThat(retrieval.toString()).isEqualTo(text);

            retrieval.close();

            // Release test agent so it may receive the completion
            // callback.
            continueSignal.countDown();

            // Wait here for test agent to be informed that retrieval
            // request is completed.
            try
            {
                doneSignal.await(5L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}

            assertThat(retrieval.isCompleted()).isTrue();
            assertThat(retrieval.completionType())
                .isEqualTo(CompletionType.USER_CANCEL);

            agent.close();
        } // openCloseFileConnection()

        @Test
        @DisplayName("get previously opened file")
        public void getPreviouslyOpenedFile()
        {
            final EfsFile<TradeEvent> file =
                EfsFile.getEventFile(mTradeKey);

            assertThat(file).isSameAs(mTradeFile);
        } // end of getPreviouslyOpenedFile()
    } // end of class EfsFileOpenCloseTests

    @Nested
    @DisplayName("EfsFile add, retrieve tests")
    public final class EfsFileAddRetrieveTests
    {
    //    @Disabled
        @Test
        @DisplayName("EfsFile retrieve past events only")
        public void pastRetrievalTest()
        {
            final AtomicBoolean publishFlag =
                new AtomicBoolean(true);
            final Duration runTime = Duration.ofSeconds(3L);
            final Decimal2f initialPrice = mPublisher.price();
            CountDownLatch doneSignal = new CountDownLatch(1);

            // Start by adding trades to file.
            mPublisher.postTrades(
                mSymbol, runTime, publishFlag, doneSignal);

            try
            {
                doneSignal.await(5L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}

            // Have the agent retrieve trades from file.
            final Instant endTime = mPublisher.instant();
            final int numTrades = mPublisher.tradeCount();
            final long tIndex0 = Math.negateExact(numTrades - 100);
            final Instant tsIndex1 = (endTime.minusSeconds(2L));
            final Decimal<Scale2f> maxPrice =
                initialPrice.add(Decimal2f.valueOfUnscaled(1, 0));
            final int minSize = 500;
            final EfsIntervalEndpoint beginning =
                (EfsIndexOffsetEndpoint.builder())
                    .indexOffset(tIndex0, Clusivity.INCLUSIVE)
                    .build();
            final EfsIntervalEndpoint ending =
                (EfsTimeEndpoint.builder(endTime))
                    .time(tsIndex1, Clusivity.EXCLUSIVE)
                    .build();
            final EfsInterval interval =
                (EfsInterval.builder()).beginning(beginning)
                                       .ending(ending)
                                       .build();

            doneSignal = new CountDownLatch(1);
            mRetriever.retrieveTrades(interval,
                                      maxPrice,
                                      minSize,
                                      doneSignal);

            try
            {
                doneSignal.await(5L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}

            assertThat(mRetriever.tradesReceived()).isGreaterThan(0);
        } // end of pastRetrievalTest()

    //    @Disabled
        @Test
        @DisplayName("EfsFile future event retrieval")
        public void futureRetrievalTest()
        {
            final AtomicBoolean publishFlag =
                new AtomicBoolean(true);
            final Duration runTime = Duration.ofSeconds(10L);
            final Decimal<Scale2f> initialPrice = mPublisher.price();
            final Decimal<Scale2f> maxPrice =
                initialPrice.add(Decimal2f.valueOfUnscaled(10, 0));
            final int minSize = 100;
            final long tIndex0 = 1L;
            final long tsIndex1 = 100L;
            final EfsIntervalEndpoint beginning =
                (EfsIndexOffsetEndpoint.builder())
                    .indexOffset(tIndex0, Clusivity.INCLUSIVE)
                    .build();
            final EfsIntervalEndpoint ending =
                (EfsIndexOffsetEndpoint.builder())
                    .indexOffset(tsIndex1, Clusivity.EXCLUSIVE)
                    .build();
            final EfsInterval interval =
                (EfsInterval.builder()).beginning(beginning)
                                       .ending(ending)
                                       .build();
            final CountDownLatch doneSignal = new CountDownLatch(2);

            sLogger.info(
                "Future test: retrieving interval {}, initial price {}, max price {}.",
                interval,
                initialPrice,
                maxPrice);

            // Start retreival first and then start publishing.
            mRetriever.retrieveTrades(interval,
                                      maxPrice,
                                      minSize,
                                      doneSignal);
            mPublisher.postTrades(
                mSymbol, runTime, publishFlag, doneSignal);

            try
            {
                doneSignal.await(5L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}

            // Stop publishing in case it is still running.
            publishFlag.set(false);

            assertThat(mRetriever.tradesReceived()).isGreaterThan(0);
        } // end of futureRetrievalTest()

    //    @Disabled
        @Test
        @DisplayName("EfsFile past and future event retrieval")
        public void pastAndFutureRetrievalTest()
        {
            final long runTimeSeconds = 15L;
            final AtomicBoolean publishFlag =
                new AtomicBoolean(true);
            Duration runTime = Duration.ofSeconds(3L);
            final Decimal<Scale2f> initialPrice = mPublisher.price();
            final Decimal<Scale2f> maxPrice =
                initialPrice.add(Decimal2f.valueOfUnscaled(1, 0));
            final int minSize = 500;
            CountDownLatch doneSignal = new CountDownLatch(1);

            // Start by adding trades to file.
            mPublisher.postTrades(
                mSymbol, runTime, publishFlag, doneSignal);

            try
            {
                doneSignal.await(5L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}

            final int numTrades = mPublisher.tradeCount();
            final long tIndex0 = Math.negateExact(numTrades - 100);
            final Duration tsIndex1 =
                Duration.ofSeconds(runTimeSeconds - 2L);
            final EfsIntervalEndpoint beginning =
                (EfsIndexOffsetEndpoint.builder())
                    .indexOffset(tIndex0, Clusivity.INCLUSIVE)
                    .build();
            final EfsIntervalEndpoint ending =
                (EfsDurationEndpoint.builder())
                    .timeOffset(tsIndex1, Clusivity.EXCLUSIVE)
                    .build();
            final EfsInterval interval =
                (EfsInterval.builder()).beginning(beginning)
                                       .ending(ending)
                                       .build();
            Retrieval<TradeEvent> request;
            String text;

            runTime = Duration.ofSeconds(runTimeSeconds);
            doneSignal = new CountDownLatch(2);

            sLogger.info(
                "Past & future test: retrieving interval {}, initial price {}, max price {}.",
                interval,
                initialPrice,
                maxPrice);

            // Start retreival first and then start publishing.
            mRetriever.retrieveTrades(interval,
                                      maxPrice,
                                      minSize,
                                      doneSignal);

            request = mRetriever.request();
            text =
                String.format(
                    "[id=%d, agent=%s, status=active, interval=%s, query=%s]",
                    request.id(),
                    mRetriever.name(),
                    interval,
                    mRetriever.query());

            assertThat(request.toString()).isEqualTo(text);

            mPublisher.postTrades(
                mSymbol, runTime, publishFlag, doneSignal);

            try
            {
                doneSignal.await(30L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}

            // Stop publishing in case it is still running.
            publishFlag.set(false);

            assertThat(mRetriever.tradesReceived()).isGreaterThan(0);

            text =
                String.format(
                    "[id=%d, agent=%s, status=completed (%s), interval=%s, query=%s]",
                    request.id(),
                    mRetriever.name(),
                    request.completionType(),
                    interval,
                    mRetriever.query());

            assertThat(request.toString()).isEqualTo(text);

            final EfsFile.Metrics metrics = mTradeFile.metrics();

            assertThat(metrics.topicKey()).isSameAs(mTradeKey);
            assertThat(metrics.openTime()).isNotNull();
            assertThat(metrics.eventsAdded()).isGreaterThan(0L);
            assertThat(metrics.retrievalsStarted())
                .isGreaterThan(0L);
            assertThat(metrics.retrievalsCompleted())
                .isGreaterThan(0L);
            assertThat(metrics.retrievalsInProgress()).isZero();
        } // end of pastAndFutureRetrievalTest()

        @Test
        @DisplayName("EfsFile state transition test")
        public void efsFileStateTest()
            throws EfsFileInitializationException
        {
            final AtomicBoolean publishFlag =
                new AtomicBoolean(true);
            final Duration runTime = Duration.ofSeconds(10L);
            final String topic = EXCHANGE + "FUBR";
            final EfsTopicKey<TradeEvent> key =
                EfsTopicKey.getKey(TradeEvent.class, topic);
            final EfsFile<TradeEvent> tradeFile =
                createEventFile(key,
                                POLICY,
                                FILE_DISPATCHER,
                                MAX_CONNECTIONS,
                                MAX_RETRIEVALS);
            final TestPublisher publisher =
                new TestPublisher(
                    "my-publisher", tradeFile, sTestClock);
            final TestRetriever retriever =
                new TestRetriever("my-retriever", tradeFile);
            CountDownLatch doneSignal = new CountDownLatch(1);

            assertThat(EfsFile.exists(key)).isTrue();
            assertThat(tradeFile.isOpen()).isTrue();
            assertThat(tradeFile.rowCount()).isZero();
            assertThat(tradeFile.topicKey()).isEqualTo(key);

            EfsDispatcher.register(publisher, AGENT_DISPATCHER);
            EfsDispatcher.register(retriever, AGENT_DISPATCHER);

            publisher.open();
            retriever.open();

            publisher.postTrades(
                mSymbol, runTime, publishFlag, doneSignal);

            try
            {
                doneSignal.await(5L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}

            // Stop publishing in case it is still running.
            publishFlag.set(false);

            final long rowCount = publisher.rowCount();

            assertThat(rowCount).isGreaterThan(0L);

            final EfsIntervalEndpoint beginning =
                (EfsIndexOffsetEndpoint.builder())
                    .indexOffset(rowCount, Clusivity.INCLUSIVE)
                    .build();
            final EfsIntervalEndpoint ending =
                (EfsIndexOffsetEndpoint.builder())
                    .indexOffset((rowCount + 100L),
                                 Clusivity.EXCLUSIVE)
                    .build();
            final EfsInterval interval =
                (EfsInterval.builder()).beginning(beginning)
                                       .ending(ending)
                                       .build();
            final Decimal<Scale2f> maxPrice =
                (publisher.price()).add(
                    Decimal2f.valueOfUnscaled(10, 0));
            final int minSize = 100;

            // Retrieve future trades which will never be published.
            doneSignal = new CountDownLatch(1);
            retriever.retrieveTrades(interval,
                                      maxPrice,
                                      minSize,
                                      doneSignal);

            // Allow time for retrieval to be put into place.
            DelayedExecution.waitUntil(Duration.ofSeconds(1L));

            // Close event file which will cancel the retrieval.
            tradeFile.close();

            try
            {
                doneSignal.await(2L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}

            assertThat(EfsFile.exists(key)).isFalse();
            assertThat(tradeFile.isOpen()).isFalse();
            assertThat(tradeFile.rowCount()).isGreaterThan(0L);
            assertThat(retriever.tradesReceived()).isZero();
            assertThat(retriever.completionReason())
                .isEqualTo(CompletionType.FILE_CLOSED);
        } // end of efsFileStateTest()

        @Test
        @DisplayName("cancel retrieval on close")
        public void cancelRetrievalOnClose()
        {
            final Decimal<Scale2f> initialPrice = mPublisher.price();
            final Decimal<Scale2f> maxPrice =
                initialPrice.add(Decimal2f.valueOfUnscaled(10, 0));
            final int minSize = 100;
            final long tIndex0 = 1L;
            final long tsIndex1 = 100L;
            final EfsIntervalEndpoint beginning =
                (EfsIndexOffsetEndpoint.builder())
                    .indexOffset(tIndex0, Clusivity.INCLUSIVE)
                    .build();
            final EfsIntervalEndpoint ending =
                (EfsIndexOffsetEndpoint.builder())
                    .indexOffset(tsIndex1, Clusivity.EXCLUSIVE)
                    .build();
            final EfsInterval interval =
                (EfsInterval.builder()).beginning(beginning)
                                       .ending(ending)
                                       .build();
            final CountDownLatch doneSignal = new CountDownLatch(1);

            mRetriever.retrieveTrades(interval,
                                      maxPrice,
                                      minSize,
                                      doneSignal);

            mRetriever.close();

            try
            {
                doneSignal.await(5L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}

            assertThat(mRetriever.completionReason())
                .isEqualTo(CompletionType.CONNECTION_CLOSED);
        } // end of cancelRetrievalOnClose()

        @Test
        @DisplayName("exceed active retrieval limit")
        @SuppressWarnings({"unchecked"})
        public void retrievalExceedsLimit()
            throws EfsFileInitializationException
        {
            final int maxConnections = 5;
            final int maxRetrievals = 1;
            final EfsIntervalEndpoint beginning =
                (EfsIndexFixedEndpoint.builder(0))
                    .fixedIndex(0L, Clusivity.INCLUSIVE)
                    .build();
            final EfsIntervalEndpoint ending =
                (EfsIndexFixedEndpoint.builder(0))
                    .endNever()
                    .build();
            final EfsInterval interval =
                (EfsInterval.builder()).beginning(beginning)
                                       .ending(ending)
                                       .build();
            CountDownLatch continueSignal =
                new CountDownLatch(1);
            CountDownLatch doneSignal = new CountDownLatch(1);

            // 1. Close existing trade and create new file with
            //    size 1 retrieval limit.
            mTradeFile.close();
            mTradeFile = createEventFile(mTradeKey,
                                         POLICY,
                                         FILE_DISPATCHER,
                                         maxConnections,
                                         maxRetrievals);
            mTradeFile.setSystemClock(sTestClock);

            // 2. Close, create, and open first test agent.
            mTestAgent.close();
            EfsDispatcher.deregister(mTestAgent);
            mTestAgent =
                new TestAgent(AGENT_NAME,
                              AccessMode.READ_WRITE,
                              mTradeFile);
            EfsDispatcher.register(mTestAgent, AGENT_DISPATCHER);
            mTestAgent.open();

            // 3. Create and open second test agent.
            final String agentName = AGENT_NAME + "-1";
            final TestAgent testAgent1 =
                new TestAgent(agentName,
                              AccessMode.READ_WRITE,
                              mTradeFile);

            EfsDispatcher.register(testAgent1, AGENT_DISPATCHER);
            testAgent1.open();

            // 4. Have test agent 0 retrieve all event from now
            //    to forever.
            mTestAgent.setContinueSignal(continueSignal);
            mTestAgent.setDoneSignal(doneSignal);
            mTestAgent.retrieve(interval, sAllQuery);

            // 5. Have test agent 1 also perform a retrieval.
            mTestAgent.setContinueSignal(continueSignal);
            mTestAgent.setDoneSignal(doneSignal);
            testAgent1.retrieve(interval, sAllQuery);

            continueSignal.countDown();

            // 6. Verify that test agent 1 retrieval completed
            //    with reason RESOURCE_EXHAUSTED.
            try
            {
                doneSignal.await(1L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}

            assertThat(testAgent1.isCompleted()).isTrue();
            assertThat(testAgent1.completionType())
                .isEqualTo(CompletionType.RESOURCE_EXHAUSTED);
        } // end of retrievalExceedsLimit()

        @Test
        @DisplayName("intervals test")
        public void intervalTest()
        {
            postTrades();

            final Instant now = mPublisher.instant();
            final Instant beginTime = now.minusSeconds(2L);
            final Instant endTime = now.minusSeconds(1L);
            final Duration beginTimeOffset = Duration.ofSeconds(-2L);
            final Duration endTimeOffset = Duration.ofSeconds(-1L);
            final int beginIndexOffset = -50;
            final int endIndexOffset = -20;

            // [fixed time, fixed time]
            EfsIntervalEndpoint beginEndpoint =
                (EfsTimeEndpoint.builder(now))
                    .time(beginTime, Clusivity.INCLUSIVE)
                    .build();
            EfsIntervalEndpoint endEndpoint =
                (EfsTimeEndpoint.builder(now))
                    .time(endTime, Clusivity.INCLUSIVE)
                    .build();
            EfsInterval interval =
                (EfsInterval.builder()).beginning(beginEndpoint)
                                       .ending(endEndpoint)
                                       .build();

            retrieveTrades(interval);

            // (fixed time, fixed time)
            beginEndpoint =
                (EfsTimeEndpoint.builder(now))
                    .time(beginTime, Clusivity.EXCLUSIVE)
                    .build();
            endEndpoint =
                (EfsTimeEndpoint.builder(now))
                    .time(endTime, Clusivity.EXCLUSIVE)
                    .build();
            interval =
                (EfsInterval.builder()).beginning(beginEndpoint)
                                       .ending(endEndpoint)
                                       .build();

            retrieveTrades(interval);

            // [time offset, time offset]
            beginEndpoint =
                (EfsDurationEndpoint.builder())
                    .timeOffset(beginTimeOffset, Clusivity.INCLUSIVE)
                    .build();
            endEndpoint =
                (EfsDurationEndpoint.builder())
                    .timeOffset(endTimeOffset, Clusivity.INCLUSIVE)
                    .build();
            interval =
                (EfsInterval.builder()).beginning(beginEndpoint)
                                       .ending(endEndpoint)
                                       .build();

            retrieveTrades(interval);

            // (time offset, time offset)
            beginEndpoint =
                (EfsDurationEndpoint.builder())
                    .timeOffset(beginTimeOffset, Clusivity.EXCLUSIVE)
                    .build();
            endEndpoint =
                (EfsDurationEndpoint.builder())
                    .timeOffset(endTimeOffset, Clusivity.EXCLUSIVE)
                    .build();
            interval =
                (EfsInterval.builder()).beginning(beginEndpoint)
                                       .ending(endEndpoint)
                                       .build();

            retrieveTrades(interval);

            // [index offset, index offset]
            beginEndpoint =
                (EfsIndexOffsetEndpoint.builder())
                    .indexOffset(beginIndexOffset,
                                 Clusivity.INCLUSIVE)
                    .build();
            endEndpoint =
                (EfsIndexOffsetEndpoint.builder())
                    .indexOffset(endIndexOffset, Clusivity.INCLUSIVE)
                    .build();
            interval =
                (EfsInterval.builder()).beginning(beginEndpoint)
                                       .ending(endEndpoint)
                                       .build();

            retrieveTrades(interval);

            // (index offset, index offset)
            beginEndpoint =
                (EfsIndexOffsetEndpoint.builder())
                    .indexOffset(beginIndexOffset,
                                 Clusivity.EXCLUSIVE)
                    .build();
            endEndpoint =
                (EfsIndexOffsetEndpoint.builder())
                    .indexOffset(endIndexOffset, Clusivity.EXCLUSIVE)
                    .build();
            interval =
                (EfsInterval.builder()).beginning(beginEndpoint)
                                       .ending(endEndpoint)
                                       .build();

            retrieveTrades(interval);
        } // end of intervalTest()
    } // end of class EfsFileAddRetrieveTests

    @Nested
    @DisplayName("EfsFile tagged event tests")
    public final class EfsFileTaggedEventsTests
    {
        @Test
        @DisplayName("tagged event add and retrieve test")
        public void taggedEventTest()
        {
            // NOTE: all of the following arrays must be the same
            // size.
            final int[][] tags =
            {
                { 101 },
                { 202, 303 },
                { 101, 303}
            };
            final Decimal2f[] prices =
            {
                Decimal2f.valueOfUnscaled(234, 2),
                Decimal2f.valueOfUnscaled(235, 2),
                Decimal2f.valueOfUnscaled(235, 2),
            };
            final int[] sizes = { 400, 700, 500 };
            final PriceTrend[] trends =
            {
                PriceTrend.ZERO_MINUS,
                PriceTrend.UP,
                PriceTrend.ZERO_PLUS
            };
            final int[] volumes = { 14_500, 15_200, 15_700 };
            CountDownLatch doneSignal = new CountDownLatch(1);
            final TagAgent agent =
                new TagAgent(TAG_AGENT_NAME, mTradeFile, sTestClock);
            int tag = 101;

            EfsDispatcher.register(agent, AGENT_DISPATCHER);
            agent.open();

            // Retrieve on empty file.
            agent.retrieve(tag, doneSignal);

            try
            {
                doneSignal.await(5L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}

            assertThat(agent.trades()).isEmpty();

            // Post tagged trades to event file.
            agent.postTrades(
                mSymbol, tags, prices, sizes, trends, volumes);

            // Retrieve trades with given tag.
            doneSignal = new CountDownLatch(1);
            agent.retrieve(tag, doneSignal);

            try
            {
                doneSignal.await(5L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}

            final List<EfsRow<TradeEvent>> trades = agent.trades();

            assertThat(trades).hasSize(2);
            validateRow(trades.get(0),
                        tag,
                        prices[0],
                        sizes[0],
                        trends[0],
                        volumes[0]);
            validateRow(trades.get(1),
                        tag,
                        prices[2],
                        sizes[2],
                        trends[2],
                        volumes[2]);

            // Now retrieve rows with an unknown tag.
            tag = 404;
            doneSignal = new CountDownLatch(1);
            agent.retrieve(tag, doneSignal);

            try
            {
                doneSignal.await(5L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}

            assertThat(agent.trades()).isEmpty();

            agent.close();
        } // end of taggedEventTest()

        @Test
        @DisplayName("trigger event overflow on tag retrieval event delivery")
        public void taggedEventRetrievalOverflow()
        {
            final int tag = 202;
            final Decimal2f price =
                Decimal2f.valueOfUnscaled(321, 2);
            final TradeEvent trade =
                (TradeEvent.builder()).symbol(mSymbol)
                                      .price(price)
                                      .size(500)
                                      .priceTrend(PriceTrend.ZERO_MINUS)
                                      .volume(123_700)
                                      .build();
            final TagRetrieveInternalEvent<TradeEvent> tagRetrieve =
                new TagRetrieveInternalEvent<>(tag,
                                               mTestAgent,
                                               mTestAgent::onEvent,
                                               mTestAgent::onDone);
            final Set<Integer> tags = new TreeSet<>();
            final long timeDelta = 10L;
            final int numEvents = (EVENT_QUEUE_SIZE + 2);
            final CountDownLatch continueSignal =
                new CountDownLatch(1);
            final CountDownLatch doneSignal = new CountDownLatch(1);
            int index;
            Instant timestamp = sTestClock.instant();
            EfsRow<TradeEvent> row;

            assertThat(tagRetrieve.agent()).isSameAs(mTestAgent);

            tags.add(tag);

            mTestAgent.setContinueSignal(continueSignal);
            mTestAgent.setDoneSignal(doneSignal);

            for (index = 0; index < numEvents; ++index)
            {
                row =
                    EfsRow.createRow(
                        timestamp, index, tags, trade);

                tagRetrieve.postRow(row);

                timestamp = timestamp.plusMillis(timeDelta);
            }

            continueSignal.countDown();

            tagRetrieve.postCompletion(
                timestamp, RETRIEVAL_COMPLETED);

            try
            {
                doneSignal.await(5L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}
        } // end of taggedEventRetrievalOverflow()

        @Test
        @DisplayName("trigger event overflow on retrieval complete delivery")
        public void taggedEventRetrievalCompleteOverflow()
        {
            final int tag = 202;
            final Decimal2f price =
                Decimal2f.valueOfUnscaled(321, 2);
            final TradeEvent trade =
                (TradeEvent.builder()).symbol(mSymbol)
                                      .price(price)
                                      .size(500)
                                      .priceTrend(PriceTrend.ZERO_MINUS)
                                      .volume(123_700)
                                      .build();
            final TagRetrieveInternalEvent<TradeEvent> tagRetrieve =
                new TagRetrieveInternalEvent<>(tag,
                                               mTestAgent,
                                               mTestAgent::onEvent,
                                               mTestAgent::onDone);
            final Set<Integer> tags = new TreeSet<>();
            final long timeDelta = 10L;
            final int numEvents = (EVENT_QUEUE_SIZE + 1);
            final CountDownLatch continueSignal =
                new CountDownLatch(1);
            final CountDownLatch doneSignal = new CountDownLatch(1);
            int index;
            Instant timestamp = sTestClock.instant();
            EfsRow<TradeEvent> row;

            tags.add(tag);

            mTestAgent.setContinueSignal(continueSignal);
            mTestAgent.setDoneSignal(doneSignal);

            for (index = 0; index < numEvents; ++index)
            {
                row =
                    EfsRow.createRow(
                        timestamp, index, tags, trade);

                tagRetrieve.postRow(row);

                timestamp = timestamp.plusMillis(timeDelta);
            }

            tagRetrieve.postCompletion(
                timestamp, RETRIEVAL_COMPLETED);

            continueSignal.countDown();
        } // end of taggedEventRetrievalCompleteOverflow()
    } // end of class EfsFileTaggedEventsTests

    @Nested
    @DisplayName("EfsFile exhaust tests")
    public final class EfsExhaustTests
    {
        @Test
        @DisplayName("Exhaust, close, and re-open event file")
        public void exhaustAndInitializeEventFile()
            throws EfsFileInitializationException
        {
            final TestExhaust exhaustAgent;
            EfsFile.Builder<TradeEvent> builder =
                EfsFile.builder(mTradeKey);
            Duration runTime = Duration.ofSeconds(3L);
            final AtomicBoolean publishFlag =
                new AtomicBoolean(true);
            CountDownLatch doneSignal = new CountDownLatch(1);

            // 1. Create exhaust agent.
            exhaustAgent = new TestExhaust(TEST_EXHAUST);
            EfsDispatcher.register(
                exhaustAgent, AGENT_DISPATCHER);

            // 2. Close and open event file with exhaust set.
            mTradeFile.close();
            EfsDispatcher.deregister(mTradeFile);
            mTradeFile =
                builder.dispatcher(FILE_DISPATCHER)
                       .fileExhaust(exhaustAgent::onExhaust,
                                     exhaustAgent)
                       .clock(sTestClock)
                       .build();

            // 3. Close and open publisher.
            mPublisher.close();
            EfsDispatcher.deregister(mPublisher);
            mPublisher =
                new TestPublisher(
                    PUBLISHER_NAME, mTradeFile, sTestClock);
            EfsDispatcher.register(mPublisher, AGENT_DISPATCHER);
            mPublisher.open();
            mPublisher.reset(sTestClock);

            // 4. Have publisher post events.
            mPublisher.postTrades(
                mSymbol, runTime, publishFlag, doneSignal);

            try
            {
                doneSignal.await(5L, TimeUnit.MINUTES);
            }
            catch (InterruptedException interrupt)
            {}

            assertThat(exhaustAgent.tradesExhausted())
                .isEqualTo(mPublisher.tradeCount());

            // 5. Close, open, and initialize event file with
            //    exhausted rows.
            mTradeFile.close();
            EfsDispatcher.deregister(mTradeFile);

            builder = EfsFile.builder(mTradeKey);
            mTradeFile =
                builder.initializer(exhaustAgent::onInitialize)
                       .dispatcher(FILE_DISPATCHER)
                       .clock(sTestClock)
                       .build();

            assertThat(mTradeFile.rowCount())
                .isEqualTo(exhaustAgent.tradesExhausted());
        } // end of exhaustAndInitializeEventFile()
    } // end of class EfsExhaustTests

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private void postTrades()
    {
        // Has the publisher already posted trades?
        if (mPublisher.tradeCount() == 0)
        {
            // No. Add trades to event file.
            final AtomicBoolean publishFlag =
                new AtomicBoolean(true);
            final Duration runTime = Duration.ofSeconds(3L);
            final CountDownLatch doneSignal =
                new CountDownLatch(1);

            // Start by adding trades to file.
            mPublisher.postTrades(
                mSymbol, runTime, publishFlag, doneSignal);

            try
            {
                doneSignal.await(5L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}
        }
    } // end of postTrades()

    private void retrieveTrades(final EfsInterval interval)
    {
        final CountDownLatch continueSignal =
            new CountDownLatch(0);
        final CountDownLatch doneSignal = new CountDownLatch(1);

        mTestAgent.setContinueSignal(continueSignal);
        mTestAgent.setDoneSignal(doneSignal);
        mTestAgent.retrieve(interval, sAllQuery);

        try
        {
            doneSignal.await(5L, TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}
    } // end of retrieveTrades(EfsInterval)

    private void validateRow(final EfsRow<TradeEvent> row,
                             final int tag,
                             final Decimal2f price,
                             final int size,
                             final PriceTrend pxTrend,
                             final int volume)
    {
        final TradeEvent trade;

        assertThat(row).isNotNull();
        assertThat(row.getTags()).contains(tag);

        trade = row.getEvent();
        assertThat(trade.getSymbol()).isEqualTo(mSymbol);
        assertThat(trade.getPrice()).isEqualTo(price);
        assertThat(trade.getSize()).isEqualTo(size);
        assertThat(trade.getPriceTrend()).isEqualTo(pxTrend);
        assertThat(trade.getVolume()).isEqualTo(volume);
    } // end of validateRow(int, EfsRow<>)

    private static <E extends IEfsEvent> EfsFile<E> createEventFile(final EfsTopicKey<E> key,
                                                                    final IConnectionPolicy policy,
                                                                    final String dispatcher,
                                                                    final int maxConnections,
                                                                    final int maxRetrievals)
        throws EfsFileInitializationException
    {
        final EfsFile.Builder<E> builder = EfsFile.builder(key);

        return (builder.dispatcher(dispatcher)
                       .connectionPolicy(policy)
                       .maxConnections(maxConnections)
                       .maxRetrievals(maxRetrievals)
                       .build());
    } // end of createEventFile(EfsTopicKey<>, String)
} // end of class EfsFileTest