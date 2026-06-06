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

package org.efs.dispatcher;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.efs.dispatcher.config.ThreadType;
import org.efs.event.IEfsEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Dispatch target tests.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@ExtendWith(MockitoExtension.class)
@DisplayName("EfsDispatchTarget Unit Tests")
public class DispatchTargetTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String DISPATCHER_NAME =
        "test-dispatcher";
    private static final ThreadType THREAD_TYPE =
        ThreadType.BLOCKING;
    private static final int THREAD_COUNT = 2;
    private static final int THREAD_PRIORITY = 3;
    private static final int MAX_EVENTS = 4;

    private static final String TEST_AGENT_NAME = "test-agent";

    //-----------------------------------------------------------
    // Statics.
    //

    private static IEfsDispatcher sDispatcher;

    //-----------------------------------------------------------
    // Locals.
    //

    private IEfsAgent mTestAgent;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void testClassSetUp()
    {
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(DISPATCHER_NAME);

        builder.threadType(THREAD_TYPE)
               .numThreads(THREAD_COUNT)
               .priority(THREAD_PRIORITY)
               .dispatcherType(EfsDispatcher.DispatcherType.EFS)
               .eventQueueCapacity(8)
               .runQueueCapacity(1)
               .maxEvents(MAX_EVENTS)
               .build();
    } // end of testClassSetUp()

    @BeforeEach
    public void setUp()
    {
        mTestAgent = mock(IEfsAgent.class);
        when(mTestAgent.name()).thenReturn(TEST_AGENT_NAME);

        EfsDispatcher.register(mTestAgent, DISPATCHER_NAME);
    } // end of setUp()

    @AfterEach
    public void tearDown()
    {
        EfsDispatcher.deregister(mTestAgent);
    } // end of tearDown()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    @DisplayName("Target constructor, null callback")
    public void ctorNullCallback()
    {
        final Consumer<TestEvent> callback = null;
        final IEfsAgent agent = mTestAgent;

        assertThatThrownBy(
            () -> new EfsDispatchTarget<>(callback, agent))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsDispatcher.NULL_CALLBACK);
    } // end of ctorNullCallback()

    @Test
    @DisplayName("Target constructor, null agent")
    public void ctorNullAgent()
    {
        final Consumer<TestEvent> callback = e -> {};
        final IEfsAgent agent = null;

        assertThatThrownBy(
            () -> new EfsDispatchTarget<>(callback, agent))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsDispatcher.NULL_AGENT);
    } // end of ctorNullAgent()

    @Test
    @DisplayName("Target constructor, unregistered agent")
    public void ctorUnregisteredAgent()
    {
        final String agentName = "test-unregistered";
        final Consumer<TestEvent> callback = e -> {};
        final IEfsAgent agent = mock(IEfsAgent.class);
        final String message =
        String.format(EfsDispatcher.UNREGISTERED_AGENT,
                      agentName);

        when(agent.name()).thenReturn(agentName);

        assertThatThrownBy(
            () -> new EfsDispatchTarget<>(callback, agent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(message);
    } // end of ctorUnregisteredAgent()

    @Test
    @DisplayName("Target constructor success")
    public void ctorSuccess()
    {
        final Consumer<TestEvent> callback = e -> {};
        final IEfsAgent agent = mTestAgent;
        final EfsDispatchTarget<TestEvent> target =
            new EfsDispatchTarget<>(callback, agent);

        assertThat(target).isNotNull();
        assertThat(target.callback()).isSameAs(callback);
        assertThat(target.agent()).isSameAs(agent);
    } // end of ctorSuccess()

    @Test
    @DisplayName("Dispatch null target")
    public void dispatchNullTarget()
    {
        final EfsDispatchTarget<TestEvent> target = null;
        final TestEvent event = mock(TestEvent.class);

        assertThatThrownBy(
            () -> EfsDispatcher.dispatch(target, event))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsDispatcher.NULL_DISPATCH_TARGET);
    } // end of dispatchNullTarget()

    @Test
    @DisplayName("Dispatch target with null event")
    public void dispatchTargetNullEvent()
    {
        final Consumer<TestEvent> callback = e -> {};
        final IEfsAgent agent = mTestAgent;
        final EfsDispatchTarget<TestEvent> target =
            new EfsDispatchTarget<>(callback, agent);
        final TestEvent event = null;

        assertThatThrownBy(
            () -> EfsDispatcher.dispatch(target, event))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsDispatcher.NULL_EVENT);
    } // end of dispatchTargetNullEvent()

    @Test
    @DisplayName("Dispatch target success")
    public void dispatchTargetSuccess()
    {
        final CountDownLatch signal = new CountDownLatch(1);
        final Consumer<TestEvent> callback =
            e -> signal.countDown();
        final IEfsAgent agent = mTestAgent;
        final EfsDispatchTarget<TestEvent> target =
            new EfsDispatchTarget<>(callback, agent);
        final TestEvent event = mock(TestEvent.class);

        EfsDispatcher.dispatch(target, event);

        try
        {
            signal.await();
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(signal.getCount()).isZero();
    } // end of dispatchTargetSuccess()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

//---------------------------------------------------------------
// Inner classes.
//

    private interface TestEvent
        extends IEfsEvent
    {}
} // end of class DispatchTargetTest