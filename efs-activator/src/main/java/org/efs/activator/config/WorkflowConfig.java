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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.ConfigException;
import java.util.List;

/**
 * An activator work flow takes one or more agents from a given
 * start state to a target end state.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class WorkflowConfig
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
     * Key {@value} contains unique workflow name.
     */
    public static final String NAME_KEY = "name";

    /**
     * Key {@value} contains workflow stages.
     */
    public static final String STAGES_KEY = "stages";

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Workflow name. Must be unique within the activator.
     */
    private String mName;

    /**
     * Stages defining this workflow.
     */
    private List<WorkflowStageConfig> mStages;

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
    public WorkflowConfig()
    {
        mName = null;
        mStages = null;
    } // end of WorkflowConfig()

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

        retval.append("  name: ").append(mName)
              .append("\n  stages [");

        for (WorkflowStageConfig s : mStages)
        {
            retval.append("\n     ")
                  .append(index++)
                  .append(" ")
                  .append(s);
        }

        retval.append("\n  ]");

        return (retval.toString());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns unique workflow name.
     * @return unique workflow name.
     */
    public String getName()
    {
        return (mName);
    } // end of getName()

    /**
     * Returns workflow stages as an immutable list.
     * @return workflow stages.
     */
    public List<WorkflowStageConfig> getStages()
    {
        return (mStages);
    } // end of getStages()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets unique workflow name. Name must be unique within
     * its activator.
     * @param name workflow name.
     * @throws ConfigException
     * if {@code name} is either {@code null} or an empty string.
     */
    public void setName(final String name)
    {
        if (Strings.isNullOrEmpty(name))
        {
            throw (
                new ConfigException.BadValue(
                    NAME_KEY,
                    "name is either null or an empty string"));
        }

        mName = name;
    } // end of setName(String)

    /**
     * Stages defining this workflow.
     * @param stages workflow stages.
     * @throws ConfigException
     * if {@code stages} is either {@code null} or an empty list.
     */
    public void setStages(final List<WorkflowStageConfig> stages)
    {
        if (stages == null || stages.isEmpty())
        {
            throw (
                new ConfigException.BadValue(
                    STAGES_KEY,
                    "stages is either null or an empty list"));
        }

        mStages = ImmutableList.copyOf(stages);
    } // end of setStages(List<>)

    //
    // end of Set Methods.
    //-----------------------------------------------------------
} // end of class WorkflowConfig
