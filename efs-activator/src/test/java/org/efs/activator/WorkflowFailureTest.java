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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import org.efs.activator.event.ActivatorEvent;
import org.efs.activator.event.ActivatorEvent.StepState;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Miscellaneous tests of workflow activation components.
 *
 * @author charlesr
 */

@ExtendWith ({DispatcherInitialization.class})
public final class WorkflowFailureTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Configuration file name.
     */
    private static final String ACTIVATOR_FILE_NAME =
        "./src/test/resources/simple-activator.conf";

    /**
     * Dispatch error agent events to this dispatcher.
     */
    private static final String UTILITY_DISPATCHER =
        "UtilityDispatcher";

    /**
     * Error agent name.
     */
    private static final String ERROR_AGENT_NAME = "error-agent";

    /**
     * Activator listener agent name prefix.
     */
    private static final String ACTIVATOR_LISTENER =
        "activator-listener";

    private static final String STEP_NAME_FORMAT = "%s-%d-%d";
    private static final Duration WAIT_DURATION =
        Duration.ofSeconds(30L);

    //-----------------------------------------------------------
    // Statics.
    //

    private static EfsActivator sActivator;
    private static CountDownLatch sContinueSignal;
    private static ErrorAgent sErrorAgent;
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
        sContinueSignal = new CountDownLatch(1);
        sErrorAgent = new ErrorAgent(sContinueSignal);
        sListener = new ActivatorListener(ACTIVATOR_LISTENER);

        EfsDispatcher.register(sErrorAgent, UTILITY_DISPATCHER);
        EfsDispatcher.register(sListener, UTILITY_DISPATCHER);

        sActivator =
            EfsActivator.loadActivator(ACTIVATOR_FILE_NAME);

        sListener.register(sActivator);
    } // end of setUpClass()

    @AfterAll
    public static void tearDownClass()
    {
        sListener.deregister(sActivator);

        EfsDispatcher.deregister(sErrorAgent);
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
    public void noWorkFlow()
    {
        assertThat(sActivator.workflow())
            .isEqualTo(EfsActivator.NO_WORKFLOW_IN_PROGRESS);
        assertThat(sActivator.isInProgress()).isFalse();
    } // end of noWorkFlow()

    @Test
    public void agentStateNullDirection()
    {
        final EfsAgentState state = EfsAgentState.STAND_BY;
        final WorkflowDirection direction = null;

        try
        {
            state.getAdjacent(direction);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex).hasMessage("direction is null");
        }
    } // end of agentStateNullDirection()

    @Test
    public void agentStateAdjacentAscend()
    {
        final EfsAgentState state = EfsAgentState.STAND_BY;
        final WorkflowDirection direction =
            WorkflowDirection.ASCEND;
        final EfsAgentState expected = EfsAgentState.ACTIVE;
        final EfsAgentState actual =
            state.getAdjacent(direction);

        assertThat(actual).isEqualTo(expected);
    } // end of agentStateAdjacentAscend()

    @Test
    public void agentStateAdjacentDescend()
    {
        final EfsAgentState state = EfsAgentState.STAND_BY;
        final WorkflowDirection direction =
            WorkflowDirection.DESCEND;
        final EfsAgentState expected = EfsAgentState.STOPPED;
        final EfsAgentState actual =
            state.getAdjacent(direction);

        assertThat(actual).isEqualTo(expected);
    } // end of agentStateAdjacentDescend()

    @Test
    public void agentStateIsAdjacentNullState()
    {
        final EfsAgentState state = EfsAgentState.STAND_BY;
        final EfsAgentState nextState = null;

        try
        {
            state.isAdjacent(nextState);
        }
        catch (Exception nullex)
        {
            assertThat(nullex).hasMessage("state is null");
        }
    } // end of agentStateIsAdjacentNullState()

    @Test
    public void initializeNullWorkflowName()
    {
        final String wfName = null;

        try
        {
            sActivator.initializeWorkflow(wfName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "workflowName is either null or an empty string");
        }
    } // end of initializeNullWorkflowName()

    @Test
    public void initializeEmptyWorkflowName()
    {
        final String wfName = "";

        try
        {
            sActivator.initializeWorkflow(wfName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "workflowName is either null or an empty string");
        }
    } // end of initializeEmptyWorkflowName()

    @Test
    public void initializeUnknownWorkflow()
    {
        final String wfName = "fubar";

        try
        {
            sActivator.initializeWorkflow(wfName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "unknown workflow \"" +
                wfName +
                "\"");
        }
    } // end of initializeUnknownWorkflow()

    @Test
    public void initializeWorkflowWhileinWorkflow()
    {
        final String wfName = "simple-start";

        sActivator.initializeWorkflow(wfName);

        try
        {
            sActivator.initializeWorkflow(wfName);
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex)
                .hasMessage(
                    "workflow " +
                    wfName +
                    " in-progress; complete before setting another workflow");
        }
    } // end of initializeWorkflowWhileinWorkflow()

    @Test
    public void setWorkflowIndexNoWorkflow()
    {
        final int stageIndex = 0;
        final int stepIndex = 0;

        try
        {
            sActivator.setWorkflowStage(stageIndex, stepIndex);
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex)
                .hasMessage(
                    EfsActivator.NO_WORKFLOW_IN_PROGRESS);
        }
    } // end of setWorkflowIndexNoWorkflow()

    @Test
    public void setWorkflowIndexNegativeStageIndex()
    {
        final String wfName = "simple-start";
        final int stageIndex = -1;
        final int stepIndex = 0;
        final String message =
            String.format(
                "stage index %d is out of bounds", stageIndex);

        sActivator.initializeWorkflow(wfName);

        try
        {
            sActivator.setWorkflowStage(stageIndex, stepIndex);
        }
        catch (IndexOutOfBoundsException index)
        {
            assertThat(index).hasMessage(message);
        }
    } // end of setWorkflowIndexNegativeStageIndex()

    @Test
    public void setWorkflowIndexStageIndexTooBig()
    {
        final String wfName = "simple-start";
        final int stageIndex = 3;
        final int stepIndex = 0;
        final String message =
            String.format(
                "stage index %d is out of bounds", stageIndex);

        sActivator.initializeWorkflow(wfName);

        try
        {
            sActivator.setWorkflowStage(stageIndex, stepIndex);
        }
        catch (IndexOutOfBoundsException index)
        {
            assertThat(index).hasMessage(message);
        }
    } // end of setWorkflowIndexStageIndexTooBig()

    @Test
    public void setWorkflowIndexNegativeStepIndex()
    {
        final String wfName = "simple-start";
        final int stageIndex = 1;
        final int stepIndex = -1;
        final String message =
            String.format(
                "step index %d is out of bounds", stepIndex);

        sActivator.initializeWorkflow(wfName);

        try
        {
            sActivator.setWorkflowStage(stageIndex, stepIndex);
        }
        catch (IndexOutOfBoundsException index)
        {
            assertThat(index).hasMessage(message);
        }
    } // end of setWorkflowIndexNegativeStepIndex()

    @Test
    public void setWorkflowIndexStepIndexTooBig()
    {
        final String wfName = "simple-start";
        final int stageIndex = 1;
        final int stepIndex = 1;
        final String message =
            String.format(
                "step index %d is out of bounds", stepIndex);

        sActivator.initializeWorkflow(wfName);

        try
        {
            sActivator.setWorkflowStage(stageIndex, stepIndex);
        }
        catch (IndexOutOfBoundsException index)
        {
            assertThat(index).hasMessage(message);
        }
    } // end of setWorkflowIndexStepIndexTooBig()

    @Test
    public void setAgentStateNullAgentName()
    {
        final String agentName = null;
        final EfsAgentState state = EfsAgentState.ACTIVE;

        try
        {
            sActivator.agentState(agentName, state);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(
                    "agentName is either null or an empty string");
        }
    } // end of setAgentStateNullAgentName()

    @Test
    public void setAgentStateEmptyAgentName()
    {
        final String agentName = "";
        final EfsAgentState state = EfsAgentState.ACTIVE;

        try
        {
            sActivator.agentState(agentName, state);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(
                    "agentName is either null or an empty string");
        }
    } // end of setAgentStateEmptyAgentName()

    @Test
    public void setAgentStateNullState()
    {
        final String agentName = ERROR_AGENT_NAME;
        final EfsAgentState state = null;

        try
        {
            sActivator.agentState(agentName, state);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex).hasMessage("state is null");
        }
    } // end of setAgentStateNullState()

    @Test
    public void executeStepNoWorkflow()
    {
        try
        {
            sActivator.executeNextStep();
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex).hasMessage(
                EfsActivator.NO_WORKFLOW_IN_PROGRESS);
        }
    } // end of executeStepNoWorkflow()

    @Test
    public void executeStageNoWorkflow()
    {
        try
        {
            sActivator.executeNextStage();
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex).hasMessage(
                EfsActivator.NO_WORKFLOW_IN_PROGRESS);
        }
    } // end of executeStageNoWorkflow()

    @Test
    public void executeWorkflowNoWorkflow()
    {
        try
        {
            sActivator.executeWorkflow();
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex).hasMessage(
                EfsActivator.NO_WORKFLOW_IN_PROGRESS);
        }
    } // end of executeWorkflowNoWorkflow()

    @Test
    public void executeStepFailure()
    {
        final String wfName = "simple-start";
        final String agentName = ERROR_AGENT_NAME;
        final int numActivations = 2;
        final List<ActivatorEvent> expectedEvents =
            new ArrayList<>(numActivations);
        final CountDownLatch doneSignal =
            new CountDownLatch(numActivations);

        sListener.doneSignal(doneSignal);

        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        0,
                        ActivatorEvent.StepState.IN_PROGRESS,
                        EfsAgentState.STOPPED,
                        EfsAgentState.STARTING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        0,
                        0,
                        ActivatorEvent.StepState.COMPLETED_FAILED,
                        EfsAgentState.STARTING,
                        EfsAgentState.STOPPED));

        try
        {
            sActivator.initializeWorkflow(wfName);
            sActivator.setWorkflowStage(0, 0);
            sActivator.agentState(agentName,
                                  EfsAgentState.STOPPED);
            sActivator.executeNextStep();
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex).hasMessage(
                "transition failed (step " +
                wfName +
                "-0-0)");
        }

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
            sActivator.agentState(ERROR_AGENT_NAME))
            .isEqualTo(EfsAgentState.STOPPED);
        assertThat(sListener.events())
            .containsExactlyElementsOf(expectedEvents);
    } // end of executeStepFailure()

    @Test
    public void executeStepTimeout()
    {
        final String wfName = "simple-start";
        final String agentName = ERROR_AGENT_NAME;
        final int numActivations = 2;
        final List<ActivatorEvent> expectedEvents =
            new ArrayList<>(numActivations);
        final CountDownLatch doneSignal =
            new CountDownLatch(numActivations);

        sListener.doneSignal(doneSignal);

        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        0,
                        ActivatorEvent.StepState.IN_PROGRESS,
                        EfsAgentState.STAND_BY,
                        EfsAgentState.ACTIVATING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        1,
                        0,
                        ActivatorEvent.StepState.COMPLETED_FAILED,
                        EfsAgentState.ACTIVATING,
                        EfsAgentState.STAND_BY));

        try
        {
            sActivator.initializeWorkflow(wfName);
            sActivator.setWorkflowStage(1, 0);
            sActivator.agentState(agentName,
                                  EfsAgentState.STAND_BY);
            sActivator.executeNextStep();
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex).hasMessage(
                "timed out waiting for transition to complete");
        }

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
            sActivator.agentState(ERROR_AGENT_NAME))
            .isEqualTo(EfsAgentState.STAND_BY);
        assertThat(sListener.events())
            .containsExactlyElementsOf(expectedEvents);

        sContinueSignal.countDown();
    } // end of executeStepTimeout()

    @Test
    public void executeStepInterrupt()
    {
        final String wfName = "simple-start";
        final String agentName = ERROR_AGENT_NAME;
        final Duration interruptDelay = Duration.ofMillis(500L);
        final Thread testThread = Thread.currentThread();
        final int numActivations = 2;
        final List<ActivatorEvent> expectedEvents =
            new ArrayList<>(numActivations);
        final CountDownLatch doneSignal =
            new CountDownLatch(numActivations);

        sListener.doneSignal(doneSignal);

        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        2,
                        0,
                        ActivatorEvent.StepState.IN_PROGRESS,
                        EfsAgentState.ACTIVE,
                        EfsAgentState.DEACTIVATING));
        expectedEvents.add(
            createEvent(agentName,
                        wfName,
                        2,
                        0,
                        ActivatorEvent.StepState.COMPLETED_FAILED,
                        EfsAgentState.DEACTIVATING,
                        EfsAgentState.ACTIVE));

        try
        {
            sActivator.initializeWorkflow(wfName);

            assertThat(sActivator.workflow()).isEqualTo(wfName);
            assertThat(sActivator.isInProgress()).isTrue();

            sActivator.setWorkflowStage(2, 0);
            sActivator.agentState(agentName,
                                  EfsAgentState.ACTIVE);

            // Hit this thread with an interrupt.
            (new Thread(
                () ->
                {
                    await().atLeast(interruptDelay);
                    testThread.interrupt();
                })).start();

            sActivator.executeNextStep();
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex).hasMessage(
                "transition interrupted (step " +
                wfName +
                "-2-0)");
        }

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
            sActivator.agentState(ERROR_AGENT_NAME))
            .isEqualTo(EfsAgentState.ACTIVE);
        assertThat(sListener.events())
            .containsExactlyElementsOf(expectedEvents);
    } // end of executeStepInterrupt()

    @Test
    public void registerNullCallback()
    {
        final Consumer<ActivatorEvent> callback = null;
        final IEfsAgent agent =
            new ActivatorListener("test-agent-2002");

        try
        {
            sActivator.registerListener(callback, agent);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(EfsActivator.NULL_CALLBACK);
        }
    } // end of registerNullCallback()

    @Test
    public void registerNullAgent()
    {
        final Consumer<ActivatorEvent> callback =
            this::onActivatorEvent;
        final IEfsAgent agent = null;

        try
        {
            sActivator.registerListener(callback, agent);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(EfsActivator.NULL_AGENT);
        }
    } // end of registerNullAgent()

    @Test
    public void registerUnregisteredAgent()
    {
        final Consumer<ActivatorEvent> callback =
            this::onActivatorEvent;
        final IEfsAgent agent =
            new ActivatorListener("test-agent-2003");

        try
        {
            sActivator.registerListener(callback, agent);
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex)
                .hasMessage(EfsActivator.UNREGISTERED_AGENT);
        }
    } // end of registerUnregisteredAgent()

    @Test
    public void loadActivatorNonexistantFile()
    {
        final String fileName =
            "./src/test/resources/no-file.conf";
        final String message =
            String.format("\"%s\" does not exist", fileName);

        try
        {
            EfsActivator.loadActivator(fileName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(message);
        }
    } // end of loadActivatorNonexistantFile()

    @Test
    public void loadActivatorNoRegularFile()
    {
        final String fileName = "./src/test/resources";
        final String message =
            String.format("\"%s\" not regular file", fileName);

        try
        {
            EfsActivator.loadActivator(fileName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(message);
        }
    } // end of loadActivatorNoRegularFile()

    @Disabled
    @Test
    public void loadActivatoraUnreadable()
    {
        final String fileName =
            "/tmp/efs-unit-test/no-read.conf";
        final String message =
            String.format("\"%s\" unreadable", fileName);

        try
        {
            EfsActivator.loadActivator(fileName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(message);
        }
    } // end of loadActivatoraUnreadable()

    @Test
    public void getAdjacentMappingsTest()
    {
        final Map<String, EfsAgentState> expected = new HashMap<>();
        String key;
        EfsAgentState exp;
        EfsAgentState actual;

        // Persistent states
        expected.put("STOPPED#ASCEND", EfsAgentState.STAND_BY);
        expected.put("STOPPED#DESCEND", null);

        expected.put("STAND_BY#ASCEND", EfsAgentState.ACTIVE);
        expected.put("STAND_BY#DESCEND", EfsAgentState.STOPPED);

        expected.put("ACTIVE#ASCEND", null);
        expected.put("ACTIVE#DESCEND", EfsAgentState.STAND_BY);

        // Transition states
        expected.put("STARTING#ASCEND", EfsAgentState.STAND_BY);
        expected.put("STARTING#DESCEND", null);

        expected.put("ACTIVATING#ASCEND", EfsAgentState.ACTIVE);
        expected.put("ACTIVATING#DESCEND", null);

        expected.put("DEACTIVATING#ASCEND", null);
        expected.put("DEACTIVATING#DESCEND", EfsAgentState.STAND_BY);

        expected.put("STOPPING#ASCEND", null);
        expected.put("STOPPING#DESCEND", EfsAgentState.STOPPED);

        for (EfsAgentState state : EfsAgentState.values())
        {
            for (WorkflowDirection dir :
                    WorkflowDirection.values())
            {
                key = state.name() + "#" + dir.name();
                exp = expected.get(key);
                actual = state.getAdjacent(dir);

                assertThat(actual).isEqualTo(exp);
            }
        }
    } // end of getAdjacentMappingsTest()

    @Test
    public void isAdjacentEveryPairTest()
    {
        final EfsAgentState[] states = EfsAgentState.values();
        EfsAgentState up;
        EfsAgentState down;
        boolean expected;
        boolean actual;

        for (EfsAgentState a : states)
        {
            up = a.getAdjacent(WorkflowDirection.ASCEND);
            down = a.getAdjacent(WorkflowDirection.DESCEND);

            for (EfsAgentState b : states)
            {
                expected = (b == up) || (b == down);

                assertThat(a.isAdjacent(b)).isEqualTo(expected);
            }
        }
    } // end of isAdjacentEveryPairTest()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private void onActivatorEvent(final ActivatorEvent event)
    {}

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

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Throws an exception on transition.
     */
    private static final class ErrorAgent
        implements IEfsActivateAgent
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private final CountDownLatch mContinueSignal;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private ErrorAgent(final CountDownLatch signal)
        {
            mContinueSignal = signal;
        } // end of ExceptionAgent(CountDownLatch)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // IEfsActivateAgent Interface Implementation.
        //

        @Override
        public String name()
        {
            return (ERROR_AGENT_NAME);
        } // end of name()

        @Override
        public void startup()
        {
            throw (new RuntimeException("Oops!"));
        }

        @Override
        public void activate()
        {
            // Hang here until told to continue.
            try
            {
                mContinueSignal.await();
            }
            catch (InterruptedException interrupt)
            {}
        } // end of activate()

        @Override
        public void deactivate()
        {
            await().atLeast(WAIT_DURATION);
        } // end of deactivate()

        @Override
        public void stop()
        {}

        //
        // end of IEfsActivateAgent Interface Implementation.
        //-------------------------------------------------------
    } // end of class ErrorAgent
} // end of WorkflowFailureTest
