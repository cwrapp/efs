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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.util.List;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import org.efs.activator.config.WorkflowConfig;
import org.efs.activator.config.WorkflowStageConfig;

/**
 * A workflow contains one or more
 * {@link WorkflowStage workflow stages} which take
 * {@link IEfsActivateAgent agent}(s)
 * from a start state to an end state.
 * <p>
 * An example workflow configuration using typesafe follows:
 * </p>
 * <pre><code>workflows = [
  {
    name = "hot-start"
    stages = [
      {
        // Stage one: move market data agent to stand-by.
        steps = [
          {
            agent = "market-data-agent"
            beginState = STOPPED
            endState = STAND_BY
            allowedTransitionTime = 100 millis
          }
        ]
      },
      {
        // Stage two: activate market data agent and put algo
        // agent into stand-by.
        steps = [
          {
            agent = "market-data-agent"
            beginState = STAND_BY
            endState = ACTIVE
            allowedTransitionTime = 100 millis
          },
          {
            agent = "make-money-algo-agent"
            beginState = STOPPED
            endState = STAND_BY
            allowedTransitionTime = 100 millis
          }
        ]
      },
      {
        // Stage three: activate money making algo.
        steps = [
          {
            agent = "make-money-algo-agent"
            beginState = STAND_BY
            endState = ACTIVE
            allowedTransitionTime = 100 millis
          }
        ]
      }
    ]
  }
]</code></pre>
 * <p>
 * Please see {@link org.efs.activator} for a detailed
 * description on {@code EfsActivator} and workflows.
 * </p>
 *
 * @see EfsActivator
 * @see WorkflowStage
 * @see WorkflowStep
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class Workflow
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Workflow name. Must be unique within the activator.
     */
    private final String mName;

    /**
     * Stages defining this workflow.
     */
    private final List<WorkflowStage> mStages;

    /**
     * Number of stages within this workflow.
     */
    private final int mStageCount;

    /**
     * Next stage to be executed. Set to {@link #mStageCount}
     * when not configured for execution. This is the initial
     * value.
     */
    private int mCurrentStageIndex;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of Workflow.
     */
    private Workflow(final Builder builder)
    {
        mName = builder.mName;
        mStages = builder.mStages;
        mStageCount = mStages.size();
        mCurrentStageIndex = mStageCount;
    } // end of Workflow(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns text containing workflow name and its stages.
     * @return textual representation of this workflow.
     */
    @Override
    public String toString()
    {
        int stageIndex = 0;
        final StringBuilder retval = new StringBuilder();

        retval.append("[name=").append(mName)
              .append(", stages={");

        for (WorkflowStage s : mStages)
        {
            retval.append("\n  [").append(stageIndex)
                  .append("] ").append(s);

            ++stageIndex;
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
     * Returns workflow name.
     * @return workflow name.
     */
    public String name()
    {
        return (mName);
    } // end of name()

    /**
     * Returns number of stages within this workflow.
     * @return workflow stage count.
     */
    public int stageCount()
    {
        return (mStageCount);
    } // end of stageCount()

    /**
     * Returns {@code true} if this workflow is in-progress and
     * not yet reached completion.
     * @return {@code true} if workflow is in-progress.
     */
    public boolean isInProgress()
    {
        return (mCurrentStageIndex < mStageCount);
    } // end of isInProgress()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets current stage index to zero. This method must
     * be called prior to performing workflow execution via
     * {@link #executeNextStep(EfsActivator)},
     * {@link #executeNextStage(EfsActivator)}, or
     * {@link #executeAllStages(EfsActivator)}.
     * <p>
     * Allowed values are [0, # stages]. Setting the current
     * stage index to a value equal to this workflow's stage
     * count it effectively terminates any in-progress stage
     * execution.
     * </p>
     * <p>
     * Initializing the stage index results each subordinate
     * stage's step index being set to zero.
     * </p>
     *
     * @see #terminateWorkflow()
     * @see #executeNextStep(EfsActivator)
     * @see #executeNextStage(EfsActivator)
     * @see #executeAllStages(EfsActivator)
     */
    /* package */ void initializeWorkflow()
    {
        mCurrentStageIndex = 0;

        mStages.forEach(WorkflowStage::intialStepIndex);
    } // end of initializeWorkflow()

    /**
     * Sets current stage and step to given values.
     * @param stageIndex current stage index.
     * @param stepIndex current step index.
     * @throws IndexOutOfBoundsException
     * if either {@code stageIndex} or {@code stepIndex} is
     * out-of-bounds.
     */
    /* package */ void setStage(final int stageIndex,
                                final int stepIndex)
    {
        if (stageIndex < 0 || stageIndex >= mStageCount)
        {
            throw (
                new IndexOutOfBoundsException(
                    String.format(
                        "stage index %d is out of bounds",
                        stageIndex)));
        }

        (mStages.get(stageIndex)).validateStepIndex(stepIndex);

        mCurrentStageIndex = stageIndex;
        (mStages.get(stageIndex)).setStepIndex(stepIndex);
    } // end of setStage(int, int)

    /**
     * Terminates this workflow by setting current stage index to
     * {@link #stageCount()}.
     */
    /* package */ void terminateWorkflow()
    {
        mCurrentStageIndex = mStageCount;

        mStages.forEach(WorkflowStage::clearStepIndex);
    } // end of terminateWorkflow()

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
     * Executes only the next step in the current stage and goes
     * not further.
     * @param activator workflow activator.
     * @return {@code true} if workflow is completed as a
     * result of this execution.
     * @throws IllegalStateException
     * if there is no activation in progress or the activation
     * fails.
     */
    /* package */ boolean executeNextStep(final EfsActivator activator)
    {
        final WorkflowStage stage =
            mStages.get(mCurrentStageIndex);
        final String stageName = mName + "-" + mCurrentStageIndex;

        // Is this stage now completed?
        if (stage.executeNextStep(stageName, activator))
        {
            // Move to the next workflow stage.
            ++mCurrentStageIndex;
        }

        return (mCurrentStageIndex == mStageCount);
    } // end of executeNextStep(EfsActivator)

    /**
     * Executes all remaining steps in the current stage and goes
     * no further.
     * @param activator workflow activator.
     * @return {@code true} if workflow is completed as a
     * result of this execution.
     * @throws IllegalStateException
     * if there is no activation in progress or the activation
     * fails.
     */
    /* package */ boolean executeNextStage(final EfsActivator activator)
    {
        final WorkflowStage stage =
            mStages.get(mCurrentStageIndex);
        final String stageName =
            mName + "-" + mCurrentStageIndex;

        // Execute all remaining steps in the current stage.
        stage.executeAllSteps(stageName, activator);

        // Move to the next workflow stage.
        ++mCurrentStageIndex;

        return (mCurrentStageIndex == mStageCount);
    } // end of executeNextStage(EfsActivator)

    /**
     * Executes all remaining stages in this workflows.
     * @param activator workflow activator.
     * @return {@code true} if workflow is completed as a
     * result of this execution.
     * @throws IllegalStateException
     * if there is no activation in progress or the activation
     * fails.
     */
    /* package */ boolean executeAllStages(final EfsActivator activator)
    {
        // Execute all remaining stages within this workflow.
        while (mCurrentStageIndex < mStageCount)
        {
            executeNextStage(activator);
        }

        return (true);
    } // end of executeAllStages(EfsActivator)

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Provides ability to programmatically define a
     * {@link Workflow} instance. Requires definition of a
     * unique workflow name and its subordinate
     * {@link WorkflowStage stage}(s).
     * <p>
     * a workflow builder instance is accessed via
     * {@link Workflow#builder()}.
     * </p>
     * <p>
     * A builder may be used in combination with a loaded
     * typesafe {@link WorkflowConfig} bean to create a workflow
     * instance.
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
         * Workflow name. Must be unique within the activator.
         */
        private String mName;

        /**
         * Workflow's stages.
         */
        private List<WorkflowStage> mStages;

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
        {}

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets workflow name which must be unique within
         * activator.
         * @param name workflow unique name.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code name} is either {@code null} or an empty
         * string.
         */
        public Builder name(final String name)
        {
            if (Strings.isNullOrEmpty(name))
            {
                throw (
                    new IllegalArgumentException(
                        "name is either null or an empty string"));
            }

            mName = name;

            return (this);
        } // end of name(String)

        /**
         * Sets stages defining workflow.
         * @param stages stages in workflow.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code stages} is either {@code null} or an empty
         * list.
         */
        public Builder stages(final List<WorkflowStage> stages)
        {
            if (stages == null || stages.isEmpty())
            {
                throw (
                    new IllegalArgumentException(
                        "stages is either null or an empty list"));
            }

            mStages = ImmutableList.copyOf(stages);

            return (this);
        } // end of stages(List<>)

        /**
         * Sets workflow properties according to given workflow
         * configuration.
         * @param config workflow configuration.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if required non-{@code null} field is set to
         * {@code null}.
         * @throws IllegalArgumentException
         * if a field is set to an invalid value.
         */
        public Builder set(final WorkflowConfig config)
        {
            final ImmutableList.Builder<WorkflowStage> builder =
                ImmutableList.builder();
            WorkflowStage.Builder stageBuilder;
            int stageIndex = 0;

            // Convert workflow stage configs list into workflow
            // stages list.
            for (WorkflowStageConfig c : config.getStages())
            {
                stageBuilder = WorkflowStage.builder();

                builder.add(stageBuilder.set(c)
                                        .stageIndex(stageIndex++)
                                        .build());
            }

            return (this.name(config.getName())
                        .stages(builder.build()));
        } // end of set(WorkflowConfig)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Returns new workflow based on this builder's settings.
         * @return new workflow.
         * @throws ValidationException
         * if {@code this Builder} contains an incomplete or
         * invalid setting.
         */
        public Workflow build()
        {
            final Validator problems = new Validator();

            problems.requireNotNull(mName, "name")
                    .requireNotNull(mStages, "stages")
                    .throwException(Workflow.class);

            return (new Workflow(this));
        } // end of build()
    } // end of class Builder
} // end of class Workflow
