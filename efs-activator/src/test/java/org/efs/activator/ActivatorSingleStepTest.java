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
package org.efs.activator;

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.activator.event.ActivatorEvent;
import org.efs.activator.event.ActivatorEvent.StepState;
import org.efs.dispatcher.EfsDispatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Exercises
 * {@link EfsActivator#execute(String, EfsAgentState, EfsAgentState, Duration) activator single-step execution}.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@ExtendWith ({DispatcherInitialization.class})
public final class ActivatorSingleStepTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String LISTENER_NAME = "TestListener";
    private static final Duration WAIT_DURATION =
        Duration.ofSeconds(30L);

    //
    // Configuration file names.
    //

    private static final String ACTIVATOR_FILE_NAME =
        "./src/test/resources/test-activator.conf";

    //
    // Dispatcher names.
    //

    private static final String MD_DISPATCHER =
        "MarketDataDispatcher";
    private static final String ALGO_DISPATCHER =
        "AlgoDispatcher";
    private static final String UTILITY_DISPATCHER =
        "UtilityDispatcher";

    //-----------------------------------------------------------
    // Statics.
    //

    private static EfsActivator sActivator;
    private static MarketDataAgent sMDAgent;
    private static MakeMoneyAlgoAgent sAlgoAgent;
    private static TestAgent sTestAgent;
    private static ActivatorListener sListener;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initailization.
    //

    @BeforeAll
    public static void setUpClass()
    {
        sActivator =
            EfsActivator.loadActivator(ACTIVATOR_FILE_NAME);

        sMDAgent = new MarketDataAgent();
        sAlgoAgent = new MakeMoneyAlgoAgent();
        sTestAgent = new TestAgent();
        sListener = new ActivatorListener(LISTENER_NAME);

        EfsDispatcher.register(sMDAgent, MD_DISPATCHER);
        EfsDispatcher.register(sAlgoAgent, ALGO_DISPATCHER);
        EfsDispatcher.register(sTestAgent, ALGO_DISPATCHER);
        EfsDispatcher.register(sListener, UTILITY_DISPATCHER);

        sListener.register(sActivator);
    } // end of setUpClass()

    @AfterAll
    public static void tearDownClass()
    {
        EfsDispatcher.deregister(sMDAgent);
        EfsDispatcher.deregister(sAlgoAgent);
        EfsDispatcher.deregister(sTestAgent);
        sListener.deregister(sActivator);
    } // end of tearDownClass()

    @BeforeEach
    public void setUp()
    {
        sListener.clearEvents();
    } // end of setUp()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void singleStepExecutionUnknownAgent()
    {
        final String agentName = "fubar";
        final EfsAgentState beginState = EfsAgentState.STOPPED;
        final EfsAgentState endState = EfsAgentState.STAND_BY;
        final Duration transitionTime = Duration.ofMillis(1L);

        try
        {
            sActivator.execute(agentName,
                               beginState,
                               endState,
                               transitionTime);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalStateException.class);
            assertThat(jex)
                .hasMessage(
                    "no such registered agent \"%s\"",
                    agentName);
        }
    } // end of singleStepExecutionUnknownAgent()

    @Test
    public void singleStepExecutionNonActivateAgent()
    {
        final String agentName = TestAgent.AGENT_NAME;
        final EfsAgentState beginState = EfsAgentState.STOPPED;
        final EfsAgentState endState = EfsAgentState.STAND_BY;
        final Duration transitionTime = Duration.ofMillis(1L);

        try
        {
            sActivator.execute(agentName,
                               beginState,
                               endState,
                               transitionTime);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalStateException.class);
            assertThat(jex)
                .hasMessage(
                    "\"%s\" is not an activate agent",
                    agentName);
        }
    } // end of singleStepExecutionNonActivateAgent()

    @Test
    public void singleStepExecutionIncorrectAgentBeginState()
    {
        final String agentName = MakeMoneyAlgoAgent.AGENT_NAME;
        final EfsAgentState beginState = EfsAgentState.STAND_BY;
        final EfsAgentState endState = EfsAgentState.ACTIVE;
        final Duration transitionTime = Duration.ofMillis(1L);

        try
        {
            sActivator.execute(agentName,
                               beginState,
                               endState,
                               transitionTime);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalStateException.class);
            assertThat(jex)
                .hasMessage(
                    "agent %s is not in expected begin state %s but %s (step standalone-step)",
                    agentName,
                    beginState,
                    EfsAgentState.STOPPED);
        }
    } // end of singleStepExecutionIncorrectAgentBeginState()

    @Test
    public void singleStepExecutionSuccess()
    {
        final String agentName = MakeMoneyAlgoAgent.AGENT_NAME;
        EfsAgentState beginState = EfsAgentState.STOPPED;
        EfsAgentState endState = EfsAgentState.STAND_BY;
        final Duration transitionTime = Duration.ofMillis(10L);
        final Duration delay = Duration.ofMillis(2L);
        List<ActivatorEvent> expected =
            ImmutableList.of(
                (ActivatorEvent.builder())
                    .agentName(agentName)
                    .stepName(EfsActivator.STAND_ALONE_STEP)
                    .stepState(StepState.IN_PROGRESS)
                    .initialState(beginState)
                    .finalState(EfsAgentState.STARTING)
                    .duration(Duration.ZERO)
                    .build(),
                (ActivatorEvent.builder())
                    .agentName(agentName)
                    .stepName(EfsActivator.STAND_ALONE_STEP)
                    .stepState(StepState.COMPLETED_SUCCESS)
                    .initialState(EfsAgentState.STARTING)
                    .finalState(endState)
                    .duration(delay)
                    .build());
        CountDownLatch doneSignal =
            new CountDownLatch(expected.size());

        sListener.doneSignal(doneSignal);

        sActivator.execute(agentName,
                           beginState,
                           endState,
                           transitionTime);

        // Give the activator listener a chance to receive all
        // activator events.
        try
        {
            doneSignal.await(WAIT_DURATION.toSeconds(),
                             TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(sActivator.agentState(agentName))
            .isEqualTo(endState);
        assertThat(sListener.events()).containsAll(expected);

        beginState = EfsAgentState.STAND_BY;
        endState = EfsAgentState.STOPPED;

        sListener.clearEvents();
        expected =
            ImmutableList.of(
                (ActivatorEvent.builder())
                    .agentName(agentName)
                    .stepName(EfsActivator.STAND_ALONE_STEP)
                    .stepState(StepState.IN_PROGRESS)
                    .initialState(beginState)
                    .finalState(EfsAgentState.STOPPING)
                    .duration(Duration.ZERO)
                    .build(),
                (ActivatorEvent.builder())
                    .agentName(agentName)
                    .stepName(EfsActivator.STAND_ALONE_STEP)
                    .stepState(StepState.COMPLETED_SUCCESS)
                    .initialState(EfsAgentState.STOPPING)
                    .finalState(endState)
                    .duration(delay)
                    .build());
        doneSignal = new CountDownLatch(expected.size());

        sListener.doneSignal(doneSignal);

        sActivator.execute(agentName,
                           beginState,
                           endState,
                           transitionTime);

        try
        {
            doneSignal.await(WAIT_DURATION.toSeconds(),
                             TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(sActivator.agentState(agentName))
            .isEqualTo(endState);
        assertThat(sListener.events()).containsAll(expected);

        // Perform the last transition again. Nothing should
        // happen since the agent is already stopped.
        sListener.clearEvents();
        expected =
            ImmutableList.of(
                (ActivatorEvent.builder())
                    .agentName(agentName)
                    .stepName(EfsActivator.STAND_ALONE_STEP)
                    .stepState(StepState.COMPLETED_SUCCESS)
                    .initialState(EfsAgentState.STOPPED)
                    .finalState(endState)
                    .duration(Duration.ZERO)
                    .build());
        doneSignal = new CountDownLatch(expected.size());

        sListener.doneSignal(doneSignal);

        sActivator.execute(agentName,
                           beginState,
                           endState,
                           transitionTime);

        try
        {
            doneSignal.await(WAIT_DURATION.toSeconds(),
                             TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(sActivator.agentState(agentName))
            .isEqualTo(endState);
        assertThat(sListener.events()).containsAll(expected);
    } // end of singleStepExecutionSuccess()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------
} // end of class ActivatorSingleStepTest
