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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.efs.event.EfsTopicKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@code EfsPublishStatus} builder and get methods.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsPublishStatusTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String TEST_TOPIC = "/foo/bar/baz";
    private static final EfsTopicKey<SampleEvent> TEST_TOPIC_KEY =
        EfsTopicKey.getKey(SampleEvent.class, TEST_TOPIC);

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    @DisplayName("Build null topic key")
    public void builderNullTopicKey()
    {
        final EfsTopicKey<SampleEvent> topicKey = null;
        final EfsPublishStatus.Builder<SampleEvent> builder =
            EfsPublishStatus.builder();

        assertThatThrownBy(() -> builder.topicKey(topicKey))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsEventBus.NULL_TOPIC_KEY);
    } // end of builderNullTopicKey()

    @Test
    @DisplayName("Build negative advertised publisher count")
    public void builderNegativeAdvertisePublishers()
    {
        final int adCount = -1;
        final EfsPublishStatus.Builder<SampleEvent> builder =
            EfsPublishStatus.builder();

        assertThatThrownBy(
            () -> builder.advertisedPublishers(adCount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsPublishStatus.INVALID_ADVERTISED_COUNT);
    } // end of builderNegativeAdvertisePublishers()

    @Test
    @DisplayName("Build negative active publisher count")
    public void builderNegativeActivePublishers()
    {
        final int actCount = -1;
        final EfsPublishStatus.Builder<SampleEvent> builder =
            EfsPublishStatus.builder();

        assertThatThrownBy(
            () -> builder.activePublishers(actCount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsPublishStatus.INVALID_ACTIVE_COUNT);
    } // end of builderNegativeActivePublishers()

    @Test
    @DisplayName("Builder success")
    public void builderSuccess()
    {
        final EfsTopicKey<SampleEvent> topicKey = TEST_TOPIC_KEY;
        final int advertisedPublishers = 2;
        final int activePublishers = 1;
        final EfsPublishStatus.Builder<SampleEvent> builder =
            EfsPublishStatus.builder();
        final EfsPublishStatus<SampleEvent> statusEvent =
            builder.topicKey(topicKey)
                   .advertisedPublishers(advertisedPublishers)
                   .activePublishers(activePublishers)
                   .build();
        final String expected =
            String.format(
                "[topic=%s, # publishers advertised=%,d, # publishers active=%,d]",
                topicKey,
                advertisedPublishers,
                activePublishers);

        assertThat(statusEvent).isNotNull();
        assertThat(statusEvent.topicKey()).isEqualTo(topicKey);
        assertThat(statusEvent.advertisedPublishers())
            .isEqualTo(advertisedPublishers);
        assertThat(statusEvent.activePublishers())
            .isEqualTo(activePublishers);
        assertThat(statusEvent.toString()).isEqualTo(expected);
    } // end of builderSuccess()

    @Test
    @DisplayName("Event equals null - false")
    public void equalsNull()
    {
        final EfsPublishStatus<SampleEvent> pse =
            createEvent(TEST_TOPIC_KEY, 2, 1);
        final Object o = null;

        assertThat(pse).isNotEqualTo(o);
    } // end of equalsNull()

    @Test
    @DisplayName("Event equals Boolean - false")
    public void equalsBoolean()
    {
        final EfsPublishStatus<SampleEvent> pse =
            createEvent(TEST_TOPIC_KEY, 2, 1);
        final Object o = Boolean.TRUE;

        assertThat(pse).isNotEqualTo(o);
    } // end of equalsBoolean()

    @Test
    @DisplayName("Event equals self - true")
    public void equalsSelf()
    {
        final EfsPublishStatus<SampleEvent> pse =
            createEvent(TEST_TOPIC_KEY, 2, 1);
        final Object o = pse;

        assertThat(pse).isEqualTo(o);
    } // end of equalsSelf()

    @Test
    @DisplayName("Event equals different topic key - false")
    public void equalsDifferentTopicKey()
    {
        final String topic = "/foo/bar/bat";
        final EfsTopicKey<SampleEvent> topicKey =
            EfsTopicKey.getKey(SampleEvent.class, topic);
        final int adCount = 2;
        final int actCount = 1;
        final EfsPublishStatus<SampleEvent> pse =
            createEvent(TEST_TOPIC_KEY, adCount, actCount);
        final Object o =
            createEvent(topicKey, adCount, actCount);

        assertThat(pse).isNotEqualTo(o);
    } // end of equalsDifferentTopicKey()

    @Test
    @DisplayName("Event equals different advertised count - false")
    public void equalsDifferentAdCount()
    {
        final EfsTopicKey<SampleEvent> topicKey = TEST_TOPIC_KEY;
        final int adCount = 2;
        final int actCount = 1;
        final EfsPublishStatus<SampleEvent> pse =
            createEvent(topicKey, adCount, actCount);
        final Object o =
            createEvent(topicKey, (adCount + 1), actCount);

        assertThat(pse).isNotEqualTo(o);
        assertThat(pse.hashCode()).isNotEqualTo(o.hashCode());
    } // end of equalsDifferentAdCount()

    @Test
    @DisplayName("Event equals different active count - false")
    public void equalsDifferentActCount()
    {
        final EfsTopicKey<SampleEvent> topicKey = TEST_TOPIC_KEY;
        final int adCount = 2;
        final int actCount = 1;
        final EfsPublishStatus<SampleEvent> pse =
            createEvent(topicKey, adCount, actCount);
        final Object o =
            createEvent(topicKey, adCount, (actCount + 1));

        assertThat(pse).isNotEqualTo(o);
        assertThat(pse.hashCode()).isNotEqualTo(o.hashCode());
    } // end of equalsDifferentActCount()

    @Test
    @DisplayName("Event equals all values equal - true")
    public void equalsTrue()
    {
        final EfsTopicKey<SampleEvent> topicKey = TEST_TOPIC_KEY;
        final int adCount = 2;
        final int actCount = 1;
        final EfsPublishStatus<SampleEvent> pse =
            createEvent(topicKey, adCount, actCount);
        final Object o =
            createEvent(topicKey, adCount, actCount);

        assertThat(pse).isEqualTo(o);
        assertThat(pse.hashCode()).isEqualTo(o.hashCode());
    } // end of equalsTrue()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private static EfsPublishStatus<SampleEvent> createEvent(final EfsTopicKey<SampleEvent> key,
                                                             final int adCount,
                                                             final int actCount)
    {
        final EfsPublishStatus.Builder<SampleEvent> builder =
            EfsPublishStatus.builder();

        return (builder.topicKey(key)
                       .advertisedPublishers(adCount)
                       .activePublishers(actCount)
                       .build());
    } // end of createEvent(EfsTopicKey<>, int, int)
} // end of class EfsPublishStatusTest