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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import org.efs.feed.EfsIntervalEndpoint.Clusivity;
import org.efs.feed.EfsIntervalEndpoint.IntervalLocation;
import org.junit.jupiter.api.Test;

/**
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class EfsDurationEndpointTest
{
//---------------------------------------------------------------
// Member data.
//

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    //
    // Builder tests.
    //

    @Test
    public void builderDurationNullOffset()
    {
        final Duration offset = null;
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsDurationEndpoint.Builder builder =
            EfsDurationEndpoint.builder();

        try
        {
            builder.timeOffset(offset, clusivity);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(
                    EfsDurationEndpoint.Builder.TIME_OFFSET_NULL);
        }
    } // end of builderDurationNullOffset()

    @Test
    public void builderDurationNullClusvity()
    {
        final Duration offset = Duration.ofHours(3);
        final Clusivity clusivity = null;
        final EfsDurationEndpoint.Builder builder =
            EfsDurationEndpoint.builder();

        try
        {
            builder.timeOffset(offset, clusivity);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(
                    EfsDurationEndpoint.Builder.CLUSIVITY_NULL);
        }
    } // end of builderDurationNullClusvity()

    @Test
    public void builderDurationNegativeOffset()
    {
        final Duration offset = Duration.ofHours(-2L);
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsDurationEndpoint.Builder builder =
            EfsDurationEndpoint.builder();
        final EfsDurationEndpoint ep =
            builder.timeOffset(offset, clusivity).build();
        final String text = offset.toString();

        assertThat(ep).isNotNull();
        assertThat(ep.timeOffset()).isEqualTo(offset);
        assertThat(ep.clusivity()).isEqualTo(clusivity);
        assertThat(ep.location())
            .isEqualTo(IntervalLocation.PAST);
        assertThat(ep.toString()).isEqualTo(text);
    } // end of builderDurationNegativeOffset()

    @Test
    public void builderDurationZeroOffset()
    {
        final Duration offset = Duration.ZERO;
        final Clusivity clusivity = Clusivity.EXCLUSIVE;
        final EfsDurationEndpoint.Builder builder =
            EfsDurationEndpoint.builder();
        final EfsDurationEndpoint ep =
            builder.timeOffset(offset, clusivity).build();

        assertThat(ep).isNotNull();
        assertThat(ep.timeOffset()).isEqualTo(offset);
        assertThat(ep.clusivity()).isEqualTo(clusivity);
        assertThat(ep.location())
            .isEqualTo(IntervalLocation.NOW);
    } // end of builderDurationZeroOffset()

    @Test
    public void builderDurationPositiveOffset()
    {
        final Duration offset = Duration.ofHours(2L);
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsDurationEndpoint.Builder builder =
            EfsDurationEndpoint.builder();
        final EfsDurationEndpoint ep =
            builder.timeOffset(offset, clusivity).build();

        assertThat(ep).isNotNull();
        assertThat(ep.timeOffset()).isEqualTo(offset);
        assertThat(ep.clusivity()).isEqualTo(clusivity);
        assertThat(ep.location())
            .isEqualTo(IntervalLocation.FUTURE);
    } // end of builderDurationPositiveOffset()

    @Test
    public void builderNowNullClusivity()
    {
        final Clusivity clusivity = null;
        final EfsDurationEndpoint.Builder builder =
            EfsDurationEndpoint.builder();

        try
        {
            builder.now(clusivity);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(
                    EfsDurationEndpoint.Builder.CLUSIVITY_NULL);
        }
    } // end of builderNowNullClusivity()

    @Test
    public void builderNowSuccess()
    {
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsDurationEndpoint.Builder builder =
            EfsDurationEndpoint.builder();
        final EfsDurationEndpoint ep =
            builder.now(clusivity).build();

        assertThat(ep).isNotNull();
        assertThat(ep.timeOffset()).isEqualTo(Duration.ZERO);
        assertThat(ep.clusivity()).isEqualTo(clusivity);
        assertThat(ep.location())
            .isEqualTo(IntervalLocation.NOW);
    } // end of builderNowSuccess()

    @Test
    public void builderEndNeverSuccess()
    {
        final Duration offset = ChronoUnit.FOREVER.getDuration();
        final EfsDurationEndpoint.Builder builder =
            EfsDurationEndpoint.builder();
        final EfsDurationEndpoint ep =
            builder.endNever().build();

        assertThat(ep).isNotNull();
        assertThat(ep.timeOffset()).isEqualTo(offset);
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
        final EfsDurationEndpoint ep =
            createEndpoint(-5, Clusivity.EXCLUSIVE);
        final Object o = null;

        assertThat(o).isNotEqualTo(ep);
    } // end of equalsNull()

    @Test
    public void equalsBoolean()
    {
        final EfsDurationEndpoint ep =
            createEndpoint(-5, Clusivity.EXCLUSIVE);
        final Object o = Boolean.TRUE;

        assertThat(o).isNotEqualTo(ep);
    } // end of equalsBoolean()

    @Test
    public void equalsSame()
    {
        final EfsDurationEndpoint ep =
            createEndpoint(-5, Clusivity.EXCLUSIVE);
        final Object o = ep;

        assertThat(o).isEqualTo(ep);
    } // end of equalsSame()

    @Test
    public void equalsDifferentClusivity()
    {
        final long offset = -5L;
        final EfsDurationEndpoint ep =
            createEndpoint(offset, Clusivity.EXCLUSIVE);
        final Object o =
            createEndpoint(offset, Clusivity.INCLUSIVE);

        assertThat(o).isNotEqualTo(ep);
        assertThat(o.hashCode()).isNotEqualTo(ep.hashCode());
    } // end of equalsDifferentClusivity()

    @Test
    public void equalsDifferentOffset()
    {
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsDurationEndpoint ep =
            createEndpoint(2L, clusivity);
        final Object o =
            createEndpoint(3L, clusivity);

        assertThat(o).isNotEqualTo(ep);
        assertThat(o.hashCode()).isNotEqualTo(ep.hashCode());
    } // end of equalsDifferentOffset()

    @Test
    public void equalsSameOffsetClusivity()
    {
        final long offset = -5L;
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsDurationEndpoint ep =
            createEndpoint(offset, clusivity);
        final Object o =
            createEndpoint(offset, clusivity);

        assertThat(o).isEqualTo(ep);
        assertThat(o.hashCode()).isEqualTo(ep.hashCode());
    } // end of equalsSameOffsetClusivity()

    //
    // CompareTo tests.
    //

    @Test
    public void compareLessThan()
    {
        final EfsDurationEndpoint ep0 =
            createEndpoint(-5, Clusivity.EXCLUSIVE);
        final EfsDurationEndpoint ep1 =
            createEndpoint(5, Clusivity.INCLUSIVE);

        assertThat(ep0.compareTo(ep1)).isLessThan(0);
    } // end of compareLessThan()

    @Test
    public void compareEquals()
    {
        final EfsDurationEndpoint ep0 =
            createEndpoint(1, Clusivity.EXCLUSIVE);
        final EfsDurationEndpoint ep1 =
            createEndpoint(1, Clusivity.INCLUSIVE);

        assertThat(ep0.compareTo(ep1)).isZero();
    } // end of compareEquals()

    @Test
    public void compareGreaterThan()
    {
        final EfsDurationEndpoint ep0 =
            createEndpoint(5, Clusivity.EXCLUSIVE);
        final EfsDurationEndpoint ep1 =
            createEndpoint(-5, Clusivity.INCLUSIVE);

        assertThat(ep0.compareTo(ep1)).isGreaterThan(0);
    } // end of compareGreaterThan()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private EfsDurationEndpoint createEndpoint(final long offset,
                                               final Clusivity clusivity)
    {
        final Duration timeOffset = Duration.ofHours(offset);
        final EfsDurationEndpoint.Builder builder =
            EfsDurationEndpoint.builder();

        return (builder.timeOffset(timeOffset, clusivity)
                       .build());
    } // end of createEndpoint(long, Clusivity)
} // end of class EfsDurationEndpointTest