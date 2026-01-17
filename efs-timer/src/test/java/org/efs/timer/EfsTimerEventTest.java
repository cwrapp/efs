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

package org.efs.timer;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class EfsTimerEventTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Statics.
    //

    private static String sTimerName;
    private static Object sDatum;
    private static long sTimestamp;
    private static EfsTimerEvent sTimerEvent;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void setUpClass()
    {
        sTimerName = "test-timer";
        sDatum = Boolean.TRUE;
        sTimestamp = System.nanoTime();

        sTimerEvent =
            new EfsTimerEvent(sTimerName, sDatum, sTimestamp);
    } // end of setUpClass()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void timerEventNullTimerName()
    {
        final String timerName = null;
        final Object datum = null;
        final long timestamp = System.nanoTime();
        final EfsTimerEvent event =
            new EfsTimerEvent(timerName, datum, timestamp);
        final String text =
            String.format(
                "[timer name=(not set), dispatch timestamp=%,d]",
                timestamp);

        assertThat(event).isNotNull();
        assertThat(event.timerName()).isEqualTo(timerName);
        assertThat(event.datum()).isEqualTo(datum);
        assertThat(event.dispatchTimestamp())
            .isEqualTo(timestamp);
        assertThat(event.toString()).isEqualTo(text);
    } // end of timerEventConstructorNullTimerName()

    @Test
    public void timerEventNonNullTimerName()
    {
        final String timerName = "mytimer";
        final Object datum = null;
        final long timestamp = System.nanoTime();
        final EfsTimerEvent event =
            new EfsTimerEvent(timerName, datum, timestamp);
        final String text =
            String.format(
                "[timer name=%s, dispatch timestamp=%,d]",
                timerName,
                timestamp);

        assertThat(event).isNotNull();
        assertThat(event.timerName()).isEqualTo(timerName);
        assertThat(event.datum()).isEqualTo(datum);
        assertThat(event.dispatchTimestamp())
            .isEqualTo(timestamp);
        assertThat(event.toString()).isEqualTo(text);
    } // end of timerEventNonNullTimerName()

    @Test
    public void timerEventNonNullDatum()
    {
        final String timerName = "mytimer";
        final Object datum = sDatum;
        final long timestamp = System.nanoTime();
        final EfsTimerEvent event =
            new EfsTimerEvent(timerName, datum, timestamp);
        final String text =
            String.format(
                "[timer name=%s, dispatch timestamp=%,d]",
                timerName,
                timestamp);

        assertThat(event).isNotNull();
        assertThat(event.timerName()).isEqualTo(timerName);
        assertThat(event.datum()).isEqualTo(datum);
        assertThat(event.dispatchTimestamp())
            .isEqualTo(timestamp);
        assertThat(event.toString()).isEqualTo(text);
    } // end of timerEventNonNullDatum()

    @Test
    public void equalsNull()
    {
        final Object o = null;

        assertThat(sTimerEvent).isNotEqualTo(o);
    } // end of equalsNull()

    @Test
    public void equalsBoolean()
    {
        final Object o = Boolean.TRUE;

        assertThat(sTimerEvent).isNotEqualTo(o);
        assertThat(sTimerEvent.hashCode())
            .isNotEqualTo(o.hashCode());
    } // end of equalsBoolean()

    @Test
    public void equalsSelf()
    {
        final Object o = sTimerEvent;

        assertThat(sTimerEvent).isEqualTo(o);
    } // end of equalsSelf()

    @Test
    public void equalsDifferentName()
    {
        final String timerName = "foobar";
        final Object o =
            new EfsTimerEvent(timerName, sDatum, sTimestamp);

        assertThat(sTimerEvent).isNotEqualTo(o);
    } // end of equalsDifferentName()

    @Test
    public void equalsDifferentTimestamp()
    {
        final long timestamp = System.nanoTime();
        final Object o =
            new EfsTimerEvent(sTimerName, sDatum, timestamp);

        assertThat(sTimerEvent).isNotEqualTo(o);
    } // end of equalsDifferentTimestamp()

    @Test
    public void equalsSameNameAndTimestamp()
    {
        final Object o =
            new EfsTimerEvent(sTimerName, sDatum, sTimestamp);

        assertThat(sTimerEvent).isEqualTo(o);
        assertThat(sTimerEvent.hashCode())
            .isEqualTo(o.hashCode());
    } // end of equalsSameNameAndTimestamp()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------
} // end of class EfsTimerEventTest