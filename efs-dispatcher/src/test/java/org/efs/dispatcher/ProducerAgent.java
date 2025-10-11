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

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.efs.event.ReplyTo;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;


/**
 * Posts performance events to {@link PerformanceAgent} and
 * expects a reply.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@SuppressWarnings({"java:S5977"})
public final class ProducerAgent
    extends AbstractTestAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String AGENT_NAME = "ProducerAgent";

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger();

    /**
     * Timer used to schedule publish timer.
     */
    private static final ScheduledExecutorService sTimer =
        Executors.newSingleThreadScheduledExecutor();

    /**
     * Used to randomly set number of published events.
     */
    private static final Random sRandomizer = new Random();

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Publish performance events at this fixed delay.
     */
    private final long mDelay;

    /**
     * {@link #mDelay} time unit.
     */
    private final TimeUnit mTimeUnit;

    /**
     * Post events to these agents.
     */
    private final PerformanceAgent[] mAgents;

    /**
     * Set to {@code true} if random events are published.
     */
    private final boolean mRandomFlag;

    private int mAgentCount;
    private int mEventIndex;
    private int mMinEvent;
    private int mMaxEvent;

    /**
     * {@code PerformanceAgent} should post a
     * {@code PerformanceEvent} via this
     */
    private ReplyTo<ReplyEvent> mReplyTo;

    /**
     * Timer used to transmit
     */
    private ScheduledFuture<?> mFuture;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public ProducerAgent(final int totalEventCount,
                         final long delay,
                         final TimeUnit timeUnit,
                         final PerformanceAgent agent)
    {
        this(totalEventCount,
             delay,
             timeUnit,
             new PerformanceAgent[] { agent },
             false,
             1,
             1);
    } // end of ProducerAgent(int,long,TimeUnit,PerformanceAgent)

    public ProducerAgent(final int totalEventCount,
                         final long delay,
                         final TimeUnit timeUnit,
                         final PerformanceAgent[] agents,
                         final boolean randomFlag,
                         final int minEvent,
                         final int maxEvent)
    {
        super (AGENT_NAME, totalEventCount);

        mDelay = delay;
        mTimeUnit = timeUnit;
        mAgents = agents;
        mRandomFlag = randomFlag;
        mAgentCount = agents.length;
        mMinEvent = minEvent;
        mMaxEvent = maxEvent;
    } // end of ProducerAgent(...)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Abstract Method Overrides.
    //

    @Override
    public void start()
    {
        super.start();

        mReplyTo = new ReplyTo<>(this::onReply, this);

        // Schedule publishing.
        final Runnable publishTask = (mRandomFlag ?
                                      this::postRandomEvents :
                                      this::postEvent);

        mFuture =
            sTimer.scheduleWithFixedDelay(publishTask,
                                          mDelay,
                                          mDelay,
                                          mTimeUnit);
    } // end of start()

    @Override
    public void stop()
    {
        super.stop();

        if (mFuture != null)
        {
            mFuture.cancel(true);
        }
    } // end of stop()

    //
    // end of Abstract Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Event Methods.
    //

    private void onReply(final ReplyEvent event)
    {
        final long timestamp = System.nanoTime();

        sLogger.trace("Received reply {}, max {}.",
                      mEventCount,
                      mTotalEventCount);

        if (mEventCount < mTotalEventCount)
        {
            mLatency[mEventCount] = (timestamp - event.nanotime);
            ++mEventCount;
        }

        mStopTime = Instant.now();
    } // end of onReply(ReplyEvent)

    //
    // end of Event Methods.
    //-----------------------------------------------------------

    private void postEvent()
    {
        final PerformanceEvent event =
            new PerformanceEvent(
                mEventIndex++, System.nanoTime(), mReplyTo);

        try
        {
            EfsDispatcher.dispatch(
                mAgents[0]::onEvent, event, mAgents[0]);
        }
        catch (IllegalStateException statex)
        {
            sLogger.warn("Event dispatch failed.", statex);
        }
    } // end of postEvent()

    private void postRandomEvents()
    {
        final int numEvents =
            (sRandomizer.nextInt(mMaxEvent) + mMinEvent);
        int i;
        int agentIndex;
        PerformanceEvent event;

        for (i = 0; i < numEvents; ++i)
        {
            agentIndex = sRandomizer.nextInt(mAgentCount);
            event =
                new PerformanceEvent(
                    mEventIndex++, System.nanoTime(), mReplyTo);

            try
            {
                EfsDispatcher.dispatch(
                    mAgents[agentIndex]::onEvent,
                    event,
                    mAgents[agentIndex]);
            }
            catch (IllegalStateException statex)
            {
                sLogger.warn("Event dispatch failed.", statex);
            }
        }
    } // end of postRandomEvents()
} // end of class ProducerAgent
