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

import com.google.common.base.Strings;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import org.efs.activator.EfsActivator;
import org.efs.activator.EfsAgentState;
import org.efs.dispatcher.IEfsAgent;
import org.efs.event.IEfsEvent;

/**
 * As {@link EfsActivator} steps through its workflow, its
 * publishes two {@code ActivatorEvent}s for each step:
 * <ol>
 *   <li>
 *     An {@link StepState#IN_PROGRESS in-progress} transition
 *     with a zero transition duration.
 *   </li>
 *   <li>
 *     Either a
 *     {@link StepState#COMPLETED_SUCCESS successful completion}
 *     or
 *     {@link StepState#COMPLETED_FAILED failed completion}.
 *     If failed, then contains exception causing the failure.
 *     In both cases, transition execution time is provided.
 *   </li>
 * </ol>
 * <p>
 * An activator event contains the following fields:
 * </p>
 * <ul>
 *   <li>
 *     Unique name of agent being transition from a begin state
 *     to end state.
 *   </li>
 *   <li>
 *     Name of workflow step being performed.
 *   </li>
 *   <li>
 *     Defines workflow step state:
 *     {@link StepState#IN_PROGRESS in-progress},
 *     {@link StepState#COMPLETED_SUCCESS completed successfully},
 *     and
 *     {@link StepState#COMPLETED_FAILED completed but failed}.
 *   </li>
 *   <li>
 *     Agent transition initial state.
 *   </li>
 *   <li>
 *     Agent transition target final state.
 *   </li>
 *   <li>
 *     Agent transition duration. Will be
 *     {@link Duration#ZERO zero} for an
 *     {@link StepState#IN_PROGRESS in-progress} step.
 *   </li>
 *   <li>
 *     Exception associated with a
 *     {@link StepState#COMPLETED_FAILED failed agent transition}.
 *   </li>
 * </ul>
 * <p>
 * An {@link IEfsAgent agent} wishing to receive activator
 * events should
 * {@link EfsActivator#registerListener(Consumer, IEfsAgent) register}
 * with the activator executing the workflows.
 * </p>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class ActivatorEvent
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member enums.
//

    /**
     * Workflow step states are in-progress, completed (success),
     * and completed (failed).
     */
    public enum StepState
    {
        /**
         * Workflow step is in-progress.
         */
        IN_PROGRESS,

        /**
         * Workflow step successfully completed.
         */
        COMPLETED_SUCCESS,

        /**
         * Workflow step execution failed.
         */
        COMPLETED_FAILED
    } // end of enum StepState

