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

package org.efs.bus;

import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.dispatcher.IEfsAgent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@code EfsEnvelope} constructor, accessors, ordering,
 * and object methods.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsEnvelopeTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String BUS_NAME = "test-bus";
    private static final Instant PUB_TIMESTAMP =
        Instant.parse("2026-08-05T12:00:00Z");
    private static final long LOGICAL_TIMESTAMP = 42L;
    private static final TestAgent PUBLISHER =
        new TestAgent("publisher-a");
    private static final SampleEvent EVENT =
        new SampleEvent("payload", 7, PUB_TIMESTAMP);

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    @DisplayName("Envelope accessors and toString")
    public void accessorsAndToString()
    {
        final EfsEnvelope<SampleEvent> envelope =
            createEnvelope();
        final String expected =
            "[bus: test-bus, pub timestamp: 2026-08-05T12:00:00Z, publisher ID: publisher-a, logical timestamp: 42, event: [text=\"payload\", number=7, timestamp=2026-08-05T12:00:00Z]";

        assertThat(envelope).isNotNull();
        assertThat(envelope.busName()).isEqualTo(BUS_NAME);
        assertThat(envelope.publishTimestamp())
            .isEqualTo(PUB_TIMESTAMP);
        assertThat(envelope.publisher()).isEqualTo(PUBLISHER);
        assertThat(envelope.logicalTimestamp())
            .isEqualTo(LOGICAL_TIMESTAMP);
        assertThat(envelope.event()).isEqualTo(EVENT);
        assertThat(envelope.toString()).isEqualTo(expected);
    } // end of accessorsAndToString()

    @Test
    @DisplayName("CompareTo different bus name")
    public void compareToDifferentBusName()
    {
        final EfsEnvelope<SampleEvent> envelope = createEnvelope();
        final EfsEnvelope<SampleEvent> other =
            createEnvelope("other-bus",
                           PUB_TIMESTAMP,
                           PUBLISHER,
                           LOGICAL_TIMESTAMP,
                           EVENT);

        assertThat(envelope.compareTo(other)).isPositive();
        assertThat(other.compareTo(envelope)).isNegative();
    } // end of compareToDifferentBusName()

    @Test
    @DisplayName("CompareTo different logical timestamp")
    public void compareToDifferentLogicalTimestamp()
    {
        final EfsEnvelope<SampleEvent> envelope = createEnvelope();
        final EfsEnvelope<SampleEvent> other =
            createEnvelope(BUS_NAME,
                           PUB_TIMESTAMP,
                           PUBLISHER,
                           (LOGICAL_TIMESTAMP + 1),
                           EVENT);

        assertThat(envelope.compareTo(other)).isNegative();
        assertThat(other.compareTo(envelope)).isPositive();
    } // end of compareToDifferentLogicalTimestamp()

    @Test
    @DisplayName("CompareTo different publish timestamp")
    public void compareToDifferentPublishTimestamp()
    {
        final EfsEnvelope<SampleEvent> envelope = createEnvelope();
        final Instant timestamp = PUB_TIMESTAMP.plusSeconds(1);
        final EfsEnvelope<SampleEvent> other =
            createEnvelope(BUS_NAME,
                           timestamp,
                           PUBLISHER,
                           LOGICAL_TIMESTAMP,
                           EVENT);

        assertThat(envelope.compareTo(other)).isNegative();
        assertThat(other.compareTo(envelope)).isPositive();
    } // end of compareToDifferentPublishTimestamp()

    @Test
    @DisplayName("CompareTo same values")
    public void compareToSameValues()
    {
        final EfsEnvelope<SampleEvent> envelope =
            createEnvelope();
        final EfsEnvelope<SampleEvent> other = createEnvelope();

        assertThat(envelope.compareTo(other)).isZero();
        assertThat(other.compareTo(envelope)).isZero();
    } // end of compareToSameValues()

    @Test
    @DisplayName("Equals null false")
    public void equalsNullFalse()
    {
        final EfsEnvelope<SampleEvent> envelope =
            createEnvelope();
        final Object o = null;

        assertThat(envelope.equals(o)).isFalse();
    } // end of equalsNullFalse()

    @Test
    @DisplayName("Equals different type false")
    public void equalsDifferentTypeFalse()
    {
        final EfsEnvelope<SampleEvent> envelope =
            createEnvelope();
        final Object o = Boolean.TRUE;

        assertThat(envelope.equals(o)).isFalse();
    } // end of equalsDifferentTypeFalse()

    @Test
    @DisplayName("Equals self true")
    public void equalsSelfTrue()
    {
        final EfsEnvelope<SampleEvent> envelope =
            createEnvelope();

        assertThat(envelope.equals(envelope)).isTrue();
    } // end of equalsSelfTrue()

    @Test
    @DisplayName("Equals different values false")
    public void equalsDifferentValuesFalse()
    {
        final EfsEnvelope<SampleEvent> envelope =
            createEnvelope();
        final EfsEnvelope<SampleEvent> other =
            createEnvelope(BUS_NAME,
                           PUB_TIMESTAMP,
                           PUBLISHER,
                           (LOGICAL_TIMESTAMP + 1),
                           EVENT);

        assertThat(envelope.equals(other)).isFalse();
    } // end of equalsDifferentValuesFalse()

    @Test
    @DisplayName("Equals same logical data true")
    public void equalsSameLogicalDataTrue()
    {
        final EfsEnvelope<SampleEvent> envelope =
            createEnvelope();
        final EfsEnvelope<SampleEvent> other = createEnvelope();

        assertThat(envelope.equals(other)).isTrue();
        assertThat(envelope.hashCode())
            .isEqualTo(other.hashCode());
    } // end of equalsSameLogicalDataTrue()

    @Test
    @DisplayName("Equals same logical data with different event still true")
    public void equalsSameLogicalDataWithDifferentEventTrue()
    {
        final EfsEnvelope<SampleEvent> envelope =
            createEnvelope();
        final EfsEnvelope<SampleEvent> other =
            createEnvelope(BUS_NAME,
                           PUB_TIMESTAMP,
                           PUBLISHER,
                           LOGICAL_TIMESTAMP,
                           new SampleEvent(
                               "other", 9, PUB_TIMESTAMP));

        assertThat(envelope.equals(other)).isTrue();
    } // end of equalsSameLogicalDataWithDifferentEventTrue()

    //-----------------------------------------------------------
    // Helpers.
    //

    private static EfsEnvelope<SampleEvent> createEnvelope()
    {
        return (createEnvelope(BUS_NAME,
                               PUB_TIMESTAMP,
                               PUBLISHER,
                               LOGICAL_TIMESTAMP,
                               EVENT));
    } // end of createEnvelope()

    private static EfsEnvelope<SampleEvent> createEnvelope(final String busName,
                                                           final Instant pubTimestamp,
                                                           final IEfsAgent publisher,
                                                           final long logicalTimestamp,
                                                           final SampleEvent event)
    {
        return (new EfsEnvelope<>(busName,
                                   pubTimestamp,
                                   publisher,
                                   logicalTimestamp,
                                   event));
    } // end of createEnvelope(...)

//---------------------------------------------------------------
// Inner classes.
//

    private static final class TestAgent
        implements IEfsAgent
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private final String mName;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private TestAgent(final String name)
        {
            mName = name;
        } // end of TestAgent(String)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // IEfsAgent Interface Implementation.
        //

        @Override
        public String name()
        {
            return (mName);
        } // end of name()

        //
        // end of IEfsAgent Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        @Override
        public String toString()
        {
            return (mName);
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------
    } // end of TestAgent
} // end of class EfsEnvelopeTest
