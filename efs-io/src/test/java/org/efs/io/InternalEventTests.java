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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.io.EfsFileConnection.Retrieval;
import org.efs.io.RetrievalCompleteEvent.CompletionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class InternalEventTests
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String EVENT_TEXT = "abc-123";
    private static final int EVENT_NUMBER = 12345;

    //-----------------------------------------------------------
    // Statics.
    //

    private static Instant sTimestamp;
    private static Retrieval<SampleEvent> sRetrieval;

    //-----------------------------------------------------------
    // Locals.
    //

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    @SuppressWarnings ("unchecked")
    public static void setUpClass()
    {
        final ZoneId tz = ZoneId.systemDefault();
        final ZonedDateTime zdt =
            ZonedDateTime.of(1776, 7, 4, 10, 24, 12, 0, tz);

        sTimestamp = zdt.toInstant();
        sRetrieval = Mockito.mock(Retrieval.class);
    } // end of setUpClass()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    @DisplayName ("AddIntervalEvent")
    @SuppressWarnings ("unchecked")
    public void addIntervalEventTest()
    {
        final SampleEvent event =
            new SampleEvent(
                EVENT_TEXT, EVENT_NUMBER, sTimestamp);
        final AddInternalEvent<SampleEvent> addEvent =
            new AddInternalEvent<>(
                sTimestamp, EfsFile.NO_TAGS, event);
        final String text =
            String.format("[timestamp=%s, tags=, event=%s]",
                          sTimestamp,
                          event);

        assertThat(addEvent.publishTimestamp())
            .isEqualTo(sTimestamp);
        assertThat(addEvent.event()).isSameAs(event);
        assertThat(addEvent.toString()).isEqualTo(text);
    } // end of addIntervalEventTest()

    @Test
    @DisplayName ("RetrieveCompletionEvent")
    @SuppressWarnings ("unchecked")
    public void retrieveCompletionEventTest()
    {
        final CompletionType type =
            CompletionType.RETRIEVAL_COMPLETED;
        final RetrievalCompleteEvent<SampleEvent> event =
            new RetrievalCompleteEvent<>(
                type, sTimestamp, sRetrieval);

        assertThat(event.completionType()).isEqualTo(type);
        assertThat(event.completionTime()).isEqualTo(sTimestamp);
        assertThat(event.retrieval()).isSameAs(sRetrieval);
    } // end of retrieveCompletionEventTest()

    @Test
    @DisplayName ("CancelInternalEvent")
    public void cancelInternalEventTest()
    {
        final CompletionType type =
            CompletionType.CONNECTION_CLOSED;
        final CancelInternalEvent<SampleEvent> event =
            new CancelInternalEvent<>(
                sTimestamp, type, sRetrieval);
        final String text =
            String.format(
                "[timestamp=%s, reason=%s, request=%s]",
                sTimestamp,
                type,
                sRetrieval);

        assertThat(event.cancelTimestamp())
            .isEqualTo(sTimestamp);
        assertThat(event.completionType()).isEqualTo(type);
        assertThat(event.request()).isSameAs(sRetrieval);
        assertThat(event.toString()).isEqualTo(text);
    } // end of cancelInternalEventTest()



    //
    // end of JUnit Tests.
    //-----------------------------------------------------------
} // end of class InternalEventTests