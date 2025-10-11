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

/**
 * A typesafe bean configuration used to define a named workflow
 * traveling in a given direction across a given set of steps.
 * <h2>Example Typesafe Configuration Entry</h2>
 * <pre><code>name = "Main"
stages = [
  <em>// Start market data agent first since all other agents depend upon it.</em>
  {
    steps = [
      {
        agent = MarketData
        beginState = STOPPED
        endState = STAND_BY
        allowedTransitionTime = 2 minutes
      }
    ]
  },
  <em>// Activate market data agent before starting algo agent.</em>
  {
    steps = [
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
    ]
  },
  <em>// Activate algo agent now that market data is active.</em>
  {
    steps = [
      {
        agent = MakeMoneyAlgo
        beginState = STAND_BY
        endState = ACTIVE
        allowedTransitionTime = 30 seconds
      }
    ]
  }
]</code></pre>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsActivatorConfig
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
     * Key {@value} contains activator workflow definitions.
     */
    public static final String WORKFLOWS_KEY = "workflows";

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Activator workflows definitions.
     */
    private List<WorkflowConfig> mWorkflows;

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
    public EfsActivatorConfig()
    {
        mWorkflows = null;
    } // end of EfsActivatorConfig()

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

        retval.append("workflows [");

        for (WorkflowConfig w : mWorkflows)
        {
            retval.append("\n  ")
                .append(index++)
                .append(" ")
                .append(w);
        }

        retval.append("\n]");

        return (retval.toString());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns activator workflows.
     * @return activator workflows.
     */
    public List<WorkflowConfig> getWorkflows()
    {
        return (mWorkflows);
    } // end of getWorkflows()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets activator workflows.
     * @param workflows activator workflows.
     * @throws ConfigException
     * if {@code workflows} is either {@code null} or an empty
     * list.
     */
    public void setWorkflows(final List<WorkflowConfig> workflows)
    {
        if (workflows == null || workflows.isEmpty())
        {
            throw (
                new ConfigException.BadValue(
                    WORKFLOWS_KEY,
                    "workflows is either null or an empty list"));
        }

        mWorkflows = ImmutableList.copyOf(workflows);
    } // end of setWorkflows(List<>)

    //
    // end of Set Methods.
    //-----------------------------------------------------------
} // end of class EfsActivatorConfig
