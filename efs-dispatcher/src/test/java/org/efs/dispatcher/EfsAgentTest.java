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
package org.efs.dispatcher;

import java.util.function.Consumer;
import net.sf.eBus.util.ValidationException;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.dispatcher.EfsAgent.RunState;
import org.efs.event.IEfsEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests building client and dispatching events.
 *
 * @author charlesr
 */

@ExtendWith (MockitoExtension.class)
public class EfsAgentTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String EFS_AGENT_NAME = "Tom";
    private static final String DISPATCHER_NAME = "Dick";
    private static final String MULTI_THREAD_DISPATCHER_NAME =
        "Harry";
    private static final int TEST_MAX_EVENTS = 8;
    private static final int EVENT_QUEUE_CAPACITY = 128;

    //-----------------------------------------------------------
    // Statics.
    //

    private static IEfsAgent sEfsObject;
    private static Consumer<IEfsEvent> sCallback;
    private static IEfsEvent sEvent;
    private static IEfsDispatcher sDispatcher;
    private static IEfsDispatcher sMultiThreadDispatcher;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    @SuppressWarnings ("unchecked")
    public static void testClassSetup()
    {
        sEfsObject = mock(IEfsAgent.class);
        when(sEfsObject.name()).thenReturn(EFS_AGENT_NAME);

        sCallback = mock(Consumer.class);
        sEvent = mock(IEfsEvent.class);

        sDispatcher = mock(IEfsDispatcher.class);
        when(sDispatcher.name()).thenReturn(DISPATCHER_NAME);
        when(sDispatcher.threadCount()).thenReturn(1);

        sMultiThreadDispatcher = mock(IEfsDispatcher.class);
        when(sMultiThreadDispatcher.name())
            .thenReturn(MULTI_THREAD_DISPATCHER_NAME);
        when(sMultiThreadDispatcher.threadCount()).thenReturn(4);
    } // end of testClassSetup()

    //
    // end of JUnit Test Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Test Methods.
    //

    @Test
    public void builderNullTarget()
    {
        final IEfsAgent eobj = null;
        final EfsAgent.Builder builder = EfsAgent.builder();

        try
        {
            builder.agent(eobj);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(NullPointerException.class);
            assertThat(jex).hasMessage("target is null");
        }
    } // end of builderNullTarget()

    @Test
    public void builderNullDispatcher()
    {
        final IEfsDispatcher dispatcher = null;
        final EfsAgent.Builder builder = EfsAgent.builder();

        try
        {
            builder.dispatcher(dispatcher);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(NullPointerException.class);
            assertThat(jex).hasMessage("dispatcher is null");
        }
    } // end of builderNullDispatcher()

    @Test
    public void builderZeroMaxEvents()
    {
        final int maxEvents = 0;
        final EfsAgent.Builder builder = EfsAgent.builder();

        try
        {
            builder.maxEvents(maxEvents);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(jex).hasMessage("maxEvents <= zero");
        }
    } // end of builderZeroMaxEvents()

    @Test
    public void builderNegativeCapacity()
    {
        final int capacity = -1;
        final EfsAgent.Builder builder = EfsAgent.builder();

        try
        {
            builder.eventQueueCapacity(capacity);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(jex).hasMessage("capacity < zero");
        }
    } // end of builderNegativeCapacity()

    @Test
    public void builderInvalidSettings()
    {
        final EfsAgent.Builder builder = EfsAgent.builder();

        try
        {
            builder.build();
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(ValidationException.class);
            assertThat(jex)
                .hasMessageContainingAll(
                    "agent: not set",
                    "maxEvents: not set",
                    "dispatcher: not set",
                    "eventQueueCapacity: not set");
        }
    } // end of builderInvalidSettings()

    @Test
    public void builderSuccess()
    {
        final EfsAgent.Builder builder = EfsAgent.builder();
        final EfsAgent agent =
            builder.agent(sEfsObject)
                   .dispatcher(sDispatcher)
                   .maxEvents(TEST_MAX_EVENTS)
                   .eventQueueCapacity(EVENT_QUEUE_CAPACITY)
                   .build();

        assertThat(agent).isNotNull();
        assertThat(agent.agent()).isEqualTo(sEfsObject);
        assertThat(agent.agentName())
            .isEqualTo(EFS_AGENT_NAME);
        assertThat(agent.maxEvents()).isEqualTo(TEST_MAX_EVENTS);
        assertThat(agent.dispatcher()).isEqualTo(sDispatcher);
        assertThat(agent.runState()).isEqualTo(RunState.IDLE);
        assertThat(agent.getAndClearReadyTimestamp()).isZero();
        assertThat(agent.toString())
            .startsWith("[" + EFS_AGENT_NAME);

        try
        {
            agent.dispatch(null, sEvent);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(NullPointerException.class);
            assertThat(jex).hasMessage("callback is null");
        }

        try
        {
            agent.dispatch(sCallback, null);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(NullPointerException.class);
            assertThat(jex).hasMessage("event is null");
        }

        agent.dispatch(sCallback, sEvent);
        assertThat(agent.runState()).isEqualTo(RunState.READY);

        // Overload the event queue.
        for (int i = 0; i < EVENT_QUEUE_CAPACITY; ++i)
        {
            try
            {
                agent.dispatch(sCallback, sEvent);
            }
            catch (IllegalStateException statex)
            {
                assertThat(statex)
                    .hasMessageEndingWith("task will not be run");
            }
        }

        final EfsAgent.AgentStats agentInfo =
            agent.generateRunStats();

        assertThat(agentInfo).isNotNull();
        assertThat(agentInfo.getAgentName())
            .isEqualTo(EFS_AGENT_NAME);
        assertThat(agentInfo.getEventQueueSize())
            .isEqualTo(EVENT_QUEUE_CAPACITY);
        assertThat(agentInfo.getMinimumRunTime()).isZero();
        assertThat(agentInfo.getMaximumRunTime()).isZero();
        assertThat(agentInfo.getTotalRunTime()).isZero();
        assertThat(agentInfo.getRunCount()).isZero();
        assertThat(agentInfo.getDispatcherName())
            .isEqualTo(DISPATCHER_NAME);
        assertThat(agentInfo.getMaxEvents())
            .isEqualTo(TEST_MAX_EVENTS);
        assertThat(agentInfo.toString())
            .startsWith(EFS_AGENT_NAME);
    } // end of builderSuccess()

    @Test
    public void builderUnboundedQueueMultiThreadDispatcher()
    {
        final EfsAgent.Builder builder = EfsAgent.builder();
        final EfsAgent agent =
            builder.agent(sEfsObject)
                   .dispatcher(sMultiThreadDispatcher)
                   .maxEvents(TEST_MAX_EVENTS)
                   .eventQueueCapacity(0)
                   .build();

        assertThat(agent).isNotNull();
        assertThat(agent.agent()).isEqualTo(sEfsObject);
        assertThat(agent.agentName())
            .isEqualTo(EFS_AGENT_NAME);
        assertThat(agent.maxEvents()).isEqualTo(TEST_MAX_EVENTS);
        assertThat(agent.dispatcher())
            .isEqualTo(sMultiThreadDispatcher);
        assertThat(agent.runState()).isEqualTo(RunState.IDLE);
        assertThat(agent.getAndClearReadyTimestamp()).isZero();
    } // end of builderUnboundedQueueMultiThreadDispatcher()

    @Test
    public void builderUnboundedQueueSingleThreadDispatcher()
    {
        final EfsAgent.Builder builder = EfsAgent.builder();
        final EfsAgent agent =
            builder.agent(sEfsObject)
                   .dispatcher(sDispatcher)
                   .maxEvents(TEST_MAX_EVENTS)
                   .eventQueueCapacity(0)
                   .build();

        assertThat(agent).isNotNull();
        assertThat(agent.agent()).isEqualTo(sEfsObject);
        assertThat(agent.agentName())
            .isEqualTo(EFS_AGENT_NAME);
        assertThat(agent.maxEvents()).isEqualTo(TEST_MAX_EVENTS);
        assertThat(agent.dispatcher()).isEqualTo(sDispatcher);
        assertThat(agent.runState()).isEqualTo(RunState.IDLE);
        assertThat(agent.getAndClearReadyTimestamp()).isZero();
    } // end of builderUnboundedQueueSingleThreadDispatcher()

    //
    // end of JUnit Test Methods.
    //-----------------------------------------------------------
} // end of class EfsAgentTest
