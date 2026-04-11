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

package org.efs.util;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Objects;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;

/**
 * TODO
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class LatencyTracker
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Invalid delta deltaCount results in an
     * {@code IllegalArgumentException} with message {@value}.
     */
    public static final String INVALID_DELTA_COUNT =
        "count <= zero";

    /**
     * Invalid nanosecond bucket interval size results in an
     * {@code IllegalArgumentException} with message {@value}.
     */
    public static final String INVALID_BUCKET_SIZE =
        "bucketIntervalSize <= 0";

    /**
     * Invalid maximum bucket nanosecond range results in an
     * {@code IllegalArgumentException} with message {@value}.
     */
    public static final String INVALID_BUCKET_MAX =
        "bucketMaximum <= 0";

    /**
     * Invalid nanosecond latency delta results in an
     * {@code IllegalArgumentException} with message {@value}.
     */
    public static final String NEGATIVE_DELTA = "delta < zero";

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Test run start timestamp.
     */
    private Instant mStartTime;

    /**
     * Test run stop timestamp.
     */
    private Instant mStopTime;

    /**
     * Stored latency deltas.
     */
    private final long[] mDeltas;

    /**
     * Place latency times into buckets.
     */
    private final Bucket[] mBuckets;

    /**
     * Bucket nanosecond interval size.
     */
    private final long mBucketSize;

    /**
     * Latency deltas &ge; this value are counted in the overflow
     * bucket.
     */
    private final long mMaxBucketTime;

    /**
     * Maximum bucket index for quick reference.
     */
    private final int mMaxIndex;


    /**
     * Insert next nanosecond latency delta into {@link #mDeltas}
     * at this index. Value will be &le; total delta count.
     */
    private int mDeltaIndex;

    /**
     * Sum up latency deltas as they are added.
     */
    private long mDeltaSum;

    /**
     * Calculate the average latency delta once.
     */
    private long mAverageDelta;

    /**
     * Minimum latency delta.
     */
    private long mMinDelta;

    /**
     * Maximum latency delta.
     */
    private long mMaxDelta;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new latency tracker instance based on builder's
     * settings. Initializes delta bucket array with the final
     * overflow bucket.
     */
    private LatencyTracker(final Builder builder)
    {
        mDeltas = new long[builder.mDeltaCount];
        mMaxBucketTime = builder.mBucketMax;
        mBucketSize = builder.mBucketSize;

        final int bucketCount =
            ((int) (mMaxBucketTime / mBucketSize) + 1);

        mBuckets = new Bucket[bucketCount];
        mMaxIndex = (bucketCount - 1);

        mMinDelta = Long.MAX_VALUE;
        mMaxDelta = -1L;

        // Initialize delta bucket array.
        long bi;
        long bnext;
        int i;

        for (i = 0, bi = 0L, bnext = mBucketSize;
             i < mMaxIndex;
             ++i, bi = bnext, bnext += mBucketSize)
        {
            mBuckets[i] = new Bucket(bi, bnext);
        }

        // Create the overflow bucket.
        mBuckets[mMaxIndex] = new Bucket(mMaxBucketTime, 0L);
    } // end of LatencyTracker(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns text reporting latency results based on collected
     * deltas.
     * @return latency results.
     */
    @Override
    public String toString()
    {
        return (mDeltaIndex == 0 ?
                "no latency deltas collected" :
                generateResults());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns test run start time. May be {@code null} if not
     * previously set.
     * @return test run start time.
     */
    @Nullable public Instant startTime()
    {
        return (mStartTime);
    } // end of startTime()

    /**
     * Returns test run stop time. May be {@code null} if not
     * previously set.
     * @return test run stop time.
     */
    @Nullable public Instant stopTime()
    {
        return (mStopTime);
    } // end of stopTime()

    /**
     * Returns number of latency deltas collected.
     * @return collected deltas count.
     */
    public int deltaCount()
    {
        return (mDeltaIndex);
    } // end of deltaCount()

    /**
     * Returns bucket nanosecond interval size.
     * @return bucket interval in nanoseconds.
     */
    public long bucketIntervalSize()
    {
        return (mBucketSize);
    } // end of bucketIntervalSize()

    /**
     * Returns overflow bucket's maximum nanosecond time. Any
     * time deltas &ge; to this value are counted against the
     * overflow bucket.
     * @return overflow bucket's maximum nanosecond time.
     */
    public long maximumBucketTime()
    {
        return (mMaxBucketTime);
    } // end of maximumBucketTime()

    /**
     * Returns minimum latency delta.
     * @return minimum latency delta.
     */
    public long minimumDelta()
    {
        return (mMinDelta);
    } // end of minimumDelta()

    /**
     * Returns maximum latency delta.
     * @return maximum latency delta.
     */
    public long maximumDelta()
    {
        return (mMaxDelta);
    } // end of maximumDelta()

    /**
     * Returns current latency delta average.
     * @return current latency delta average.
     */
    public long averageDelta()
    {
        return (mAverageDelta);
    } // end of averageDelta()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets test run start time.
     * @param timestamp test run start time.
     */
    public void startTime(@Nonnull final Instant timestamp)
    {
        mStartTime =
            Objects.requireNonNull(
                timestamp, "timestamp is null");
    } // end of startTime(Instant)

    /**
     * Sets test run stop time.
     * @param timestamp test run stop time.
     */
    public void stopTime(@Nonnull final Instant timestamp)
    {
        mStopTime =
            Objects.requireNonNull(
                timestamp, "timestamp is null");
    } // end of stopTime(Instant)

    /**
     * Adds given latency delta to collected deltas array and
     * updates appropriate bucket tally. Updates average,
     * minimum, maximum delta values as needed. Ignores delta if
     * delta collection limit previously reached.
     * @param delta nanosecond latency delta.
     * @return index into delta array.
     * @throws IllegalArgumentException
     * if {@code delta} &lt; zero.
     */
    public int addDelta(final long delta)
    {
        final int retval = mDeltaIndex;

        if (delta < 0)
        {
            throw (new IllegalArgumentException(NEGATIVE_DELTA));
        }

        // Have we reached delta limit yet?
        if (mDeltaIndex < mDeltas.length)
        {
            final int bucketIndex =
                (delta >= mMaxBucketTime ?
                 mMaxIndex :
                 ((int) (delta / mBucketSize)));

            // No. Add delta to array.
            mDeltas[mDeltaIndex] = delta;
            ++mDeltaIndex;

            mDeltaSum += delta;
            mAverageDelta = (mDeltaSum / mDeltaIndex);

            // Is this a new minimum delta?
            if (delta < mMinDelta)
            {
                // Yes. Set new minimum delta value.
                mMinDelta = delta;
            }

            // Is this a new maximum delta?
            if (delta > mMaxDelta)
            {
                // Yes. Set new maximum delta value.
                mMaxDelta = delta;
            }

            mBuckets[bucketIndex].increment();
        }

        return (retval);
    } // end of addDelta(long)

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    /**
     * Returns a new {@link Builder} instance.
     * @return new latency tracker builder.
     */
    public static Builder builder()
    {
        return (new Builder());
    } // end of builder()

    /**
     * Returns latency report based on collected deltas. Caller
     * has determined that collected delta count &gt; zero.
     * @return latency report.
     */
    private String generateResults()
    {
        final Duration runTime =
            (mStartTime == null || mStopTime == null ?
             Duration.ZERO :
             Duration.between(mStartTime, mStopTime));
        final String retval;

        try (final Formatter output = new Formatter())
        {
            final long[] deltas = new long[mDeltaIndex];
            final int median = (mDeltaIndex / 2);
            final int p95 = (int) (mDeltaIndex * .95d);
            final int p99 = (int) (mDeltaIndex * .99d);
            final int p999 = (int) (mDeltaIndex * .999d);
            final int p9999 = (int) (mDeltaIndex * .9999d);
            int index;

            System.arraycopy(mDeltas, 0, deltas, 0, mDeltaIndex);
            Arrays.sort(deltas);

            output.format("Run time: %s%n%n",
                          formatDuration(runTime))
                  .format("Latency:%n")
                  .format("  minimum = %,11d nanoseconds%n",
                          mMinDelta)
                  .format("   median = %,11d nanseconds%n",
                          deltas[median])
                  .format("  average = %,11d nanoseconds%n",
                          mAverageDelta)
                  .format("      95%% = %,11d nanoseconds%n",
                          deltas[p95])
                  .format("      99%% = %,11d nanoseconds%n",
                          deltas[p99])
                  .format("    99.9%% = %,11d nanoseconds%n",
                          deltas[p999])
                  .format("   99.99%% = %,11d nanoseconds%n",
                          deltas[p9999])
                  .format("  maximum = %,11d nanoseconds%n%n",
                          mMaxDelta)
                  .format("Time intervals:%n");

            for (index = 0; index < mBuckets.length; ++index)
            {
                output.format("%s%n", mBuckets[index]);
            }

            retval = output.toString();
        }

        return (retval);
    } // end of generateResults()

    /**
     * Returns a textual representation of given duration.
     * @param d format this duration.
     * @return duration as text.
     */
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

    /**
     * {@link LatencyTracker} builder class. Used to set
     * following values:
     * <ul>
     *   <li>
    delta deltaCount: tracker collects up to this many latency
    deltas.
  </li>
     *   <li>
     *     bucket interval size: a {@link Bucket bucket} tracks
     *     number of deltas within a nanosecond interval.
     *   </li>
     *   <li>
     *     maximum bucket: latency deltas &ge; to this nanosecond
     *     value are stored in an overflow bucket.
     *   </li>
     * </ul>
     * After setting the above values, {@code LatencyTracker}
     * instance is created via {@link #build()}.
     */
    public static final class Builder
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Maximum number of beginTime deltas collected by latency
tracker.
         */
        private int mDeltaCount;

        /**
         * Each bucket represents this many nanoseconds.
         */
        private long mBucketSize;

        /**
         * Deltas &ge; to this value are stored in one bucket.
         */
        private long mBucketMax;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates a new latency tracker builder instance. Sets
         * all data members to negative values to aid in unset
         * configurations.
         */
        private Builder()
        {
            // Set data members to negative numbers to detect
            // unconfigured settings.
            mDeltaCount = -1;
            mBucketSize = -1L;
            mBucketMax = -1L;
        } // end of Builder()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets total number of deltas collected by latency
         * tracker. Tracker ignores any deltas beyond this limit.
         * Value must be &gt; zero.
         * @param count track up to this many deltas.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code deltaCount} &le; zero.
         */
        public Builder deltaCount(final int count)
        {
            if (count <= 0)
            {
                throw (
                    new IllegalArgumentException(
                        INVALID_DELTA_COUNT));
            }

            mDeltaCount = count;

            return (this);
        } // end of deltaCount(int)

        /**
         * Sets bucket nanosecond size. Deltas that are &ge; to
         * bucket minimum value and &lt; bucket maximum value
result in incrementing bucket delta deltaCount.
         * @param bucketSize bucket nanosecond size.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code bucketSize} &le; zero.
         */
        public Builder bucketIntervalSize(final long bucketSize)
        {
            if (bucketSize <= 0L)
            {
                throw (
                    new IllegalArgumentException(
                        INVALID_BUCKET_SIZE));
            }

            mBucketSize = bucketSize;

            return (this);
        } // end of bucketIntervalSize(long)

        /**
         * A final bucket used to track number of deltas &ge;
         * given value. Think of this as an overflow bucket for
         * those deltas too big to fit into other buckets.
         * @param maximum maximum bucket nanosecond delta size.
         * @return {@code this Builder} instance.
         */
        public Builder bucketMaximum(final long maximum)
        {
            if (maximum <= 0L)
            {
                throw (
                    new IllegalArgumentException(
                        INVALID_BUCKET_MAX));
            }

            mBucketMax = maximum;

            return (this);
        } // end of bucketMaximum(long)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Returns a new latency tracker instance based on this
         * builder's settings.
         * @return new latency tracker instance.
         * @throws ValidationException
         * if this builder's settings are invalid.
         */
        public LatencyTracker build()
        {
            final Validator problems = new Validator();

            problems.requireTrue((mDeltaCount > 0),
                                 "deltaCount",
                                 Validator.NOT_SET)
                    .requireTrue((mBucketSize > 0L),
                                 "bucketIntervalSize",
                                 Validator.NOT_SET)
                    .requireTrue((mBucketMax > 0L),
                                 "bucketMaximum",
                                 Validator.NOT_SET)
                    // Ignore max bucket limit and bucket size
                    // comparison if neither is set.
                    .requireTrue((mBucketSize < 0 ||
                                  mBucketMax < 0 ||
                                  mBucketMax >= mBucketSize),
                                  "bucketMaximum",
                                  "< bucketSize")
                    .throwException(LatencyTracker.class);

            return (new LatencyTracker(this));
        } // end of build()
    } // end of class Builder

    /**
     * Tracks number of nanosecond latency deltas within a given
     * interval.
     */
    public static final class Bucket
    {

    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Inclusive bucket interval start beginTime.
         */
        private final long mBeginTime;

        /**
         *
         * Exclusive bucket interval end beginTime.
         */
        private final long mEndTime;

        /**
         * Number of nanosecond latency deltas within bucket
         * interval.
         */
        private int mDeltaCount;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Bucket(final long beginTime,
                       final long endTime)
        {
            mBeginTime = beginTime;
            mEndTime = endTime;
            mDeltaCount = 0;
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
                    mBeginTime,
                    mEndTime,
                    mDeltaCount));
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns bucket interval inclusive begin time.
         * @return bucket interval inclusive begin time.
         */
        public long beginTime()
        {
            return (mBeginTime);
        } // end of beginTime()

        /**
         * Returns bucket interval exclusive end time.
         * @return bucket interval exclusive end time.
         */
        public long endTime()
        {
            return (mEndTime);
        } // end of endTime()

        /**
         * Returns bucket interval's latency delta count.
         * @return latency delta count.
         */
        public int deltaCount()
        {
            return (mDeltaCount);
        } // end of deltaCount()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Increments delta deltaCount by one.
         */
        private void increment()
        {
            ++mDeltaCount;
        } // end of increment()

        //
        // end of Set Methods.
        //-------------------------------------------------------
    } // end of class Bucket
} // end of class LatencyTracker
