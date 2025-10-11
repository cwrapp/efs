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

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Formatter;


/**
 * Base class for performance test agents and contains statistics
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
    private static final long MAX_BUCKET = 50_000L; // nanos

    /**
     * The number of buckets is the maximum bucket time limit
     * divided by the bucket time size plus one.
     */
    private static final int BUCKET_COUNT =
        ((int) (MAX_BUCKET / BUCKET_SIZE) + 1);

    //-----------------------------------------------------------
    // Statics.
    //

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Agent unique name.
     */
    protected final String mAgentName;

    /**
     * Total number of events expected.
     */
    protected final int mTotalEventCount;

    /**
     * Stored latency timestamps.
     */
    protected final long[] mLatency;

    /**
     * Place latency times into buckets.
     */
    private final Bucket[] mBuckets;

    /**
     * Agent start timestamp.
     */
    protected Instant mStartTime;

    /**
     * Agent stop timestamp.
     */
    protected Instant mStopTime;

    /**
     * Maximum bucket index for quick reference.
     */
    private final int mMaxIndex;

    /**
     * Track total number of events processed.
     */
    protected int mEventCount;

    /**
     * Calculate the average latency once.
     */
    private long mAverageLatency;

    /**
     * Minimum latency.
     */
    private long mMinLatency;

    /**
     * Maximum latency.
     */
    private long mMaxLatency;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    protected AbstractTestAgent(final String agentName,
                                final int totalEventCount)
    {
        mAgentName = agentName;
        mTotalEventCount = totalEventCount;

        mLatency = new long[mTotalEventCount];
        mBuckets = new Bucket[BUCKET_COUNT];
        mMaxIndex = (BUCKET_COUNT - 1);

        // Initialize the bucket array.
        long bi;
        long bnext;
        int i;

        for (i = 0, bi = 0L, bnext = BUCKET_SIZE;
             i < mMaxIndex;
             ++i, bi = bnext, bnext += BUCKET_SIZE)
        {
            mBuckets[i] = new Bucket(bi, bnext);
        }

        mBuckets[mMaxIndex] = new Bucket(MAX_BUCKET, 0L);
    } // end of AbstractTestAgent(String, int)

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

    public final int eventCount()
    {
        return (mEventCount);
    } // end of eventCount()

    public final long averageLatency()
    {
        return (mAverageLatency);
    } // end of averageLatency()

    public final long minimumLantency()
    {
        return (mMinLatency);
    } // end of minimumLantency()

    public final long maximumLatency()
    {
        return (mMaxLatency);
    } // end of maximumLatency()

    public final Bucket[] buckets()
    {
        return (mBuckets);
    } // end of buckets()

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
        mStartTime = Instant.now();
        mStopTime = mStartTime;
    } // end of start()

    public void stop()
    {
        mStopTime = Instant.now();
    } // end of stop()

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    public final String generateResults()
    {
        final String retval;

        if (mEventCount == 0)
        {
            retval =
                String.format(
                    "%n%s: no events received.", mAgentName);
        }
        else
        {
            retval = doGenerateResults();
        }

        return (retval);
    } // end of generateResults()

    private String doGenerateResults()
    {
        final Duration runTime =
            Duration.between(mStartTime, mStopTime);
        final Formatter output = new Formatter();
        final int median = (mEventCount / 2);
        final int p75 = (int) (mEventCount * .75d);
        final int p90 = (int) (mEventCount * .9d);
        final int p95 = (int) (mEventCount * .95d);
        final int p99 = (int) (mEventCount * .99d);
        final long[] latencies = calculateLatency();
        int i;

        output.format("%n%s:%nRun time: %s%n%n",
                      mAgentName,
                      formatDuration(runTime));

        output.format("Messages received: %,d%n", mEventCount);
        output.format("Latency:%n");
        output.format(
            "       minimum= %,11d nanoseconds%n", mMinLatency);
        output.format(
            "        median= %,11d nanoseconds.%n",
            latencies[median]);
        output.format(
            "75%% percentile= %,11d nanoseconds%n",
            latencies[p75]);
        output.format(
            "90%% percentile= %,11d nanoseconds%n",
            latencies[p90]);
        output.format(
            "95%% percentile= %,11d nanoseconds%n",
            latencies[p95]);
        output.format(
            "99%% percentile= %,11d nanoseconds%n",
            latencies[p99]);
        output.format(
            "       maximum= %,11d nanoseconds.%n", mMaxLatency);
        output.format(
            "       average= %,11d nanoseconds.%n%n", mAverageLatency);

        output.format("Time intervals:%n");
        for (i = 0; i < BUCKET_COUNT; ++i)
        {
            output.format("%s%n", mBuckets[i]);
        }

        return (output.toString());
    } // end of doGenerateResults()

    private long[] calculateLatency()
    {
        int i;
        int bucketIndex;
        long sum = 0L;
        final long[] retval = new long[mEventCount];

        System.arraycopy(mLatency, 0,
                         retval, 0,
                         mEventCount);
        Arrays.sort(retval);

        mMinLatency = Long.MAX_VALUE;
        mMaxLatency = -1L;

        for (i = 0; i < mEventCount; ++i)
        {
            sum += retval[i];

            if (retval[i] < mMinLatency)
            {
                mMinLatency = retval[i];
            }

            if (retval[i] > mMaxLatency)
            {
                mMaxLatency = retval[i];
            }

            if (retval[i] >= MAX_BUCKET)
            {
                bucketIndex = mMaxIndex;
            }
            else
            {
                bucketIndex = (int) (retval[i] / BUCKET_SIZE);
            }

            mBuckets[bucketIndex].increment();
        }

        mAverageLatency = (sum / mEventCount);

        return (retval);
    } // end of calculateLatency()

    private String formatDuration(final Duration d)
    {
        final StringBuilder retval = new StringBuilder();
        final int hours = d.toHoursPart();
        final int minutes = d.toMinutesPart();
        final int seconds = d.toSecondsPart();
        final int millisecs = d.toMillisPart();

        if (hours > 0)
        {
            retval.append(hours).append(':');
        }

        if (hours > 0 || minutes > 0)
        {
            retval.append(minutes).append(':');
        }

        retval.append(seconds).append('.')
              .append(millisecs);

        return (retval.toString());
    } // end of formatDuration(Duration)

//---------------------------------------------------------------
// Inner classes.
//

    public static final class Bucket
    {

    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private final long mTime;
        private final long mMaxTime;
        private int mCount;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Bucket(final long time, final long maxTime)
        {
            mTime = time;
            mMaxTime = maxTime;
            mCount = 0;
        } // end of Bucket(long, long)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        @Override
        public String toString()
        {
            return (
                String.format(
                    "[%,10d, %,10d) %,7d items.",
                    mTime,
                    mMaxTime,
                    mCount));
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        public long time()
        {
            return (mTime);
        } // end of time()

        public int count()
        {
            return (mCount);
        } // end of count()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        public void increment()
        {
            ++mCount;
        } // end of increment()

        //
        // end of Set Methods.
        //-------------------------------------------------------
    } // end of class Bucket
} // end of class AbstractTestAgent
