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

package org.efs.io;

import java.time.Duration;
import java.time.Instant;
import net.sf.eBus.util.ValidationException;
import org.assertj.core.api.Assertions;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.io.EfsIntervalEndpoint.Clusivity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@code EfsInterval} class.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsIntervalTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Add subtract {@value} seconds from the current time to
     * create the interval.
     */
    private static final long DELTA_SECONDS = 5L;

    /**
     * Fixed test timestamp.
     */
    private static final String TEST_TIME =
        "2026-05-26T11:10:45.000Z";

    /**
     * Number of events in efs event file.
     */
    private static final int ROW_COUNT = 50;

    private static final String VALIDATION_MESSAGE =
        """
        org.efs.io.EfsInterval failed to build due to the following problems:
        beginning: beginning > ending""";

    //-----------------------------------------------------------
    // Locals.
    //

    private Instant mCurrentTime;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeEach
    public void setUp()
    {
        mCurrentTime = Instant.parse(TEST_TIME);
    } // end of setUp()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    //
    // Builder Tests
    //

    @Test
    public void builderNullBeginning()
    {
        final EfsIntervalEndpoint beginning = null;
        final EfsInterval.Builder builder =
            EfsInterval.builder();

        try
        {
            builder.beginning(beginning);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(
                    EfsInterval.Builder.BEGINNING_ENDPOINT_NULL);
        }
    } // end of builderNullBeginning()

    @Test
    public void builderNullEnding()
    {
        final EfsIntervalEndpoint ending = null;
        final EfsInterval.Builder builder =
            EfsInterval.builder();

        try
        {
            builder.ending(ending);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(
                    EfsInterval.Builder.ENDING_ENDPOINT_NULL);
        }
    } // end of builderNullEnding()

    @Test
    public void builderNotSet()
    {
        final EfsInterval.Builder builder =
            EfsInterval.builder();

        try
        {
            builder.build();
        }
        catch (ValidationException vex)
        {
            assertThat(vex)
                .hasMessage(
                    """
                    org.efs.io.EfsInterval failed to build due to the following problems:
                    beginning: not set
                    ending: not set""");
        }
    } // end of builderNotSet()

    @Test
    public void builderBeginningAfterEnding0()
    {
        final EfsIntervalEndpoint beginning =
            createOffsetEndpoint(10, Clusivity.INCLUSIVE);
        final EfsIntervalEndpoint ending =
            createOffsetEndpoint(-10, Clusivity.INCLUSIVE);
        final EfsInterval.Builder builder =
            EfsInterval.builder();

        Assertions.assertThatThrownBy(
            () -> builder.beginning(beginning)
                         .ending(ending)
                         .build())
            .isInstanceOf(ValidationException.class)
            .hasMessage(VALIDATION_MESSAGE);
    } // end of builderBeginningAfterEnding0()

    @Test
    public void builderBeginningAfterEnding1()
    {
        final EfsIntervalEndpoint beginning =
            createDeltaEndpoint(DELTA_SECONDS, Clusivity.INCLUSIVE);
        final EfsIntervalEndpoint ending =
            createDeltaEndpoint(-DELTA_SECONDS, Clusivity.INCLUSIVE);
        final EfsInterval.Builder builder =
            EfsInterval.builder();

        Assertions.assertThatThrownBy(
            () -> builder.beginning(beginning)
                         .ending(ending)
                         .build())
            .isInstanceOf(ValidationException.class)
            .hasMessage(VALIDATION_MESSAGE);
    } // end of builderBeginningAfterEnding1()

    @Test
    public void builderBeginningAfterEnding2()
    {
        final EfsIntervalEndpoint beginning =
            createDeltaEndpoint(1L, Clusivity.INCLUSIVE);
        final EfsIntervalEndpoint ending =
            createDeltaEndpoint(0L, Clusivity.INCLUSIVE);
        final EfsInterval.Builder builder =
            EfsInterval.builder();

        Assertions.assertThatThrownBy(
            () -> builder.beginning(beginning)
                         .ending(ending)
                         .build())
            .isInstanceOf(ValidationException.class)
            .hasMessage(VALIDATION_MESSAGE);
    } // end of builderBeginningAfterEnding2()

    @Test
    @DisplayName ("begin offset > end offset")
    public void builderBeginningAfterEnding3()
    {
        final Duration beginOffset = Duration.ofMinutes(-5L);
        final Duration endOffset = Duration.ofMinutes(-10L);
        final EfsIntervalEndpoint beginning =
            createEndpoint(beginOffset, Clusivity.EXCLUSIVE);
        final EfsIntervalEndpoint ending =
            createEndpoint(endOffset, Clusivity.INCLUSIVE);
        final EfsInterval.Builder builder =
            EfsInterval.builder();

        Assertions.assertThatThrownBy(
            () -> builder.beginning(beginning)
                         .ending(ending)
                         .build())
            .isInstanceOf(ValidationException.class)
            .hasMessage(VALIDATION_MESSAGE);
    } // end of builderBeginningAfterEnding3()

    @Test
    @DisplayName ("begin fixed index offset > end offset")
    public void builderBeginningAfterEnding4()
    {
        final long beginIndex = 37L;
        final long endIndex = 32L;
        final EfsIntervalEndpoint beginning =
            createFixedEndpoint(beginIndex, Clusivity.EXCLUSIVE);
        final EfsIntervalEndpoint ending =
            createFixedEndpoint(endIndex, Clusivity.INCLUSIVE);
        final EfsInterval.Builder builder =
            EfsInterval.builder();

        Assertions.assertThatThrownBy(
            () -> builder.beginning(beginning)
                         .ending(ending)
                         .build())
            .isInstanceOf(ValidationException.class)
            .hasMessage(VALIDATION_MESSAGE);
    } // end of builderBeginningAfterEnding4()

    @Test
    @DisplayName ("successful interval build")
    public void builderSuccess()
    {
        final EfsIntervalEndpoint beginning =
            createOffsetEndpoint(-10, Clusivity.EXCLUSIVE);
        final EfsIntervalEndpoint ending =
            createDeltaEndpoint(DELTA_SECONDS, Clusivity.INCLUSIVE);
        final EfsInterval.Builder builder =
            EfsInterval.builder();
        final EfsInterval interval =
            builder.beginning(beginning)
                   .ending(ending)
                   .build();
        final String text =
            "(" + beginning + ", " + ending + "]";

        assertThat(interval).isNotNull();
        assertThat(interval.beginning()).isEqualTo(beginning);
        assertThat(interval.ending()).isEqualTo(ending);
        assertThat(interval.toString()).isEqualTo(text);
    } // end of builderSuccess()

    //
    // equals tests.
    //

    @Test
    public void equalsNull()
    {
        final Clusivity beginClusivity = Clusivity.INCLUSIVE;
        final Clusivity endClusivity = Clusivity.EXCLUSIVE;
        final EfsInterval interval =
            createInterval(
                createDeltaEndpoint(-DELTA_SECONDS, beginClusivity),
                createDeltaEndpoint(DELTA_SECONDS, endClusivity));
        final Object o = null;

        assertThat(interval).isNotEqualTo(o);
    } // end of equalsNull()

    @Test
    public void equalsSelf()
    {
        final Clusivity beginClusivity = Clusivity.INCLUSIVE;
        final Clusivity endClusivity = Clusivity.EXCLUSIVE;
        final EfsInterval interval =
            createInterval(
                createDeltaEndpoint(-DELTA_SECONDS, beginClusivity),
                createDeltaEndpoint(DELTA_SECONDS, endClusivity));
        final Object o = interval;

        assertThat(interval).isEqualTo(o);
    } // end of equalsSelf()

    @Test
    public void equalsBoolean()
    {
        final Clusivity beginClusivity = Clusivity.INCLUSIVE;
        final Clusivity endClusivity = Clusivity.EXCLUSIVE;
        final EfsInterval interval =
            createInterval(
                createDeltaEndpoint(-DELTA_SECONDS, beginClusivity),
                createDeltaEndpoint(DELTA_SECONDS, endClusivity));
        final Object o = Boolean.TRUE;

        assertThat(interval).isNotEqualTo(o);
    } // end of equalsBoolean()

    @Test
    public void equalsDifferentBeginning()
    {
        final Clusivity beginClusivity = Clusivity.INCLUSIVE;
        final Clusivity endClusivity = Clusivity.EXCLUSIVE;
        final EfsInterval interval =
            createInterval(
                createDeltaEndpoint(-DELTA_SECONDS, beginClusivity),
                createDeltaEndpoint(DELTA_SECONDS, endClusivity));
        final Object o =
            createInterval(
                createDeltaEndpoint((DELTA_SECONDS * -2),
                               beginClusivity),
                createDeltaEndpoint(DELTA_SECONDS, endClusivity));

        assertThat(interval).isNotEqualTo(o);
        assertThat(interval.hashCode())
            .isNotEqualTo(o.hashCode());
    } // end of equalsDifferentBeginning()

    @Test
    public void equalsDifferentEnding()
    {
        final Clusivity beginClusivity = Clusivity.INCLUSIVE;
        final Clusivity endClusivity = Clusivity.EXCLUSIVE;
        final EfsInterval interval =
            createInterval(
                createDeltaEndpoint(-DELTA_SECONDS, beginClusivity),
                createDeltaEndpoint(DELTA_SECONDS, endClusivity));
        final Object o =
            createInterval(
                createDeltaEndpoint(-DELTA_SECONDS, beginClusivity),
                createOffsetEndpoint(100, endClusivity));

        assertThat(interval).isNotEqualTo(o);
        assertThat(interval.hashCode())
            .isNotEqualTo(o.hashCode());
    } // end of equalsDifferentEndTime()

    @Test
    public void equalsTrue()
    {
        final Clusivity beginClusivity = Clusivity.INCLUSIVE;
        final Clusivity endClusivity = Clusivity.EXCLUSIVE;
        final EfsInterval interval =
            createInterval(
                createDeltaEndpoint(-DELTA_SECONDS, beginClusivity),
                createDeltaEndpoint(DELTA_SECONDS, endClusivity));
        final Object o =
            createInterval(
                createDeltaEndpoint(-DELTA_SECONDS, beginClusivity),
                createDeltaEndpoint(DELTA_SECONDS, endClusivity));

        assertThat(interval).isEqualTo(o);
        assertThat(interval.hashCode()).isEqualTo(o.hashCode());
    } // end of equalsTrue()

    //
    // isFuture interval tests.
    //

    @Test
    public void pastTimeEndpoint()
    {
        final long nextIndex = 20L;
        final long delta = -30L;
        final EfsIntervalEndpoint ep =
            createDeltaEndpoint(delta, Clusivity.INCLUSIVE);

        assertThat(ep.isFuture(nextIndex, mCurrentTime))
            .isFalse();
    } // end of pastTimeEndpoint()

    @Test
    public void currentTimeEndpoint()
    {
        final long nextIndex = 20L;
        final long delta = 0L;
        final EfsIntervalEndpoint ep =
            createDeltaEndpoint(delta, Clusivity.INCLUSIVE);

        assertThat(ep.isFuture(nextIndex, mCurrentTime))
            .isTrue();
    } // end of currentTimeEndpoint()

    @Test
    public void futureTimeEndpoint()
    {
        final long nextIndex = 20L;
        final long delta = 30L;
        final EfsIntervalEndpoint ep =
            createDeltaEndpoint(delta, Clusivity.INCLUSIVE);

        assertThat(ep.isFuture(nextIndex, mCurrentTime))
            .isTrue();
    } // end of futureTimeEndpoint()

    @Test
    public void pastTimeOffsetEndpoint()
    {
        final long nextIndex = 20L;
        final Duration offset = Duration.ofSeconds(-30L);
        final EfsIntervalEndpoint ep =
            createEndpoint(offset, Clusivity.INCLUSIVE);

        assertThat(ep.isFuture(nextIndex, mCurrentTime))
            .isFalse();
    } // end of pastTimeOffsetEndpoint()

    @Test
    public void zeroTimeOffsetEndpoint()
    {
        final long nextIndex = 20L;
        final Duration offset = Duration.ZERO;
        final EfsIntervalEndpoint ep =
            createEndpoint(offset, Clusivity.INCLUSIVE);

        assertThat(ep.isFuture(nextIndex, mCurrentTime))
            .isTrue();
    } // end of zeroTimeOffsetEndpoint()

    @Test
    public void futureTimeOffsetEndpoint()
    {
        final long nextIndex = 20L;
        final Duration offset = Duration.ofSeconds(30L);
        final EfsIntervalEndpoint ep =
            createEndpoint(offset, Clusivity.INCLUSIVE);

        assertThat(ep.isFuture(nextIndex, mCurrentTime))
            .isTrue();
    } // end of futureTimeOffsetEndpoint()

    @Test
    public void pastIndexOffsetEndpoint()
    {
        final long rowCount = 20L;
        final int offset = -10;
        final EfsIntervalEndpoint ep =
            createOffsetEndpoint(offset, Clusivity.INCLUSIVE);

        assertThat(ep.isFuture(rowCount, mCurrentTime))
            .isFalse();
    } // end of pastIndexOffsetEndpoint()

    @Test
    public void zeroIndexOffsetEndpoint()
    {
        final long rowCount = 20L;
        final int offset = 0;
        final EfsIntervalEndpoint ep =
            createOffsetEndpoint(offset, Clusivity.INCLUSIVE);

        assertThat(ep.isFuture(rowCount, mCurrentTime))
            .isFalse();
    } // end of zeroIndexOffsetEndpoint()

    @Test
    public void futureIndexOffsetEndpoint()
    {
        final long rowCount = 20L;
        final int offset = 10;
        final EfsIntervalEndpoint ep =
            createOffsetEndpoint(offset, Clusivity.INCLUSIVE);

        assertThat(ep.isFuture(rowCount, mCurrentTime))
            .isTrue();
    } // end of futureIndexOffsetEndpoint()

    @Test
    public void pastIndexFixedEndpoint()
    {
        final long rowCount = 20L;
        final long index = 10L;
        final EfsIntervalEndpoint ep =
            createFixedEndpoint(index, Clusivity.INCLUSIVE);

        assertThat(ep.isFuture(rowCount, mCurrentTime))
            .isFalse();
    } // end of pastIndexFixedEndpoint()

    @Test
    public void zeroIndexFixedEndpoint()
    {
        final long rowCount = 20L;
        final long index = 19L;
        final EfsIntervalEndpoint ep =
            createFixedEndpoint(index, Clusivity.INCLUSIVE);

        assertThat(ep.isFuture(rowCount, mCurrentTime))
            .isFalse();
    } // end of zeroIndexFixedEndpoint()

    @Test
    public void futureIndexFixedEndpoint()
    {
        final long rowCount = 20L;
        final long index = 30L;
        final EfsIntervalEndpoint ep =
            createFixedEndpoint(index, Clusivity.INCLUSIVE);

        assertThat(ep.isFuture(rowCount, mCurrentTime))
            .isTrue();
    } // end of futureIndexFixedEndpoint()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private static EfsInterval createInterval(final EfsIntervalEndpoint beginning,
                                              final EfsIntervalEndpoint ending)
    {
        final EfsInterval.Builder builder =
            EfsInterval.builder();

        return (builder.beginning(beginning)
                       .ending(ending)
                       .build());
    } // end of createInterval(...)

    private EfsTimeEndpoint createDeltaEndpoint(final long delta,
                                                final Clusivity clusivity)
    {
        final Instant time = mCurrentTime.plusSeconds(delta);
        final EfsTimeEndpoint.Builder builder =
            EfsTimeEndpoint.builder(mCurrentTime);

        return (builder.time(time, clusivity).build());
    } // end of createDeltaEndpoint(long, Clusivity)

    private EfsDurationEndpoint createEndpoint(final Duration offset,
                                               final Clusivity clusivity)
    {
        final EfsDurationEndpoint.Builder builder =
            EfsDurationEndpoint.builder();

        return (builder.timeOffset(offset, clusivity)).build();
    } // end of createOffsetEndpoint(Duration, Clusivity)

    private EfsIndexFixedEndpoint createFixedEndpoint(final long index,
                                                      final Clusivity clusivity)
    {
        final EfsIndexFixedEndpoint.Builder builder =
            EfsIndexFixedEndpoint.builder(ROW_COUNT);

        return (builder.fixedIndex(index, clusivity).build());
    } // end of createFixedEndpoint(long, Clusivity)

    private EfsIndexOffsetEndpoint createOffsetEndpoint(final int offset,
                                                        final Clusivity clusivity)
    {
        final EfsIndexOffsetEndpoint.Builder builder =
            EfsIndexOffsetEndpoint.builder();

        return (builder.indexOffset(offset, clusivity).build());
    } // end of createOffsetEndpoint(int, Clusivity)
} // end of class EfsIntervalTest