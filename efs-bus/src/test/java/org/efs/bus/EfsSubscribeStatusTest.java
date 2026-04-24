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
 * Exercises {@code EfsSubscribeStatus} builder and get methods.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsSubscribeStatusTest
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
        final EfsSubscribeStatus.Builder<SampleEvent> builder =
            EfsSubscribeStatus.builder();

        assertThatThrownBy(() -> builder.topicKey(topicKey))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsEventBus.NULL_TOPIC_KEY);
    } // end of builderNullTopicKey()

    @Test
    @DisplayName("Build negative previous subscriber count")
    public void builderNegativePreviousSubscribers()
    {
        final int count = -1;
        final EfsSubscribeStatus.Builder<SampleEvent> builder =
            EfsSubscribeStatus.builder();

        assertThatThrownBy(
            () -> builder.previousSubscribers(count))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsSubscribeStatus.INVALID_SUBSCRIBERS_COUNT);
    } // end of builderNegativePreviousSubscribers()

    @Test
    @DisplayName("Build negative active subscriber count")
    public void builderNegativeActiveSubscribers()
    {
        final int count = -1;
        final EfsSubscribeStatus.Builder<SampleEvent> builder =
            EfsSubscribeStatus.builder();

        assertThatThrownBy(
            () -> builder.activeSubscribers(count))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsSubscribeStatus.INVALID_SUBSCRIBERS_COUNT);
    } // end of builderNegativeActiveSubscribers()

    @Test
    @DisplayName("Builder success")
    public void builderSuccess()
    {
        final EfsTopicKey<SampleEvent> topicKey = TEST_TOPIC_KEY;
        final int prevCount = 3;
        final int activeCount = 2;
        final EfsSubscribeStatus.Builder<SampleEvent> builder =
            EfsSubscribeStatus.builder();
        final EfsSubscribeStatus<SampleEvent> statusEvent =
            builder.topicKey(topicKey)
                   .previousSubscribers(prevCount)
                   .activeSubscribers(activeCount)
                   .build();
        final String expected =
            String.format(
                "[topic=%s, # subscribers previous=%,d, # subscribers active=%,d]",
                topicKey,
                prevCount,
                activeCount);

        assertThat(statusEvent).isNotNull();
        assertThat(statusEvent.topicKey()).isEqualTo(topicKey);
        assertThat(statusEvent.previousSubscribers())
            .isEqualTo(prevCount);
        assertThat(statusEvent.activeSubscribers())
            .isEqualTo(activeCount);
        assertThat(statusEvent.toString()).isEqualTo(expected);
    } // end of builderSuccess()

    @Test
    @DisplayName("Event equals null - false")
    public void equalsNull()
    {
        final EfsSubscribeStatus<SampleEvent> sse =
            createEvent(TEST_TOPIC_KEY, 2, 1);
        final Object o = null;

        assertThat(sse).isNotEqualTo(o);
    } // end of equalsNull()

    @Test
    @DisplayName("Event equals Boolean - false")
    public void equalsBoolean()
    {
        final EfsSubscribeStatus<SampleEvent> sse =
            createEvent(TEST_TOPIC_KEY, 2, 1);
        final Object o = Boolean.TRUE;

        assertThat(sse).isNotEqualTo(o);
    } // end of equalsBoolean()

    @Test
    @DisplayName("Event equals self - true")
    public void equalsSelf()
    {
        final EfsSubscribeStatus<SampleEvent> sse =
            createEvent(TEST_TOPIC_KEY, 2, 1);
        final Object o = sse;

        assertThat(sse).isEqualTo(o);
    } // end of equalsSelf()

    @Test
    @DisplayName("Event equals different topic key - false")
    public void equalsDifferentTopicKey()
    {
        final String topic = "/foo/bar/bat";
        final EfsTopicKey<SampleEvent> topicKey =
            EfsTopicKey.getKey(SampleEvent.class, topic);
        final int prevCount = 2;
        final int actCount = 1;
        final EfsSubscribeStatus<SampleEvent> sse =
            createEvent(TEST_TOPIC_KEY, prevCount, actCount);
        final Object o =
            createEvent(topicKey, prevCount, actCount);

        assertThat(sse).isNotEqualTo(o);
    } // end of equalsDifferentTopicKey()

    @Test
    @DisplayName("Event equals different advertised count - false")
    public void equalsDifferentAdCount()
    {
        final EfsTopicKey<SampleEvent> topicKey = TEST_TOPIC_KEY;
        final int prevCount = 2;
        final int actCount = 1;
        final EfsSubscribeStatus<SampleEvent> sse =
            createEvent(topicKey, prevCount, actCount);
        final Object o =
            createEvent(topicKey, (prevCount + 1), actCount);

        assertThat(sse).isNotEqualTo(o);
        assertThat(sse.hashCode()).isNotEqualTo(o.hashCode());
    } // end of equalsDifferentAdCount()

    @Test
    @DisplayName("Event equals different active count - false")
    public void equalsDifferentActCount()
    {
        final EfsTopicKey<SampleEvent> topicKey = TEST_TOPIC_KEY;
        final int prevCount = 2;
        final int actCount = 1;
        final EfsSubscribeStatus<SampleEvent> sse =
            createEvent(topicKey, prevCount, actCount);
        final Object o =
            createEvent(topicKey, prevCount, (actCount + 1));

        assertThat(sse).isNotEqualTo(o);
        assertThat(sse.hashCode()).isNotEqualTo(o.hashCode());
    } // end of equalsDifferentActCount()

    @Test
    @DisplayName("Event equals all values equal - true")
    public void equalsTrue()
    {
        final EfsTopicKey<SampleEvent> topicKey = TEST_TOPIC_KEY;
        final int prevCount = 2;
        final int actCount = 1;
        final EfsSubscribeStatus<SampleEvent> pse =
            createEvent(topicKey, prevCount, actCount);
        final Object o =
            createEvent(topicKey, prevCount, actCount);

        assertThat(pse).isEqualTo(o);
        assertThat(pse.hashCode()).isEqualTo(o.hashCode());
    } // end of equalsTrue()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private static EfsSubscribeStatus<SampleEvent> createEvent(final EfsTopicKey<SampleEvent> key,
                                                               final int prevCount,
                                                               final int actCount)
    {
        final EfsSubscribeStatus.Builder<SampleEvent> builder =
            EfsSubscribeStatus.builder();

        return (builder.topicKey(key)
                       .previousSubscribers(prevCount)
                       .activeSubscribers(actCount)
                       .build());
    } // end of createEvent(EfsTopicKey<>, int, int)
} // end of class EfsSubscribeStatusTest