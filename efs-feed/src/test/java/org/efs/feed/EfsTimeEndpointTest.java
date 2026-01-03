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
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import org.efs.feed.EfsIntervalEndpoint.Clusivity;
import org.efs.feed.EfsIntervalEndpoint.IntervalLocation;
import org.efs.feed.EfsIntervalEndpoint.IntervalSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class EfsTimeEndpointTest
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
    // Builder tests.
    //

    @Test
    public void builderTimeNullTime()
    {
        final Instant time = null;
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsTimeEndpoint.Builder builder =
            EfsTimeEndpoint.builder();

        try
        {
            builder.time(time, clusivity);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(EfsTimeEndpoint.Builder.TIME_NULL);
        }
    } // end of builderTimeNullTime()

    @Test
    public void builderTimeNullClusivity()
    {
        final Instant time = mCurrentTime;
        final Clusivity clusivity = null;
        final EfsTimeEndpoint.Builder builder =
            EfsTimeEndpoint.builder();

        try
        {
            builder.time(time, clusivity);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(
                    EfsTimeEndpoint.Builder.CLUSIVITY_NULL);
        }
    } // end of builderTimeNullClusivity()

    @Test
    public void builderTimePast()
    {
        final Instant time =
            mCurrentTime.minusSeconds(DELTA_SECONDS);
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsTimeEndpoint.Builder builder =
            EfsTimeEndpoint.builder();
        final EfsTimeEndpoint ep =
            builder.time(time, clusivity).build();

        assertThat(ep).isNotNull();
        assertThat(ep.time()).isEqualTo(time);
        assertThat(ep.clusivity()).isEqualTo(clusivity);
        assertThat(ep.location())
            .isEqualTo(IntervalLocation.PAST);
    } // end of builderTimePast()

    @Test
    public void builderTimeFuture()
    {
        final Instant time =
            mCurrentTime.plusSeconds(DELTA_SECONDS);
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsTimeEndpoint.Builder builder =
            EfsTimeEndpoint.builder();
        final EfsTimeEndpoint ep =
            builder.time(time, clusivity).build();

        assertThat(ep).isNotNull();
        assertThat(ep.time()).isEqualTo(time);
        assertThat(ep.clusivity()).isEqualTo(clusivity);
        assertThat(ep.location())
            .isEqualTo(IntervalLocation.FUTURE);
    } // end of builderTimeFuture()

    @Test
    public void builderNowNullClusivity()
    {
        final Clusivity clusivity = null;
        final EfsTimeEndpoint.Builder builder =
            EfsTimeEndpoint.builder();

        try
        {
            builder.now(clusivity);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(
                    EfsTimeEndpoint.Builder.CLUSIVITY_NULL);
        }
    } // end of builderNowNullClusivity()

    @Test
    public void builderNowSuccess()
    {
        final Clusivity clusivity = Clusivity.EXCLUSIVE;
        final EfsTimeEndpoint.Builder builder =
            EfsTimeEndpoint.builder();
        final EfsTimeEndpoint ep =
            builder.now(clusivity).build();
        final String text =
            String.format(
                "%c%s",
                clusivity.beginSymbol(),
                (ep.location()).name());

        assertThat(ep).isNotNull();
        assertThat(ep.time()).isAfter(mCurrentTime);
        assertThat(ep.clusivity()).isEqualTo(clusivity);
        assertThat(ep.location())
            .isEqualTo(IntervalLocation.NOW);
        assertThat(ep.toString(IntervalSide.BEGINNING))
            .isEqualTo(text);
    } // end of builderNowSuccess()

    @Test
    public void builderEndNeverSuccess()
    {
        final EfsTimeEndpoint.Builder builder =
            EfsTimeEndpoint.builder();
        final EfsTimeEndpoint ep =
            builder.endNever().build();

        assertThat(ep).isNotNull();
        assertThat(ep.time()).isAfter(mCurrentTime);
        assertThat(ep.clusivity())
            .isEqualTo(Clusivity.EXCLUSIVE);
        assertThat(ep.location())
            .isEqualTo(IntervalLocation.FUTURE);
    } // end of builderEndNeverSuccess()

    //
    // Equals tests.
    //

    @Test
    public void equalsNull()
    {
        final EfsTimeEndpoint ep =
            createEndpoint((DELTA_SECONDS * -1),
                           Clusivity.EXCLUSIVE);
        final Object o = null;

        assertThat(o).isNotEqualTo(ep);
    } // end of equalsNull()

    @Test
    public void equalsBoolean()
    {
        final EfsTimeEndpoint ep =
            createEndpoint((DELTA_SECONDS * -1),
                           Clusivity.EXCLUSIVE);
        final Object o = Boolean.TRUE;

        assertThat(o).isNotEqualTo(ep);
    } // end of equalsBoolean()

    @Test
    public void equalsSame()
    {
        final EfsTimeEndpoint ep =
            createEndpoint((DELTA_SECONDS * -1),
                           Clusivity.EXCLUSIVE);
        final Object o = ep;

        assertThat(o).isEqualTo(ep);
    } // end of equalsSame()

    @Test
    public void equalsDifferentClusivity()
    {
        final long delta = (DELTA_SECONDS * -1);
        final EfsTimeEndpoint ep =
            createEndpoint(delta, Clusivity.EXCLUSIVE);
        final Object o =
            createEndpoint(delta, Clusivity.INCLUSIVE);

        assertThat(o).isNotEqualTo(ep);
        assertThat(o.hashCode()).isNotEqualTo(ep.hashCode());
    } // end of equalsDifferentClusivity()

    @Test
    public void equalsDifferentTime()
    {
        final EfsTimeEndpoint ep =
            createEndpoint((DELTA_SECONDS * -1),
                           Clusivity.EXCLUSIVE);
        final Object o =
            createEndpoint(DELTA_SECONDS, Clusivity.EXCLUSIVE);

        assertThat(o).isNotEqualTo(ep);
        assertThat(o.hashCode()).isNotEqualTo(ep.hashCode());
    } // end of equalsDifferentTime()

    @Test
    public void equalsSameTime()
    {
        final long delta = (DELTA_SECONDS * -1);
        final EfsTimeEndpoint ep =
            createEndpoint(delta, Clusivity.INCLUSIVE);
        final Object o =
            createEndpoint(delta, Clusivity.INCLUSIVE);

        assertThat(o).isEqualTo(ep);
        assertThat(o.hashCode()).isEqualTo(ep.hashCode());
    } // end of equalsSameTime()

    //
    // CompareTo tests.
    //

    @Test
    public void compareLessThan()
    {
        final EfsTimeEndpoint ep0 =
            createEndpoint((DELTA_SECONDS * -1),
                           Clusivity.INCLUSIVE);
        final EfsTimeEndpoint ep1 =
            createEndpoint(DELTA_SECONDS, Clusivity.INCLUSIVE);

        assertThat(ep0.compareTo(ep1)).isLessThan(0);
    } // end of compareLessThan()

    @Test
    public void compareEquals()
    {
        final EfsTimeEndpoint ep0 =
            createEndpoint(DELTA_SECONDS, Clusivity.INCLUSIVE);
        final EfsTimeEndpoint ep1 =
            createEndpoint(DELTA_SECONDS, Clusivity.EXCLUSIVE);

        assertThat(ep0.compareTo(ep1)).isEqualTo(0);
    } // end of compareEquals()

    @Test
    public void compareGreaterThan()
    {
        final EfsTimeEndpoint ep0 =
            createEndpoint(DELTA_SECONDS, Clusivity.INCLUSIVE);
        final EfsTimeEndpoint ep1 =
            createEndpoint((DELTA_SECONDS * -1),
                           Clusivity.EXCLUSIVE);

        assertThat(ep0.compareTo(ep1)).isGreaterThan(0);
    } // end of compareGreaterThan()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private EfsTimeEndpoint createEndpoint(final long delta,
                                           final Clusivity clusivity)
    {
        final Instant time = mCurrentTime.plusSeconds(delta);
        final EfsTimeEndpoint.Builder builder =
            EfsTimeEndpoint.builder();

        return (builder.time(time, clusivity).build());
    } // end of createEndpoint(long, Clusivity)
} // end of class EfsTimeEndpointTest