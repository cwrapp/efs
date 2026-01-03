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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import org.efs.feed.EfsIntervalEndpoint.Clusivity;
import org.efs.feed.EfsIntervalEndpoint.IntervalLocation;
import org.junit.jupiter.api.Test;

/**
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class EfsIndexEndpointTest
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
    public void builderIndexNullClusivity()
    {
        final int offset = -10;
        final Clusivity clusivity = null;
        final EfsIndexEndpoint.Builder builder =
            EfsIndexEndpoint.builder();

        try
        {
            builder.indexOffset(offset, clusivity);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(
                    EfsIndexEndpoint.Builder.CLUSIVITY_NULL);
        }
    } // end of builderIndexNullClusivity()

    @Test
    public void builderIndexNegativeOffset()
    {
        final int offset = -10;
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsIndexEndpoint.Builder builder =
            EfsIndexEndpoint.builder();
        final EfsIndexEndpoint ep =
            builder.indexOffset(offset, clusivity).build();

        assertThat(ep).isNotNull();
        assertThat(ep.indexOffset()).isEqualTo(offset);
        assertThat(ep.clusivity()).isEqualTo(clusivity);
        assertThat(ep.location())
            .isEqualTo(IntervalLocation.PAST);
    } // end of builderIndexNegativeOffset()

    @Test
    public void builderIndexZeroOffset()
    {
        final int offset = 0;
        final Clusivity clusivity = Clusivity.EXCLUSIVE;
        final EfsIndexEndpoint.Builder builder =
            EfsIndexEndpoint.builder();
        final EfsIndexEndpoint ep =
            builder.indexOffset(offset, clusivity).build();

        assertThat(ep).isNotNull();
        assertThat(ep.indexOffset()).isEqualTo(offset);
        assertThat(ep.clusivity()).isEqualTo(clusivity);
        assertThat(ep.location())
            .isEqualTo(IntervalLocation.NOW);
    } // end of builderIndexZeroOffset()

    @Test
    public void builderIndexPositiveOffset()
    {
        final int offset = 10;
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsIndexEndpoint.Builder builder =
            EfsIndexEndpoint.builder();
        final EfsIndexEndpoint ep =
            builder.indexOffset(offset, clusivity).build();

        assertThat(ep).isNotNull();
        assertThat(ep.indexOffset()).isEqualTo(offset);
        assertThat(ep.clusivity()).isEqualTo(clusivity);
        assertThat(ep.location())
            .isEqualTo(IntervalLocation.FUTURE);
    } // end of builderIndexPositiveOffset()

    @Test
    public void builderNowNullClusivity()
    {
        final Clusivity clusivity = null;
        final EfsIndexEndpoint.Builder builder =
            EfsIndexEndpoint.builder();

        try
        {
            builder.now(clusivity);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(
                    EfsIndexEndpoint.Builder.CLUSIVITY_NULL);
        }
    } // end of builderNowNullClusivity()

    @Test
    public void builderNowSuccess()
    {
        final Clusivity clusivity = Clusivity.EXCLUSIVE;
        final EfsIndexEndpoint.Builder builder =
            EfsIndexEndpoint.builder();
        final EfsIndexEndpoint ep =
            builder.now(clusivity).build();

        assertThat(ep).isNotNull();
        assertThat(ep.indexOffset()).isEqualTo(0);
        assertThat(ep.clusivity()).isEqualTo(clusivity);
        assertThat(ep.location())
            .isEqualTo(IntervalLocation.NOW);
    } // end of builderNowSuccess()

    @Test
    public void builderEndNeverSuccess()
    {
        final EfsIndexEndpoint.Builder builder =
            EfsIndexEndpoint.builder();
        final EfsIndexEndpoint ep = builder.endNever().build();

        assertThat(ep).isNotNull();
        assertThat(ep.indexOffset())
            .isEqualTo(Integer.MAX_VALUE);
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
        final EfsIndexEndpoint ep =
            createEndpoint(-10, Clusivity.EXCLUSIVE);
        final Object o = null;

        assertThat(o).isNotEqualTo(ep);
    } // end of equalsNull()

    @Test
    public void equalsBoolean()
    {
        final EfsIndexEndpoint ep =
            createEndpoint(-10, Clusivity.EXCLUSIVE);
        final Object o = Boolean.TRUE;

        assertThat(o).isNotEqualTo(ep);
    } // end of equalsBoolean()

    @Test
    public void equalsSame()
    {
        final EfsIndexEndpoint ep =
            createEndpoint(-10, Clusivity.EXCLUSIVE);
        final Object o = ep;

        assertThat(o).isEqualTo(ep);
    } // end of equalsSame()

    @Test
    public void equalsDifferentClusivity()
    {
        final int offset = -10;
        final EfsIndexEndpoint ep =
            createEndpoint(offset, Clusivity.EXCLUSIVE);
        final Object o =
            createEndpoint(offset, Clusivity.INCLUSIVE);

        assertThat(o).isNotEqualTo(ep);
        assertThat(o.hashCode()).isNotEqualTo(ep.hashCode());
    } // end of equalsDifferentClusivity()

    @Test
    public void equalsDifferentOffset()
    {
        final EfsIndexEndpoint ep =
            createEndpoint(-10, Clusivity.EXCLUSIVE);
        final Object o =
            createEndpoint(10, Clusivity.INCLUSIVE);

        assertThat(o).isNotEqualTo(ep);
        assertThat(o.hashCode()).isNotEqualTo(ep.hashCode());
    } // end of equalsDifferentOffset()

    @Test
    public void equalsSameOffsetClusivity()
    {
        final int offset = -10;
        final Clusivity clusivity = Clusivity.EXCLUSIVE;
        final EfsIndexEndpoint ep =
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
        final EfsIndexEndpoint ep0 =
            createEndpoint(-10, Clusivity.EXCLUSIVE);
        final EfsIndexEndpoint ep1 =
            createEndpoint(10, Clusivity.INCLUSIVE);

        assertThat(ep0.compareTo(ep1)).isLessThan(0);
    } // end of compareLessThan()

    @Test
    public void compareEquals()
    {
        final EfsIndexEndpoint ep0 =
            createEndpoint(0, Clusivity.EXCLUSIVE);
        final EfsIndexEndpoint ep1 =
            createEndpoint(0, Clusivity.INCLUSIVE);

        assertThat(ep0.compareTo(ep1)).isEqualTo(0);
    } // end of compareEquals()

    @Test
    public void compareGreaterThan()
    {
        final EfsIndexEndpoint ep0 =
            createEndpoint(10, Clusivity.EXCLUSIVE);
        final EfsIndexEndpoint ep1 =
            createEndpoint(-10, Clusivity.INCLUSIVE);

        assertThat(ep0.compareTo(ep1)).isGreaterThan(0);
    } // end of compareGreaterThan()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private EfsIndexEndpoint createEndpoint(final int offset,
                                            final Clusivity clusivity)
    {
        final EfsIndexEndpoint.Builder builder =
            EfsIndexEndpoint.builder();

        return (builder.indexOffset(offset, clusivity).build());
    } // end of createEndpoint(int, Clusivity)
} // end of class EfsIndexEndpointTest