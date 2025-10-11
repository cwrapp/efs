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
package org.efs.activator.event;

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import net.sf.eBus.util.MultiKey2;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.activator.EfsAgentState;
import org.efs.activator.event.ActivatorEvent.StepState;
import org.junit.jupiter.api.Test;

/**
 *
 * @author charlesr
 */

public final class ActivatorEventTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String AGENT_NAME = "test-agent";
    private static final String STEP_NAME = "step-1";
    private static final StepState STEP_STATE =
        StepState.COMPLETED_SUCCESS;
    private static final EfsAgentState INITIAL_STATE =
        EfsAgentState.STAND_BY;
    private static final EfsAgentState FINAL_STATE =
        EfsAgentState.ACTIVE;
    private static final Duration STEP_DURATION =
        Duration.ofNanos(150_000L);

    //-----------------------------------------------------------
    // Statics.
    //

    private static final ActivatorEvent sTestEvent =
        createEvent(AGENT_NAME,
                    STEP_NAME,
                    STEP_STATE,
                    INITIAL_STATE,
                    FINAL_STATE);

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void buildNullAgentName()
    {
        final String agentName = null;
        final ActivatorEvent.Builder builder =
            ActivatorEvent.builder();

        try
        {
            builder.agentName(agentName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "name is either null or an empty string");
        }
    } // end of buildNullAgentName()

    @Test
    public void buildEmptyAgentName()
    {
        final String agentName = "";
        final ActivatorEvent.Builder builder =
            ActivatorEvent.builder();

        try
        {
            builder.agentName(agentName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "name is either null or an empty string");
        }
    } // end of buildEmptyAgentName()

    @Test
    public void buildNullStepName()
    {
        final String stepName = null;
        final ActivatorEvent.Builder builder =
            ActivatorEvent.builder();

        try
        {
            builder.stepName(stepName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "name is either null or an empty string");
        }
    } // end of buildNullStepName()

    @Test
    public void buildEmptyStepName()
    {
        final String stepName = null;
        final ActivatorEvent.Builder builder =
            ActivatorEvent.builder();

        try
        {
            builder.stepName(stepName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(
                "name is either null or an empty string");
        }
    } // end of buildEmptyStepName()

    @Test
    public void buildNegativeDuration()
    {
        final Duration duration = Duration.ofMillis(-1L);
        final ActivatorEvent.Builder builder =
            ActivatorEvent.builder();

        try
        {
            builder.duration(duration);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage("duration < zero");
        }
    } // end of buildNegativeDuration()

    @Test
    public void buildInvalidSettings()
    {
        final List<MultiKey2<String, String>> expected =
            ImmutableList.of(
                new MultiKey2<>("agentName", Validator.NOT_SET),
                new MultiKey2<>("stepName", Validator.NOT_SET),
                new MultiKey2<>("stepState", Validator.NOT_SET),
                new MultiKey2<>("initialState",
                                Validator.NOT_SET),
                new MultiKey2<>("finalState",
                                Validator.NOT_SET),
                new MultiKey2<>("duration",
                                Validator.NOT_SET));
        final ActivatorEvent.Builder builder =
            ActivatorEvent.builder();

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
    } // end of buildInvalidSettings()

    @Test
    public void buildSuccess()
    {
        final String agentName = "test-agent";
        final String stepName = "step-0";
        final StepState stepState = StepState.COMPLETED_FAILED;
        final EfsAgentState initialState =
            EfsAgentState.STAND_BY;
        final EfsAgentState finalState = EfsAgentState.STAND_BY;
        final Duration duration = Duration.ofMillis(5L);
        final Exception exception =
            new RuntimeException();
        final String text =
            String.format(
                "[agent=%s, step=%s (%s), initial state=%s, final state=%s, duration=%s, exception=%s]",
                agentName,
                stepName,
                stepState,
                initialState,
                finalState,
                duration,
                ActivatorEvent.NO_MESSAGE);
        final int hashcode = Objects.hash(agentName,
                                          stepName,
                                          stepState,
                                          initialState,
                                          finalState);
        final ActivatorEvent.Builder builder =
            ActivatorEvent.builder();
        final ActivatorEvent event =
            builder.agentName(agentName)
                   .stepName(stepName)
                   .stepState(stepState)
                   .initialState(initialState)
                   .finalState(finalState)
                   .duration(duration)
                   .exception(exception)
                   .build();

        assertThat(event.getAgentName()).isEqualTo(agentName);
        assertThat(event.getStepName()).isEqualTo(stepName);
        assertThat(event.getStepState()).isEqualTo(stepState);
        assertThat(event.getInitialState())
            .isEqualTo(initialState);
        assertThat(event.getFinalState()).isEqualTo(finalState);
        assertThat(event.getDuration()).isEqualTo(duration);
        assertThat(event.getException()).isEqualTo(exception);
        assertThat(event.hashCode()).isEqualTo(hashcode);
        assertThat(event.toString()).isEqualTo(text);
    } // end of buildSuccess()

    @Test
    public void equalsNull()
    {
        final Object event = null;

        assertThat(sTestEvent).isNotEqualTo(event);
    } // end of equalsNull()

    @Test
    public void equalsBoolean()
    {
        final Object event = Boolean.TRUE;

        assertThat(sTestEvent).isNotEqualTo(event);
    } // end of equalsBoolean()

    @Test
    public void equalsSelf()
    {
        final Object event = sTestEvent;

        assertThat(sTestEvent).isEqualTo(event);
    } // end of equalsSelf()

    @Test
    public void equalsDifferent()
    {
        final Object event = createEvent(AGENT_NAME,
                                         STEP_NAME,
                                         STEP_STATE,
                                         INITIAL_STATE,
                                         EfsAgentState.STOPPED);

        assertThat(sTestEvent).isNotEqualTo(event);
    } // end of equalsDifferent()

    @Test
    public void equalsSame()
    {
        final Object event = createEvent(AGENT_NAME,
                                         STEP_NAME,
                                         STEP_STATE,
                                         INITIAL_STATE,
                                         FINAL_STATE);

        assertThat(sTestEvent).isEqualTo(event);
    } // end of equalsSame()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private static ActivatorEvent createEvent(final String agentName,
                                              final String stepName,
                                              final StepState stepState,
                                              final EfsAgentState initialState,
                                              final EfsAgentState finalState)
    {
        final ActivatorEvent.Builder builder =
            ActivatorEvent.builder();

        return (builder.agentName(agentName)
                       .stepName(stepName)
                       .stepState(stepState)
                       .initialState(initialState)
                       .finalState(finalState)
                       .duration(STEP_DURATION)
                       .build());
    } // end of createEvent(...)
} // end of class ActivatorEventTest
