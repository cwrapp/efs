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
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.efs.dispatcher.ReplyTo;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * Publishes performance events on the ping topic and receives
 * it back on the pong topic.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@SuppressWarnings({"java:S5977"})
public final class PingAgent
    extends AbstractTestAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Statics.
    //

    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(PingAgent.class);

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
     * Set to {@code true} if random events are published.
     */
    private final boolean mRandomFlag;

    /**
     * Publish at least this many events on each timer
     * expiration.
     */
    private final int mMinEvent;

    /**
     * Publish at most this many events on each timer
     * expiration.
     */
    private final int mMaxEvent;

    /**
     * Number of {@link PongAgent}s subscribing to pings.
     */
    private final int mPongerCount;

    /**
     * Decrement counter every time a event is received
     * <em>after</em> event processing is complete.
     */
    private final CountDownLatch mDoneSignal;

    /**
     * Used for reply performance tests. This reply is created
     * when pinger is started due to it containing a reference
     * back to this pinger instance.
     */
    private ReplyTo<PerformanceEvent> mReply;

    /**
     * When this timer expires, publish on pinger topic.
     */
    private ScheduledFuture<?> mFuture;

    /**
     * Increment each time a performance event is published.
     */
    private int mEventIndex;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public PingAgent(final String agentName,
                     final EfsEventBus bus,
                     final int totalEventCount,
                     final EfsTopicKey<PerformanceEvent> pingKey,
                     final EfsTopicKey<PerformanceEvent> pongKey,
                     final long delay,
                     final TimeUnit timeUnit)
    {
        this (agentName,
              bus,
              totalEventCount,
              pingKey,
              pongKey,
              delay,
              timeUnit,
              false,
              1,
              1,
              1);
    } // end of PingAgent(...)

    public PingAgent(final String agentName,
                     final EfsEventBus bus,
                     final int totalEventCount,
                     final EfsTopicKey<PerformanceEvent> pingKey,
                     final EfsTopicKey<PerformanceEvent> pongKey,
                     final long delay,
                     final TimeUnit timeUnit,
                     final boolean randomFlag,
                     final int minEvent,
                     final int maxEvent,
                     final int pongerCount)
    {
        super (agentName,
               bus,
               (totalEventCount * pongerCount),
               pingKey,
               pongKey);

        mDelay = delay;
        mTimeUnit = timeUnit;
        mDoneSignal = new CountDownLatch(totalEventCount);
        mRandomFlag = randomFlag;
        mMinEvent = minEvent;
        mMaxEvent = maxEvent;
        mPongerCount = pongerCount;
    } // end of PingAgent(...)

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

        mReply = new ReplyTo<>(this::onEvent, this);

        mAdvertisement = mBus.advertise(mPingKey,
                                        this::onSubscribeStatus,
                                        this);
        mSubscription = mBus.subscribe(mPongKey,
                                       this::onPublishStatus,
                                       this::onEvent,
                                       this);
        mAdvertisement.publishStatus(true);
    } // end of start()

    @Override
    public void stop()
    {
        mPingFlag = false;
        super.stop();

        stopPublishTimer();
    } // end of stop()

    //
    // end of AbstractTestAgent Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    public CountDownLatch doneSignal()
    {
        return (mDoneSignal);
    } // end of doneSignal()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Event Methods.
    //

    private void onSubscribeStatus(final EfsSubscribeStatus<PerformanceEvent> sse)
    {
        sLogger.debug("{}: received {}.", mAgentName, sse);

        // Have all pong agents subscribed to to ping topic?
        if (!mPingFlag &&
            sse.activeSubscribers() == mPongerCount)
        {
            mPingFlag = true;

            sLogger.debug("{}: topic {} has subscribers.",
                          mAgentName,
                          mPingKey);

            // Schedule publishing.
            startPublishTimer();
        }
        // Have all pong agents unsubscribed from ping topic?
        else if (mPingFlag && sse.activeSubscribers() == 0)
        {
            // Yes.
            mPongFlag = false;

            sLogger.debug("{}: topic {} no subscribers.",
                          mAgentName,
                          mPingKey);
        }
    } // end of onSubscribeStatus(EfsSubscribeStatus)

    private void onPublishStatus(final EfsPublishStatus<PerformanceEvent> pse)
    {
        sLogger.debug("{}: received {}.", mAgentName, pse);

        // Are all pong agents publishing to the pong topic?
        if (pse.activePublishers() == mPongerCount)
        {
            sLogger.debug("{}: topic {} has publishers.",
                          mAgentName,
                          mPingKey);

            mPongFlag = true;

            // Schedule publishing.
            startPublishTimer();
        }
        // Have all pong agents stopped publishing to the pong
        // topic?
        else if (pse.activePublishers() == 0)
        {
            sLogger.debug("{}: topic {} no publishers.",
                          mAgentName,
                          mPingKey);

            mPongFlag = false;
        }
    } // end of onPublishStatus(EfsPublishStatus)

    private void onEvent(final PerformanceEvent event)
    {
        final long timestamp = System.nanoTime();
        final int deltaCount =
            mLatencyTracker.addDelta(timestamp - event.nanotime);

        if (deltaCount < mTotalEventCount)
        {
            sLogger.debug(
                "{}: received performance reply {}, max {}.",
                mAgentName,
                deltaCount,
                mTotalEventCount);

            mDoneSignal.countDown();
        }
    } // end of onEvent(PerformanceEvent)

    //
    // end of Event Methods.
    //-----------------------------------------------------------

    /**
     * Starts the ping publish timer if all pong agents are in
     * place.
     */
    private void startPublishTimer()
    {
        // Are all pong agents subscribed to the ping topic?
        // Is there someone publishing to the pong topic?
        if (mPingFlag && mPongFlag)
        {
            // Yes to both. Clear to start sending performance
            // events.
            sLogger.info("{}: starting publish timer {} {}.",
                         mAgentName,
                         mDelay,
                         mTimeUnit);

            final Runnable publishTask = (mRandomFlag ?
                                          this::postRandomEvents :
                                          this::postEvent);

            mFuture =
                sTimer.scheduleWithFixedDelay(publishTask,
                                              mDelay,
                                              mDelay,
                                              mTimeUnit);
        }
    } // end of startPublishTimer()

    /**
     * Stops ping publish timer if running.
     */
    private void stopPublishTimer()
    {
        final ScheduledFuture<?> timer = mFuture;

        mFuture = null;

        if (timer != null)
        {
            sLogger.info("{}: stopping publish timer.",
                         mAgentName);

            timer.cancel(true);
        }
    } // end of stopPublishTimer()

    /**
     * Publishes a single performance event on ping topic.
     */
    private void postEvent()
    {
        final PerformanceEvent event =
            new PerformanceEvent(
                mEventIndex++, System.nanoTime(), mReply);

        if (mPingFlag)
        {
            sLogger.debug("{}: publishing performance event {}.",
                          mAgentName,
                          event.index);

            try
            {
                mAdvertisement.publish(event);
            }
            catch (Exception jex)
            {
                sLogger.warn("{}: failed to publish {} event.",
                             mAgentName,
                             mPingKey,
                             jex);

                mException = jex;
            }
        }
    } // end of postEvent()

    /**
     * Publishes a random number of performance events on ping
     * topic.
     */
    private void postRandomEvents()
    {
        // Are pong agents subscribed to ping topic?
        // Are pong agents advertised to pong topic?
        if (mPingFlag && mPongFlag)
        {
            final int numEvents =
                (sRandomizer.nextInt(mMaxEvent) + mMinEvent);
            int i;
            PerformanceEvent event;

            sLogger.debug("{}: publishing {} {} events.",
                          mAgentName,
                          numEvents,
                          mPingKey);

            for (i = 0; i < numEvents; ++i)
            {
                event =
                    new PerformanceEvent(
                        mEventIndex++, System.nanoTime(), mReply);

                sLogger.debug("{}: publishing {}.",
                              mAgentName,
                              event);

                try
                {
                    mAdvertisement.publish(event);
                }
                catch (Exception jex)
                {
                    sLogger.warn("Failed to publish {} event.",
                                 mPingKey,
                                 jex);

                    mException = jex;
                }
            }
        }
    } // end of postRandomEvents()
} // end of class PingAgent
