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

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.sf.eBus.util.MultiKey2;

/**
 * Lists the possible efs agent states. These states include:
 * <ol>
 *   <li>
 *     {@code STOPPED}: Agent is fully stopped and ready to be
 *     either started or the application halted.
 *   </li>
 *   <li>
 *     {@code STARTING}: Agent is transitioning from
 *     {@code STOPPED} to {@code STAND_BY}.
 *   </li>
 *   <li>
 *     {@code STAND_BY}: Agent is successfully started
 *     and ready to be activated or stopped.
 *   </li>
 *   <li>
 *     {@code ACTIVATING}: Agent is transitioning from
 *     {@code STAND_BY} to {@code ACTIVE}.
 *   </li>
 *   <li>
 *     {@code ACTIVE}: Agent is successfully started and
 *     active meaning the agent is fully operational. It may
 *     now be either deactivated or stopped.
 *   </li>
 *   <li>
 *     {@code DEACTIVATING}: Agent is transitioning from
 *     {@code ACTIVE} to {@code STAND_BY}.
 *   </li>
 *   <li>
 *     {@code STOPPING}: Agent is transitioning from
 *     {@code STAND_BY} to {@code STOPPED}.
 *   </li>
 * </ol>
 * <p>
 * These agents states ordering is significant. Activation
 * advances from {@code STOPPED} to {@code STAND_BY} and
 * ends at {@code ACTIVE}. De-activation is in the opposite
 * order.
 * </p>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public enum EfsAgentState
{
    /**
     * Agent is fully stopped. Agent is ready to be
     * either started or the application halted. The agent
     * may not be activated in this state.
     * <p>
     * This is a persistent state.
     * </p>
     */
    STOPPED (false),

    /**
     * Agent is in this temporary state while transitioning
     * from {@link #STOPPED} to {@link #STAND_BY}. If the
     * start fails, then agent returns to {@code STOPPED}
     * state.
     * <p>
     * This is a transition state.
     * </p>
     */
    STARTING (true),

    /**
     * Agent is successfully started but not yet active. The
     * agent may be either activated or stopped at this point.
     * <p>
     * This is a persistent state.
     * </p>
     */
    STAND_BY (false),

    /**
     * Agent is in this temporary state while transitioning
     * from {@link #STAND_BY} to {@link #ACTIVE}. If activation
     * fails, then agent returns to {@code STAND_BY} state.
     * <p>
     * This is a transition state.
     * </p>
     */
    ACTIVATING (true),

    /**
     * Agent is both successfully started and active. This
     * means the agent is fully up. Agent may either be
     * deactivated or fully stopped at this point.
     * <p>
     * This is a persistent state.
     * </p>
     */
    ACTIVE (false),

    /**
     * Agent is in this temporary state while transitioning
     * from {@link #ACTIVE} to {@link #STAND_BY}. If the
     * deactivation fails, then agent still moves to
     * {@code STAND_BY} state.
     * <p>
     * This is a transition state.
     * </p>
     */
    DEACTIVATING (true),

    /**
     * Agent is in this temporary state while transitioning
     * from {@link #STAND_BY} to {@link #STOPPED}. If the
     * stop fails, then agent still moves to the {@code STOPPED}
     * state.
     * <p>
     * This is a transition state.
     * </p>
     */
    STOPPING (true);

