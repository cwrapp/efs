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
import static com.googlecode.cqengine.query.QueryFactory.all;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.decimal4j.immutable.Decimal2f;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.EfsDispatcher.DispatcherType;
import org.efs.dispatcher.config.ThreadType;
import org.efs.event.EfsTopicKey;
import static org.efs.io.AbstractTestAgent.sRandomizer;
import org.efs.io.EfsFile.AccessMode;
import static org.efs.io.EfsFileTest.AGENT_DISPATCHER;
import static org.efs.io.EfsFileTest.EVENT_QUEUE_SIZE;
import static org.efs.io.EfsFileTest.EXCHANGE;
import static org.efs.io.EfsFileTest.FILE_DISPATCHER;
import static org.efs.io.EfsFileTest.GMT;
import static org.efs.io.EfsFileTest.SYMBOL;
import static org.efs.io.EfsFileTest.TEST_TIME;
import org.efs.io.EfsIntervalEndpoint.Clusivity;
import org.efs.io.TradeEvent.PriceTrend;
import org.efs.logging.AsyncLoggerFactory;
import org.junit.jupiter.api.AfterAll;
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

public class EfsFileInitializationTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Test agent name is {@value}.
     */
    private static final String AGENT_NAME = "test-agent";

    private static final Decimal2f INITIAL_PRICE =
        Decimal2f.valueOfUnscaled(1234, 2);
    private static final Decimal2f INITIAL_PRICE_DELTA =
        Decimal2f.valueOfUnscaled(0L);

    private static final int MIN_PRICE_DELTA = -4;
    private static final int MAX_PRICE_DELTA = 4;
    private static final int PRICE_SCALE = 2;

    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 11;
    private static final int LOT_SIZE = 100;

    private static final long MIN_TIME_DELTA = 100_000L;
    private static final long MAX_TIME_DELTA = 5_000_000L;

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Clock used for testing purposes.
     */
    private static Clock sTestClock;

    /**
     * Used to generate a unique symbol for each test.
     */
    private static AtomicInteger sSymbolIndex;

    /**
     * Query to retrieve all rows.
     */
    private static Query sAllQuery;

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(
            EfsFileInitializationTest.class);

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Latest trade symbol.
     */
    private String mSymbol;

    /**
     * Latest trade key for this test.
     */
    private EfsTopicKey<TradeEvent> mTradeKey;

    /**
     * Test clock always updated to latest publish timestamp.
     */
    private Clock mTestClock;

    /**
     * Store received trades in this list.
     */
    private List<EfsRow<TradeEvent>> mReceivedTrades;

    /**
     * Stored exhausted trades in this list.
     */
    private List<EfsRow<TradeEvent>> mExhaustTrades;

    /**
     * Decrement when retrieval is complete.
     */
    private CountDownLatch mDoneSignal;

    // TradeEvent parameters.

    private Decimal2f mPrice;
    private Decimal2f mPriceDelta;
    private int mSize;
    private TradeEvent.PriceTrend mPriceTrend;
    private int mVolume;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void setUpClass()
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
        sSymbolIndex = new AtomicInteger();
        sAllQuery = all(TradeEvent.class);
    } // end of setUpClass()

    @AfterAll
    public static void tearDownClass()
    {
    }

    @BeforeEach
    public void setUp()
    {
        final String topic;

        mSymbol = SYMBOL + sSymbolIndex.getAndIncrement();
        topic = EXCHANGE + mSymbol;
        mTradeKey = EfsTopicKey.getKey(TradeEvent.class, topic);

        mTestClock = sTestClock;
        mReceivedTrades = new ArrayList<>();
        mDoneSignal = new CountDownLatch(1);

        mPrice = INITIAL_PRICE;
        mPriceDelta = INITIAL_PRICE_DELTA;
        mSize = 0;
        mPriceTrend = TradeEvent.PriceTrend.UP;
        mVolume = 0;
    } // end of setUp()

    @AfterEach
    public void tearDown()
    {
    } // end of tearDown()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Nested
    @DisplayName("EfsFile initialization failure tests")
    public final class EfsInitializationFailureTests
    {
        @Test
        @DisplayName("build EfsFile, null initializer")
        public void buildNullInitializer()
        {
            final Supplier<Iterator<EfsRow<TradeEvent>>> initializer =
                null;
            final EfsFile.Builder<TradeEvent> builder =
                EfsFile.builder(mTradeKey);

            assertThatThrownBy(
                () -> builder.tableInitializer(initializer))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsFile.NULL_INITIALIZER);
        } // end of buildNullInitializer()

        @Test
        @DisplayName("Initialize EfsFile, initializer throws exception")
        public void initialzerException()
        {

            final Supplier<Iterator<EfsRow<TradeEvent>>> initializer =
                () ->
                {
                    throw (new RuntimeException("OOPS!!!"));
                };
            final EfsFile.Builder<TradeEvent> builder =
                EfsFile.builder(mTradeKey);
            final String text =
                String.format(
                    "%s initialization failed: initializer exception",
                    mTradeKey);

            assertThatThrownBy(
                () -> builder.dispatcher(FILE_DISPATCHER)
                             .tableInitializer(initializer)
                             .build())
                .isInstanceOf(EfsFileInitializationException.class)
                .hasMessage(text);
        } // end of initialzerException()

        @Test
        @DisplayName("Initialze EfsFile, initializer returns null row")
        public void initializerReturnsNullRow()
        {
            final List<EfsRow<TradeEvent>> rows =
                new ArrayList<>();
            final Supplier<Iterator<EfsRow<TradeEvent>>> initializer =
                () ->
                {
                    return (rows.iterator());
                };
            final EfsFile.Builder<TradeEvent> builder =
                EfsFile.builder(mTradeKey);
            final String text =
                String.format(
                    "%s initialization failed: null row returned",
                    mTradeKey);

            rows.add(null);

            assertThatThrownBy(
                () -> builder.dispatcher(FILE_DISPATCHER)
                             .tableInitializer(initializer)
                             .build())
                .isInstanceOf(EfsFileInitializationException.class)
                .hasMessage(text);
        } // end of initializerReturnsNullRow()

        @Test
        @DisplayName("Initialize EfsFile, invalid row index")
        public void initializerReturnsInvalidRowIndex()
        {
            final ImmutableList.Builder<EfsRow<TradeEvent>> rowBuilder =
                ImmutableList.builder();
            final List<EfsRow<TradeEvent>> rows;
            final Supplier<Iterator<EfsRow<TradeEvent>>> initializer;
            Instant pubTime = mTestClock.instant();
            long rowIndex = 0;
            final EfsFile.Builder<TradeEvent> builder =
                EfsFile.builder(mTradeKey);
            final String text =
                String.format(
                    "%s initialization failed: row index 2 < expected index 4",
                    mTradeKey);

            rowBuilder.add(
                EfsRow.createRow(pubTime,
                                 rowIndex,
                                 generateTradeEvent()));

            pubTime = pubTime.plusMillis(500L);
            rowIndex = 1;
            rowBuilder.add(
                EfsRow.createRow(pubTime,
                                 rowIndex,
                                 generateTradeEvent()));

            rowIndex = 2;
            rowBuilder.add(
                EfsRow.createRow(pubTime,
                                 rowIndex,
                                 generateTradeEvent()));

            pubTime = pubTime.plusMillis(500L);
            rowIndex = 3;
            rowBuilder.add(
                EfsRow.createRow(pubTime,
                                 rowIndex,
                                 generateTradeEvent()));

            rowIndex = 2;
            rowBuilder.add(
                EfsRow.createRow(pubTime,
                                 rowIndex,
                                 generateTradeEvent()));

            rows = rowBuilder.build();
            initializer = () -> { return (rows.iterator()); };

            assertThatThrownBy(
                () -> builder.dispatcher(FILE_DISPATCHER)
                             .tableInitializer(initializer)
                             .build())
                .isInstanceOf(EfsFileInitializationException.class)
                .hasMessage(text);
        } // end of initializerReturnsInvalidRowIndex()

        @Test
        @DisplayName("Initialize EfsFile, missing row index")
        public void initializeMissingRowIndex()
        {
            final ImmutableList.Builder<EfsRow<TradeEvent>> rowBuilder =
                ImmutableList.builder();
            final List<EfsRow<TradeEvent>> rows;
            final Supplier<Iterator<EfsRow<TradeEvent>>> initializer;
            Instant pubTime = mTestClock.instant();
            long rowIndex = 0;
            final EfsFile.Builder<TradeEvent> builder =
                EfsFile.builder(mTradeKey);
            final String text =
                String.format(
                    "%s initialization failed: row index 3 > expected index 2",
                    mTradeKey);

            rowBuilder.add(
                EfsRow.createRow(pubTime,
                                 rowIndex,
                                 generateTradeEvent()));

            pubTime = pubTime.plusMillis(500L);
            rowIndex = 1;
            rowBuilder.add(
                EfsRow.createRow(pubTime,
                                 rowIndex,
                                 generateTradeEvent()));

            pubTime = pubTime.plusMillis(500L);
            rowIndex = 3;
            rowBuilder.add(
                EfsRow.createRow(pubTime,
                                 rowIndex,
                                 generateTradeEvent()));

            rows = rowBuilder.build();
            initializer = () -> { return (rows.iterator()); };

            assertThatThrownBy(
                () -> builder.dispatcher(FILE_DISPATCHER)
                             .tableInitializer(initializer)
                             .build())
                .isInstanceOf(EfsFileInitializationException.class)
                .hasMessage(text);
        } // end of initializeMissingRowIndex()

        @Test
        @DisplayName("Initialize EfsFile, invalid publish timestamp")
        public void initializeInvalidPublishTimestamp()
        {
            final ImmutableList.Builder<EfsRow<TradeEvent>> rowBuilder =
                ImmutableList.builder();
            final List<EfsRow<TradeEvent>> rows;
            final Supplier<Iterator<EfsRow<TradeEvent>>> initializer;
            Instant pubTime = mTestClock.instant();
            long rowIndex = 0;
            Instant prevPubTime;
            final EfsFile.Builder<TradeEvent> builder =
                EfsFile.builder(mTradeKey);
            final String text;

            rowBuilder.add(
                EfsRow.createRow(pubTime,
                                 rowIndex,
                                 generateTradeEvent()));

            pubTime = pubTime.plusMillis(500L);
            rowIndex = 1;
            rowBuilder.add(
                EfsRow.createRow(pubTime,
                                 rowIndex,
                                 generateTradeEvent()));

            rowIndex = 2;
            rowBuilder.add(
                EfsRow.createRow(pubTime,
                                 rowIndex,
                                 generateTradeEvent()));

            prevPubTime = pubTime;
            pubTime = pubTime.minusMillis(100L);
            rowIndex = 3;
            rowBuilder.add(
                EfsRow.createRow(pubTime,
                                 rowIndex,
                                 generateTradeEvent()));

            text =
                String.format(
                    "%s initialization failed: row publish timestamp %s < previous timestamp %s",
                    mTradeKey,
                    pubTime,
                    prevPubTime);

            rows = rowBuilder.build();
            initializer = () -> { return (rows.iterator()); };

            assertThatThrownBy(
                () -> builder.dispatcher(FILE_DISPATCHER)
                             .tableInitializer(initializer)
                             .build())
                .isInstanceOf(EfsFileInitializationException.class)
                .hasMessage(text);
        } // end of initializeInvalidPublishTimestamp()

        @Test
        @DisplayName("Create no-arg EfsFileInitializationException")
        public void noargEfsFileInitializationException()
        {
            final EfsFileInitializationException nfex =
                new EfsFileInitializationException();

            assertThat(nfex).isNotNull();
            assertThat(nfex.getMessage()).isNull();
            assertThat(nfex.getCause()).isNull();
        } // end of noargEfsFileInitializationException()

        @Test
        @DisplayName("Create row negative index")
        public void createRowNegativeIndex()
        {
            final Instant timestamp = sTestClock.instant();
            final long rowIndex = -1;
            final TradeEvent.Builder builder =
                TradeEvent.builder();
            final TradeEvent trade =
                builder.symbol(SYMBOL)
                       .price(mPrice)
                       .size(1_000)
                       .priceTrend(mPriceTrend)
                       .volume(150_000_100)
                       .build();

            assertThatThrownBy(
                () -> EfsRow.createRow(timestamp,
                                       rowIndex,
                                       EfsFile.NO_TAGS,
                                       trade))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsRow.INVALID_ROW_INDEX);
        } // end of createRowNegativeIndex()
    } // end of class EfsInitializationFailureTests

    @Nested
    @DisplayName("EfsFile initialization success tests")
    public final class EfsInitializationSuccessTests
    {
        @Test
        @DisplayName("EfsFile initialization success")
        @SuppressWarnings ("unchecked")
        public void initializeSuccess()
            throws EfsFileInitializationException
        {
            final ImmutableList.Builder<EfsRow<TradeEvent>> rowBuilder =
                ImmutableList.builder();
            final List<EfsRow<TradeEvent>> rows;
            final Supplier<Iterator<EfsRow<TradeEvent>>> initializer;
            Instant pubTime = mTestClock.instant();
            long rowIndex = 0;
            final EfsFile.Builder<TradeEvent> builder =
                EfsFile.builder(mTradeKey);
            final EfsFile<TradeEvent> tradeFile;
            final EfsFileConnection<TradeEvent> connection;
            final EfsInterval interval;
            final TestAgent agent;

            rowBuilder.add(
                EfsRow.createRow(pubTime,
                                 rowIndex,
                                 generateTradeEvent()));

            pubTime = pubTime.plusMillis(500L);
            ++rowIndex;
            rowBuilder.add(
                EfsRow.createRow(pubTime,
                                 rowIndex,
                                 generateTradeEvent()));

            ++rowIndex;
            rowBuilder.add(
                EfsRow.createRow(pubTime,
                                 rowIndex,
                                 generateTradeEvent()));

            pubTime = pubTime.plusMillis(500L);
            ++rowIndex;
            rowBuilder.add(
                EfsRow.createRow(pubTime,
                                 rowIndex,
                                 generateTradeEvent()));

            rows = rowBuilder.build();
            initializer = () -> { return (rows.iterator()); };

            tradeFile = builder.dispatcher(FILE_DISPATCHER)
                               .tableInitializer(initializer)
                               .clock(sTestClock)
                               .build();

            assertThat(tradeFile.getSystemClock())
                .isSameAs(sTestClock);

            agent = new TestAgent(AGENT_NAME,
                                   AccessMode.READ_WRITE,
                                   tradeFile);
            EfsDispatcher.register(agent, AGENT_DISPATCHER);
            connection =
                tradeFile.connect(AccessMode.READ_ONLY, agent);
            interval = generateInterval(rows.size());

            sLogger.debug(
                "Retrieving trade events over interval {}.",
                interval);

            connection.retrieve(interval,
                                sAllQuery,
                                this::onTrade,
                                this::onDone);

            try
            {
                mDoneSignal.await(1L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupt)
            {}

            assertThat(mReceivedTrades).hasSameElementsAs(rows);

            EfsDispatcher.deregister(agent);
        } // end of initializeSuccess()

        @Test
        @DisplayName("Fill empty EfsFile with exhaust, close, initialize, and retrieve")
        public void exhaustAndInitializeTest()
        {
        } // end of exhaustAndInitializeTest()

        // Event handlers.

        private void onTrade(final EfsRow<TradeEvent> trade)
        {
            sLogger.debug("Received row {}.", trade);

            mReceivedTrades.add(trade);
        } // end of onTrade(EfsRow<>)

        private void onDone(final RetrievalCompleteEvent<TradeEvent> event)
        {
            sLogger.debug("Retrieval complete, {}.", event);

            mDoneSignal.countDown();
        } // end of onDone(RetrievalCompleteEvent<>)
    } // end of class EfsInitializationSuccessTests

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private TradeEvent generateTradeEvent()
    {
        final TradeEvent.Builder builder = TradeEvent.builder();
        final Duration timeDelta;
        final TradeEvent retval;

        mPrice = mPrice.add(mPriceDelta);
        mSize = generateSize();
        mPriceTrend = getTrend(mPriceDelta, mPriceTrend);
        mVolume += mSize;

        retval = builder.symbol(mSymbol)
                        .price(mPrice)
                        .size(mSize)
                        .priceTrend(mPriceTrend)
                        .volume(mVolume)
                        .build();

            mPriceDelta = generatePriceDelta();
            timeDelta = generateTimeDelta();
            mTestClock =
                Clock.fixed(
                    (mTestClock.instant()).plus(timeDelta),
                    GMT);

        return (retval);
    } // end of generateTradeEvent()

    private Decimal2f generatePriceDelta()
    {
        final int unscaledValue =
            (sRandomizer.nextInt(
                MIN_PRICE_DELTA, MAX_PRICE_DELTA) + 1);

        return (
            Decimal2f.valueOfUnscaled(
                unscaledValue, PRICE_SCALE));
    } // end of generatePrice()

    private int generateSize()
    {
        return (
            (sRandomizer.nextInt(MIN_SIZE, MAX_SIZE) + 1) *
            LOT_SIZE);
    } // end of generateSize()

    private PriceTrend getTrend(final Decimal2f priceDelta,
                                final PriceTrend priceTrend)
    {
        final int compareResult =
            priceDelta.compareTo(INITIAL_PRICE_DELTA);
        final PriceTrend retval;

        if (compareResult < 0)
        {
            retval = PriceTrend.DOWN;
        }
        else if (compareResult > 0)
        {
            retval = PriceTrend.UP;
        }
        // No price change.
        else if (priceTrend == PriceTrend.DOWN)
        {
            retval = PriceTrend.ZERO_MINUS;
        }
        else
        {
            retval = PriceTrend.ZERO_PLUS;
        }

        return (retval);
    } // end of getTrend(Decimal2f)

    private Duration generateTimeDelta()
    {
        final long nanodelta =
            sRandomizer.nextLong(
                MIN_TIME_DELTA, MAX_TIME_DELTA);

        return (Duration.ofNanos(nanodelta));
    } // end of generateTimeDelta()

    private EfsInterval generateInterval(final int numRows)
    {
        final EfsIntervalEndpoint beginEndpoint =
            (EfsIndexOffsetEndpoint.builder())
                .indexOffset(Math.negateExact(numRows),
                             Clusivity.INCLUSIVE)
                .build();
        final EfsIntervalEndpoint endEndpoint =
            (EfsIndexOffsetEndpoint.builder())
                .indexOffset(0, Clusivity.INCLUSIVE)
                .build();

        return (
            (EfsInterval.builder()).beginning(beginEndpoint)
                                   .ending(endEndpoint)
                                   .build());
    } // end of generateInterval(long, long)
} // end of class EfsFileInitializationTest