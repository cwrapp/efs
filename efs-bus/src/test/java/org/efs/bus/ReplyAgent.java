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
import org.efs.dispatcher.ReplyTo;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * This pong agent replies to ping agent events using a
 * {@code ReplyTo}.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class ReplyAgent
    extends PongAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Statics.
    //

    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(ReplyAgent.class);

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public ReplyAgent(final String agentName,
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
    } // end of ReplyAgent()

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Event Methods.
    //

    @Override
    protected void onEvent(final PerformanceEvent event)
    {
        final long timestamp = System.nanoTime();
        final int eventIndex = event.index;
        final ReplyTo<PerformanceEvent> reply = event.reply;
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

            reply.dispatch(
                new PerformanceEvent(
                    eventIndex, System.nanoTime(), null));
        }
    } // end of onEvent(PerformanceEvent)

    //
    // end of Event Methods.
    //-----------------------------------------------------------
} // end of class ReplyAgent
