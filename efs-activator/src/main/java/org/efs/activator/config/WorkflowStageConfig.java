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
import java.util.List;
import org.efs.activator.EfsAgentState;

/**
 * A typesafe bean configuration used to define steps within a
 * workflow stage.
 * <h2>Example Typesafe Configuration Entry</h2>
 * <pre><code>steps = [
  <em>// Activate market data agent before starting algo agent.</em>
  {
    agent = MarketData
    beginState = STAND_BY
    endState = ACTIVE
    allowedTransitionTime = 2 minutes
  },
  {
    agent = MakeMoneyAlgo
    beginState = STOPPED
    endState = STAND_BY
    allowedTransitionTime = 30 seconds
  }
]</code></pre>
 *
 * @see WorkflowStepConfig
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class WorkflowStageConfig
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    //
    // Typesafe Property keys.
    //

    /**
     * Key {@value} contains workflow steps list.
     */
    public static final String STEPS_KEY = "steps";

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Steps contained within this stage.
     */
    private List<WorkflowStepConfig> mSteps;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Default constructor required for a typesafe bean class.
     * Does no field initialization.
     */
    public WorkflowStageConfig()
    {
        mSteps = null;
    } // end of WorkflowStageConfig()

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    @Override
    public String toString()
    {
        int index = 0;
        final StringBuilder retval = new StringBuilder();

        retval.append("    steps [");

        for (WorkflowStepConfig s : mSteps)
        {
            retval.append("\n      ")
                  .append(index++)
                  .append(" ")
                  .append(s);
        }

        retval.append("\n    ]");

        return (retval.toString());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns steps contained within this workflow stage.
     * @return workflow stage's steps.
     */
    public List<WorkflowStepConfig> getSteps()
    {
        return (mSteps);
    } // end of getSteps()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets workflow stage steps to given list.
     * @param steps steps withing workflow stage.
     * @throws ConfigException
     * if {@code steps} is either {@code null}, an empty list,
     * or contains an invalid step.
     */
    public void setSteps(final List<WorkflowStepConfig> steps)
    {
        if (steps == null || steps.isEmpty())
        {
            throw (
                new ConfigException.BadValue(
                    STEPS_KEY, "steps is either null or empty"));
        }

        validateSteps(steps);

        mSteps = ImmutableList.copyOf(steps);
    } // end of setSteps(List<>)

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    /**
     * Validates that each step contains begin and end states
     * which are adjacent. This method is called for effect only.
     * @param steps validate these workflow steps.
     * @throws ConfigException
     * if {@code steps} contains a step whose begin and end
     * states are not adjacent.
     */
    private void validateSteps(final List<WorkflowStepConfig> steps)
    {
        EfsAgentState beginState;
        EfsAgentState endState;
        int index = 0;

        for (WorkflowStepConfig s : steps)
        {
            beginState = s.getBeginState();
            endState = s.getEndState();

            if (!beginState.isAdjacent(endState))
            {
                throw (
                    new ConfigException.BadValue(
                        STEPS_KEY,
                        String.format(
                            "step %d: %s, %s are not adjacent",
                            index,
                            beginState,
                            endState)));
            }

            ++index;
        }
    } // end of validateSteps(List<>)
} // end of class WorkflowStageConfig
