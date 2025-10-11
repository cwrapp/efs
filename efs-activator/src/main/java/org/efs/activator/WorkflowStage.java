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
import java.util.List;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import org.efs.activator.config.WorkflowStageConfig;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * A workflow stage contains one or more
 * {@link WorkflowStep steps} which take agent(s) from a start
 * state to end state. A stage may be executed completely in one
 * execution or executed step-by-step or partially executed
 * followed by a complete execution of its remaining steps.
 * <p>
 * Please see {@link org.efs.activator} for a detailed
 * description on {@code EfsActivator} and workflows.
 * </p>
 *
 * @see EfsActivator
 * @see Workflow
 * @see WorkflowStep
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class WorkflowStage
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger();

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * This stage's index within the workflow.
     */
    private final int mStageIndex;

    /**
     * Steps defining this workflow stage.
     */
    private final List<WorkflowStep> mSteps;

    /**
     * Number of steps in {@link #mSteps}.
     */
    private final int mStepCount;

    /**
     * Current step index. Set to {@link #mStepCount} when
     * this stage is not configured for step execution. This is
     * the initial value.
     */
    private int mCurrentStepIndex;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new workflow stage based on builder settings.
     * @param builder contains valid workflow stage settings.
     */
    private WorkflowStage(final Builder builder)
    {
        mStageIndex = builder.mStageIndex;
        mSteps = builder.mSteps;
        mStepCount = mSteps.size();
        mCurrentStepIndex = mStepCount;
    } // end of WorkflowStage(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns text containing workflow stage index and
     * subordinate steps. Example output is:
     * <pre><code>[agent=market-data-agent, transition=STAND_BY -> STOPPED, allowed time=PT0.5S</code></pre>
     * @return textual representation of this workflow step.
     */
    @Override
    public String toString()
    {
        int stepIndex = 0;
        final StringBuilder retval = new StringBuilder();

        retval.append("[index=").append(mStageIndex)
              .append(", steps={");

        for (WorkflowStep s : mSteps)
        {
            retval.append("\n  [").append(stepIndex)
                  .append("] ").append(s);
            ++stepIndex;
        }

        return (retval.append("\n}]").toString());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns this stage's index within the workflow.
     * @return workflow index for this stage.
     */
    public int stageIndex()
    {
        return (mStageIndex);
    } // end of stageIndex()

    /**
     * Returns number of steps in this workflow stage.
     * @return workflow stage's step count.
     */
    public int stepCount()
    {
        return (mStepCount);
    } // end of stepCount()

    /**
     * Validates if given step index is in-bounds.
     * @param stepIndex validate this step index.
     * @throws IndexOutOfBoundsException
     * if {@code stepIndex} is out-of-bounds.
     */
    /* package */ void validateStepIndex(final int stepIndex)
    {
        if (stepIndex < 0 && stepIndex < mStepCount)
        {
            throw (
                new IndexOutOfBoundsException(
                    String.format(
                        "step index %d is out of bounds",
                        stepIndex)));
        }
    } // end of validateStepIndex(int)

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets current step index to zero. This method must
     * be called prior to performing workflow execution via
     * {@link #executeNextStep(String, EfsActivator)} or
     * {@link #executeAllSteps(String, EfsActivator)}.
     *
     * @see #clearStepIndex()
     * @see #executeNextStep(String, EfsActivator)
     * @see #executeAllSteps(String, EfsActivator)
     */
    /* package */ void intialStepIndex()
    {
        mCurrentStepIndex = 0;
    } // end of intialStepIndex()

    /**
     * Sets step index to given value.
     * @param stepIndex step index.
     */
    /* package */ void setStepIndex(final int stepIndex)
    {
        mCurrentStepIndex = stepIndex;
    } // end of setStepIndex(int)

    /**
     * Sets current step index to {@link #stepCount()} which
     * terminates any in-progress step execution.
     */
    /* package */ void clearStepIndex()
    {
        mCurrentStepIndex = mStepCount;
    } // end of clearStepIndex()

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    /**
     * Returns a new {@code Builder} instance.
     * @return new {@code Builder} instance.
     */
    public static Builder builder()
    {
        return (new Builder());
    } // end of builder()

    /**
     * Executes current workflow step only and goes no further.
     * @param stageName name uniquely identifying this stage
     * within a workflow.
     * @param activator workflow activator.
     * @return {@code true} if all steps in this stage are now
     * executed.
     * @throws IllegalStateException
     * if this stage is not currently configured to activate
     * steps or execution fails.
     * @throws RuntimeException
     * if current workflow step execution fails. Contains
     * workflow step exception as cause.
     *
     * @see #setStepIndex(int)
     * @see #executeAllSteps(String, EfsActivator)
     */
    /* package */ boolean executeNextStep(final String stageName,
                                          final EfsActivator activator)
    {
        final WorkflowStep step = mSteps.get(mCurrentStepIndex);
        final String stepName =
            stageName + "-" + mCurrentStepIndex;

        sLogger.trace("{}: executing step {}.",
                      stageName,
                      mCurrentStepIndex);

        step.execute(stepName, activator);

        sLogger.trace("{}: step {} executed successfully.",
                      stageName,
                      mCurrentStepIndex);

        // If we arrived here, then the step executed
        // successfully. Advance to the next step.
        ++mCurrentStepIndex;

        return (mCurrentStepIndex == mStepCount);
    } // end of executeNextStep(EfsActivator)

    /**
     * Executes all remaining steps in this stage.
     * @param stageName name uniquely identifying this stage
     * within a workflow.
     * @param activator workflow activator.
     * @return {@code true} since all steps in this stage are
     * now executed.
     *
     * @see #setStepIndex(int)
     */
    /* package */ boolean executeAllSteps(final String stageName,
                                          final EfsActivator activator)
    {
        // Continue executing steps until there is a step
        // execution failure or all steps successfully executed.
        while (mCurrentStepIndex < mStepCount)
        {
            executeNextStep(stageName, activator);
        }

        return (true);
    } // end of executeAllSteps()

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Provides ability to programmatically define a
     * {@link WorkflowStage} instance. Requires definition of
     * stage index (within its parent {@link Workflow}) and
     * its child {@link WorkflowStep workflow steps}.
     * <p>
     * A workflow stage builder instance is accessed via
     * {@link WorkflowStage#builder()}
     * </p>
     * <p>
     * A builder may be used in combination with a loaded
     * typesafe {@link WorkflowStageConfig} bean to create a
     * workflow stage instance.
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

        /**
         * This stage's index within the parent workflow.
         */
        private int mStageIndex;

        /**
         * Workflow stage's steps.
         */
        private List<WorkflowStep> mSteps;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Private constructor to prevent {@code Builder}
         * instantiation outside of {@code builder()} method.
         */
        private Builder()
        {
            mStageIndex = -1;
        } // end of Builder()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets stage index within parent workflow.
         * @param index stage index.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code index} &lt; zero.
         */
        public Builder stageIndex(final int index)
        {
            if (index < 0)
            {
                throw (
                    new IllegalArgumentException(
                        "index < zero"));
            }

            mStageIndex = index;

            return (this);
        } // end of stageIndex(int)

        /**
         * Sets steps defining workflow stage.
         * @param steps steps in workflow stage.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code steps} is either {@code null} or an empty
         * list.
         */
        public Builder steps(final List<WorkflowStep> steps)
        {
            if (steps == null || steps.isEmpty())
            {
                throw (
                    new IllegalArgumentException(
                        "steps is either null or an empty list"));
            }

            mSteps = ImmutableList.copyOf(steps);

            return (this);
        } // end of steps(List<>)

        /**
         * Sets stage properties according to given workflow
         * stage configuration.
         * @param config workflow stage configuration.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if required non-{@code null} field is set to
         * {@code null}.
         * @throws IllegalArgumentException
         * if a field is set to an invalid value.
         */
        public Builder set(final WorkflowStageConfig config)
        {
            final ImmutableList.Builder<WorkflowStep> builder =
                ImmutableList.builder();

            // Convert workflow step configs list to workflow
            // steps list.
            config.getSteps()
                  .forEach(
                      c ->
                          builder.add(
                              (WorkflowStep.builder()).set(c)
                                                      .build()));

            return (this.steps(builder.build()));
        } // end of set(WorkflowStageConfig)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Returns new workflow stage based on this builder's
         * settings.
         * @return new workflow stage.
         * @throws ValidationException
         * if {@code this Builder} contains an incomplete or
         * invalid setting.
         */
        public WorkflowStage build()
        {
            final Validator problems = new Validator();

            problems.requireTrue((mStageIndex >= 0),
                                 "stageIndex",
                                 Validator.NOT_SET)
                    .requireNotNull(mSteps, "steps")
                    .throwException(WorkflowStage.class);

            return (new WorkflowStage(this));
        } // end of build()
    } // end of class Builder
} // end of class WorkflowStage
