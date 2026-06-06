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

import com.google.common.collect.ImmutableMap;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.SimpleAttribute;
import com.googlecode.cqengine.query.Query;
import static com.googlecode.cqengine.query.QueryFactory.greaterThanOrEqualTo;
import static com.googlecode.cqengine.query.QueryFactory.lessThanOrEqualTo;
import static com.googlecode.cqengine.query.QueryFactory.or;
import com.googlecode.cqengine.query.option.QueryOptions;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.decimal4j.immutable.Decimal2f;
import org.efs.io.TradeEvent.PriceTrend;
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
    // Constants.
    //

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Market data event attributes.
     */
    private static final Map<String, Attribute<TradeEvent, ?>> sAttributes;

    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(TestRetriever.class);

    // Class static initialization.
    static
    {
        final ImmutableMap.Builder<String, Attribute<TradeEvent, ?>> builder =
            ImmutableMap.builder();
        String attributeName;

        attributeName = "symbol";
        builder.put(
            attributeName,
            new SimpleAttribute<TradeEvent, String>(attributeName)
            {
                @Override
                public String getValue(final TradeEvent row,
                                       final QueryOptions qOptions)
                {
                    return (row.getSymbol());
                }
            });

        attributeName = "price";
        builder.put(
            attributeName,
            new SimpleAttribute<TradeEvent, Double>(attributeName)
            {
                @Override
                public Double getValue(final TradeEvent row,
                                       final QueryOptions qo)
                {
                    return ((row.getPrice()).doubleValue());
                }
            });

        attributeName = "size";
        builder.put(
            attributeName,
            new SimpleAttribute<TradeEvent, Integer>(attributeName)
            {
                @Override
                public Integer getValue(final TradeEvent row,
                                        final QueryOptions qo)
                {
                    return (row.getSize());
                }
            });

        attributeName = "priceTrend";
        builder.put(
            attributeName,
            new SimpleAttribute<TradeEvent, PriceTrend>(attributeName)
            {
                @Override
                public PriceTrend getValue(final TradeEvent row,
                                           final QueryOptions qo)
                {
                    return (row.getPriceTrend());
                }
            });

        attributeName = "volume";
        builder.put(
            attributeName,
            new SimpleAttribute<TradeEvent, Integer>(attributeName)
            {
                @Override
                public Integer getValue(final TradeEvent row,
                                        final QueryOptions qo)
                {
                    return (row.getVolume());
                }
            });

        sAttributes = builder.build();
    } // end of class static initialization.

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
        super (agentName, tradeFile);
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

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Event Handlers.
    //

    private void onEvent(final EfsRow<TradeEvent> event)
    {
        ++mTradesReceived;

        sLogger.debug("{}: received {}.", mAgentName, event);
    } // end of onEvent(DeliveryEvent<>)

    private void onDone(final RetrievalCompleteEvent<TradeEvent> event)
    {
        sLogger.info("{}: retrieval completed, {} trades.",
                     mAgentName,
                     mTradesReceived);

        mRetrieveSignal.countDown();
    } // end of onDone(RetrievalCompleteEvent)

    //
    // end of Event Handlers.
    //-----------------------------------------------------------

    // TODO: add retrieveal bad argument tests.

    @SuppressWarnings ("unchecked")
    public void retrieveTrades(final EfsInterval interval,
                               final Decimal2f maxPrice,
                               final int minSize,
                               final CountDownLatch doneSignal)
    {
        final Attribute<TradeEvent, Double> priceAttribute =
            (Attribute<TradeEvent, Double>)
                sAttributes.get("price");
        final Attribute<TradeEvent, Integer> sizeAttribute =
            (Attribute<TradeEvent, Integer>)
                sAttributes.get("size");
        final Query<TradeEvent> query =
            or(lessThanOrEqualTo(
                   priceAttribute, maxPrice.doubleValue()),
               greaterThanOrEqualTo(sizeAttribute, minSize));

        sLogger.info(
            "{}: retrieving trades over interval {}, max price {}, min size {}.",
            mAgentName,
            interval,
            maxPrice,
            minSize);

        mRetrieveSignal = doneSignal;

        // Reset trades received count to zero.
        mTradesReceived = 0;

        mTradeFile.retrieve(interval,
                            query,
                            this::onEvent,
                            this::onDone,
                            this);
    } // end of retrieveTrades(...)
} // end of class TestRetriever
