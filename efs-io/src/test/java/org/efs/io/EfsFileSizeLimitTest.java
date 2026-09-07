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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.decimal4j.immutable.Decimal2f;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsDispatcher;
import org.efs.dispatcher.config.ThreadType;
import org.efs.event.EfsTopicKey;
import static org.efs.io.AbstractTestAgent.sRandomizer;
import org.efs.io.EfsFile.SizePolicy;
import static org.efs.io.EfsFileTest.AGENT_DISPATCHER;
import static org.efs.io.EfsFileTest.EVENT_QUEUE_SIZE;
import static org.efs.io.EfsFileTest.FILE_DISPATCHER;
import static org.efs.io.EfsFileTest.GMT;
import static org.efs.io.EfsFileTest.TEST_TIME;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exercises efs event file size limit code.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class EfsFileSizeLimitTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Event file holds up to {@value} rows.
     */
    private static final long SIZE_LIMIT = 10L;

    /**
     * Event file topic key format is {@value}.
     */
    private static final String TOPIC_FORMAT = "/abc/efg/%2d";

    /**
     * Event publisher name format is {@value}.
     */
    private static final String PUBLISHER_NAME_FORMAT =
        "test-publisher-%2d";

    /**
     * Trade events are for symbol {@value}.
     */
    private static final String SYMBOL = "ACME";

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
     * Index used to generate a new, unique topic key.
     */
    private static int sTestIndex;

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Test event file topic key.
     */
    private EfsTopicKey<TradeEvent> mTopicKey;

    /**
     * Test clock always updated to latest publish timestamp.
     */
    private Clock mTestClock;

    /**
     * Trade event file.
     */
    private EfsFile<TradeEvent> mTradeFile;

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
                   .dispatcherType(IEfsDispatcher.DispatcherType.EFS)
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
                   .dispatcherType(IEfsDispatcher.DispatcherType.EFS)
                   .eventQueueCapacity(EVENT_QUEUE_SIZE)
                   .runQueueCapacity(4)
                   .maxEvents(EVENT_QUEUE_SIZE)
                   .build();
        }

        sTestClock = Clock.fixed(Instant.parse(TEST_TIME), GMT);
    } // end of setUpClass()

    @AfterAll
    public static void tearDownClass()
    {
        ++sTestIndex;
    } // end of tearDownClass()

    @BeforeEach
    public void setUp()
    {
        final String topic =
            String.format(TOPIC_FORMAT, sTestIndex);

        mTopicKey = EfsTopicKey.getKey(TradeEvent.class, topic);
        mTestClock = sTestClock;

        mPrice = INITIAL_PRICE;
        mPriceDelta = INITIAL_PRICE_DELTA;
        mSize = 0;
        mPriceTrend = TradeEvent.PriceTrend.UP;
        mVolume = 0;
    } // end of setUp()

    @AfterEach
    public void tearDown()
    {
        if (mTradeFile != null)
        {
            mTradeFile.close();
            mTradeFile = null;
        }
    } // end of tearDown()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    @DisplayName("Exceed size limit on initialize, fifo policy")
    public void initializeFifoLimit()
    {
        final SizePolicy sizePolicy =
            SizePolicy.FIFO_ON_LIMIT_REACHED;
        final int sizeLimit = 5;
        final int initializeSize = (sizeLimit * 2);
        final Supplier<Iterator<EfsRow<TradeEvent>>> initializer =
            createInitializer(initializeSize);
        final EfsFile.Builder<TradeEvent> builder =
            EfsFile.builder(mTopicKey);

        mTradeFile = builder.dispatcher(FILE_DISPATCHER)
                            .sizePolicy(sizePolicy)
                            .sizeLimit(sizeLimit)
                            .initializer(initializer)
                            .build();

        assertThat(mTradeFile.sizePolicy()).isEqualTo(sizePolicy);
        assertThat(mTradeFile.sizeLimit()).isEqualTo(sizeLimit);
        assertThat(mTradeFile.rowCount()).isEqualTo(sizeLimit);
        assertThat(mTradeFile.isAtSizeLimit()).isTrue();
        assertThat(mTradeFile.isOkToAdd()).isTrue();
    } // end of initializeFifoLimit()

    @Test
    @DisplayName("Exceed size limit on intialize, fail policy")
    public void intializeFailLimit()
    {
        final SizePolicy sizePolicy =
            SizePolicy.FAIL_ON_LIMIT_REACHED;
        final int sizeLimit = 5;
        final int initializeSize = (sizeLimit + 1);
        final Supplier<Iterator<EfsRow<TradeEvent>>> initializer =
            createInitializer(initializeSize);
        final EfsFile.Builder<TradeEvent> builder =
            EfsFile.builder(mTopicKey);
        final String message =
            String.format(
                "%s row add failed: maximum row limit %,d exceeded",
                mTopicKey,
                sizeLimit);

        assertThatThrownBy(
            () -> builder.dispatcher(FILE_DISPATCHER)
                         .sizePolicy(sizePolicy)
                         .sizeLimit(sizeLimit)
                         .initializer(initializer)
                         .build())
            .isInstanceOf(EfsFileInitializationException.class)
            .hasMessage(message);
    } // end of intializeFailLimit()

    @Test
    @DisplayName("Exceed size limit on publish, fifo policy")
    public void publishFifoLimit()
    {
        final SizePolicy sizePolicy =
            SizePolicy.FIFO_ON_LIMIT_REACHED;
        final int sizeLimit = 5;
        final int initializeSize = (sizeLimit * 2);
        final Iterator<EfsRow<TradeEvent>> rIt =
            (createInitializer(initializeSize)).get();
        final EfsFile.Builder<TradeEvent> builder =
            EfsFile.builder(mTopicKey);
        final TestLimitPublisher publisher;

        mTradeFile = builder.dispatcher(FILE_DISPATCHER)
                            .sizePolicy(sizePolicy)
                            .sizeLimit(sizeLimit)
                            .build();

        publisher = generatePublisher();
        publisher.open();
        publisher.postTrades(rIt);
        publisher.close();
        EfsDispatcher.deregister(publisher);

        assertThat(mTradeFile.rowCount()).isEqualTo(sizeLimit);
        assertThat(publisher.caughtException()).isNull();
    } // end of publishFifoLimit()

    @Test
    @DisplayName("Exceed size limit on publish, fail policy")
    public void publishFailLimit()
    {
        final SizePolicy sizePolicy =
            SizePolicy.FAIL_ON_LIMIT_REACHED;
        final int sizeLimit = 5;
        final int initializeSize = (sizeLimit + 1);
        final Iterator<EfsRow<TradeEvent>> rIt =
            (createInitializer(initializeSize)).get();
        final EfsFile.Builder<TradeEvent> builder =
            EfsFile.builder(mTopicKey);
        final TestLimitPublisher publisher;
        final String message;

        mTradeFile = builder.dispatcher(FILE_DISPATCHER)
                            .sizePolicy(sizePolicy)
                            .sizeLimit(sizeLimit)
                            .build();
        message =
            String.format(
                EfsFileConnection.FILE_AT_SIZE_LIMIT,
                mTradeFile.name(),
                sizeLimit);

        publisher = generatePublisher();
        publisher.open();
        publisher.postTrades(rIt);
        publisher.close();
        EfsDispatcher.deregister(publisher);

        assertThat(mTradeFile.rowCount()).isEqualTo(sizeLimit);
        assertThat(publisher.caughtException()).isNotNull();
        assertThat(publisher.caughtException())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(message);
    } // end of publishFailLimit()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private Supplier<Iterator<EfsRow<TradeEvent>>> createInitializer(final int initializeSize)
    {
        final ImmutableList.Builder<EfsRow<TradeEvent>> rowBuilder =
            ImmutableList.builder();
        final List<EfsRow<TradeEvent>> rows;
        Instant pubTime = mTestClock.instant();
        long rowIndex = 0;
        int i;

        for (i = 0; i < initializeSize; ++i)
        {
            rowBuilder.add(
                EfsRow.createRow(
                    pubTime, rowIndex, generateTradeEvent()));
            pubTime = pubTime.plusMillis(500L);
            ++rowIndex;
        }

        rows = rowBuilder.build();

        return (() -> { return (rows.iterator()); });
    } // end of createInitializer(int)

    private TradeEvent generateTradeEvent()
    {
        final TradeEvent.Builder builder = TradeEvent.builder();
        final Duration timeDelta;
        final TradeEvent retval;

        mPrice = mPrice.add(mPriceDelta);
        mSize = generateSize();
        mPriceTrend = getTrend(mPriceDelta, mPriceTrend);
        mVolume += mSize;

        retval = builder.symbol(SYMBOL)
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

    private TradeEvent.PriceTrend getTrend(final Decimal2f priceDelta,
                                final TradeEvent.PriceTrend priceTrend)
    {
        final int compareResult =
            priceDelta.compareTo(INITIAL_PRICE_DELTA);
        final TradeEvent.PriceTrend retval;

        if (compareResult < 0)
        {
            retval = TradeEvent.PriceTrend.DOWN;
        }
        else if (compareResult > 0)
        {
            retval = TradeEvent.PriceTrend.UP;
        }
        // No price change.
        else if (priceTrend == TradeEvent.PriceTrend.DOWN)
        {
            retval = TradeEvent.PriceTrend.ZERO_MINUS;
        }
        else
        {
            retval = TradeEvent.PriceTrend.ZERO_PLUS;
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

    private TestLimitPublisher generatePublisher()
    {
        final String publisherName =
            String.format(PUBLISHER_NAME_FORMAT, sTestIndex);
        final TestLimitPublisher retval =
            new TestLimitPublisher(publisherName, mTradeFile);

        EfsDispatcher.register(retval, AGENT_DISPATCHER);

        return (retval);
    } // end of generatePublisher()
} // end of class EfsFileSizeLimitTest