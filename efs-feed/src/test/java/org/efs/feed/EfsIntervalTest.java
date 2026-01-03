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

package org.efs.feed;

import java.time.Instant;
import net.sf.eBus.util.ValidationException;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.feed.EfsIntervalEndpoint.Clusivity;
import org.junit.jupiter.api.BeforeEach;
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
        mCurrentTime = Instant.now();
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
                    org.efs.feed.EfsInterval failed to build due to the following problems:
                    beginning: not set
                    ending: not set""");
        }
    } // end of builderNotSet()

    @Test
    public void builderBeginningAfterEnding0()
    {
        final EfsIntervalEndpoint beginning =
            createEndpoint(10, Clusivity.INCLUSIVE);
        final EfsIntervalEndpoint ending =
            createEndpoint(-10, Clusivity.INCLUSIVE);
        final EfsInterval.Builder builder =
            EfsInterval.builder();

        try
        {
            builder.beginning(beginning)
                   .ending(ending)
                   .build();
        }
        catch (ValidationException vex)
        {
            assertThat(vex)
                .hasMessage(
                    """
                    org.efs.feed.EfsInterval failed to build due to the following problems:
                    beginning: beginning > ending""");
        }
    } // end of builderBeginningAfterEnding0()

    @Test
    public void builderBeginningAfterEnding1()
    {
        final EfsIntervalEndpoint beginning =
            createEndpoint(DELTA_SECONDS, Clusivity.INCLUSIVE);
        final EfsIntervalEndpoint ending =
            createEndpoint(-DELTA_SECONDS, Clusivity.INCLUSIVE);
        final EfsInterval.Builder builder =
            EfsInterval.builder();

        try
        {
            builder.beginning(beginning)
                   .ending(ending)
                   .build();
        }
        catch (ValidationException vex)
        {
            assertThat(vex)
                .hasMessage(
                    """
                    org.efs.feed.EfsInterval failed to build due to the following problems:
                    beginning: beginning > ending""");
        }
    } // end of builderBeginningAfterEnding1()

    @Test
    public void builderBeginningAfterEnding2()
    {
        final EfsIntervalEndpoint beginning =
            createEndpoint(0, Clusivity.INCLUSIVE);
        final EfsIntervalEndpoint ending =
            createEndpoint(0L, Clusivity.INCLUSIVE);
        final EfsInterval.Builder builder =
            EfsInterval.builder();

        try
        {
            builder.beginning(beginning)
                   .ending(ending)
                   .build();
        }
        catch (ValidationException vex)
        {
            assertThat(vex)
                .hasMessage(
                    """
                    org.efs.feed.EfsInterval failed to build due to the following problems:
                    beginning: beginning > ending""");
        }
    } // end of builderBeginningAfterEnding2()

    @Test
    public void builderSuccess()
    {
        final EfsIntervalEndpoint beginning =
            createEndpoint(-10, Clusivity.EXCLUSIVE);
        final EfsIntervalEndpoint ending =
            createEndpoint(DELTA_SECONDS, Clusivity.INCLUSIVE);
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
                createEndpoint(-DELTA_SECONDS, beginClusivity),
                createEndpoint(DELTA_SECONDS, endClusivity));
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
                createEndpoint(-DELTA_SECONDS, beginClusivity),
                createEndpoint(DELTA_SECONDS, endClusivity));
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
                createEndpoint(-DELTA_SECONDS, beginClusivity),
                createEndpoint(DELTA_SECONDS, endClusivity));
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
                createEndpoint(-DELTA_SECONDS, beginClusivity),
                createEndpoint(DELTA_SECONDS, endClusivity));
        final Object o =
            createInterval(
                createEndpoint((DELTA_SECONDS * -2),
                               beginClusivity),
                createEndpoint(DELTA_SECONDS, endClusivity));

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
                createEndpoint(-DELTA_SECONDS, beginClusivity),
                createEndpoint(DELTA_SECONDS, endClusivity));
        final Object o =
            createInterval(
                createEndpoint(-DELTA_SECONDS, beginClusivity),
                createEndpoint(100, endClusivity));

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
                createEndpoint(-DELTA_SECONDS, beginClusivity),
                createEndpoint(DELTA_SECONDS, endClusivity));
        final Object o =
            createInterval(
                createEndpoint(-DELTA_SECONDS, beginClusivity),
                createEndpoint(DELTA_SECONDS, endClusivity));

        assertThat(interval).isEqualTo(o);
        assertThat(interval.hashCode()).isEqualTo(o.hashCode());
    } // end of equalsTrue()

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

    private EfsTimeEndpoint createEndpoint(final long delta,
                                           final Clusivity clusivity)
    {
        final Instant time = mCurrentTime.plusSeconds(delta);
        final EfsTimeEndpoint.Builder builder =
            EfsTimeEndpoint.builder();

        return (builder.time(time, clusivity).build());
    } // end of createEndpoint(long, Clusivity)

    private EfsIndexEndpoint createEndpoint(final int offset,
                                            final Clusivity clusivity)
    {
        final EfsIndexEndpoint.Builder builder =
            EfsIndexEndpoint.builder();

        return (builder.indexOffset(offset, clusivity).build());
    } // end of createEndpoint(int, Clusivity)
} // end of class EfsIntervalTest