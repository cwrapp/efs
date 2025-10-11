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
import com.typesafe.config.ConfigException;
import java.time.Duration;
import org.efs.activator.EfsAgentState;
import org.efs.activator.IEfsActivateAgent;

/**
 * A typesafe bean configuration used to define a workflow
 * step within a workflow stage.
 * <h2>Example Typesafe Configuration Entry</h2>
 * <pre><code>agent = MakeMoneyAlgo
beginState = STAND_BY
endState = ACTIVE
allowedTransitionTime = 30 seconds</code></pre>
 * <p>
 * where:
 * </p>
 * <ul>
 *   <li>
 *     {@code agent}: unique {@link IEfsActivateAgent agent}
 *     name,
 *   </li>
 *   <li>
 *     {@code beginState}: agent's initial state,
 *   </li>
 *   <li>
 *     {@code endState}: agent's target state,
 *   </li>
 *   <li>
 *     {@code allowedTransitionTime}: agent is allowed this
 *     much time to complete transition. Failure to do so results
 *     in agent left in a transitioning state with status
 *     unknown.
 *   </li>
 * </ul>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class WorkflowStepConfig
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
     * Key {@value} contains unique activate agent name.
     */
    public static final String AGENT_NAME_KEY = "agent";

    /**
     * Key {@value} contains transition begin state.
     */
    public static final String BEGIN_STATE_KEY = "beginState";

    /**
     * Key {@value} contains transition end state.
     */
    public static final String END_STATE_KEY = "endState";

    /**
     * Key {@value} contains allowed agent transition time.
     */
    public static final String ALLOWED_TRANSITION_TIME_KEY =
        "allowedTransitionTime";

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Unique {@link IEfsActivateAgent agent} name.
     */
    private String mAgentName;

    /**
     * Step transitions agent out of this initial state.
     */
    private EfsAgentState mBeginState;

    /**
     * Step transitions agent into this final state.
     */
    private EfsAgentState mEndState;

    /**
     * Agent is allowed this much time to complete this
     * transition.
     */
    private Duration mAllowedTransitionTime;

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
    public WorkflowStepConfig()
    {
        mAgentName = null;
        mBeginState = null;
        mEndState = null;
        mAllowedTransitionTime = null;
    } // end of WorkflowStepConfig()

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    @Override
    public String toString()
    {
        return (
            String.format(
                "[name=%s, transition=%s -> %s, time allowed=%s]",
                mAgentName,
                mBeginState,
                mEndState,
                mAllowedTransitionTime));
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns unique {@link IEfsActivateAgent agent} name
     * returned by {@link IEfsActivateAgent#name()}.
     * @return unique agent name.
     */
    public String getAgent()
    {
        return (mAgentName);
    } // end of getAgent()

    /**
     * Returns agent's expected begin state.
     * @return agent begin state.
     */
    public EfsAgentState getBeginState()
    {
        return mBeginState;
    } // end of getBeginState()

    /**
     * Returns agent's target end state.
     * @return agent end state.
     */
    public EfsAgentState getEndState()
    {
        return (mEndState);
    } // end of getEndState()

    /**
     * Returns time agent is allowed to complete transition from
     * begin to end state.
     * @return agent allowed transition time.
     */
    public Duration getAllowedTransitionTime()
    {
        return (mAllowedTransitionTime);
    } // end of getAllowedTransitionTime()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets agent name.
     * @param name agent name.
     * @throws ConfigException
     * if {@code name} is either {@code null} or an empty string.
     */
    public void setAgent(final String name)
    {
        if (Strings.isNullOrEmpty(name))
        {
            throw (
                new ConfigException.BadValue(
                    AGENT_NAME_KEY,
                    "name is either null or an empty string"));
        }

        this.mAgentName = name;
    } // end of setAgent(String)

    /**
     * Sets agent begin state.
     * @param state agent begin state.
     * @throws ConfigException
     * if {@code state} is {@code null} or is a transition state.
     */
    public void setBeginState(final EfsAgentState state)
    {
        if (state == null)
        {
            throw (
                new ConfigException.BadValue(
                    BEGIN_STATE_KEY, "state is null"));
        }

        if (state.isTransition())
        {
            throw (
                new ConfigException.BadValue(
                    BEGIN_STATE_KEY,
                    "\"" + state + "\" is a transition state"));
        }

        this.mBeginState = state;
    } // end of setBeginState(EfsAgentState)

    /**
     * Sets agent end state.
     * @param state agent end state.
     * @throws ConfigException
     * if {@code state} is {@code null} or is a transition state.
     */
    public void setEndState(final EfsAgentState state)
    {
        if (state == null)
        {
            throw (
                new ConfigException.BadValue(
                    END_STATE_KEY, "state is null"));
        }

        if (state.isTransition())
        {
            throw (
                new ConfigException.BadValue(
                    END_STATE_KEY,
                    "\"" + state + "\" is a transition state"));
        }

        this.mEndState = state;
    } // end of setEndState(EfsAgentState)

    /**
     * Sets time allowed to agent to complete transition from
     * begin to end state.
     * @param timeAllowed allowed agent transition time.
     * @throws ConfigException
     * if {@code timeAllowed} is {@code null} or &le; zero.
     */
    public void setAllowedTransitionTime(final Duration timeAllowed)
    {
        if (timeAllowed == null)
        {
            throw (
                new ConfigException.BadValue(
                    ALLOWED_TRANSITION_TIME_KEY,
                    "timeAllowed is null"));
        }

        if (timeAllowed.compareTo(Duration.ZERO) <= 0)
        {
            throw (
                new ConfigException.BadValue(
                    ALLOWED_TRANSITION_TIME_KEY,
                    "timeAllowed <= zero"));
        }

        mAllowedTransitionTime = timeAllowed;
    } // end of setAllowedTransitionTime(Duration)

    //
    // end of Set Methods.
    //-----------------------------------------------------------
} // end of class WorkflowStepConfig
