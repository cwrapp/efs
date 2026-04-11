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
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.efs.dispatcher.EfsDispatchTarget;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;
import org.efs.logging.AsyncLoggerFactory;
import org.efs.util.LatencyTracker;
import org.slf4j.Logger;


/**
 * Routes performance events between multiple {@link PongAgent}s.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class PongRouter
    implements IEfsAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final long INTERVAL_SIZE = 1_000L; // nanoseconds
    private static final long MAX_INTERVAL = 10_000L; // nanoseconds

    //-----------------------------------------------------------
    // Statics.
    //

    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(PongRouter.class);

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Agent unique name.
     */
    private final String mAgentName;

    /**
     * Performance events are published and received via this
     * event bus.
     */
    private final EfsEventBus mBus;

    /**
     * Register {@link ChildPongAgent}s with this dispatcher.
     */
    private final String mDispatcher;

    /**
     * Total number of events expected.
     */
    private final int mTotalEventCount;

    /**
     * Pinger publishes performance events on this key.
     */
    private final EfsTopicKey<PerformanceEvent> mPingKey;

    /**
     * Ponger echos performance events back on this key.
     */
    private final EfsTopicKey<PerformanceEvent> mPongKey;

    /**
     * Used to track dispatch latencies.
     */
    private final LatencyTracker mLatencyTracker;

    /**
     * Subscription to pinged performance event.
     */
    protected EfsEventBus.Subscription<PerformanceEvent> mSubscription;

    /**
     * Advertisement of ponged performance event.
     */
    protected EfsEventBus.Advertisement<PerformanceEvent> mAdvertisement;

    /**
     * Set to {@code true} when ping topic is up from agent's
     * perspective (has subscribers for pinger and has publishers
     * for ponger). Initialized to {@code false}.
     */
    protected boolean mPingFlag;

    /**
     * Set to {@code true} when pong topic is up from agent's
     * perspective (has publishers for pinger and has subscribers
     * for ponger). Initialized to {@code false}.
     */
    protected boolean mPongFlag;

    /**
     * Number of {@code ChildPongAgent}s.
     */
    private final int mChildCount;

    /**
     * Loop over this list, forwarding performance events to
     * each target in turn.
     */
    private final List<EfsDispatchTarget<PerformanceEvent>> mTargets;

    /**
     * Store caught exception causing a failure.
     */
    protected Throwable mException;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public PongRouter(final String agentName,
                      final EfsEventBus bus,
                      final String dispatcher,
                      final int totalEventCount,
                      final EfsTopicKey<PerformanceEvent> pingKey,
                      final EfsTopicKey<PerformanceEvent> pongKey,
                      final int childCount)
    {
        mAgentName = agentName;
        mBus = bus;
        mDispatcher = dispatcher;
        mTotalEventCount = totalEventCount;
        mPingKey = pingKey;
        mPongKey = pongKey;

        mChildCount = childCount;
        mTargets = new ArrayList<>(childCount);

        final LatencyTracker.Builder builder =
            LatencyTracker.builder();

        mLatencyTracker =
            builder.deltaCount(totalEventCount)
                   .bucketIntervalSize(INTERVAL_SIZE)
                   .bucketMaximum(MAX_INTERVAL)
                   .build();
    } // end of PongRouter(...)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // IEfsAgent Interface Implementation.
    //

    @Override
    public final String name()
    {
        return (mAgentName);
    } // end of name()

    //
    // end of IEfsAgent Interface Implementation.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // IEventRouter Interface Implementation.
    //

    /**
     * Routes event to next child pong agent based on event
     * index.
     * @param event route this event to a child pong agent.
     * @return routing target.
     */
    private EfsDispatchTarget<PerformanceEvent> doRouting(final PerformanceEvent event)
    {
        final int index = (event.index % mChildCount);

        return (mTargets.get(index));
    } // end of doRouting(PerformanceEvent)

    //
    // end of IEventRouter Interface Implementation.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    @Nullable public final Throwable exception()
    {
        return (mException);
    } // end of exception()

    public final String generateResults()
    {
        return (mLatencyTracker.toString());
    } // end of generateResults()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Update latency tracker with latest dispatch delta.
     * @param delta time between event dispatch and event
     * receipt.
     * @param agentName child ponger agent name.
     * @return {@code true} if total event count not yet
     * reached.
     */
    /* package */ boolean updateLatency(final long delta,
                                        final String agentName)
    {
        final int deltaCount = mLatencyTracker.addDelta(delta);
        final boolean retcode = (deltaCount < mTotalEventCount);

        sLogger.debug("{}: received event {}, max {}.",
                      mAgentName,
                      deltaCount,
                      mTotalEventCount);

        return (retcode);
    } // end of updateLatency(long, String)

    /**
     * Echos performance event back to ping if advertisement is
     * up.
     * @param event echo this performance event.
     * @param agentName child pong agent name.
     */
    /* package */ void echo(final PerformanceEvent event,
                            final String agentName)
    {
        // Is pong topic up?
        if (mPongFlag)
        {
            final int eventIndex = event.index;

            // Yes. Echo performance event back to pinger.
            sLogger.debug("{}: echoing performance event {}.",
                          agentName,
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
                    agentName,
                    mPongKey,
                    eventIndex,
                    jex);

                mException = jex;
            }
        }
    } // end of echo(PerformanceEvent, String)

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    /**
     * Creates child pong agents and registers them with
     * configured dispatcher. Then advertises pong topic and
     * subscribes to ping topic.
     */
    public void start()
    {
        final Instant now = Instant.now();
        int index;
        String agentName;
        ChildPongAgent child;

        sLogger.debug("Starting pong router.");

        mLatencyTracker.startTime(now);
        mLatencyTracker.stopTime(now);

        // Create subordinate pong agents, assigning number of
        // expected events to each.
        for (index = 0; index < mChildCount; ++index)
        {
            agentName =
                BusPerformanceTest.PONGER_AGENT + "-" + index;

            child = new ChildPongAgent(agentName, this);
            EfsDispatcher.register(child, mDispatcher);

            mTargets.add(
                new EfsDispatchTarget<>(child::onEvent, child));
        }

        mAdvertisement = mBus.advertise(mPongKey,
                                        this::onSubscribeStatus,
                                        this);
        mSubscription =
            mBus.subscribeRouter(mPingKey,
                                 this::onPublishStatus,
                                 this::doRouting,
                                 this);
        mAdvertisement.publishStatus(true);
    } // end of start()

    public void stop()
    {
        String accessPoint = "(not set)";

        sLogger.debug("Stopping pong router.");

        mLatencyTracker.stopTime(Instant.now());

        mPingFlag = false;
        mPongFlag = false;

        try
        {
            accessPoint = "subscription";
            mSubscription.close();

            accessPoint = "advertisement";
            mAdvertisement.close();
        }
        catch (Exception jex)
        {
            sLogger.warn("Failed to close {}.",
                         accessPoint,
                         jex);
        }
    } // end of stop()

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

    //
    // end of Event Methods.
    //-----------------------------------------------------------
} // end of class PongRouter