//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Contains next <em>persistent</em> state when transitioning
     * from given begin state and workflow direction. May be
     * set to {@code null} if state is final state in given
     * direction.
     */
    private static final Map<MultiKey2<EfsAgentState, WorkflowDirection>,
                             Optional<EfsAgentState>> sTransitionMap;

    // Class static initialization.
    static
    {
        final ImmutableMap.Builder<MultiKey2<EfsAgentState, WorkflowDirection>,
                                   Optional<EfsAgentState>> builder =
            ImmutableMap.builder();
        MultiKey2<EfsAgentState, WorkflowDirection> key;

        // Persistent state definitions.
        key = new MultiKey2<>(STOPPED,
                              WorkflowDirection.DESCEND);
        builder.put(key, Optional.empty());

        key = new MultiKey2<>(STOPPED,
                              WorkflowDirection.ASCEND);
        builder.put(key, Optional.of(STAND_BY));

        key = new MultiKey2<>(STAND_BY,
                              WorkflowDirection.ASCEND);
        builder.put(key, Optional.of(ACTIVE));

        key = new MultiKey2<>(STAND_BY,
                              WorkflowDirection.DESCEND);
        builder.put(key, Optional.of(STOPPED));

        key = new MultiKey2<>(ACTIVE,
                              WorkflowDirection.ASCEND);
        builder.put(key, Optional.empty());

        key = new MultiKey2<>(ACTIVE,
                              WorkflowDirection.DESCEND);
        builder.put(key, Optional.of(STAND_BY));

        // Transition state definitions.
        key = new MultiKey2<>(STARTING,
                              WorkflowDirection.ASCEND);
        builder.put(key, Optional.of(STAND_BY));

        key = new MultiKey2<>(STARTING,
                              WorkflowDirection.DESCEND);
        builder.put(key, Optional.empty());

        key = new MultiKey2<>(ACTIVATING,
                              WorkflowDirection.ASCEND);
        builder.put(key, Optional.of(ACTIVE));

        key = new MultiKey2<>(ACTIVATING,
                              WorkflowDirection.DESCEND);
        builder.put(key, Optional.empty());

        key = new MultiKey2<>(DEACTIVATING,
                              WorkflowDirection.ASCEND);
        builder.put(key, Optional.empty());

        key = new MultiKey2<>(DEACTIVATING,
                              WorkflowDirection.DESCEND);
        builder.put(key, Optional.of(STAND_BY));

        key = new MultiKey2<>(STOPPING,
                              WorkflowDirection.ASCEND);
        builder.put(key, Optional.empty());

        key = new MultiKey2<>(STOPPING,
                              WorkflowDirection.DESCEND);
        builder.put(key, Optional.of(STOPPED));

        sTransitionMap = builder.build();
    } // end of class static initialization.

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Set to {@code true} if this is a transition state instead
     * of a persistent state.
     */
    private final boolean mTransitionState;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new efs agent state instance.
     * @param transitionFlag {@code true} if this is a transition
     * state.
     */
    private EfsAgentState(final boolean transitionFlag)
    {
        mTransitionState = transitionFlag;
    } // end of EfsAgentState(boolean)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns {@code true} if this is a transition state and
     * {@code false} if this is a persistent state.
     * @return {@code true} if this is a transition state.
     */
    public boolean isTransition()
    {
        return (mTransitionState);
    } // end of isTransition()

    /**
     * Returns next state from {@code this} agent state in the
     * given direction. May return {@code null} if this agent
     * state is the final state for {@code direction}.
     * @param direction find next agent state in this direction
     * from {@code this} agent state.
     * @return next agent state for given direction.
     */
    @Nullable
    public EfsAgentState getAdjacent(final WorkflowDirection direction)
    {
        final MultiKey2<EfsAgentState, WorkflowDirection> key =
            new MultiKey2<>(this, direction);

        return ((sTransitionMap.get(key)).get());
    } // end of getAdjacent(WorkflowDirection)

    /**
     * Returns {@code true} if given state is adjacent to this
     * state either ascending or descending direction; otherwise
     * returns {@code false}.
     * @param state check adjacency with this state.
     * @return {@code true} if {@code state} is adjacent to
     * {@code this EfsAgentState}.
     */
    public boolean isAdjacent(final EfsAgentState state)
    {
        final MultiKey2<EfsAgentState, WorkflowDirection> upKey =
            new MultiKey2<>(this, WorkflowDirection.ASCEND);
        final MultiKey2<EfsAgentState, WorkflowDirection> downKey =
            new MultiKey2<>(this, WorkflowDirection.DESCEND);
        final Optional<EfsAgentState> upValue =
            sTransitionMap.get(upKey);
        final Optional<EfsAgentState> downValue =
            sTransitionMap.get(downKey);
        final EfsAgentState upState =
            (upValue.isPresent() ? upValue.get() : null);
        final EfsAgentState downState =
            (downValue.isPresent() ? downValue.get() : null);

        return (state == upState || state == downState);
    } // end of getAdjacent(EfsAgentState)

    //
    // end of Get Methods.
    //-----------------------------------------------------------
} // end of enum EfsAgentState
