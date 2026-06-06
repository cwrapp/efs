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
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.decimal4j.immutable.Decimal2f;
import org.efs.dispatcher.EfsDispatcher;
import static org.efs.io.EfsFileTest.GMT;
import static org.efs.io.EfsFileTest.SYMBOL;
import org.efs.io.TradeEvent.PriceTrend;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * Agent posts trade events to efs trade file.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class TestPublisher
    extends AbstractTestAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

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

    private static final int PAUSE_RATE = 1_024;
    private static final long PAUSE_TIME = 100_000L;

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Used to generate random prices, sizes, and trade delays.
     */
    private static final Random sRandomizer =
        ThreadLocalRandom.current();

    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(TestPublisher.class);

    //-----------------------------------------------------------
    // Locals.
    //

    private Decimal2f mPrice;
    private Decimal2f mPriceDelta;
    private int mSize;
    private PriceTrend mPriceTrend;
    private int mVolume;
    private int mTradeCount;

    /**
     * Test clock always updated to latest publish timestamp.
     */
    private Clock mTestClock;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public TestPublisher(final String agentName,
                         final EfsFile<TradeEvent> tradeFile,
                         final Clock testClock)
    {
        super (agentName, tradeFile);

        mPrice = INITIAL_PRICE;
        mPriceDelta = INITIAL_PRICE_DELTA;
        mSize = 0;
        mPriceTrend = PriceTrend.UP;
        mVolume = 0;
        mTradeCount = 0;

        mTestClock = testClock;
    } // end of TestPublisher(String, EfsFile<>)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    public Decimal2f price()
    {
        return (mPrice);
    } // end of price()

    public Decimal2f priceDelta()
    {
        return (mPriceDelta);
    } // end of priceDelta()

    public int size()
    {
        return (mSize);
    } // end of size()

    public PriceTrend priceTrend()
    {
        return (mPriceTrend);
    } // end of priceTrend()

    public int volume()
    {
        return (mVolume);
    } // end of volume()

    public int tradeCount()
    {
        return (mTradeCount);
    } // end of tradeCount()

    public Instant instant()
    {
        return (mTestClock.instant());
    } // end of instant()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Reset trade values to initial settings.
     */
    public void reset(final Clock clock)
    {
        mTestClock = clock;
        mPrice = INITIAL_PRICE;
        mPriceDelta = INITIAL_PRICE_DELTA;
        mSize = 0;
        mPriceTrend = PriceTrend.UP;
        mVolume = 0;
        mTradeCount = 0;
    } // end of reset(Clock)

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Event Handlers.
    //

    private void doPostTrades(final Duration runTime,
                              final AtomicBoolean continueFlag,
                              final CountDownLatch doneSignal)
    {
        final Instant endTime =
            (mTestClock.instant()).plus(runTime);
        TradeEvent.Builder tradeBuilder;
        TradeEvent trade;
        int tradeCount;
        Duration timeDelta;

        sLogger.debug(
            "{}: publishing trades to file. start time {}, run time {}, price {}, delta {}.",
            mAgentName,
            mTestClock.instant(),
            runTime,
            mPrice,
            mPriceDelta);

        while (continueFlag.get() &&
               (mTestClock.instant()).compareTo(endTime) < 0)
        {
            tradeBuilder = TradeEvent.builder();

            mPrice = mPrice.add(mPriceDelta);
            mSize = generateSize();
            mPriceTrend = getTrend(mPriceDelta, mPriceTrend);
            mVolume += mSize;

            trade = tradeBuilder.symbol(SYMBOL)
                                .price(mPrice)
                                .size(mSize)
                                .priceTrend(mPriceTrend)
                                .volume(mVolume)
                                .build();
            tradeCount = mTradeCount;

            // If add fails, wait a bit and try again.
            while (tradeCount == mTradeCount)
            {
                try
                {
                    mTradeFile.add(trade);
                    ++mTradeCount;
                }
                catch (IllegalStateException statex)
                {
                    // Wait for event queue to clear.
                    sLogger.warn("{}: failed to add trade {}.",
                                 mAgentName,
                                 trade,
                                 statex);
                }
            }

            mPriceDelta = generatePriceDelta();
            timeDelta = generateTimeDelta();
            mTestClock =
                Clock.fixed(
                    (mTestClock.instant()).plus(timeDelta),
                    GMT);
            EfsFile.setSystemClock(mTestClock);

            LockSupport.parkNanos(timeDelta.toNanos());
        }

        sLogger.info(
            "{}: {} trades published to file, end time {}.",
            mAgentName,
            mTradeCount,
            mTestClock.instant());

        // Trade publishing completed.
        doneSignal.countDown();
    } // end of doPostTrades(...)

    //
    // end of Event Handlers.
    //-----------------------------------------------------------

    public void postTrades(final Duration runTime,
                           final AtomicBoolean continueFlag,
                           final CountDownLatch doneSignal)
    {
        EfsDispatcher.dispatch(
            () -> this.doPostTrades(runTime,
                                    continueFlag,
                                    doneSignal),
            this);
    } // end of postTrades(...)

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
} // end of class TestPublisher
