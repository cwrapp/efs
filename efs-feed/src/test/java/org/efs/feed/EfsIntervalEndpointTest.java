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
 * Tests for {@code EfsIntervalEndpoint} enums.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class EfsIntervalEndpointTest
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

    @Test
    public void clusivityTest()
    {
        final Clusivity exclusive = Clusivity.EXCLUSIVE;
        final Clusivity inclusive = Clusivity.INCLUSIVE;

        assertThat(inclusive.beginSymbol()).isEqualTo('[');
        assertThat(inclusive.endSymbol()).isEqualTo(']');

        assertThat(exclusive.beginSymbol()).isEqualTo('(');
        assertThat(exclusive.endSymbol()).isEqualTo(')');
    } // end of clusivityTest()

    @Test
    public void locationTest()
    {
        assertThat(IntervalLocation.PAST.isPast()).isTrue();
        assertThat(IntervalLocation.PAST.isFuture()).isFalse();
        assertThat(IntervalLocation.PAST.toString())
            .isEqualTo("past");

        assertThat(IntervalLocation.NOW.isPast()).isFalse();
        assertThat(IntervalLocation.NOW.isFuture()).isFalse();
        assertThat(IntervalLocation.NOW.toString())
            .isEqualTo("now");

        assertThat(IntervalLocation.FUTURE.isPast()).isFalse();
        assertThat(IntervalLocation.FUTURE.isFuture()).isTrue();
        assertThat(IntervalLocation.FUTURE.toString())
            .isEqualTo("future");
    } // end of locationTest()

    @Test
    public void compareEndpointsTest()
    {
        final EfsTimeEndpoint timeEp =
            (EfsTimeEndpoint.builder()).now(Clusivity.EXCLUSIVE)
                                       .build();
        final EfsIndexEndpoint indexEp =
            (EfsIndexEndpoint.builder()).now(Clusivity.INCLUSIVE)
                                        .build();

        assertThat(EfsIntervalEndpoint.compareEndpoints(timeEp,
                                                        indexEp))
            .isLessThan(0);
    } // end of compareEndpointsTest()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------
} // end of class EfsIntervalEndpointTest