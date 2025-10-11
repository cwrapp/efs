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

package org.efs.timer.config;

import com.google.common.collect.ImmutableList;
import com.typesafe.config.ConfigException;
import java.util.HashSet;
import java.util.List;

/**
 * Contains list of {@link EfsScheduledExecutorConfig} instances.
 * This class is defined as a
 * <a href="https://github.com/lightbend/config" target="_blank">typesafe</a>
 * configuration bean.
 * <h2>Example EfsScheduledExecutorsConfig File Entry</h2>
 * <pre><code>executors : [
  {
    executorName = LowLatencyTimer
    threadType = SPINNING
    priority = 10
  },
  {
    executorName = AlgoTimer
    threadType = SPINPARK
    priority = 9
    spinLimit = 1000000
    parkTime = 500 nanos
  },
  {
    executorName = UtilityTimer
    threadType = BLOCKING
    priority = 2
  }
]</code></pre>
 *
 * @see EfsScheduledExecutorConfig
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsScheduledExecutorsConfig
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Scheduled executor configuration.
     */
    private List<EfsScheduledExecutorConfig> mExecutors;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Default constructor required for typesafe config bean.
     */
    @SuppressWarnings ({"java:S1186"})
    public EfsScheduledExecutorsConfig()
    {}

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    @Override
    public String toString()
    {
        final StringBuilder output = new StringBuilder();

        output.append("[executors={");

        for (EfsScheduledExecutorConfig s : mExecutors)
        {
            output.append('\n').append(s);
        }

        return (output.append('}').toString());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns immutable {@code EfsScheduledExecutorConfig} list.
     * @return executors list.
     */
    public List<EfsScheduledExecutorConfig> getExecutors()
    {
        return (mExecutors);
    } // end of getExecutors()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets scheduled executors configuration list.
     * @param executors scheduled executors list.
     * @throws ConfigException
     * if {@code executors} contains two executors with the same
     * name.
     */
    public void setExecutors(final List<EfsScheduledExecutorConfig> executors)
    {
        final HashSet<String> names = new HashSet<>();
        String executorName;

        // Does this list have more than one executor with the
        // same name?
        for (EfsScheduledExecutorConfig c : executors)
        {
            executorName = c.getExecutorName();

            // Is this name already in the set?
            if (!names.add(executorName))
            {
                // Yes. Can't have that.
                throw (
                    new ConfigException.BadValue(
                        "executors",
                        "duplicate executors named \"" +
                        executorName +
                        "\""));
            }
        }

        mExecutors = ImmutableList.copyOf(executors);
    } // end of setExecutors(List<>)

    //
    // end of Set Methods.
    //-----------------------------------------------------------
} // end of class EfsScheduledExecutorsConfig
