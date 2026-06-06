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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;
import org.decimal4j.immutable.Decimal2f;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.config.ThreadType;
import org.efs.event.EfsTopicKey;
import org.efs.io.EfsIntervalEndpoint.Clusivity;
import org.efs.logging.AsyncLoggerFactory;
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

public class EfsFileTest
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

    /**
     * Test trading symbol.
     */
    /* package */ static final String SYMBOL = "ACME";

    /**
     * Test trading efs file topic.
     */
    /* package */ static final String TOPIC =
        "/exchange/acme/equity/" + SYMBOL;

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
     * Trade event file.
     */
    private static EfsFile<TradeEvent> sTradeFile;

    /**
     * Agent publishes trades to file.
     */
    private static TestPublisher sPublisher;

    /**
     * Agent retrieves trades from file.
     */
    private static TestRetriever sRetriever;

    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(EfsFileTest.class);

    //-----------------------------------------------------------
    // Locals.
    //

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

        sTradeFile =
            EfsFile.openEventFile(TRADE_KEY, FILE_DISPATCHER);
        sPublisher =
            new TestPublisher(
                PUBLISHER_NAME, sTradeFile, sTestClock);
        sRetriever = new TestRetriever(RETRIEVER_NAME, sTradeFile);

        EfsDispatcher.register(sPublisher, AGENT_DISPATCHER);
        EfsDispatcher.register(sRetriever, AGENT_DISPATCHER);
    } // end of setUpClass()

    @AfterAll
    public static void tearDownClass()
    {
        EfsFile.setSystemClock(sSystemClock);
    } // end of tearDownClass()

    @BeforeEach
    public void setUp()
    {
        sPublisher.reset(sTestClock);
        EfsFile.setSystemClock(sTestClock);
    } // end of setUp()

    @AfterEach
    public void tearDown()
    {
    }

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

//    @Disabled
    @Test
    @DisplayName("EfsFile retrieve past events only")
    public void pastRetrievalTest()
    {
        final AtomicBoolean publishFlag =
            new AtomicBoolean(true);
        final Duration runTime = Duration.ofSeconds(3L);
        final Decimal2f initialPrice = sPublisher.price();
        CountDownLatch doneSignal = new CountDownLatch(1);

        // Start by adding trades to file.
        sPublisher.postTrades(runTime, publishFlag, doneSignal);

        try
        {
            doneSignal.await(1L, TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        // Have the agent retrieve trades from file.
        final Instant endTime = sPublisher.instant();
        final int numTrades = sPublisher.tradeCount();
        final long tIndex0 = Math.negateExact(numTrades - 100);
        final Instant tsIndex1 = (endTime.minusMillis(1L));
        final Decimal2f maxPrice =
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
        sRetriever.retrieveTrades(interval,
                                  maxPrice,
                                  minSize,
                                  doneSignal);

        try
        {
            doneSignal.await(1L, TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(sRetriever.tradesReceived()).isGreaterThan(0);
    } // end of pastRetrievalTest()

//    @Disabled
    @Test
    @DisplayName("EfsFile future event retrieval")
    public void futureRetrievalTest()
    {
        final AtomicBoolean publishFlag =
            new AtomicBoolean(true);
        final Duration runTime = Duration.ofSeconds(10L);
        final Decimal2f initialPrice = sPublisher.price();
        final Decimal2f maxPrice =
            initialPrice.add(Decimal2f.valueOfUnscaled(10, 0));
        final int minSize = 100;
        final Instant startTime = sPublisher.instant();
        final long tIndex0 = 1;
        final Duration tsIndex1 = Duration.ofSeconds(8L);
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
        final CountDownLatch doneSignal = new CountDownLatch(1);

        sLogger.info(
            "Future test: retrieving interval {}, initial price {}, max price {}.",
            interval,
            initialPrice,
            maxPrice);

        // Start retreival first and then start publishing.
        sRetriever.retrieveTrades(interval,
                                  maxPrice,
                                  minSize,
                                  doneSignal);
        sPublisher.postTrades(runTime, publishFlag, doneSignal);

        try
        {
            doneSignal.await(300L, TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        // Stop publishing in case it is still running.
        publishFlag.set(false);

        assertThat(sRetriever.tradesReceived()).isGreaterThan(0);
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
        final Decimal2f initialPrice = sPublisher.price();
        final Decimal2f maxPrice =
            initialPrice.add(Decimal2f.valueOfUnscaled(1, 0));
        final int minSize = 500;
        CountDownLatch doneSignal = new CountDownLatch(1);

        // Start by adding trades to file.
        sPublisher.postTrades(runTime, publishFlag, doneSignal);

        try
        {
            doneSignal.await(1L, TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        final int numTrades = sPublisher.tradeCount();
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
        sRetriever.retrieveTrades(interval,
                                  maxPrice,
                                  minSize,
                                  doneSignal);
        sPublisher.postTrades(runTime, publishFlag, doneSignal);

        try
        {
            doneSignal.await(10L, TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        // Stop publishing in case it is still running.
        publishFlag.set(false);

        assertThat(sRetriever.tradesReceived()).isGreaterThan(0);
    } // end of pastAndFutureRetrievalTest()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------
} // end of class EfsFileTest