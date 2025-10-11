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
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigBeanFactory;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import org.efs.activator.config.EfsActivatorConfig;
import org.efs.activator.event.ActivatorEvent;
import org.efs.activator.event.ActivatorEvent.StepState;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * {@code EfsActivator} is responsible for starting, enabling,
 * disabling, and stopping {@link IEfsActivateAgent agents}
 * according to a given {@link Workflow}.
 * <p>
 * An activator may be created either programmatically using
 * {@link EfsActivator.Builder} or by
 * {@link EfsActivator#loadActivator(String) loading} a typesafe
 * configuration from a file.
 * </p>
 * <p>
 * Please see {@link org.efs.activator} package documentation for
 * a detailed description on how to use {@code EfsActivator}.
 * </p>
 *
 * @see Workflow
 * @see WorkflowStage
 * @see WorkflowStep
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsActivator
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * {@code IllegalStateException} message when execution is
     * called but there is not workflow in progress
     * ("{@value}").
     */
    public static final String NO_WORKFLOW_IN_PROGRESS =
        "no workflow in-progress";

    /**
     * {@code NullPointerException} message for a null agent is
     * {@value}.
     */
    public static final String NULL_AGENT = "agent is null";

    /**
     * {@code NullPointerException} message for a null callback
     * is {@value}.
     */
    public static final String NULL_CALLBACK = "callback is null";

    /**
     * Stand-alone executed steps are named {@value}.
     */
    public static final String STAND_ALONE_STEP =
        "standalone-step";

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger();

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Maps workflow name to its workflow instance.
     */
    private final Map<String, Workflow> mWorkflows;

    /**
     * Maps agent name to registered agent instance.
     */
    private final Map<String, AgentInfo> mAgents;

    /**
     * Registered agents listening for activator events.
     */
    private final List<ActivatorListener> mListeners;

    /**
     * Currently executing this workflow.
     */
    private Workflow mCurrentWorkflow;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of EfsActivator.
     */
    private EfsActivator(final Builder builder)
    {
        mWorkflows = createWorkflowMap(builder.mWorkflows);
        mAgents = new HashMap<>();
        mListeners = new CopyOnWriteArrayList<>();
    } // end of EfsActivator(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns current, in-progress workflow name. If there is no
     * workflow in progress, then returns
     * {@link #NO_WORKFLOW_IN_PROGRESS}.
     * @return name of workflow currently in-progress.
     */
    public String workflow()
    {
        return (mCurrentWorkflow == null ?
                NO_WORKFLOW_IN_PROGRESS :
                mCurrentWorkflow.name());
    } // end of workflow()

    /**
     * Returns named agent's current state.
     * @param agentName agent's unique name.
     * @return agent's current state.
     * @throws IllegalStateException
     * if {@code agentName} either references an un-registered
     * agent or the agent does not implement
     * {@code IEfsActivateAgent}.
     */
    public EfsAgentState agentState(final String agentName)
    {
        final AgentInfo agentInfo = findAgent(agentName);

        return (agentInfo.state());
    } // end of agentState(String)

    /**
     * Returns {@code true} if agent is a registered activator
     * listener and {@code false} otherwise.
     * @param agent check if this agent is registered as an
     * activator listener.
     * @return {@code true} if {@code agent} is a registered
     * activator listener.
     */
    public boolean isRegisteredListener(final IEfsAgent agent)
    {
        boolean retcode = false;

        Objects.requireNonNull(agent, NULL_AGENT);

        final Iterator<ActivatorListener> lIt =
            mListeners.iterator();

        while (!retcode && lIt.hasNext())
        {
            retcode = ((lIt.next()).mAgent == agent);
        }

        return (retcode);
    } // end of isRegisteredListener(IEfsAgent)

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets workflow to be executed. This method <em>must</em>
     * be successfully called prior to calling the following
     * workflow execution methods:
     * <ul>
     *   <li>
     *     {@link #executeWorkflow()},
     *   </li>
     *   <li>
     *     {@link #executeNextStage()}, and
     *   </li>
     *   <li>
     *     {@link #executeNextStep()}.
     *   </li>
     * </ul>
     * <p>
     * This activator may not have an in-progress workflow when
     * attempting initialize a workflow.
     * </p>
     * <p>
     * Note: {@link #execute(String, EfsAgentState, EfsAgentState, Duration)}
     * may always be called whether a workflow is in progress or
     * not.
     * </p>
     * @param workflowName set in place this activator workflow.
     * @throws IllegalArgumentException
     * if {@code workflowName} is either {@code null}, an empty
     * string, or does not reference a known workflow.
     * @throws IllegalStateException
     * if this activator already has an in-progress workflow.
     *
     * @see #terminateWorkflow()
     * @see #executeWorkflow()
     * @see #executeNextStage()
     * @see #executeNextStep()
     */
    public void initializeWorkflow(final String workflowName)
    {
        if (Strings.isNullOrEmpty(workflowName))
        {
            throw (
                new IllegalArgumentException(
                    "workflowName is either null or an empty string"));
        }

        // Is this workflow known?
        if (!mWorkflows.containsKey(workflowName))
        {
            throw (
                new IllegalArgumentException(
                    "unknown workflow \"" + workflowName + "\""));
        }

        // Is there a workflow in progress?
        if (mCurrentWorkflow != null &&
            mCurrentWorkflow.isInProgress())
        {
            throw (
                new IllegalStateException(
                    "workflow " +
                    mCurrentWorkflow.name() +
                    " in-progress; complete before setting another workflow"));
        }

        mCurrentWorkflow = mWorkflows.get(workflowName);

        // Set all stages to initial state.
        mCurrentWorkflow.initializeWorkflow();
    } // end of initializeWorkflow(String)

    /**
     * Sets workflow position to given stage and step index.
     * @param stageIndex set current stage to this index.
     * @param stepIndex set current step within stage to this
     * index.
     * @throws IllegalStateException
     * if there is no workflow in-progress.
     * @throws IndexOutOfBoundsException
     * if either {@code stageIndex} or {@code stepIndex} is
     * out-of-bounds.
     */
    public void setWorkflowStage(final int stageIndex,
                                 final int stepIndex)
    {
        if (mCurrentWorkflow == null)
        {
            throw (
                new IllegalStateException(
                    NO_WORKFLOW_IN_PROGRESS));
        }

        mCurrentWorkflow.setStage(stageIndex, stepIndex);
    } // end of setWorkflowStage(int, int)

    /**
     * Forcibly sets named agent's state.
     * <strong>Use this method with care!</strong> This method
     * should be used only when an activation step fails but it
     * is determined that named agent is actually in the target
     * state. Using this method indiscriminately will result in
     * activator no longer able to correctly change an agent's
     * state.
     * @param agentName set this agent's state.
     * @param state agent's current state.
     * @throws NullPointerException
     * if {@code state} is {@code null}.
     * @throws IllegalArgumentException
     * if {@code agentName} is either {@code null}, an empty
     * string, or does not reference a known agent.
     */
    public void agentState(final String agentName,
                              final EfsAgentState state)
    {
        if (Strings.isNullOrEmpty(agentName))
        {
            throw (
                new IllegalArgumentException(
                    "agentName is either null or an empty string"));
        }

        Objects.requireNonNull(state, "state is null");

        final AgentInfo agentInfo = findAgent(agentName);

        agentInfo.state(state);
    } // end of agentState(String, EfsAgentState)

    /**
     * Terminates an in-progress workflow. No further executions
     * are possible <em>except</em>
     * {@link #execute(String, EfsAgentState, EfsAgentState, Duration)}.
     *
     * @see #initializeWorkflow(String)
     */
    public void terminateWorkflow()
    {
        if (mCurrentWorkflow != null)
        {
            mCurrentWorkflow.terminateWorkflow();
            mCurrentWorkflow = null;
        }
    } // end of terminateWorkflow()

    /**
     * Registers agent for listening to activator state changes.
     * {@code ActivatorEvent}s are delivered to agent via the
     * given callback.
     * <p>
     * Please note that an agent may only have one current
     * registration. If the agent is
     * {@link #deregisterListener(Consumer, IEfsAgent) de-registered},
     * then it may register again.
     * </p>
     * @param callback agent callback lambda.
     * @param agent agent listening to activator changes.
     * @throws NullPointerException
     * if either {@code calback} or {@code agent} is
     * {@code null}.
     * @throws IllegalStateException
     * if {@code agent} is already registered.
     *
     * @see #deregisterListener(Consumer, IEfsAgent)
     */
    public void registerListener(final Consumer<ActivatorEvent> callback,
                                 final IEfsAgent agent)
    {
        Objects.requireNonNull(callback, NULL_CALLBACK);
        Objects.requireNonNull(agent, NULL_AGENT);

        final ActivatorListener l =
            new ActivatorListener(callback, agent);

        if (mListeners.contains(l))
        {
            throw (
                new IllegalStateException(
                    "agent " +
                    agent.name() +
                    " is already registered"));
        }

        mListeners.add(l);
    } // end of registerListener(Consumer<>, IEfsAgent)

    /**
     * Retracts registered agent from activator state listening.
     * Does nothing if agent is not currently registered.
     * @param callback agent callback lambda.
     * @param agent agent listening to activator changes.
     * @throws NullPointerException
     * if either {@code calback} or {@code agent} is
     * {@code null}.
     *
     * @see #registerListener(Consumer, IEfsAgent)
     */
    public void deregisterListener(final Consumer<ActivatorEvent> callback,
                                   final IEfsAgent agent)
    {
        Objects.requireNonNull(callback, NULL_CALLBACK);
        Objects.requireNonNull(agent, NULL_AGENT);

        mListeners.remove(
            new ActivatorListener(callback, agent));
    } // end of deregisterListener(Consumer<>, IEfsAgent)

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    /**
     * Executes next step in workflow. Returns {@code true} if
     * workflow is completed after successfully completing step.
     * @return {@code true} if workflow is completed.
     */
    public boolean executeNextStep()
    {
        final boolean retcode;

        if (mCurrentWorkflow == null)
        {
            throw (
                new IllegalStateException(
                    NO_WORKFLOW_IN_PROGRESS));
        }

        retcode = mCurrentWorkflow.executeNextStep(this);

        // Is the workflow completed?
        if (retcode)
        {
            mCurrentWorkflow = null;
        }

        return (retcode);
    } // end of executeNextStep()

    /**
     * Executes next stage in workflow. Returns {@code true} if
     * workflow is completed after successfully completing stage.
     * @return {@code true} if workflow is completed.
     */
    public boolean executeNextStage()
    {
        final boolean retcode;

        if (mCurrentWorkflow == null)
        {
            throw (
                new IllegalStateException(
                    NO_WORKFLOW_IN_PROGRESS));
        }

        retcode = mCurrentWorkflow.executeNextStage(this);

        // Is the workflow completed?
        if (retcode)
        {
            mCurrentWorkflow = null;
        }

        return (retcode);
    } // end of executeNextStage()

    /**
     * Executes all remaining stages and steps in this workflow.
     * @return {@code true}.
     */
    public boolean executeWorkflow()
    {
        if (mCurrentWorkflow == null)
        {
            throw (
                new IllegalStateException(
                    NO_WORKFLOW_IN_PROGRESS));
        }

        mCurrentWorkflow.executeAllStages(this);
        mCurrentWorkflow = null;

        return (true);
    } // end of executeWorkflow()

    /**
     * Executes a single explicit step on a given agent.
     * <p>
     * This method may throw any number of
     * {@code RuntimeException}s either due to the given
     * parameters being invalid (name does not reference a
     * known {@code IEfsActivateAgent}, begin and end states not
     * being adjacent) or the step execution failing. You are
     * advised to use a general {@code Exception} catch when
     * calling this method.
     * </p>
     * @param agentName unique agent name
     * @param beginState expected agent current state.
     * @param endState target agent state.
     * @param transitionTime time limit for state transition.
     */
    public void execute(final String agentName,
                        final EfsAgentState beginState,
                        final EfsAgentState endState,
                        final Duration transitionTime)
    {
        // Create a one-time workflow step to transition the
        // given arguments.
        final WorkflowStep step =
            (WorkflowStep.builder()).agentName(agentName)
                                    .beginState(beginState)
                                    .endState(endState)
                                    .allowedTransitionTime(
                                        transitionTime)
                                    .build();

        // Now have the step execute and transition the agent
        // to the desired end state.
        step.execute(STAND_ALONE_STEP, this);
    } // end of execute(...)

    /**
     * Returns a new {@code Builder} instance.
     * @return new {@code Builder} instance.
     */
    public static Builder builder()
    {
        return (new Builder());
    } // end of builder()

    /**
     * Returns activator based on configuration loaded from
     * given file name.
     * @param fileName name of file containing efs activator
     * definition.
     * @return activator loaded from configuration file.
     * @throws ConfigException
     * if {@code fileName} contain an invalid efs activator
     * configuration.
     */
    public static EfsActivator loadActivator(final String fileName)
    {
        final File configFile = new File(fileName);
        final Config configSource =
            ConfigFactory.parseFile(configFile);
        final EfsActivatorConfig activatorConfig =
            ConfigBeanFactory.create(
                configSource, EfsActivatorConfig.class);
        final EfsActivator.Builder builder =
            EfsActivator.builder();

        return (builder.set(activatorConfig).build());
    } // end of loadActivator(String)

    /**
     * Returns agent information associated with given agent
     * name.
     * @param agentName unique agent name.
     * @return {@code AgentIfno} associated with agent name.
     * @throws IllegalStateException
     * if there is not agent with {@code agentName} registered or
     * agent is not an {@code IEfsActivateAgent} instance.
     */
    /* package */ AgentInfo findAgent(final String agentName)
    {
        return (
            mAgents.computeIfAbsent(
                agentName, this::createAgent));
    } // end of findAgent(String)

    /**
     * Returns a new {@link AgentInfo} encapsulating an agent
     * with given unique agent name.
     * @param agentName agent name.
     * @return {@code AgentIfno} associated with agent name.
     * @throws IllegalStateException
     * if there is not agent with {@code agentName} registered or
     * agent is not an {@code IEfsActivateAgent} instance.
     */
    private AgentInfo createAgent(final String agentName)
    {
        final IEfsAgent agent = EfsDispatcher.agent(agentName);

        // Is this a known agent name?
        if (agent == null)
        {
            throw (
                new IllegalStateException(
                    "no such registered agent \"" +
                    agentName +
                    "\""));
        }

        // Is this agent and activate agent:
        if (!IEfsActivateAgent.class.isAssignableFrom(
                agent.getClass()))
        {
            throw (
                new IllegalStateException(
                    "\"" +
                    agentName +
                    "\" is not an activate agent"));
        }

        return (new AgentInfo((IEfsActivateAgent) agent));
    } // end of createAgent(String)

    /**
     * Returns a mapping of workflow name to the workflow
     * instance.
     * @param workflows activator workflows.
     * @return workflow name to workflow instance mapping.
     */
    private static Map<String, Workflow> createWorkflowMap(final List<Workflow> workflows)
    {
        ImmutableMap.Builder<String, Workflow> builder =
            ImmutableMap.builder();

        workflows.forEach(w -> builder.put(w.name(), w));

        return (builder.build());
    } // end of createWorkflowMap()

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Builder class used to create an activator. This builder
     * guarantees correct arguments are passed to the workflow
     * stage constructor.
     * <p>
     * An {@code EfsActivator.Builder} instance is obtained by
     * calling {@link EfsActivator#builder()}.
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
         * Activator workflows.
         */
        private List<Workflow> mWorkflows;

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
         * Sets activator workflows.
         * @param workflows activator workflows.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code workflows} is either {@code null} or an
         * empty list.
         */
        public Builder workflows(final List<Workflow> workflows)
        {
            if (workflows == null || workflows.isEmpty())
            {
                throw (
                    new IllegalArgumentException(
                        "workflows is either null or an empty list"));
            }

            final Set<String> wfNames = new TreeSet<>();
            final Set<String> duplicateNames = new TreeSet<>();
            String wfName;

            for (Workflow w : workflows)
            {
                wfName = w.name();

                if (wfNames.contains(wfName))
                {
                    duplicateNames.add(wfName);
                }
                else
                {
                    wfNames.add(wfName);
                }
            }

            if (!duplicateNames.isEmpty())
            {
                final StringBuilder message =
                    new StringBuilder();

                message.append(
                    "workflows contains duplicate names:");

                for (String n : duplicateNames)
                {
                    message.append(' ').append(n);
                }

                throw (
                    new IllegalArgumentException(
                         message.toString()));
            }

            mWorkflows = ImmutableList.copyOf(workflows);

            return (this);
        } // end of workflows(List<>)

        /**
         * Sets activator properties according to given activator
         * configuration.
         * @param config activator configuration.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if required non-{@code null} field is set to
         * {@code null}.
         * @throws IllegalArgumentException
         * if a field is set to an invalid value.
         */
        public Builder set(final EfsActivatorConfig config)
        {
            final ImmutableList.Builder<Workflow> builder =
                ImmutableList.builder();

            // Convert workflow configs list into workflows list.
            config.getWorkflows()
                  .forEach(
                      c ->
                          builder.add(
                              (Workflow.builder()).set(c)
                                                  .build()));

            return (this.workflows(builder.build()));
        } // end of set(EfsActivatorConfig)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Returns a new activator based on this builder's
         * settings.
         * @return new activator.
         * @throws ValidationException
         * if {@code this Builder} contains an incomplete or
         * invalid setting.
         */
        public EfsActivator build()
        {
            final Validator problems = new Validator();

            problems.requireNotNull(mWorkflows, "workflows")
                    .throwException(EfsActivator.class);

            return (new EfsActivator(this));
        } // end of build()
    } // end of class Builder

    /**
     * Tracks a registered agent's state. As a side effect, this
     * object informs registered activator listener agents about
     * changes in activate agent state and whether agent state
     * transitions fail and why.
     */
    /* package */ final class AgentInfo
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Registered agent.
         */
        private final IEfsActivateAgent mAgent;

        /**
         * {@link #mAgent Agent's} current state.
         */
        private final AtomicReference<EfsAgentState> mState;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private AgentInfo(final IEfsActivateAgent agent)
        {
            mAgent = agent;

            // Agent starts life in stopped state.
            mState = new AtomicReference<>(EfsAgentState.STOPPED);
        } // end of AgentInfo()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns encapsulated activate agent.
         * @return activate agent.
         */
        public IEfsActivateAgent agent()
        {
            return (mAgent);
        } // end of agent()

        /**
         * Returns agent current state.
         * @return agent state
         */
        public EfsAgentState state()
        {
            return (mState.get());
        } // end of state()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets new agent state due to given failure. Informs
         * activator listeners about this failure.
         * @param finalState new agent state.
         * @param tex agent transition failure.
         */
        /* package */ void state(final String stepName,
                                 final StepState stepState,
                                 final EfsAgentState finalState,
                                 final Duration duration,
                                 final Throwable tex)
        {
            final EfsAgentState initialState = mState.get();
            final ActivatorEvent event =
                (ActivatorEvent.builder())
                    .agentName(mAgent.name())
                    .stepName(stepName)
                    .stepState(stepState)
                    .initialState(initialState)
                    .finalState(finalState)
                    .duration(duration)
                    .exception(tex)
                    .build();

            sLogger.trace(
                "Step {} ({}): changing {} state from {} to {} (duration {}).",
                stepName,
                stepState,
                mAgent.name(),
                initialState,
                finalState,
                duration,
                tex);

            mState.set(finalState);

            // Inform listeners about this activator change.
            mListeners.forEach(l -> l.dispatch(event));
        } // end of state(...)

        /**
         * Forcibly sets agent state to given value.
         * @param state agent state.
         */
        private void state(final EfsAgentState state)
        {
            mState.set(state);
        } // end of state(EfsAgentState)

        //
        // end of Set Methods.
        //-------------------------------------------------------
    } // end of class AgentInfo

    /**
     * Contains activator listener agent and callback lambda.
     */
    private static final class ActivatorListener
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Dispatch activator event to this callback.
         */
        private final Consumer<ActivatorEvent> mCallback;

        /**
         * Dispatch activator event to this agent.
         */
        private final IEfsAgent mAgent;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private ActivatorListener(final Consumer<ActivatorEvent> callback,
                                  final IEfsAgent agent)
        {
            mCallback = callback;
            mAgent = agent;
        } // end of ActivatorListener(Consumer<>, IEfsAgent)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        /**
         * Returns {@code true} if {@code o} is a
         * non-{@code null ActivatorListener} with the same
         * underlying agent; otherwise returns {@code false}.
         * @param o comparison object.
         * @return {@code true} if {@code o} equals
         * {@code this ActivatorListener}.
         */
        @Override
        public boolean equals(final Object o)
        {
            boolean retcode = (this == o);

            if (!retcode && o instanceof ActivatorListener)
            {
                final ActivatorListener l =
                    (ActivatorListener) o;

                retcode = (mAgent == l.mAgent);
            }

            return (retcode);
        } // end of equals(Object)

        /**
         * Returns encapsulated agent's hash code.
         * @return agent hash code.
         */
        @Override
        public int hashCode()
        {
            return (mAgent.hashCode());
        } // end of hashCode()

        //
        // end of Constructors.
        //-------------------------------------------------------

        /**
         * Forwards activator event to agent.
         * @param event forward this event to agent.
         */
        private void dispatch(final ActivatorEvent event)
        {
            EfsDispatcher.dispatch(mCallback, event, mAgent);
        } // end of dispatcher(ActivatorEvent)
    } // end of class ActivatorListener
} // end of class EfsActivator
