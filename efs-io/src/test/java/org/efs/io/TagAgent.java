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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;
import org.decimal4j.immutable.Decimal2f;
import org.efs.io.EfsFile.AccessMode;
import static org.efs.io.EfsFileTest.GMT;
import org.efs.io.TradeEvent.PriceTrend;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;


/**
 * Agent used to add and retrieve tagged events.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class TagAgent
    extends AbstractTestAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    //-----------------------------------------------------------
    // Statics.
    //

    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(TagAgent.class);

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Store retrieved trade events here.
     */
    private final List<EfsRow<TradeEvent>> mTrades;

    /**
     * Decrement when retrieval is complete.
     */
    private CountDownLatch mDoneSignal;

    /**
     * Retrieval completion reason.
     */
    private RetrievalCompleteEvent.CompletionType mReason;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public TagAgent(final String agentName,
                    final EfsFile<TradeEvent> tradeFile,
                    final Clock testClock)
    {
        super (agentName, AccessMode.READ_WRITE, tradeFile);

        mTrades = new ArrayList<>();
        mTestClock = testClock;
    } // end of TagAgent(String, EfsFile, Clock)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    public List<EfsRow<TradeEvent>> trades()
    {
        return (ImmutableList.copyOf(mTrades));
    } // end of trades()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Event Handlers.
    //

    private void onEvent(final EfsRow<TradeEvent> row)
    {
        mTrades.add(row);
    } // end of onEvent(EfsRow<>)

    private void onDone(final RetrievalCompleteEvent<TradeEvent> event)
    {
        mReason = event.completionType();

        sLogger.info(
            "{}: tagged event retrieval completed, reason {}, {} trades.",
            mAgentName,
            mReason,
            mTrades.size());

        mDoneSignal.countDown();
    } // end of onDone(RetrievalCompleteEvent<>)

    //
    // end of Event Handlers.
    //-----------------------------------------------------------

    public void postTrades(final String symbol,
                           final int[][] tags,
                           final Decimal2f[] prices,
                           final int[] sizes,
                           final PriceTrend[] pxTrends,
                           final int[] volumes)
    {
        final int numTrades = tags.length;
        int index;
        TradeEvent.Builder tradeBuilder;
        TradeEvent trade;
        Duration timeDelta;
        Set<Integer> eventTags;

        sLogger.debug(
            "{}: publishing {} tagged trades to {} file.",
            mAgentName,
            numTrades,
            mTradeFile.topicKey());

        for (index = 0; index < numTrades; ++index)
        {
            tradeBuilder = TradeEvent.builder();
            trade = tradeBuilder.symbol(symbol)
                                .price(prices[index])
                                .size(sizes[index])
                                .priceTrend(pxTrends[index])
                                .volume(volumes[index])
                                .build();
            eventTags= generateTagSet(tags[index]);

            try
            {
                mTradeConnection.add(eventTags, trade);
            }
            catch (Exception jex)
            {
                sLogger.warn("{}: failed to add trade {}.",
                             mAgentName,
                             trade,
                             jex);
            }

            timeDelta = generateTimeDelta();
            mTestClock =
                Clock.fixed(
                    (mTestClock.instant()).plus(timeDelta),
                    GMT);
            mTradeFile.setSystemClock(mTestClock);

            LockSupport.parkNanos(timeDelta.toNanos());
        }

        sLogger.debug(
            "{}: {} tagged trades published to {} file, end time {}.",
            mAgentName,
            numTrades,
            mTradeFile.topicKey(),
            mTestClock.instant());
    } // end of postTrades(int[][])

    public void retrieve(final int tag,
                         final CountDownLatch doneSignal)
    {
        sLogger.debug(
            "{}: retrieving trades tagged {}.",
            mAgentName,
            tag);

        mDoneSignal = doneSignal;
        mTrades.clear();
        mTradeConnection.retrieve(
            tag, this::onEvent, this::onDone);

        sLogger.info(
            "{}: tagged event retrieval request in place.",
            mAgentName);
    } // end of retrieve(int, CountDownLatch)

    private static Set<Integer> generateTagSet(final int[] tags)
    {
        final Set<Integer> retval = new TreeSet<>();

        for (int tag : tags)
        {
            retval.add(tag);
        }

        return (retval);
    } // end of generateTagSet(int[])
} // end of class TagAgent
