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

import com.googlecode.cqengine.query.Query;
import static com.googlecode.cqengine.query.QueryFactory.all;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import org.decimal4j.api.Decimal;
import org.decimal4j.immutable.Decimal2f;
import org.decimal4j.scale.Scale2f;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;
import org.efs.dispatcher.config.ThreadType;
import org.efs.event.EfsTopicKey;
import org.efs.io.EfsFile.AccessMode;
import org.efs.io.EfsFileConnection.Retrieval;
import org.efs.io.EfsIntervalEndpoint.Clusivity;
import org.efs.io.RetrievalCompleteEvent.CompletionType;
import org.efs.io.TradeEvent.PriceTrend;
import org.efs.logging.AsyncLoggerFactory;
import org.efs.util.DelayedExecution;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    /* package */ static final String SYMBOL = "ACME";

    /**
     * Test trading efs file topic.
     */
    /* package */ static final String TOPIC = EXCHANGE + SYMBOL;

    private static final EfsTopicKey<TradeEvent> TRADE_KEY =
        EfsTopicKey.getKey(TradeEvent.class, TOPIC);

    /**
     * {@link EfsFile} dispatcher is named {@value}.
     */
    private static final String FILE_DISPATCHER =
        "file-dispatcher";

    /**
     * Agents use dispatcher named {@value}.
     */
    private static final String AGENT_DISPATCHER =
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
     * Unregistered agent name is {@value}.
     */
    private static final String UNREGISERED_NAME =
        "test-unregistered";

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Clock used for testing purposes.
     */
    private static Clock sTestClock;

    /**
     * Original system clock. Put back in place at end of
     * testing.
     */
    private static Clock sSystemClock;

    /**
     * Interval used for retrieval failure tests.
     */
    private static EfsInterval sInterval;

    /**
     * Query to retrieve all rows.
     */
    private static Query sAllQuery;

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
        final int eventQueueSize = 8_192;
        EfsDispatcher.Builder builder =
            EfsDispatcher.builder(FILE_DISPATCHER);

        // Create efs file dispatcher.
        builder.threadType(ThreadType.SPINPARK)
               .numThreads(1)
               .priority(10)
               .spinLimit(2_500_000L)
               .parkTime(Duration.ofNanos(500L))
               .dispatcherType(EfsDispatcher.DispatcherType.EFS)
               .eventQueueCapacity(eventQueueSize)
               .runQueueCapacity(4)
               .maxEvents(eventQueueSize)
               .build();

        // Create agent dispatcher.
        builder = EfsDispatcher.builder(AGENT_DISPATCHER);
        builder.threadType(ThreadType.SPINPARK)
               .numThreads(2)
               .priority(10)
               .spinLimit(2_500_000L)
               .parkTime(Duration.ofNanos(500L))
               .dispatcherType(EfsDispatcher.DispatcherType.EFS)
               .eventQueueCapacity(eventQueueSize)
               .runQueueCapacity(4)
               .maxEvents(eventQueueSize)
               .build();

        sTestClock = Clock.fixed(Instant.parse(TEST_TIME), GMT);
        sSystemClock = EfsFile.getSystemClock();


        final EfsIntervalEndpoint beginEndpoint =
            (EfsIndexEndpoint.builder())
                .indexOffset(0L, Clusivity.INCLUSIVE)
                .build();
        final EfsIntervalEndpoint endEndpoint =
            (EfsIndexEndpoint.builder())
                .indexOffset(1_000L, Clusivity.EXCLUSIVE)
                .build();

        sInterval =
            (EfsInterval.builder()).beginning(beginEndpoint)
                                   .ending(endEndpoint)
                                   .build();

        sAllQuery = all(TradeEvent.class);
    } // end of setUpClass()

    @AfterAll
    public static void tearDownClass()
    {
        EfsFile.setSystemClock(sSystemClock);
    } // end of tearDownClass()

    @BeforeEach
    public void setUp()
        throws IOException
    {
        mTradeFile =
            EfsFile.createEventFile(TRADE_KEY, FILE_DISPATCHER);
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
        EfsFile.setSystemClock(sTestClock);
    } // end of setUp()

    @AfterEach
    public void tearDown()
    {
        mPublisher.close();
        mRetriever.close();
        mTestAgent.close();

        mTradeFile.close();
    } // end of tearDown()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    // Failure Tests.

    @Test
    @DisplayName("Create event file with null key")
    public void nullTopicKeyTest()
    {
        final EfsTopicKey<TradeEvent> key = null;

        assertThatThrownBy(
            () -> EfsFile.createEventFile(key, FILE_DISPATCHER))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsFile.NULL_TOPIC_KEY);
    } // end of nullTopicKeyTest()

    @Test
    @DisplayName("Create event file with null dispatcher")
    public void nullDispatcherTest()
    {
        final String dispatcher = null;

        assertThatThrownBy(
            () -> EfsFile.createEventFile(
                TRADE_KEY, dispatcher))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsFile.INVALID_DISPATCHER);
    } // end of nullDispatcherTest()

    @Test
    @DisplayName("Create event file with empty dispatcher")
    public void emptyDispatcherTest()
    {
        final String dispatcher = "";

        assertThatThrownBy(
            () -> EfsFile.createEventFile(
                TRADE_KEY, dispatcher))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsFile.INVALID_DISPATCHER);
    } // end of emptyDispatcherTest()

    @Test
    @DisplayName("Create event file with blank dispatcher")
    public void blankDispatcherTest()
    {
        final String dispatcher = "\t";

        assertThatThrownBy(
            () -> EfsFile.createEventFile(
                TRADE_KEY, dispatcher))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsFile.INVALID_DISPATCHER);
    } // end of blankDispatcherTest()

    @Test
    @DisplayName("Create event file with unknown dispatcher")
    public void unknownDispatcherTest()
    {
        final String dispatcher = "snafu";
        final String message =
            String.format(
                EfsFile.UNKNOWN_DISPATCHER, dispatcher);

        assertThatThrownBy(
            () -> EfsFile.createEventFile(
                TRADE_KEY, dispatcher))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(message);
    } // end of unknownDispatcherTest()

    @Test
    @DisplayName("Duplicate event file creation")
    public void duplicateEventFileCreationTest()
    {
        final String message =
            String.format(EfsFile.FILE_PREVIOUSLY_CREATED,
                          TRADE_KEY);

        assertThatThrownBy(
            () -> EfsFile.createEventFile(
                TRADE_KEY, FILE_DISPATCHER))
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
    @DisplayName ("connect to closed filed")
    public void connectClosedFile()
        throws IOException
    {
        final AbstractTestAgent agent = mPublisher;
        final AccessMode mode = AccessMode.READ_WRITE;
        final String symbol = "EQTC";
        final String topic = EXCHANGE + symbol;
        final EfsTopicKey<TradeEvent> key =
            EfsTopicKey.getKey(TradeEvent.class, topic);
        final EfsFile<TradeEvent> file =
            EfsFile.createEventFile(key, FILE_DISPATCHER);
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
            (TradeEvent.builder()).symbol(SYMBOL)
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
            (TradeEvent.builder()).symbol(SYMBOL)
                                  .price(Decimal2f.valueOf(1.23d))
                                  .size(1_000)
                                  .priceTrend(PriceTrend.DOWN)
                                  .volume(4_300_000)
                                  .build();
        final EfsFileConnection<TradeEvent> eventFile =
            mTradeFile.connect(mode, agent);
        final String message =
            String.format(EfsFileConnection.CLOSED_FILE, mTradeFile.name());

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
            String.format(EfsFileConnection.CLOSED_FILE, mTradeFile.name());

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

    // Success Tests.

    @Test
    @DisplayName ("access mask test")
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
    @DisplayName ("open, examine, close EfsFileConnection")
    @SuppressWarnings ("unchecked")
    public void openCloseFileConnection()
        throws Exception
    {
        final TestAgent agent = mTestAgent;
        final EfsInterval pastInterval =
            (EfsInterval.builder())
                .beginning(
                    (EfsIndexEndpoint.builder())
                        .indexOffset(-20, Clusivity.EXCLUSIVE)
                        .build())
                .ending(
                    (EfsIndexEndpoint.builder())
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

        // Create retrieval, examine, and close.
        final String text =
            String.format(
                "[id=%d, agent=%s, interval=%s, query=%s]",
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
    @DisplayName ("get previously opened file")
    public void getPreviouslyOpenedFile()
    {
        final EfsFile<TradeEvent> file =
            EfsFile.getEventFile(TRADE_KEY);

        assertThat(file).isSameAs(mTradeFile);
    } // end of getPreviouslyOpenedFile()

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
        mPublisher.postTrades(runTime, publishFlag, doneSignal);

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
            (EfsIndexEndpoint.builder())
                .indexOffset(tIndex0, Clusivity.INCLUSIVE)
                .build();
        final EfsIntervalEndpoint ending =
            (EfsTimeEndpoint.builder())
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
            (EfsIndexEndpoint.builder())
                .indexOffset(tIndex0, Clusivity.INCLUSIVE)
                .build();
        final EfsIntervalEndpoint ending =
            (EfsIndexEndpoint.builder())
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
        mPublisher.postTrades(runTime, publishFlag, doneSignal);

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
        CountDownLatch doneSignal = new CountDownLatch(2);

        // Start by adding trades to file.
        mPublisher.postTrades(runTime, publishFlag, doneSignal);

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
            (EfsIndexEndpoint.builder())
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

        runTime = Duration.ofSeconds(runTimeSeconds);
        doneSignal = new CountDownLatch(1);

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
        mPublisher.postTrades(runTime, publishFlag, doneSignal);

        try
        {
            doneSignal.await(5L, TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        // Stop publishing in case it is still running.
        publishFlag.set(false);

        assertThat(mRetriever.tradesReceived()).isGreaterThan(0);
    } // end of pastAndFutureRetrievalTest()

    @Test
    @DisplayName("EfsFile state transition test")
    public void efsFileStateTest()
        throws IOException
    {
        final AtomicBoolean publishFlag =
            new AtomicBoolean(true);
        final Duration runTime = Duration.ofSeconds(10L);
        final String topic = EXCHANGE + "FUBR";
        final EfsTopicKey<TradeEvent> key =
            EfsTopicKey.getKey(TradeEvent.class, topic);
        final EfsFile<TradeEvent> tradeFile =
            EfsFile.createEventFile(key, FILE_DISPATCHER);
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

        publisher.postTrades(runTime, publishFlag, doneSignal);

        try
        {
            doneSignal.await(5L, TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        // Stop publishing in case it is still running.
        publishFlag.set(false);

        final long rowCount = tradeFile.rowCount();

        assertThat(rowCount).isGreaterThan(0L);

        final EfsIntervalEndpoint beginning =
            (EfsIndexEndpoint.builder())
                .indexOffset(rowCount, Clusivity.INCLUSIVE)
                .build();
        final EfsIntervalEndpoint ending =
            (EfsIndexEndpoint.builder())
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
        assertThat(tradeFile.rowCount()).isZero();
        assertThat(retriever.tradesReceived()).isZero();
        assertThat(retriever.completionReason())
            .isEqualTo(CompletionType.FILE_CLOSED);
    } // end of efsFileStateTest()

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
            (EfsTimeEndpoint.builder())
                .time(beginTime, Clusivity.INCLUSIVE)
                .build();
        EfsIntervalEndpoint endEndpoint =
            (EfsTimeEndpoint.builder())
                .time(endTime, Clusivity.INCLUSIVE)
                .build();
        EfsInterval interval =
            (EfsInterval.builder()).beginning(beginEndpoint)
                                   .ending(endEndpoint)
                                   .build();

        retrieveTrades(interval);

        // (fixed time, fixed time)
        beginEndpoint =
            (EfsTimeEndpoint.builder())
                .time(beginTime, Clusivity.EXCLUSIVE)
                .build();
        endEndpoint =
            (EfsTimeEndpoint.builder())
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
            (EfsIndexEndpoint.builder())
                .indexOffset(beginIndexOffset,
                             Clusivity.INCLUSIVE)
                .build();
        endEndpoint =
            (EfsIndexEndpoint.builder())
                .indexOffset(endIndexOffset, Clusivity.INCLUSIVE)
                .build();
        interval =
            (EfsInterval.builder()).beginning(beginEndpoint)
                                   .ending(endEndpoint)
                                   .build();

        retrieveTrades(interval);

        // (index offset, index offset)
        beginEndpoint =
            (EfsIndexEndpoint.builder())
                .indexOffset(beginIndexOffset,
                             Clusivity.EXCLUSIVE)
                .build();
        endEndpoint =
            (EfsIndexEndpoint.builder())
                .indexOffset(endIndexOffset, Clusivity.EXCLUSIVE)
                .build();
        interval =
            (EfsInterval.builder()).beginning(beginEndpoint)
                                   .ending(endEndpoint)
                                   .build();

        retrieveTrades(interval);
    } // end of intervalTest()

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
            mPublisher.postTrades(runTime, publishFlag, doneSignal);

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
} // end of class EfsFileTest