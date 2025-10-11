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

import org.efs.dispatcher.IEfsAgent;

/**
 * Extends {@link IEfsAgent} with methods supporting activation.
 * These methods are: startup, activate, deactivate, and stop.
 * EFS agents named in {@link WorkflowStep}s must implement
 * {@code IEfsActivateAgent} <em>and</em> be registered with its
 * dispatcher before any activator executions may be performed.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public interface IEfsActivateAgent
    extends IEfsAgent
{
//---------------------------------------------------------------
// Member methods.
//

    /**
     * This method transitions an agent from
     * {@link EfsAgentState#STOPPED stopped} to
     * {@link EfsAgentState#STAND_BY stand-by}. This transition
     * is considered successful if the method returns normally.
     * Any exception thrown by this method is interpreted as a
     * failure and the agent remains in the stop state.
     */
    void startup();

    /**
     * This method transitions an agent from
     * {@link EfsAgentState#STAND_BY stand-by} to
     * {@link EfsAgentState#ACTIVE active}. This transition
     * is considered successful if the method returns normally.
     * Any exception thrown by this method is interpreted as a
     * failure and the agent remains in the stand-by state.
     */
    void activate();

    /**
     * This method transitions an agent from
     * {@link EfsAgentState#ACTIVE active} to
     * {@link EfsAgentState#STAND_BY stand-by}. This transition
     * is considered successful if the the method returns
     * normally. Any exception thrown by this method is
     * interpreted as a failure and the agent remains in the
     * active state.
     */
    void deactivate();

    /**
     * This method transitions an agent from
     * {@link EfsAgentState#STAND_BY} to
     * {@link EfsAgentState#STOPPED}. This transition
     * is considered successful if the the method returns
     * normally. Any exception thrown by this method is
     * interpreted as a failure and the agent remains in the
     * stand-by state.
     */
    void stop();
} // end of interface IEfsActivateAgent