//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Exception did not contain a message.
     */
    public static final String NO_MESSAGE = "(no message)";

    /**
     * Common failure reason.
     */
    public static final String NULL_STATE = "state is null";

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * {@code IEfsActivateAgent} name.
     */
    private final String mAgentName;

    /**
     * Transition occurred due to this workflow step.
     */
    private final String mStepName;

    /**
     * Step's transition state.
     */
    private final StepState mStepState;

    /**
     * {@code IEfsActivateAgent} transition initial state.
     */
    private final EfsAgentState mInitialState;

    /**
     * {@code IEfsActivateAgent} transition final state.
     */
    private final EfsAgentState mFinalState;

    /**
     * Transition took this long to complete. Will be zero for an
     * in-progress transition.
     */
    private final Duration mDuration;

    /**
     * Optional getException associated with a failed transition
 attempt.
     */
    @Nullable private final Throwable mException;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new activator event based on the given builder
     * properties.
     * @param builder contains event properties.
     */
    private ActivatorEvent(final Builder builder)
    {
        mAgentName = builder.mAgentName;
        mStepName = builder.mStepName;
        mStepState = builder.mStepState;
        mInitialState = builder.mInitialState;
        mFinalState = builder.mFinalState;
        mDuration = builder.mDuration;
        mException = builder.mException;
    } // end of ActivatorEvent(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns {@code true} if {@code o} is a
     * non-{@code null ActivorEvent} instance whose agent name,
     * step name, step state, initial and final states are
     * equal. This method ignores the duration and optional
     * exception associated with the event.
     * <p>
     * This equals implementation is designed to facilitate
     * units tests only.
     * </p>
     * @param o comparison object.
     * @return {@code true} if {@code this ActivatorEvent} equals
     * {@code o} and {@code false} otherwise.
     */
    @Override
    public boolean equals(final Object o)
    {
        boolean retcode = (this == o);

        if (!retcode && o instanceof ActivatorEvent)
        {
            final ActivatorEvent ae = (ActivatorEvent) o;

            // Leaves duration out of equality since they
            // may be different even though all other values are
            // equal.
            retcode =
                (Objects.equals(mAgentName, ae.mAgentName) &&
                 Objects.equals(mStepName, ae.mStepName) &&
                 mStepState == ae.mStepState &&
                 mInitialState == ae.mInitialState &&
                 mFinalState == ae.mFinalState);
        }

        return (retcode);
    } // end of equals(Object)

    /**
     * Activator event hash code based on unique agent name,
     * workflow step name, workflow step state, agent current
     * state, and agent target final state.
     * @return integer hash value for this activator event.
     */
    @Override
    public int hashCode()
    {
        return (Objects.hash(mAgentName,
                             mStepName,
                             mStepState,
                             mInitialState,
                             mFinalState));
    } // end of hashCode()

    /**
     * Returns a single text line containing agent name, initial
     * state, and final state.
     * @return activator event as text.
     */
    @Override
    public String toString()
    {
        final StringBuilder output = new StringBuilder();

        output.append("[agent=").append(mAgentName)
              .append(", step=").append(mStepName)
              .append(" (").append(mStepState)
              .append("), initial state=").append(mInitialState)
              .append(", final state=").append(mFinalState)
              .append(", duration=").append(mDuration);

        // Is there an getException to report?
        if (mStepState == StepState.COMPLETED_FAILED &&
            mException != null)
        {
            // Yes. Add getException message to output.
            String exMessage = mException.getMessage();

            if (Strings.isNullOrEmpty(exMessage))
            {
                exMessage = NO_MESSAGE;
            }

            output.append(", exception=").append(exMessage);
        }

        return (output.append(']').toString());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns efs agent name.
     * @return agent name.
     */
    public String getAgentName()
    {
        return (mAgentName);
    } // end of getAgentName()

    /**
     * Returns workflow step name.
     * @return workflow step name.
     */
    public String getStepName()
    {
        return (mStepName);
    } // end of getStepName()

    /**
     * Returns workflow step's state.
     * @return workflow step state.
     */
    public StepState getStepState()
    {
        return (mStepState);
    } // end of getStepState()

    /**
     * Returns agent initial state.
     * @return agent initial state.
     */
    public EfsAgentState getInitialState()
    {
        return (mInitialState);
    } // end of getInitialState()

    /**
     * Returns target agent final state.
     * @return agent final state.
     */
    public EfsAgentState getFinalState()
    {
        return (mFinalState);
    } // end of getFinalState()

    /**
     * Returns allowed transition time.
     * @return allowed transition time.
     */
    public Duration getDuration()
    {
        return (mDuration);
    } // end of getDuration()

    /**
     * Returns optional getException associated with a failed step.
     * @return getException associated with failed step.
     */
    @Nullable public Throwable getException()
    {
        return (mException);
    } // end of getException()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Returns a new activator event builder instance.
     * @return new activator event builder.
     */
    public static Builder builder()
    {
        return (new Builder());
    } // end of builder()

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Builder class used to create an activator event. A
     * builder instance guarantees that only correctly configured
     * {@code ActivatorEvent} instances are created.
     * <p>
     * Call {@link ActivatorEvent#builder()} to acquire an
     * {@code ActivatorEvent.Builder} instance.
     * </p>
     */
    public static final class Builder
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private String mAgentName;
        private String mStepName;
        private StepState mStepState;
        private EfsAgentState mInitialState;
        private EfsAgentState mFinalState;
        private Duration mDuration;
        @Nullable private Throwable mException;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates a new builder instance for activator event.
         */
        private Builder()
        {}

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets {@code IEfsActivateAgent} unique name.
         * @param name agent name.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code name} is either {@code null} or an empty
         * string.
         */
        public Builder agentName(final String name)
        {
            if (Strings.isNullOrEmpty(name))
            {
                throw (
                    new IllegalArgumentException(
                        "name is either null or an empty string"));
            }

            mAgentName = name;

            return (this);
        } // end of agentName(String)

        /**
         * Sets workflow step name.
         * @param name step name.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code name} is either {@code null} or an empty
         * string.
         */
        public Builder stepName(final String name)
        {
            if (Strings.isNullOrEmpty(name))
            {
                throw (
                    new IllegalArgumentException(
                        "name is either null or an empty string"));
            }

            mStepName = name;

            return (this);
        } // end of stepName(String)

        /**
         * Sets workflow step state.
         * @param state step state.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code state} is {@code null}.
         */
        public Builder stepState(final StepState state)
        {
            mStepState =
                Objects.requireNonNull(state, NULL_STATE);

            return (this);
        } // end of stepState(StepState)

        /**
         * Sets transition initial state.
         * @param state transition initial state.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code state} is {@code null}.
         */
        public Builder initialState(final EfsAgentState state)
        {
            mInitialState =
                Objects.requireNonNull(state, NULL_STATE);

            return (this);
        } // end of initialState(EfsAgentState)

        /**
         * Sets transition final state.
         * @param state transition final state.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code state} is {@code null}.
         */
        public Builder finalState(final EfsAgentState state)
        {
            mFinalState =
                Objects.requireNonNull(state, NULL_STATE);

            return (this);
        } // end of finalState(EfsAgentState)

        /**
         * Sets transition duration. if step state is
         * {@link StepState#IN_PROGRESS}, then duration is
         * automatically set to {@link Duration#ZERO}.
         * @param duration transition duration.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code duration} is {@code null}.
         * @throws IllegalArgumentException
         * if {@code duration} &lt; zero.
         */
        public Builder duration(final Duration duration)
        {
            Objects.requireNonNull(duration, "duration is null");

            if (duration.isNegative())
            {
                throw (
                    new IllegalArgumentException(
                        "duration < zero"));
            }

            mDuration = duration;

            return (this);
        } // end of duration(Duration)

        /**
         * Sets getException associated with a failed step
 execution.
         * @param tex step execution getException.
         * @return {@code this Builder} instance.
         */
        public Builder exception(@Nullable final Throwable tex)
        {
            mException = tex;

            return (this);
        } // end of getException(final Throwable tex)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Returns a newly created activator event based on this
         * builder's properties.
         * @return new target activator event instance.
         * @throws ValidationException
         * if this builder's properties are not set to valid
         * values.
         */
        public final ActivatorEvent build()
        {
            final Validator problems = new Validator();

            // If this is an in-progress duration, set duration
            // to zero.
            if (mStepState == StepState.IN_PROGRESS)
            {
                mDuration = Duration.ZERO;
            }

            problems.requireNotNull(mAgentName,
                                    "agentName")
                    .requireNotNull(mStepName,
                                    "stepName")
                    .requireNotNull(mStepState,
                                    "stepState")
                    .requireNotNull(mInitialState,
                                    "initialState")
                    .requireNotNull(mFinalState,
                                    "finalState")
                    .requireNotNull(mDuration,
                                    "duration")
                    .throwException(ActivatorEvent.class);

            return (new ActivatorEvent(this));
        } // end of build()
    } // end of class Builder
} // end of class ActivatorEvent
