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
import net.sf.eBus.util.MultiKey2;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 * Exercises workflow object creation.
 *
 * @author charlesr
 */

public class WorkflowBuildTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final Duration TRANSITION_TIME =
        Duration.ofMillis(500L);
    private static final String DUPLICATE_FILE_NAME =
        "./src/test/resources/duplicate-workflows.conf";

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void buildStepNullAgentName()
    {
        final String agentName = null;
        final WorkflowStep.Builder builder =
            WorkflowStep.builder();

        try
        {
            builder.agentName(agentName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "name is either null or an empty string");
        }
    } // end of buildStepNullAgentName()

    @Test
    public void buildStepEmptyAgentName()
    {
        final String agentName = "";
        final WorkflowStep.Builder builder =
            WorkflowStep.builder();

        try
        {
            builder.agentName(agentName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "name is either null or an empty string");
        }
    } // end of buildStepEmptyAgentName()

    @Test
    public void buildStepNullBeginState()
    {
        final EfsAgentState beginState = null;
        final WorkflowStep.Builder builder =
            WorkflowStep.builder();

        try
        {
            builder.beginState(beginState);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex).hasMessage("state is null");
        }
    } // end of buildStepNullBeginState()

    @Test
    public void buildStepNullEndState()
    {
        final EfsAgentState endState = null;
        final WorkflowStep.Builder builder =
            WorkflowStep.builder();

        try
        {
            builder.endState(endState);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex).hasMessage("state is null");
        }
    } // end of buildStepNullEndState()

    @Test
    public void buildStepNullTransitionTime()
    {
        final Duration time = null;
        final WorkflowStep.Builder builder =
            WorkflowStep.builder();

        try
        {
            builder.allowedTransitionTime(time);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex).hasMessage("time is null");
        }
    } // end of buildStepNullTransitionTime()

    @Test
    public void buildStepZeroTransitionTime()
    {
        final Duration time = Duration.ZERO;
        final WorkflowStep.Builder builder =
            WorkflowStep.builder();

        try
        {
            builder.allowedTransitionTime(time);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage("time <= zero");
        }
    } // end of buildStepZeroTransitionTime()

    @Test
    public void buildStepInvalidSettings()
    {
        final List<MultiKey2<String, String>> expected =
            ImmutableList.of(
                new MultiKey2<>("agentName", Validator.NOT_SET),
                new MultiKey2<>("beginState", Validator.NOT_SET),
                new MultiKey2<>("endState", Validator.NOT_SET),
                new MultiKey2<>("allowedTransitionTime",
                                Validator.NOT_SET),
                new MultiKey2<>("beginState",
                                "not adjacent to null"));
        final WorkflowStep.Builder builder =
            WorkflowStep.builder();

        try
        {
            builder.build();
        }
        catch (ValidationException vex)
        {
            final List<MultiKey2<String, String>> problems =
                vex.problems();

            assertThat(problems).containsAll(expected);
        }
    } // end of buildStepInvalidSettings()

    @Test
    public void buildStepSuccess()
    {
        final String agentName = "test-agent";
        final EfsAgentState beginState = EfsAgentState.STOPPED;
        final EfsAgentState endState = EfsAgentState.STAND_BY;
        final Duration transitionTime =
            Duration.ofMillis(200L);
        final String text =
            String.format(
                "[agent=%s, transition=%s -> %s, allowed time=%s]",
                agentName,
                beginState,
                endState,
                transitionTime);
        final WorkflowStep.Builder builder =
            WorkflowStep.builder();
        final WorkflowStep step =
            builder.agentName(agentName)
                   .beginState(beginState)
                   .endState(endState)
                   .allowedTransitionTime(transitionTime)
                   .build();

        assertThat(step.agentName()).isEqualTo(agentName);
        assertThat(step.beginState()).isEqualTo(beginState);
        assertThat(step.endState()).isEqualTo(endState);
        assertThat(step.allowedTransitionTime())
            .isEqualTo(transitionTime);
        assertThat(step.toString()).isEqualTo(text);
    } // end of buildStepSuccess()

    @Test
    public void buildStateInvalidBeginEndStates()
    {
        final String agentName = "test-agent";
        final EfsAgentState beginState = EfsAgentState.STOPPED;
        final EfsAgentState endState = EfsAgentState.ACTIVE;
        final List<MultiKey2<String, String>> expected =
            ImmutableList.of(
                new MultiKey2<>("beginState",
                                "not adjacent to " + endState));
        final WorkflowStep.Builder builder =
            WorkflowStep.builder();

        try
        {
            builder.agentName(agentName)
                   .beginState(beginState)
                   .endState(endState)
                   .allowedTransitionTime(TRANSITION_TIME)
                   .build();
        }
        catch (ValidationException vex)
        {
            final List<MultiKey2<String, String>> problems =
                vex.problems();

            assertThat(problems).containsAll(expected);
        }
    } // end of buildStateInvalidBeginEndStates()

    @Test
    public void buildStateSuccess()
    {
        final String agentName = "test-agent";
        final EfsAgentState beginState = EfsAgentState.STOPPED;
        final EfsAgentState endState = EfsAgentState.STAND_BY;
        final WorkflowStep.Builder builder =
            WorkflowStep.builder();
        final WorkflowStep wfStep =
            builder.agentName(agentName)
                   .beginState(beginState)
                   .endState(endState)
                   .allowedTransitionTime(TRANSITION_TIME)
                   .build();

        assertThat(wfStep.agentName()).isEqualTo(agentName);
        assertThat(wfStep.beginState()).isEqualTo(beginState);
        assertThat(wfStep.endState()).isEqualTo(endState);
        assertThat(wfStep.allowedTransitionTime())
            .isEqualTo(TRANSITION_TIME);
    } // end of buildStateSuccess()

    @Test
    public void buildStageNegativeIndex()
    {
        final int stageIndex = -1;
        final WorkflowStage.Builder builder =
            WorkflowStage.builder();

        try
        {
            builder.stageIndex(stageIndex);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage("index < zero");
        }
    } // end of buildStageNegativeIndex()

    @Test
    public void buildStageNullSteps()
    {
        final List<WorkflowStep> steps = null;
        final WorkflowStage.Builder builder =
            WorkflowStage.builder();

        try
        {
            builder.steps(steps);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "steps is either null or an empty list");
        }
    } // end of buildStageNullSteps()

    @Test
    public void buildStageEmptySteps()
    {
        final List<WorkflowStep> steps = ImmutableList.of();
        final WorkflowStage.Builder builder =
            WorkflowStage.builder();

        try
        {
            builder.steps(steps);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "steps is either null or an empty list");
        }
    } // end of buildStageEmptySteps()

    @Test
    public void buildStageInvalidSettings()
    {
        final List<MultiKey2<String, String>> expected =
            ImmutableList.of(
                new MultiKey2<>("stageIndex", Validator.NOT_SET),
                new MultiKey2<>("steps", Validator.NOT_SET));
        final WorkflowStage.Builder builder =
            WorkflowStage.builder();

        try
        {
            builder.build();
        }
        catch (ValidationException vex)
        {
            final List<MultiKey2<String, String>> problems =
                vex.problems();

            assertThat(problems).containsAll(expected);
        }
    } // end of buildStageInvalidSettings()

    @Test
    public void buildStageSuccess()
    {
        final String agentName = "test-agent";
        final EfsAgentState beginState = EfsAgentState.STAND_BY;
        final EfsAgentState endState = EfsAgentState.STOPPED;
        final int stageIndex = 4;
        final List<WorkflowStep> steps =
            ImmutableList.of(createStep(agentName,
                                        beginState,
                                        endState));
        final String text =
            String.format(
                "[index=%d, steps={%n  [0] %s%n}]",
                stageIndex,
                steps.get(0));
        final WorkflowStage.Builder builder =
            WorkflowStage.builder();
        final WorkflowStage stage =
            builder.stageIndex(stageIndex)
                   .steps(steps)
                   .build();

        assertThat(stage).isNotNull();
        assertThat(stage.stageIndex()).isEqualTo(stageIndex);
        assertThat(stage.stepCount()).isEqualTo(steps.size());
        assertThat(stage.toString()).isEqualTo(text);
    } // end of buildStageSuccess()

    @Test
    public void buildWorkflowNullName()
    {
        final String name = null;
        final Workflow.Builder builder = Workflow.builder();

        try
        {
            builder.name(name);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "name is either null or an empty string");
        }
    } // end of buildWorkflowNullName()

    @Test
    public void buildWorkflowEmptyName()
    {
        final String name = "";
        final Workflow.Builder builder = Workflow.builder();

        try
        {
            builder.name(name);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "name is either null or an empty string");
        }
    } // end of buildWorkflowEmptyName()

    @Test
    public void buildWorkflowNullStages()
    {
        final List<WorkflowStage> stages = null;
        final Workflow.Builder builder = Workflow.builder();

        try
        {
            builder.stages(stages);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "stages is either null or an empty list");
        }
    } // end of buildWorkflowNullStages()

    @Test
    public void buildWorkflowEmptyStages()
    {
        final List<WorkflowStage> stages = ImmutableList.of();
        final Workflow.Builder builder = Workflow.builder();

        try
        {
            builder.stages(stages);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "stages is either null or an empty list");
        }
    } // end of buildWorkflowEmptyStages()

    @Test
    public void buildWorkflowInvalidSettings()
    {
        final List<MultiKey2<String, String>> expected =
            ImmutableList.of(
                new MultiKey2<>("name", Validator.NOT_SET),
                new MultiKey2<>("stages", Validator.NOT_SET));
        final Workflow.Builder builder = Workflow.builder();

        try
        {
            builder.build();
        }
        catch (ValidationException vex)
        {
            final List<MultiKey2<String, String>> problems =
                vex.problems();

            assertThat(problems).containsAll(expected);
        }
    } // end of buildWorkflowInvalidSettings()

    @Test
    public void buildWorkflowSuccess()
    {
        final String wfName = "simple-stop";
        final String agentName = "test-agent";
        final EfsAgentState beginState = EfsAgentState.STAND_BY;
        final EfsAgentState endState = EfsAgentState.STOPPED;
        final int stageIndex = 4;
        final List<WorkflowStep> steps =
            ImmutableList.of(createStep(agentName,
                                        beginState,
                                        endState));
        final List<WorkflowStage> stages =
            ImmutableList.of(createStage(stageIndex, steps));
        final String text =
            String.format(
                "[name=%s, stages={%n  [0] %s%n}]",
                wfName,
                stages.get(0));
        final Workflow.Builder builder = Workflow.builder();
        final Workflow wf = builder.name(wfName)
                                   .stages(stages)
                                   .build();

        assertThat(wf).isNotNull();
        assertThat(wf.name()).isEqualTo(wfName);
        assertThat(wf.stageCount()).isEqualTo(stages.size());
        assertThat(wf.toString()).isEqualTo(text);
    } // end of buildWorkflowSuccess()

    @Test
    public void buildActivatorNullWorkflows()
    {
        final List<Workflow> workflows = null;
        final EfsActivator.Builder builder =
            EfsActivator.builder();

        try
        {
            builder.workflows(workflows);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "workflows is either null or an empty list");
        }
    } // end of buildActivatorNullWorkflows()

    @Test
    public void buildActivatorEmptyWorkflows()
    {
        final List<Workflow> workflows = ImmutableList.of();
        final EfsActivator.Builder builder =
            EfsActivator.builder();

        try
        {
            builder.workflows(workflows);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "workflows is either null or an empty list");
        }
    } // end of buildActivatorEmptyWorkflows()

    @Test
    public void buildActivatorDuplicateWorkflowNames()
    {
        try
        {
            EfsActivator.loadActivator(DUPLICATE_FILE_NAME);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "workflows contains duplicate names: test-workflow");
        }
    } // end of buildActivatorDuplicateWorkflowNames()

    @Test
    public void buildActivatorInvalidSettings()
    {
        final List<MultiKey2<String, String>> expected =
            ImmutableList.of(
                new MultiKey2<>("workflows", Validator.NOT_SET));
        final EfsActivator.Builder builder =
            EfsActivator.builder();

        try
        {
            builder.build();
        }
        catch (ValidationException vex)
        {
            final List<MultiKey2<String, String>> problems =
                vex.problems();

            assertThat(problems).containsAll(expected);
        }
    } // end of buildActivatorInvalidSettings()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private static WorkflowStep createStep(final String agentName,
                                           final EfsAgentState beginState,
                                           final EfsAgentState endState)
    {
        final WorkflowStep.Builder builder =
            WorkflowStep.builder();

        return (builder.agentName(agentName)
                       .beginState(beginState)
                       .endState(endState)
                       .allowedTransitionTime(TRANSITION_TIME)
                       .build());
    } // end of createStep(...)

    private static WorkflowStage createStage(final int stageIndex,
                                             final List<WorkflowStep> steps)
    {
        final WorkflowStage.Builder builder =
            WorkflowStage.builder();

        return (builder.stageIndex(stageIndex)
                       .steps(steps)
                       .build());
    } // end of createStage(...)
} // end of class WorkflowBuildTest
