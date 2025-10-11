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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.activator.event.ActivatorEvent;
import org.efs.activator.event.ActivatorEvent.StepState;
import org.efs.dispatcher.EfsDispatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests workflow executions.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@ExtendWith ({DispatcherInitialization.class})
public class WorkflowExecutionTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String STEP_NAME_FORMAT = "%s-%d-%d";
    private static final Duration WAIT_DURATION =
        Duration.ofSeconds(30L);

    /**
     * Configuration file names.
     */
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

    //
    // Workflow names.
    //

    private static final String HOT_START = "hot-start";
    private static final String HOT_SHUTDOWN = "hot-shutdown";
    private static final String WARM_START = "warm-start";
    private static final String WARM_SHUTDOWN = "warm-shutdown";
    private static final String WARM_TO_HOT = "warm-to-hot";

    //
    // Agent names.
    //

    private static final String ACTIVATOR_LISTENER =
        "activator-listener";

    //-----------------------------------------------------------
    // Statics.
    //

    private static EfsActivator sActivator;
    private static MarketDataAgent sMDAgent;
    private static MakeMoneyAlgoAgent sAlgoAgent;
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
        sMDAgent = new MarketDataAgent();
        sAlgoAgent = new MakeMoneyAlgoAgent();
        sListener = new ActivatorListener(ACTIVATOR_LISTENER);

        EfsDispatcher.register(sMDAgent, MD_DISPATCHER);
        EfsDispatcher.register(sAlgoAgent, ALGO_DISPATCHER);
        EfsDispatcher.register(sListener, UTILITY_DISPATCHER);

        sActivator =
            EfsActivator.loadActivator(ACTIVATOR_FILE_NAME);

        sListener.register(sActivator);
    } // end of setUpClass()

    @AfterAll
    public static void tearDownClass()
    {
        sListener.deregister(sActivator);

        EfsDispatcher.deregister(sMDAgent);
        EfsDispatcher.deregister(sAlgoAgent);
        EfsDispatcher.deregister(sListener);
    } // end of tearDownClass()

    @BeforeEach
    public void setUp()
    {
        sListener.clearEvents();
    } // end of setUp()

    @AfterEach
    public void tearDown()
    {
        sActivator.terminateWorkflow();
    } // end of tearDown()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void fullExecution()
    {
        final int numActivations = 8;
        String wfName;
        String agentName;
        final List<ActivatorEvent> expectedEvents =
            new ArrayList<>(numActivations);
        CountDownLatch doneSignal =
            new CountDownLatch(numActivations);

        sListener.doneSignal(doneSignal);

        wfName = HOT_START;
        agentName = MarketDataAgent.AGENT_NAME;
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        0,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STOPPED,
                        EfsAgentState.STARTING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        0,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.STARTING,
                        EfsAgentState.STAND_BY));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        0,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STAND_BY,
                        EfsAgentState.ACTIVATING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        0,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.ACTIVATING,
                        EfsAgentState.ACTIVE));
        agentName = MakeMoneyAlgoAgent.AGENT_NAME;
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        1,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STOPPED,
                        EfsAgentState.STARTING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        1,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.STARTING,
                        EfsAgentState.STAND_BY));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        2,
                        0,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STAND_BY,
                        EfsAgentState.ACTIVATING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        2,
                        0,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.ACTIVATING,
                        EfsAgentState.ACTIVE));

        sActivator.initializeWorkflow(wfName);
        sActivator.executeWorkflow();

        assertThat(sActivator.workflow())
            .isEqualTo(EfsActivator.NO_WORKFLOW_IN_PROGRESS);

        // Give the activator listener a chance to receive all
        // activator events.
        try
        {
            doneSignal.await(WAIT_DURATION.toSeconds(),
                             TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(
            sActivator.agentState(MarketDataAgent.AGENT_NAME))
            .isEqualTo(EfsAgentState.ACTIVE);
        assertThat(
            sActivator.agentState(MakeMoneyAlgoAgent.AGENT_NAME))
            .isEqualTo(EfsAgentState.ACTIVE);
        assertThat(sListener.events())
            .containsExactlyElementsOf(expectedEvents);

        wfName = HOT_SHUTDOWN;
        expectedEvents.clear();
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        0,
                        StepState.IN_PROGRESS,
                        EfsAgentState.ACTIVE,
                        EfsAgentState.DEACTIVATING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        0,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.DEACTIVATING,
                        EfsAgentState.STAND_BY));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        0,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STAND_BY,
                        EfsAgentState.STOPPING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        0,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.STOPPING,
                        EfsAgentState.STOPPED));

        agentName = MarketDataAgent.AGENT_NAME;
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        1,
                        StepState.IN_PROGRESS,
                        EfsAgentState.ACTIVE,
                        EfsAgentState.DEACTIVATING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        1,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.DEACTIVATING,
                        EfsAgentState.STAND_BY));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        2,
                        0,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STAND_BY,
                        EfsAgentState.STOPPING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        2,
                        0,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.STOPPING,
                        EfsAgentState.STOPPED));

        doneSignal = new CountDownLatch(numActivations);
        sListener.clearEvents();
        sListener.doneSignal(doneSignal);

        sActivator.initializeWorkflow(wfName);
        sActivator.executeWorkflow();

        // Give the activator listener a chance to receive all
        // activator events.
        try
        {
            doneSignal.await(WAIT_DURATION.toSeconds(),
                             TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(
            sActivator.agentState(MarketDataAgent.AGENT_NAME))
            .isEqualTo(EfsAgentState.STOPPED);
        assertThat(
            sActivator.agentState(MakeMoneyAlgoAgent.AGENT_NAME))
            .isEqualTo(EfsAgentState.STOPPED);
        assertThat(sListener.events())
            .containsExactlyElementsOf(expectedEvents);
    } // end of fullExecution()

    @Test
    public void byStageExecution()
    {
        final int numActivations = 6;
        String wfName;
        String agentName;
        final List<ActivatorEvent> expectedEvents =
            new ArrayList<>(numActivations);
        CountDownLatch doneSignal =
            new CountDownLatch(numActivations);

        sListener.doneSignal(doneSignal);

        wfName = WARM_START;
        agentName = MarketDataAgent.AGENT_NAME;
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        0,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STOPPED,
                        EfsAgentState.STARTING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        0,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.STARTING,
                        EfsAgentState.STAND_BY));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        0,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STAND_BY,
                        EfsAgentState.ACTIVATING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        0,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.ACTIVATING,
                        EfsAgentState.ACTIVE));

        agentName = MakeMoneyAlgoAgent.AGENT_NAME;
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        1,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STOPPED,
                        EfsAgentState.STARTING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        1,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.STARTING,
                        EfsAgentState.STAND_BY));

        sActivator.initializeWorkflow(wfName);

        while (!sActivator.executeNextStage())
        {}

        // Give the activator listener a chance to receive all
        // activator events.
        try
        {
            doneSignal.await(WAIT_DURATION.toSeconds(),
                             TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(
            sActivator.agentState(MarketDataAgent.AGENT_NAME))
            .isEqualTo(EfsAgentState.ACTIVE);
        assertThat(
            sActivator.agentState(MakeMoneyAlgoAgent.AGENT_NAME))
            .isEqualTo(EfsAgentState.STAND_BY);
        assertThat(sListener.events())
            .containsExactlyElementsOf(expectedEvents);

        wfName = WARM_SHUTDOWN;
        expectedEvents.clear();
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        0,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STAND_BY,
                        EfsAgentState.STOPPING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        0,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.STOPPING,
                        EfsAgentState.STOPPED));

        agentName = MarketDataAgent.AGENT_NAME;
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        1,
                        StepState.IN_PROGRESS,
                        EfsAgentState.ACTIVE,
                        EfsAgentState.DEACTIVATING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        1,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.DEACTIVATING,
                        EfsAgentState.STAND_BY));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        0,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STAND_BY,
                        EfsAgentState.STOPPING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        0,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.STOPPING,
                        EfsAgentState.STOPPED));

        doneSignal = new CountDownLatch(numActivations);
        sListener.clearEvents();
        sListener.doneSignal(doneSignal);

        sActivator.initializeWorkflow(wfName);

        while (!sActivator.executeNextStage())
        {}

        // Give the activator listener a chance to receive all
        // activator events.
        try
        {
            doneSignal.await(WAIT_DURATION.toSeconds(),
                             TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(
            sActivator.agentState(MarketDataAgent.AGENT_NAME))
            .isEqualTo(EfsAgentState.STOPPED);
        assertThat(
            sActivator.agentState(MakeMoneyAlgoAgent.AGENT_NAME))
            .isEqualTo(EfsAgentState.STOPPED);
        assertThat(sListener.events())
            .containsExactlyElementsOf(expectedEvents);
    } // end of byStageExecution()

    @Test
    public void byStepExecution()
    {
        int numActivations = 6;
        String wfName;
        String agentName;
        final List<ActivatorEvent> expectedEvents =
            new ArrayList<>();
        CountDownLatch doneSignal =
            new CountDownLatch(numActivations);

        sListener.doneSignal(doneSignal);

        wfName = WARM_START;
        agentName = MarketDataAgent.AGENT_NAME;
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        0,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STOPPED,
                        EfsAgentState.STARTING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        0,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.STARTING,
                        EfsAgentState.STAND_BY));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        0,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STAND_BY,
                        EfsAgentState.ACTIVATING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        0,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.ACTIVATING,
                        EfsAgentState.ACTIVE));

        agentName = MakeMoneyAlgoAgent.AGENT_NAME;
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        1,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STOPPED,
                        EfsAgentState.STARTING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        1,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.STARTING,
                        EfsAgentState.STAND_BY));

        sActivator.initializeWorkflow(wfName);

        while (!sActivator.executeNextStep())
        {}

        // Give the activator listener a chance to receive all
        // activator events.
        try
        {
            doneSignal.await(WAIT_DURATION.toSeconds(),
                             TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(
            sActivator.agentState(MarketDataAgent.AGENT_NAME))
            .isEqualTo(EfsAgentState.ACTIVE);
        assertThat(
            sActivator.agentState(MakeMoneyAlgoAgent.AGENT_NAME))
            .isEqualTo(EfsAgentState.STAND_BY);
        assertThat(sListener.events())
            .containsExactlyElementsOf(expectedEvents);

        wfName = WARM_TO_HOT;
        numActivations = 2;
        expectedEvents.clear();
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        0,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STAND_BY,
                        EfsAgentState.ACTIVATING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        0,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.ACTIVATING,
                        EfsAgentState.ACTIVE));

        doneSignal = new CountDownLatch(numActivations);
        sListener.clearEvents();
        sListener.doneSignal(doneSignal);

        sActivator.initializeWorkflow(wfName);

        while (!sActivator.executeNextStep())
        {}

        // Give the activator listener a chance to receive all
        // activator events.
        try
        {
            doneSignal.await(WAIT_DURATION.toSeconds(),
                             TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(
            sActivator.agentState(MarketDataAgent.AGENT_NAME))
            .isEqualTo(EfsAgentState.ACTIVE);
        assertThat(
            sActivator.agentState(MakeMoneyAlgoAgent.AGENT_NAME))
            .isEqualTo(EfsAgentState.ACTIVE);
        assertThat(sListener.events())
            .containsExactlyElementsOf(expectedEvents);

        wfName = HOT_SHUTDOWN;
        numActivations = 8;
        expectedEvents.clear();
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        0,
                        StepState.IN_PROGRESS,
                        EfsAgentState.ACTIVE,
                        EfsAgentState.DEACTIVATING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        0,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.DEACTIVATING,
                        EfsAgentState.STAND_BY));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        0,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STAND_BY,
                        EfsAgentState.STOPPING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        0,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.STOPPING,
                        EfsAgentState.STOPPED));

        agentName = MarketDataAgent.AGENT_NAME;
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        1,
                        StepState.IN_PROGRESS,
                        EfsAgentState.ACTIVE,
                        EfsAgentState.DEACTIVATING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        1,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.DEACTIVATING,
                        EfsAgentState.STAND_BY));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        2,
                        0,
                        StepState.IN_PROGRESS,
                        EfsAgentState.STAND_BY,
                        EfsAgentState.STOPPING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        2,
                        0,
                        StepState.COMPLETED_SUCCESS,
                        EfsAgentState.STOPPING,
                        EfsAgentState.STOPPED));

        doneSignal = new CountDownLatch(numActivations);
        sListener.clearEvents();
        sListener.doneSignal(doneSignal);

        sActivator.initializeWorkflow(wfName);
        sActivator.executeWorkflow();

        // Give the activator listener a chance to receive all
        // activator events.
        try
        {
            doneSignal.await(WAIT_DURATION.toSeconds(),
                             TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(
            sActivator.agentState(MarketDataAgent.AGENT_NAME))
            .isEqualTo(EfsAgentState.STOPPED);
        assertThat(
            sActivator.agentState(MakeMoneyAlgoAgent.AGENT_NAME))
            .isEqualTo(EfsAgentState.STOPPED);
        assertThat(sListener.events())
            .containsExactlyElementsOf(expectedEvents);
    } // end of byStepExecution()

    @Test
    public void registerListenerTwice()
    {
        assertThat(sActivator.isRegisteredListener(sListener))
            .isTrue();

        try
        {
            sListener.register(sActivator);
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex)
                .hasMessage("agent %s is already registered",
                            sListener.name());
        }

        assertThat(sActivator.isRegisteredListener(sListener))
            .isTrue();

        sListener.deregister(sActivator);

        assertThat(sActivator.isRegisteredListener(sListener))
            .isFalse();
    } // end of registerListenerTwice()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private static ActivatorEvent createEvent(final String agentName,
                                              final String wfName,
                                              final int stageIndex,
                                              final int stepIndex,
                                              final StepState stepState,
                                              final EfsAgentState initialState,
                                              final EfsAgentState finalState)
    {
        final String stepName =
            String.format(STEP_NAME_FORMAT,
                          wfName,
                          stageIndex,
                          stepIndex);
        final ActivatorEvent.Builder builder =
            ActivatorEvent.builder();

        return (builder.agentName(agentName)
                       .stepName(stepName)
                       .stepState(stepState)
                       .duration(Duration.ZERO)
                       .initialState(initialState)
                       .finalState(finalState)
                       .build());
    } // createEvent(...)
} // end of class WorkflowExecutionTest
