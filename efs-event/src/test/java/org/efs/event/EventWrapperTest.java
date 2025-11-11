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

package org.efs.event;

import java.time.Instant;
import net.sf.eBus.util.ValidationException;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link EfsEventWrapper} class.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class EventWrapperTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    private EfsEventWrapper.Builder<TestEvent> mBuilder;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeEach
    public void testSetup()
    {
        mBuilder = EfsEventWrapper.builder();
    } // end of testSetup()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void buildNullPublishTimestamp()
    {
        final Instant timestamp = null;

        try
        {
            mBuilder.publishTimestamp(timestamp);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex).hasMessage("timestamp is null");
        }
    } // end of buildNullPublishTimestamp()

    @Test
    public void buildNegativeEventId()
    {
        final int eventId = -1;

        try
        {
            mBuilder.eventId(eventId);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage("id < zero");
        }
    } // end of buildNegativeEventId()

    @Test
    public void buildZeroPublisherId()
    {
        final int pubId = 0;

        try
        {
            mBuilder.publisherId(pubId);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage("id is zero");
        }
    } // end of buildZeroPublisherId()

    @Test
    public void buildNegativeSubscriberCount()
    {
        final int count = -1;

        try
        {
            mBuilder.subscriberCount(count);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage("count < zero");
        }
    } // end of buildNegativeSubscriberCount()

    @Test
    public void buildNullEvent()
    {
        final TestEvent event = null;

        try
        {
            mBuilder.event(event);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex).hasMessage("event is null");
        }
    } // end of buildNullEvent()

    @Test
    public void invalidBuild()
    {
        final String message =
            """
            org.efs.event.EfsEventWrapper failed to build due to the following problems:
            eventId: not set
            publishTimestamp: not set
            publisherId: not set
            subscriberCount: not set
            event: not set""";

        try
        {
            mBuilder.build();
        }
        catch (ValidationException vex)
        {
            assertThat(vex).hasMessage(message);
        }
    } // end of invalidBuild()

    @Test
    public void successfulEventWrapperBuild()
    {
        final Instant pubTime = Instant.now();
        final int eventId = 2_001;
        final long pubId = 0x00102030L;
        final int subCount = 1;
        final TestEvent event =
            new TestEvent(101, System.nanoTime());
        final String text =
            "EfsEventWrapper{publishTimestamp=" + pubTime +
            ", eventId=" + eventId +
            ", publisherId=" + pubId +
            ", subscriberCount=" + subCount +
            ", event=" + event + "}";
        final EfsEventWrapper<TestEvent> wrapper =
            mBuilder.publishTimestamp(pubTime)
                    .eventId(eventId)
                    .publisherId(pubId)
                    .subscriberCount(subCount)
                    .event(event)
                    .build();

        assertThat(wrapper).isNotNull();
        assertThat(wrapper.publishTimestamp()).isEqualTo(pubTime);
        assertThat(wrapper.eventId()).isEqualTo(eventId);
        assertThat(wrapper.publisherId()).isEqualTo(pubId);
        assertThat(wrapper.subscriberCount()).isEqualTo(subCount);
        assertThat(wrapper.event()).isSameAs(event);
        assertThat(wrapper.toString()).isEqualTo(text);
    } // end of successfulEventWrapperBuild()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------
} // end of class EventWrapperTest