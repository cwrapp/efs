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

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;
import org.efs.dispatcher.IEfsDispatcher;
import org.efs.dispatcher.PerformanceAgent;
import org.efs.dispatcher.PerformanceEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@code ReplyTo} construction. Reply-to dispatch is
 * tested in dispatcher performance test.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class ReplyToTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String AGENT_NAME = "test-agent";
    private static final int EVENT_COUNT = 1;
    private static final String DISPATCHER_NAME =
        "test-dispatcher";

    //-----------------------------------------------------------
    // Statics.
    //

    private static PerformanceAgent sAgent;
    private static Consumer<PerformanceEvent> sCallback;
    private static IEfsDispatcher sDispatcher;
    private static IEfsEvent sEvent;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void setUpClass()
    {
        final CountDownLatch signal =
            new CountDownLatch(EVENT_COUNT);

        sDispatcher = mock(IEfsDispatcher.class);
        when(sDispatcher.name()).thenReturn(DISPATCHER_NAME);
        when(sDispatcher.maxEvents()).thenReturn(8);
        when(sDispatcher.eventQueueCapacity()).thenReturn(64);

        // Add mocked dispatcher to EfsDispatcher dispatchers
        // map.
        EfsDispatcher.addDispatcher(
            DISPATCHER_NAME, sDispatcher);

        sAgent = new PerformanceAgent(AGENT_NAME,
                                      EVENT_COUNT,
                                      signal);
        sCallback = sAgent::onEvent;
        EfsDispatcher.register(sAgent, DISPATCHER_NAME);

        sEvent = mock(IEfsEvent.class);
    } // end of setUpClass()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void replyToCtorNullCallback()
    {
        final Consumer<PerformanceEvent> callback = null;
        final IEfsAgent agent = sAgent;

        try
        {
            new ReplyTo<>(callback, agent);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex).hasMessage("callback is null");
        }
    } // end of replyToCtorNullCallback()

    @Test
    public void replyToCtorNullAgent()
    {
        final Consumer<PerformanceEvent> callback = sCallback;
        final IEfsAgent agent = null;

        try
        {
            new ReplyTo<>(callback, agent);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex).hasMessage("agent is null");
        }
    } // end of replyToCtorNullAgent()

    @Test
    public void replyToCtorSuccess()
    {
        final Consumer<PerformanceEvent> callback = sCallback;
        final IEfsAgent agent = sAgent;
        final ReplyTo replyTo = new ReplyTo<>(callback, agent);

        assertThat(replyTo).isNotNull();

        // Have reply-to dispatch a reply. Verifying that it does
        // not throw an exception.
        replyTo.dispatch(sEvent);
    } // end of replyToCtorSuccess()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------
} // end of class ReplyToTest