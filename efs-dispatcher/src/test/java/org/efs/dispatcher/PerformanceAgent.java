//
// Copyright 2025 Charles W. Rapp
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

package org.efs.dispatcher;

import java.util.concurrent.CountDownLatch;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * Post performance events to this agent. Agent tracks latency
 * and makes final report.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class PerformanceAgent
    extends AbstractTestAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger();

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Decrement counter every time a event is received
     * <em>after</em> event processing is complete.
     */
    private final CountDownLatch mDoneSignal;

    /**
     * Store caught exception causing a failure.
     */
    private Throwable mException;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public PerformanceAgent(final String agentName,
                            final int totalEventCount,
                            final CountDownLatch doneSignal)
    {
        super (agentName, totalEventCount);

        mDoneSignal = doneSignal;
    } // end of PerformanceAgent(...)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    public CountDownLatch doneSignal()
    {
        return (mDoneSignal);
    } // end of doneSignal()

    public Throwable exception()
    {
        return (mException);
    } // end of exception()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Event Methods.
    //

    /**
     * Tracks event delivery latency.
     * @param event performance event containing event creation
     * time.
     */
    public void onEvent(final PerformanceEvent event)
    {
        final long timestamp = System.nanoTime();

        sLogger.trace("Received event {}, max {}.",
                      mEventCount,
                      mTotalEventCount);

        if (mEventCount < mTotalEventCount)
        {
            mLatency[mEventCount] = (timestamp - event.nanotime);
            ++mEventCount;

            // Reply to this performance event.
            (event.replyTo).dispatch(
                new ReplyEvent(event.index, timestamp));
        }

        mDoneSignal.countDown();
    } // end of onEvent(PerformanceEvent)

    //
    // end of Event Methods.
    //-----------------------------------------------------------
} // end of class PerformanceAgent
