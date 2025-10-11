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
package org.efs.activator.config;

import com.google.common.collect.ImmutableList;
import com.typesafe.config.ConfigException;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.activator.EfsAgentState;
import org.junit.jupiter.api.Test;

/**
 *
 * @author charlesr
 */

public final class WorkflowConfigTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String TEST_WORKFLOW_NAME =
        "test-workflow";
    private static final String TEST_AGENT_NAME = "test-agent";
    private static final Duration TIME_ALLOWED =
        Duration.ofSeconds(5L);

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void workflowStepConfigNullAgentName()
    {
        final String agentName = null;
        final WorkflowStepConfig config =
            new WorkflowStepConfig();

        try
        {
            config.setAgent(agentName);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'agent': name is either null or an empty string");
        }
    } // end of workflowStepConfigNullAgentName()

    @Test
    public void workflowStepConfigEmptyAgentName()
    {
        final String agentName = "";
        final WorkflowStepConfig config =
            new WorkflowStepConfig();

        try
        {
            config.setAgent(agentName);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'agent': name is either null or an empty string");
        }
    } // end of workflowStepConfigEmptyAgentName()

    @Test
    public void workflowStepConfigNullBeginState()
    {
        final EfsAgentState beginState = null;
        final WorkflowStepConfig config =
            new WorkflowStepConfig();

        try
        {
            config.setBeginState(beginState);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'beginState': state is null");
        }
    } // end of workflowStepConfigNullBeginState()

    @Test
    public void workflowStepConfigTransitionBeginState()
    {
        final EfsAgentState beginState = EfsAgentState.STARTING;
        final WorkflowStepConfig config =
            new WorkflowStepConfig();

        try
        {
            config.setBeginState(beginState);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'beginState': \"" +
                    beginState +
                    "\" is a transition state");
        }
    } // end of workflowStepConfigTransitionBeginState()

    @Test
    public void workflowStepConfigNullEndState()
    {
        final EfsAgentState endState = null;
        final WorkflowStepConfig config =
            new WorkflowStepConfig();

        try
        {
            config.setEndState(endState);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'endState': state is null");
        }
    } // end of workflowStepConfigNullEndState()

    @Test
    public void workflowStepConfigTransitionEndState()
    {
        final EfsAgentState endState = EfsAgentState.STARTING;
        final WorkflowStepConfig config =
            new WorkflowStepConfig();

        try
        {
            config.setEndState(endState);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'endState': \"" +
                    endState +
                    "\" is a transition state");
        }
    } // end of workflowStepConfigTransitionEndState()

    @Test
    public void workflowStepConfigNullTimeAllowed()
    {
        final Duration timeAllowed = null;
        final WorkflowStepConfig config =
            new WorkflowStepConfig();

        try
        {
            config.setAllowedTransitionTime(timeAllowed);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'allowedTransitionTime': timeAllowed is null");
        }
    } // end of workflowStepConfigNullTimeAllowed()

    @Test
    public void workflowStepConfigZeroTimeAllowed()
    {
        final Duration timeAllowed = Duration.ZERO;
        final WorkflowStepConfig config =
            new WorkflowStepConfig();

        try
        {
            config.setAllowedTransitionTime(timeAllowed);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'allowedTransitionTime': timeAllowed <= zero");
        }
    } // end of workflowStepConfigZeroTimeAllowed()

    @Test
    public void workflowStepConfigSuccess()
    {
        final String agentName = "test-agent";
        final EfsAgentState beginState = EfsAgentState.STOPPED;
        final EfsAgentState endState = EfsAgentState.STAND_BY;
        final Duration timeAllowed = Duration.ofMillis(200L);
        final String text =
            String.format(
                "[name=%s, transition=%s -> %s, time allowed=%s]",
                agentName,
                beginState,
                endState,
                timeAllowed);
        final WorkflowStepConfig config =
            new WorkflowStepConfig();

        config.setAgent(agentName);
        config.setBeginState(beginState);
        config.setEndState(endState);
        config.setAllowedTransitionTime(timeAllowed);

        assertThat(config.getAgent()).isEqualTo(agentName);
        assertThat(config.getBeginState()).isEqualTo(beginState);
        assertThat(config.getEndState()).isEqualTo(endState);
        assertThat(config.getAllowedTransitionTime())
            .isEqualTo(timeAllowed);
        assertThat(config.toString()).isEqualTo(text);
    } // end of workflowStepConfigSuccess()

    @Test
    public void workflowStageConfigNullSteps()
    {
        final List<WorkflowStepConfig> steps = null;
        final WorkflowStageConfig config =
            new WorkflowStageConfig();

        try
        {
            config.setSteps(steps);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'steps': steps is either null or empty");
        }
    } // end of workflowStageConfigNullSteps()

    @Test
    public void workflowStageConfigEmptySteps()
    {
        final List<WorkflowStepConfig> steps =
            ImmutableList.of();
        final WorkflowStageConfig config =
            new WorkflowStageConfig();

        try
        {
            config.setSteps(steps);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'steps': steps is either null or empty");
        }
    } // end of workflowStageConfigEmptySteps()

    @Test
    public void workflowStageConfigInvalidStep()
    {
        final List<WorkflowStepConfig> steps =
            ImmutableList.of(
                createStepConfig(TEST_AGENT_NAME,
                                 EfsAgentState.STOPPED,
                                 EfsAgentState.ACTIVE));
        final WorkflowStageConfig config =
            new WorkflowStageConfig();

        try
        {
            config.setSteps(steps);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'steps': step 0: STOPPED, ACTIVE are not adjacent");
        }
    } // end of workflowStageConfigInvalidStep()

    @Test
    public void workflowStageConfigSuccess()
    {
        final List<WorkflowStepConfig> steps =
            ImmutableList.of(
                createStepConfig(TEST_AGENT_NAME,
                                 EfsAgentState.STOPPED,
                                 EfsAgentState.STAND_BY));
        final String text =
            "    steps [\n      0 " +
            steps.get(0) +
            "\n    ]";
        final WorkflowStageConfig config =
            new WorkflowStageConfig();

        config.setSteps(steps);

        assertThat(config.getSteps()).containsAll(steps);
        assertThat(config.toString()).isEqualTo(text);
    } // end of workflowStageConfigSuccess()

    @Test
    public void workflowConfigNullName()
    {
        final String name = null;
        final WorkflowConfig config = new WorkflowConfig();

        try
        {
            config.setName(name);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'name': name is either null or an empty string");
        }
    } // end of workflowConfigNullName()

    @Test
    public void workflowConfigEmptyName()
    {
        final String name = "";
        final WorkflowConfig config = new WorkflowConfig();

        try
        {
            config.setName(name);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'name': name is either null or an empty string");
        }
    } // end of workflowConfigEmptyName()

    @Test
    public void workflowConfigNullStages()
    {
        final List<WorkflowStageConfig> stages = null;
        final WorkflowConfig config = new WorkflowConfig();

        try
        {
            config.setStages(stages);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'stages': stages is either null or an empty list");
        }
    } // end of workflowConfigNullStages()

    @Test
    public void workflowConfigEmptyStages()
    {
        final List<WorkflowStageConfig> stages =
            ImmutableList.of();
        final WorkflowConfig config = new WorkflowConfig();

        try
        {
            config.setStages(stages);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'stages': stages is either null or an empty list");
        }
    } // end of workflowConfigEmptyStages()

    @Test
    public void workflowConfigSuccess()
    {
        final String wfName = "test-workflow";
        final List<WorkflowStepConfig> steps =
            ImmutableList.of(
                createStepConfig(TEST_AGENT_NAME,
                                 EfsAgentState.STOPPED,
                                 EfsAgentState.STAND_BY));
        final List<WorkflowStageConfig> stages =
            ImmutableList.of(createStageConfig(steps));
        final String text =
            "  name: " +
            wfName +
            "\n  stages [\n     0 " +
            stages.get(0) +
            "\n  ]";
        final WorkflowConfig config = new WorkflowConfig();

        config.setName(wfName);
        config.setStages(stages);

        assertThat(config.getName()).isEqualTo(wfName);
        assertThat(config.getStages()).containsAll(stages);
        assertThat(config.toString()).isEqualTo(text);
    } // end of workflowConfigSuccess()

    @Test
    public void activatorConfigNullWorkflows()
    {
        final List<WorkflowConfig> workflows = null;
        final EfsActivatorConfig config =
            new EfsActivatorConfig();

        try
        {
            config.setWorkflows(workflows);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'workflows': workflows is either null or an empty list");
        }
    } // end of activatorConfigNullWorkflows()

    @Test
    public void activatorConfigEmptyWorkflows()
    {
        final List<WorkflowConfig> workflows =
            ImmutableList.of();
        final EfsActivatorConfig config =
            new EfsActivatorConfig();

        try
        {
            config.setWorkflows(workflows);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .hasMessage(
                    "Invalid value at 'workflows': workflows is either null or an empty list");
        }
    } // end of activatorConfigEmptyWorkflows()

    @Test
    public void activatorConfigSuccess()
    {
        final List<WorkflowStepConfig> steps =
            ImmutableList.of(
                createStepConfig(TEST_AGENT_NAME,
                                 EfsAgentState.STOPPED,
                                 EfsAgentState.STAND_BY));
        final List<WorkflowStageConfig> stages =
            ImmutableList.of(createStageConfig(steps));
        final List<WorkflowConfig> workflows =
            ImmutableList.of(
                createWorkflowConfig(
                    TEST_WORKFLOW_NAME, stages));
        final String text =
            "workflows [\n  0 " +
            workflows.get(0) +
            "\n]";
        final EfsActivatorConfig config =
            new EfsActivatorConfig();

        config.setWorkflows(workflows);

        assertThat(config.getWorkflows()).containsAll(workflows);
        assertThat(config.toString()).isEqualTo(text);
    } // end of activatorConfigSuccess()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private static WorkflowStepConfig createStepConfig(final String agentName,
                                                       final EfsAgentState beginState,
                                                       final EfsAgentState endState)
    {
        final WorkflowStepConfig retval =
            new WorkflowStepConfig();

        retval.setAgent(agentName);
        retval.setBeginState(beginState);
        retval.setEndState(endState);
        retval.setAllowedTransitionTime(TIME_ALLOWED);

        return (retval);
    } // end of createStepConfig(...)

    private static WorkflowStageConfig createStageConfig(final List<WorkflowStepConfig> steps)
    {
        final WorkflowStageConfig retval =
            new WorkflowStageConfig();

        retval.setSteps(steps);

        return (retval);
    } // end of createStageConfig(List<>)

    private static WorkflowConfig createWorkflowConfig(final String name,
                                                       final List<WorkflowStageConfig> stages)
    {
        final WorkflowConfig retval = new WorkflowConfig();

        retval.setName(name);
        retval.setStages(stages);

        return (retval);
    } // end of createWorkflowConfig(String, List<>)
} // end of class WorkflowConfigTest
