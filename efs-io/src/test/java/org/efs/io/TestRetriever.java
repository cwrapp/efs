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

import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.query.Query;
import static com.googlecode.cqengine.query.QueryFactory.greaterThanOrEqualTo;
import static com.googlecode.cqengine.query.QueryFactory.lessThanOrEqualTo;
import static com.googlecode.cqengine.query.QueryFactory.or;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import org.decimal4j.api.Decimal;
import org.decimal4j.scale.Scale2f;
import org.efs.io.EfsFile.AccessMode;
import org.efs.io.EfsFileConnection.Retrieval;
import org.efs.io.RetrievalCompleteEvent.CompletionType;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;


/**
 * Requests market data events from {@link EfsFile}.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class TestRetriever
    extends AbstractTestAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Statics.
    //

    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(TestRetriever.class);

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Decrement when retrieval is complete.
     */
    private CountDownLatch mRetrieveSignal;

    /**
     * Track number of trades received.
     */
    private int mTradesReceived;

    /**
     * Verify that received trade price is &le; to this price.
     */
    private Decimal<Scale2f> mMaxPrice;

    /**
     * Verify that received trade size is &ge; to this size.
     */
    private int mMinSize;

    /**
     * Retrieval completion reason.
     */
    private CompletionType mReason;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new test agent instance with given name and
     * trade file.
     * @param agentName unique agent name.
     * @param tradeFile efs file containing trades.
     */
    public TestRetriever(final String agentName,
                         final EfsFile<TradeEvent> tradeFile)
    {
        super (agentName, AccessMode.READ_ONLY, tradeFile);
    } // end of TestRetriever(String, EfsFile<>)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    public int tradesReceived()
    {
        return (mTradesReceived);
    } // end of tradesReceived()

    public CompletionType completionReason()
    {
        return (mReason);
    } // end of completionReason()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Event Handlers.
    //

    public void onEvent(final EfsRow<TradeEvent> row)
    {
        final TradeEvent trade = row.getEvent();
        final int priceCompare =
            (trade.getPrice()).compareTo(mMaxPrice);

        ++mTradesReceived;

        sLogger.debug("{}: received {}.", mAgentName, row);

        if (priceCompare > 0)
        {
            assertThat(trade.getSize())
                .isGreaterThanOrEqualTo(mMinSize);
        }
        else
        {
            assertThat(trade.getPrice())
                .isLessThanOrEqualTo(mMaxPrice);
        }
    } // end of onEvent(DeliveryEvent<>)

    public void onDone(final RetrievalCompleteEvent<TradeEvent> event)
    {
        mReason = event.completionType();

        sLogger.info(
            "{}: retrieval {} completed, reason {}, {} trades.",
            mAgentName,
            (event.retrieval()).id(),
            mReason,
            mTradesReceived);

        mRetrieveSignal.countDown();
    } // end of onDone(RetrievalCompleteEvent)

    //
    // end of Event Handlers.
    //-----------------------------------------------------------

    public void add(final TradeEvent trade)
    {
        mTradeConnection.add(trade);
    } // end of add(final TradeEvent trade)

    public void retrieve(final EfsInterval interval,
                         final Query<EfsRow<TradeEvent>> query,
                         final Consumer<EfsRow<TradeEvent>> eventCB,
                         final Consumer<RetrievalCompleteEvent<TradeEvent>> completionCB)
    {
        mTradeConnection.retrieve(
            interval, query, eventCB, completionCB);
    } // end of retrieve(...)

    @SuppressWarnings ("unchecked")
    public void retrieveTrades(final EfsInterval interval,
                               final Decimal<Scale2f> maxPrice,
                               final int minSize,
                               final CountDownLatch doneSignal)
    {
        final Query<EfsRow<TradeEvent>> query =
            generateQuery(maxPrice, minSize);
        final Retrieval<TradeEvent> request;

        sLogger.info(
            "{}: retrieving trades over interval {}, max price {}, min size {}.",
            mAgentName,
            interval,
            maxPrice,
            minSize);

        mRetrieveSignal = doneSignal;
        mMaxPrice = maxPrice;
        mMinSize = minSize;

        // Reset trades received count to zero.
        mTradesReceived = 0;

        request =
            mTradeConnection.retrieve(interval,
                                      query,
                                      this::onEvent,
                                      this::onDone);

        sLogger.info("{}: retrieval request {} in place.",
                     mAgentName,
                     request.id());
    } // end of retrieveTrades(...)

    @SuppressWarnings ("unchecked")
    private Query<EfsRow<TradeEvent>> generateQuery(final Decimal<Scale2f> maxPrice,
                                                    final int minSize)
    {
        final Attribute<EfsRow<TradeEvent>, Decimal<Scale2f>> pxAttr =
            (Attribute<EfsRow<TradeEvent>, Decimal<Scale2f>>)
                mTradeFile.attribute("price");
        final Attribute<EfsRow<TradeEvent>, Integer> szAttr =
            (Attribute<EfsRow<TradeEvent>, Integer>)
                mTradeFile.attribute("size");

        return (or(lessThanOrEqualTo(pxAttr, maxPrice),
                   greaterThanOrEqualTo(szAttr, minSize)));
    } // end of generateQuery(Decimal<>, int)
} // end of class TestRetriever
