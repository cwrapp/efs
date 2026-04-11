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

package org.efs.bus;

import org.efs.event.EfsTopicKey;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * Receives performance events on the ping topic and echoes back
 * on the pong topic.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class PongAgent
    extends AbstractTestAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Statics.
    //

    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(PongAgent.class);

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public PongAgent(final String agentName,
                     final EfsEventBus bus,
                     final int totalEventCount,
                     final EfsTopicKey<PerformanceEvent> pingKey,
                     final EfsTopicKey<PerformanceEvent> pongKey)
    {
        super (agentName,
               bus,
               totalEventCount,
               pingKey,
               pongKey);
    } // end of PongAgent(...)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // AbstractTestAgent Method Overrides.
    //

    @Override
    public void start()
    {
        super.start();

        mAdvertisement = mBus.advertise(mPongKey,
                                        this::onSubscribeStatus,
                                        this);
        mSubscription = mBus.subscribe(mPingKey,
                                       this::onPublishStatus,
                                       this::onEvent,
                                       this);
        mAdvertisement.publishStatus(true);
    } // end of start()

    //
    // end of AbstractTestAgent Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Event Methods.
    //

    private void onSubscribeStatus(final EfsSubscribeStatus<PerformanceEvent> sse)
    {
        sLogger.debug("{}: received {}.", mAgentName, sse);

        // Has pong subscribe status transitioned from down to
        // up?
        if (!mPongFlag && sse.activeSubscribers() > 0)
        {
            // Yes.
            mPongFlag = true;

            sLogger.debug("{}: topic {} has subscribers.",
                          mAgentName,
                          mPongKey);
        }
        // Has pong subscribe status transitioned from up to
        // down?
        else if (mPongFlag && sse.activeSubscribers() == 0)
        {
            // Yes.
            mPongFlag = false;

            sLogger.debug("{}: topic {} no subscribers.",
                          mAgentName,
                          mPongKey);
        }
    } // end of onSubscribeStatus(EfsSubscribeStatus)

    private void onPublishStatus(final EfsPublishStatus<PerformanceEvent> pse)
    {
        sLogger.debug("{}: received {}.", mAgentName, pse);

        if (pse.activePublishers() == 1)
        {
            sLogger.debug("{}: topic {} has publishers.",
                          mAgentName,
                          mPingKey);

            mPingFlag = true;
        }
        else if (pse.activePublishers() == 0)
        {
            sLogger.debug("{}: topic {} no publishers.",
                          mAgentName,
                          mPingKey);

            mPingFlag = true;
        }
    } // end of onPublishStatus(EfsPublishStatus)

    /**
     * Tracks event delivery latency.
     * @param event performance event containing event creation
     * time.
     */
    protected void onEvent(final PerformanceEvent event)
    {
        final long timestamp = System.nanoTime();
        final int eventIndex = event.index;
        final int deltaCount =
            mLatencyTracker.addDelta(timestamp - event.nanotime);

        sLogger.debug("{}: received performance event {}, max {}.",
                      mAgentName,
                      deltaCount,
                      mTotalEventCount);

        // Has total event count reached?
        // Is pong topic up?
        if (deltaCount < mTotalEventCount && mPongFlag)
        {
            sLogger.debug("{}: echoing performance event {}.",
                          mAgentName,
                          eventIndex);

            try
            {
                mAdvertisement.publish(
                    new PerformanceEvent(
                        eventIndex, System.nanoTime(), null));
            }
            catch (Exception jex)
            {
                sLogger.warn(
                    "{}: failed to publish {} event {}.",
                    mAgentName,
                    mPongKey,
                    eventIndex,
                    jex);

                mException = jex;
            }
        }
    } // end of onEvent(PerformanceEvent)

    //
    // end of Event Methods.
    //-----------------------------------------------------------
} // end of class PongAgent
