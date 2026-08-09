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

package org.efs.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.efs.io.EfsIntervalEndpoint.Clusivity;
import org.efs.io.EfsIntervalEndpoint.IntervalLocation;
import org.junit.jupiter.api.Test;

/**
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class EfsIndexFixedEndpointTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final long EVENT_ROW_COUNT = 50L;

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
    // JUnit Tests.
    //

    //
    // Builder tests.
    //

    @Test
    public void builderIndexNullClusivity()
    {
        final long index = 10L;
        final Clusivity clusivity = null;
        final EfsIndexFixedEndpoint.Builder builder =
            EfsIndexFixedEndpoint.builder(EVENT_ROW_COUNT);

        assertThatThrownBy(
            () -> builder.fixedIndex(index, clusivity))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(
                EfsIntervalEndpoint.EndpointBuilder.CLUSIVITY_NULL);
    } // end of builderIndexNullClusivity()

    @Test
    public void builderIndexNegativeIndex()
    {
        final long index = -1L;
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsIndexFixedEndpoint.Builder builder =
            EfsIndexFixedEndpoint.builder(EVENT_ROW_COUNT);

        assertThatThrownBy(
            () -> builder.fixedIndex(index, clusivity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                EfsIndexFixedEndpoint.INVALID_INDEX);
    } // end of builderIndexNegativeIndex()

    @Test
    public void builderIndexPast()
    {
        final long index = (EVENT_ROW_COUNT - 2L);
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsIndexFixedEndpoint.Builder builder =
            EfsIndexFixedEndpoint.builder(EVENT_ROW_COUNT);
        final EfsIndexFixedEndpoint ep =
            builder.fixedIndex(index, clusivity).build();

        assertThat(ep).isNotNull();
        assertThat(ep.fixedIndex()).isEqualTo(index);
        assertThat(ep.clusivity()).isEqualTo(clusivity);
        assertThat(ep.location())
            .isEqualTo(IntervalLocation.PAST);
    } // end of builderIndexPast()

    @Test
    public void builderIndexNow()
    {
        final long index = (EVENT_ROW_COUNT - 1L);
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsIndexFixedEndpoint.Builder builder =
            EfsIndexFixedEndpoint.builder(EVENT_ROW_COUNT);
        final EfsIndexFixedEndpoint ep =
            builder.fixedIndex(index, clusivity).build();

        assertThat(ep).isNotNull();
        assertThat(ep.fixedIndex()).isEqualTo(index);
        assertThat(ep.clusivity()).isEqualTo(clusivity);
        assertThat(ep.location())
            .isEqualTo(IntervalLocation.NOW);
    } // end of builderIndexNow()

    @Test
    public void builderIndexFuture()
    {
        final long index = EVENT_ROW_COUNT;
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsIndexFixedEndpoint.Builder builder =
            EfsIndexFixedEndpoint.builder(EVENT_ROW_COUNT);
        final EfsIndexFixedEndpoint ep =
            builder.fixedIndex(index, clusivity).build();

        assertThat(ep).isNotNull();
        assertThat(ep.fixedIndex()).isEqualTo(index);
        assertThat(ep.clusivity()).isEqualTo(clusivity);
        assertThat(ep.location())
            .isEqualTo(IntervalLocation.FUTURE);
    } // end of builderIndexFuture()

    @Test
    public void builderNowNullClusivity()
    {
        final Clusivity clusivity = null;
        final EfsIndexFixedEndpoint.Builder builder =
            EfsIndexFixedEndpoint.builder(EVENT_ROW_COUNT);

        assertThatThrownBy(() -> builder.now(clusivity))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(
                EfsIntervalEndpoint.EndpointBuilder.CLUSIVITY_NULL);
    } // end of builderNowNullClusivity()

    @Test
    public void builderNowSuccess()
    {
        final Clusivity clusivity = Clusivity.EXCLUSIVE;
        final EfsIndexFixedEndpoint.Builder builder =
            EfsIndexFixedEndpoint.builder(EVENT_ROW_COUNT);
        final EfsIndexFixedEndpoint ep =
            builder.now(clusivity).build();

        assertThat(ep).isNotNull();
        assertThat(ep.fixedIndex())
            .isEqualTo(EVENT_ROW_COUNT - 1);
        assertThat(ep.clusivity()).isEqualTo(clusivity);
        assertThat(ep.location())
            .isEqualTo(IntervalLocation.NOW);
    } // end of builderNowSuccess()

    @Test
    public void builderEndNeverSuccess()
    {
        final EfsIndexFixedEndpoint.Builder builder =
            EfsIndexFixedEndpoint.builder(EVENT_ROW_COUNT);
        final EfsIndexFixedEndpoint ep =
            builder.endNever().build();

        assertThat(ep).isNotNull();
        assertThat(ep.fixedIndex()).isEqualTo(Long.MAX_VALUE);
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
        final long index = 20L;
        final EfsIndexFixedEndpoint ep =
            createEndpoint(index, Clusivity.EXCLUSIVE);
        final Object o = null;

        assertThat(o).isNotEqualTo(ep);
    } // end of equalsNull()

    @Test
    public void equalsBoolean()
    {
        final long index = 20L;
        final EfsIndexFixedEndpoint ep =
            createEndpoint(index, Clusivity.EXCLUSIVE);
        final Object o = Boolean.TRUE;

        assertThat(o).isNotEqualTo(ep);
    } // end of equalsBoolean()

    @Test
    public void equalsSame()
    {
        final long index = 20L;
        final EfsIndexFixedEndpoint ep =
            createEndpoint(index, Clusivity.EXCLUSIVE);
        final Object o = ep;

        assertThat(o).isEqualTo(ep);
    } // end of equalsSame()

    @Test
    public void equalsDifferentClusivity()
    {
        final long index = 20L;
        final EfsIndexFixedEndpoint ep =
            createEndpoint(index, Clusivity.EXCLUSIVE);
        final Object o =
            createEndpoint(index, Clusivity.INCLUSIVE);

        assertThat(o).isNotEqualTo(ep);
    } // end of equalsDifferentClusivity()

    @Test
    public void equalsDifferentIndex()
    {
        final long index = 20L;
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsIndexFixedEndpoint ep =
            createEndpoint(index, clusivity);
        final Object o =
            createEndpoint((index + 1), clusivity);

        assertThat(o).isNotEqualTo(ep);
        assertThat(o.hashCode()).isNotEqualTo(ep.hashCode());
    } // end of equalsDifferentIndex()

    @Test
    public void equalsSameIndexClusivity()
    {
        final long index = 20L;
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final EfsIndexFixedEndpoint ep =
            createEndpoint(index, clusivity);
        final Object o =
            createEndpoint(index, clusivity);
        final String text = String.format("%,d", index);

        assertThat(o).isEqualTo(ep);
        assertThat(o.hashCode()).isEqualTo(ep.hashCode());
        assertThat(o.toString()).isEqualTo(text);
    } // end of equalsSameIndexClusivity()

    //
    // CompareTo tests.
    //

    @Test
    public void compareLessThan()
    {
        final long index = 10L;
        final EfsIndexFixedEndpoint ep0 =
            createEndpoint(index, Clusivity.EXCLUSIVE);
        final EfsIndexFixedEndpoint ep1 =
             createEndpoint((index + 1L), Clusivity.EXCLUSIVE);

        assertThat(ep0.compareTo(ep1)).isLessThan(0);
    } // end of compareLessThan()

    @Test
    public void compareEquals()
    {
        final long index = 10L;
        final EfsIndexFixedEndpoint ep0 =
            createEndpoint(index, Clusivity.EXCLUSIVE);
        final EfsIndexFixedEndpoint ep1 =
             createEndpoint(index, Clusivity.INCLUSIVE);

        assertThat(ep0.compareTo(ep1)).isZero();
    } // end of compareEquals()

    @Test
    public void compareGreaterThan()
    {
        final long index = 10L;
        final EfsIndexFixedEndpoint ep0 =
            createEndpoint(index, Clusivity.EXCLUSIVE);
        final EfsIndexFixedEndpoint ep1 =
             createEndpoint((index - 1L), Clusivity.EXCLUSIVE);

        assertThat(ep0.compareTo(ep1)).isGreaterThan(0);
    } // end of compareGreaterThan()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private EfsIndexFixedEndpoint createEndpoint(final long index,
                                                 final Clusivity clusivity)
    {
        final EfsIndexFixedEndpoint.Builder builder =
            EfsIndexFixedEndpoint.builder(EVENT_ROW_COUNT);

        return (builder.fixedIndex(index, clusivity).build());
    } // end of createEndpoint(long, Clusivity)
} // end of class EfsIndexFixedEndpointTest