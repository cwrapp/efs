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
import org.efs.bus.EfsEventBus.Advertisement;
import org.efs.bus.EfsEventBus.Subscription;
import org.efs.dispatcher.IEfsAgent;
import org.efs.logging.AsyncLoggerFactory;
import org.efs.util.LatencyTracker;
import org.slf4j.Logger;


/**
 * Base class for performance test agents, containing statistics
 * data members.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public abstract class AbstractTestAgent
    implements IEfsAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Each bucket represents this many nanoseconds-worth of
     * data.
     */
    private static final long BUCKET_SIZE = 1_000L; // nanos.

    /**
     * Latencies &ge; to this value are stored in one bucket.
     */
    private static final long MAX_BUCKET = 10_000L; // nanos

    //-----------------------------------------------------------
    // Statics.
    //

    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(AbstractTestAgent.class);

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Agent unique name.
     */
    protected final String mAgentName;

    /**
     * Performance events are published and received via this
     * event bus.
     */
    protected final EfsEventBus mBus;

    /**
     * Total number of events expected.
     */
    protected final int mTotalEventCount;

    /**
     * Pinger publishes performance events on this key.
     */
    protected final EfsTopicKey<PerformanceEvent> mPingKey;

    /**
     * Ponger echos performance events back on this key.
     */
    protected final EfsTopicKey<PerformanceEvent> mPongKey;

    /**
     * Store event transmission latencies in this tracker.
     */
    protected final LatencyTracker mLatencyTracker;

    /**
     * Subscription to pinged performance event.
     */
    protected Subscription<PerformanceEvent> mSubscription;

    /**
     * Advertisement of ponged performance event.
     */
    protected Advertisement<PerformanceEvent> mAdvertisement;

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
     * Store caught exception causing a failure.
     */
    protected Throwable mException;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public AbstractTestAgent(final String agentName,
                             final EfsEventBus bus,
                             final int totalEventCount,
                             final EfsTopicKey<PerformanceEvent> pingKey,
                             final EfsTopicKey<PerformanceEvent> pongKey)
    {
        mAgentName = agentName;
        mBus = bus;
        mTotalEventCount = totalEventCount;
        mPingKey = pingKey;
        mPongKey = pongKey;
        mLatencyTracker =
            (LatencyTracker.builder())
                .deltaCount(mTotalEventCount)
                .bucketIntervalSize(BUCKET_SIZE)
                .bucketMaximum(MAX_BUCKET)
                .build();

        mPingFlag = false;
        mPongFlag = false;
    } // end of AbstractTestAgent(...)

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
     * This method may be overridden but override method should
     * call {@code super.start()} as its first statement.
     */
    public void start()
    {
        final Instant now = Instant.now();

        mLatencyTracker.startTime(now);
        mLatencyTracker.stopTime(now);
    } // end of start()

    public void stop()
    {
        String accessPoint = "(not set)";

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

    //
    // end of Set Methods.
    //-----------------------------------------------------------
} // end of class AbstractTestAgent
