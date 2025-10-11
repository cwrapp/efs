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

package org.efs.dispatcher;


/**
 * Dispatcher behavior interface. Provided for mocking purposes.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public interface IEfsDispatcher
{
//---------------------------------------------------------------
// Member methods.
//

    /**
     * Returns dispatcher's unique name.
     * @return dispatcher name.
     */
    String name();

    /**
     * Returns dispatcher's subordinate thread count.
     * @return dispatcher thread count.
     */
    int threadCount();

    /**
     * Returns maximum number of events an agent is allowed to
     * process per callback.
     * @return maximum events per agent callback.
     */
    int maxEvents();

    /**
     * Returns configured agent event queue maximum capacity.
     * @return agent event queue maximum capacity.
     */
    int eventQueueCapacity();

    /**
     * Enqueues given agent to dispatcher run queue.
     * @param agent enqueue this efs agent.
     */
    void dispatch(EfsAgent agent);
} // end of interface IEfsDispatcher

