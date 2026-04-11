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

import java.time.Instant;
import net.sf.eBus.util.ValidationException;
import static org.assertj.core.api.Java6Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class LatencyTrackerTest
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

    //-----------------------------------------------------------
    // Locals.
    //

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void setUpClass()
    {
    }

    @AfterAll
    public static void tearDownClass()
    {
    }

    @BeforeEach
    public void setUp()
    {
    }

    @AfterEach
    public void tearDown()
    {
    }

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void builderZeroDeltaCount()
    {
        final int count = 0;
        final LatencyTracker.Builder builder =
            LatencyTracker.builder();

        try
        {
            builder.deltaCount(count);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .hasMessage(LatencyTracker.INVALID_DELTA_COUNT);
        }
    } // end of builderZeroDeltaCount()

    @Test
    public void builderZeroBucketInvervalSize()
    {
        final long size = 0L;
        final LatencyTracker.Builder builder =
            LatencyTracker.builder();

        try
        {
            builder.bucketIntervalSize(size);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .hasMessage(LatencyTracker.INVALID_BUCKET_SIZE);
        }
    } // end of builderZeroBucketInvervalSize()

    @Test
    public void builderZeroBucketMaximum()
    {
        final long max = 0L;
        final LatencyTracker.Builder builder =
            LatencyTracker.builder();

        try
        {
            builder.bucketMaximum(max);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .hasMessage(LatencyTracker.INVALID_BUCKET_MAX);
        }
    } // end of builderZeroBucketMaximum()

    @Test
    public void builderNotSet()
    {
        final String text =
            """
            org.efs.util.LatencyTracker failed to build due to the following problems:
            deltaCount: not set
            bucketIntervalSize: not set
            bucketMaximum: not set""";
        final LatencyTracker.Builder builder =
            LatencyTracker.builder();

        try
        {
            builder.build();
        }
        catch (ValidationException vex)
        {
            assertThat(vex)
                .hasMessage(text);
        }
    } // end of builderNotSet()

    @Test
    public void builderBucketMaxLessThanSize()
    {
        final int deltaCount = 1_000_000;
        final long size = 1_000L;
        final long max = 900L;
        final String text =
            """
            org.efs.util.LatencyTracker failed to build due to the following problems:
            bucketMaximum: < bucketSize""";
        final LatencyTracker.Builder builder =
            LatencyTracker.builder();

        try
        {
            builder.deltaCount(deltaCount)
                   .bucketIntervalSize(size)
                   .bucketMaximum(max)
                   .build();
        }
        catch (ValidationException vex)
        {
            assertThat(vex)
                .hasMessage(text);
        }
    } // end of builderBucketMaxLessThanSize()

    @Test
    public void builderSucess()
    {
        final int deltaCount = 1_000_000;
        final long size = 1_000L;
        final long max = 10_000L;
        final LatencyTracker.Builder builder =
            LatencyTracker.builder();
        final LatencyTracker tracker =
            builder.deltaCount(deltaCount)
                   .bucketIntervalSize(size)
                   .bucketMaximum(max)
                   .build();

        assertThat(tracker).isNotNull();
        assertThat(tracker.deltaCount()).isZero();
        assertThat(tracker.bucketIntervalSize()).isEqualTo(size);
        assertThat(tracker.maximumBucketTime()).isEqualTo(max);
        assertThat(tracker.minimumDelta())
            .isEqualTo(Long.MAX_VALUE);
        assertThat(tracker.maximumDelta()).isEqualTo(-1L);
        assertThat(tracker.averageDelta()).isZero();
    } // end of builderSucess()

    @Test
    public void trackerTest()
    {
        final Instant startTime = Instant.now();
        final Instant stopTime = startTime.plusSeconds(5L);
        final int deltaCount = 1_000_000;
        final long size = 1_000L;
        final long max = 10_000L;
        final long minDelta = 1L;
        final long maxDelta = 20_000L;
        final LatencyTracker.Builder builder =
            LatencyTracker.builder();
        final LatencyTracker tracker =
            builder.deltaCount(deltaCount)
                   .bucketIntervalSize(size)
                   .bucketMaximum(max)
                   .build();
        final String text =
            """
            Run time: 5.0

            Latency:
              minimum =           1 nanoseconds
               median =      10,001 nanseconds
              average =      10,000 nanoseconds
                  95% =      19,001 nanoseconds
                  99% =      19,801 nanoseconds
                99.9% =      19,981 nanoseconds
               99.99% =      19,999 nanoseconds
              maximum =      20,000 nanoseconds

            Time intervals:
            [         0,      1,000)  49,950 items.
            [     1,000,      2,000)  50,000 items.
            [     2,000,      3,000)  50,000 items.
            [     3,000,      4,000)  50,000 items.
            [     4,000,      5,000)  50,000 items.
            [     5,000,      6,000)  50,000 items.
            [     6,000,      7,000)  50,000 items.
            [     7,000,      8,000)  50,000 items.
            [     8,000,      9,000)  50,000 items.
            [     9,000,     10,000)  50,000 items.
            [    10,000,          0) 500,050 items.
            """;
        int i;
        long delta = minDelta;
        long deltaSum = 0L;
        long avgDelta;

        tracker.startTime(startTime);

        // Fill the tracker with latencies.
        for (i = 0; i < deltaCount; ++i, ++delta)
        {
            delta = (delta > maxDelta ?
                     minDelta :
                     delta);
            tracker.addDelta(delta);
            deltaSum += delta;
        }

        tracker.stopTime(stopTime);

        avgDelta = (deltaSum / deltaCount);

        assertThat(tracker.startTime()).isEqualTo(startTime);
        assertThat(tracker.stopTime()).isEqualTo(stopTime);
        assertThat(tracker.deltaCount()).isEqualTo(deltaCount);
        assertThat(tracker.minimumDelta()).isEqualTo(minDelta);
        assertThat(tracker.maximumDelta()).isEqualTo(maxDelta);
        assertThat(tracker.averageDelta()).isEqualTo(avgDelta);
        assertThat(tracker.toString()).isEqualTo(text);
    } // end of trackerTest;

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------
} // end of class LatencyTrackerTest