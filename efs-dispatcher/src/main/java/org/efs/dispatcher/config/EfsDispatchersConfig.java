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

package org.efs.dispatcher.config;

import com.google.common.collect.ImmutableList;
import com.typesafe.config.ConfigException;
import java.util.HashSet;
import java.util.List;

/**
 * Contains list of {@link EfsDispatcherConfig} instances. This
 * class is defined as a
 * <a href="https://github.com/lightbend/config" target="_blank">typesafe</a>
 * configuration bean.
 * <h2>Example EfsDispatchersConfig File Entry</h2>
 * <pre><code>dispatchers : [
  {
    dispatcherName = AlgoDispatcher
    threadType = SPINNING
    numThreads = 1
    priority = 10
    eventQueueCapacity = 128
    maxEvents = 128
    runQueueCapacity = 8
  },
  {
    dispatcherName = MonitorDispatcher
    threadType = SPINPARK
    numThreads = 1
    priority = 8
    spinLimit = 1000000
    parkTime = 500 nanos
    eventQueueCapacity = 256
    maxEvents = 64
    runQueueCapacity = 16
  },
  {
    dispatcherName = UtilityDispatcher
    threadType = BLOCKING
    numThreads = 4
    priority = 1
    eventQueueCapacity = 0 <em>// Unlimited event queue size.</em>
    maxEvents = 8
    runQueueCapacity = 64
  }
]</code></pre>
 *
 * @see EfsDispatcherConfig
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsDispatchersConfig
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Dispatcher configurations.
     */
    private List<EfsDispatcherConfig> mDispatchers;

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
    public EfsDispatchersConfig()
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

        output.append("[dispatchers={");

        for (EfsDispatcherConfig c : mDispatchers)
        {
            output.append('\n').append(c);
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
     * Returns immutable {@code EfsDispatcherConfig} list.
     * @return dispatchers list.
     */
    public List<EfsDispatcherConfig> getDispatchers()
    {
        return (mDispatchers);
    } // end of getDispatchers()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets efs dispatchers list.
     * @param dispatchers efs dispatchers.
     * @throws ConfigException
     * if {@code dispatchers} contains duplicate dispatcher
     * names.
     */
    public void setDispatchers(final List<EfsDispatcherConfig> dispatchers)
    {
        final HashSet<String> names = new HashSet<>();
        String dispatcherName;

        // Does this list have more than one dispatcher with the
        // same name?
        // Does this list have more than one default dispatcher?
        for (EfsDispatcherConfig c : dispatchers)
        {
            dispatcherName = c.getDispatcherName();

            // Is this name already in the set?
            if (!names.add(dispatcherName))
            {
                // Yes. Can't have that.
                throw (
                    new ConfigException.BadValue(
                        "executors",
                        "duplicate dispatcher named \"" +
                        dispatcherName +
                        "\""));
            }
        }

        mDispatchers = ImmutableList.copyOf(dispatchers);
    } // end of setDispatchers(List<>)

    //
    // end of Set Methods.
    //-----------------------------------------------------------
} // end of class EfsDispatchersConfig
