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
import com.google.common.collect.ImmutableMap;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.sf.eBus.util.MultiKey2;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import org.efs.activator.EfsActivator.AgentInfo;
import org.efs.activator.config.WorkflowStepConfig;
import org.efs.activator.event.ActivatorEvent;
import org.efs.activator.event.ActivatorEvent.StepState;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * A workflow step takes a single {@code IEfsActivateAgent} from
 * an expected begin state to an adjacent next state. A step is
 * assigned an allowed transition time. If the agent's transition
 * exceeds this time limit, then the transition is reported as
 * failed and an {@link ActivatorEvent} is reported to listening
 * agents with {@link StepState#COMPLETED_FAILED} and
 * {@code IllegalStateException}.
 * <p>
 * Please see {@link org.efs.activator} for a detailed
 * description on {@code EfsActivator} and workflows.
 * </p>
 *
 * @see EfsActivator
 * @see Workflow
 * @see WorkflowStage
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class WorkflowStep
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Maps a begin state, end state pair to the
     * {@link IEfsActivateAgent} method which enacts the
     * transition.
     */
    private static final Map<MultiKey2<EfsAgentState,
                                       EfsAgentState>,
                             MethodHandle> sTransitions;

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger();

    // Class static initialization.
    static
    {
        final ImmutableMap.Builder<MultiKey2<EfsAgentState,
                                             EfsAgentState>,
                                   MethodHandle> builder =
            ImmutableMap.builder();
        final MethodHandles.Lookup lookup =
            MethodHandles.publicLookup();
        final MethodType methodType =
            MethodType.methodType(void.class);
        MultiKey2<EfsAgentState, EfsAgentState> key;
        String methodName = "(not set)";
        MethodHandle methodHandle;

        try
        {
            key = new MultiKey2<>(EfsAgentState.STOPPED,
                                  EfsAgentState.STAND_BY);
            methodName = "startup";
            methodHandle =
                lookup.findVirtual(IEfsActivateAgent.class,
                                   methodName,
                                   methodType);
            builder.put(key, methodHandle);

            key = new MultiKey2<>(EfsAgentState.STAND_BY,
                                  EfsAgentState.ACTIVE);
            methodName = "activate";
            methodHandle =
                lookup.findVirtual(IEfsActivateAgent.class,
                                   methodName,
                                   methodType);
            builder.put(key, methodHandle);

            key = new MultiKey2<>(EfsAgentState.ACTIVE,
                                  EfsAgentState.STAND_BY);
            methodName = "deactivate";
            methodHandle =
                lookup.findVirtual(IEfsActivateAgent.class,
                                   methodName,
                                   methodType);
            builder.put(key, methodHandle);

            key = new MultiKey2<>(EfsAgentState.STAND_BY,
                                  EfsAgentState.STOPPED);
            methodName = "stop";
            methodHandle =
                lookup.findVirtual(IEfsActivateAgent.class,
                                   methodName,
                                   methodType);
            builder.put(key, methodHandle);
        }
        catch (IllegalAccessException |
               NoSuchMethodException jex)
        {
            sLogger.error(
                "Method lookup for IEfsActivateAgent.{} failed.",
                methodName,
                jex);
        }

        sTransitions = builder.build();
    } // end of class static initialization.

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Step applies to this named
     * {@link IEfsActivateAgent agent}.
     */
    private final String mAgentName;

    /**
     * Expected agent begin state.
     */
    private final EfsAgentState mBeginState;

    /**
     * Intermediate state between begin and end states.
     */
    private final EfsAgentState mIntermediateState;

    /**
     * Target agent end state.
     */
    private final EfsAgentState mEndState;

    /**
     * Agent is allowed this much time to complete this
     * transition.
     */
    private final Duration mAllowedTransitionTime;

    /**
     * {@link IEfsActivateAgent Agent} method which effects the
     * begin state, end state transition.
     */
    private final MethodHandle mTransition;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new workflow step instance based on builder
     * settings.
     * @param builder contains valid workflow step settings.
     */
    private WorkflowStep(final Builder builder)
    {
        mAgentName = builder.mAgentName;
        mBeginState = builder.mBeginState;
        mEndState = builder.mEndState;
        mIntermediateState =
            findIntermediateState(mBeginState, mEndState);
        mAllowedTransitionTime = builder.mAllowedTransitionTime;

        final MultiKey2<EfsAgentState, EfsAgentState> key =
            new MultiKey2<>(mBeginState, mEndState);

        mTransition = sTransitions.get(key);
    } // end of WorkflowStep(builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns text containing agent name, transition begin and
     * end states, and allowed transition time. Example output
     * is:
     * <pre><code>[agent=market-data-agent, transition=STAND_BY -> STOPPED, allowed time=PT0.5S</code></pre>
     * @return textual representation of this workflow step.
     */
    @Override
    public String toString()
    {
        return (
            String.format(
                "[agent=%s, transition=%s -> %s, allowed time=%s]",
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
     * Returns step's agent name.
     * @return step applies to this agent.
     */
    public String agentName()
    {
        return (mAgentName);
    } // end of agentName()

    /**
     * Returns agent expected begin state.
     * @return agent expected begin state.
     */
    public EfsAgentState beginState()
    {
        return (mBeginState);
    } // end of beginState()

    /**
     * Returns agent target end state.
     * @return agent target end state.
     */
    public EfsAgentState endState()
    {
        return (mEndState);
    } // end of endState()

    /**
     * Returns agent's allowed time to execute this transition.
     * @return allowed agent transition time.
     */
    public Duration allowedTransitionTime()
    {
        return (mAllowedTransitionTime);
    } // end of allowedTransitionTime()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Returns a new {@code Builder} instance.
     * @return new {@code Builder} instance.
     */
    public static Builder builder()
    {
        return (new Builder());
    } // end of builder()

    /**
     * Executes agent transition from begin state to end state.
     * If agent is already in the end state, then nothing is
     * done but the fact is reported to activator listeners.
     * @param stepName name uniquely identifying step within a
     * workflow.
     * @param activator activator executing this step.
     * @throws IllegalStateException
     * if:
     * <ul>
     *   <li>
     *     there is no agent with configured name registered,
     *   </li>
     *   <li>
     *     there is an agent registered with configured name but
     *     it is not {@link IEfsActivateAgent} instance,
     *   </li>
     *   <li>
     *     agent's current state is not the expected begin state
     *     (but not if agent is in target end state),
     *   </li>
     *   <li>
     *     agent's transition from begin state to end state took
     *     longer than allowed, or
     *   </li>
     *   <li>
     *     agent's transition failed for given exception cause.
     *   </li>
     * </ul>
     */
    /* package */ void execute(final String stepName,
                               final EfsActivator activator)
    {
        // Note: if mAgentName does not reference a known agent,
        // an IllegalStateException is thrown.
        final AgentInfo agentInfo =
            activator.findAgent(mAgentName);
        final CountDownLatch doneSignal = new CountDownLatch(1);
        final ActivateTask task =
            new ActivateTask(
                stepName, agentInfo.agent(), doneSignal);
        final Instant startTime;
        Duration executionTime;

        // Is this agent already in the target end state?
        if (agentInfo.state() == mEndState)
        {
            // Yes. Nothing to do but do report that the
            // agent is in the target end state and return.
            agentInfo.state(stepName,
                            StepState.COMPLETED_SUCCESS,
                            mEndState,
                            Duration.ZERO,
                            null);

            return;
        }

        // Is this agent in the expected begin state?
        if (agentInfo.state() != mBeginState)
        {
            throw (
                new IllegalStateException(
                    String.format(
                        "agent %s is not in expected begin state %s but %s (step %s)",
                        mAgentName,
                        mBeginState,
                        agentInfo.state(),
                        stepName)));
        }

        sLogger.info("{}: {} transitioning from {} to {} ...",
                     stepName,
                     mAgentName,
                     mBeginState,
                     mEndState);

        // Post transition to agent via dispatcher, timing its
        // completion.
        agentInfo.state(stepName,
                        StepState.IN_PROGRESS,
                        mIntermediateState,
                        Duration.ZERO,
                        null);
        startTime = Instant.now();
        EfsDispatcher.dispatch(task, agentInfo.agent());

        // Wait only so long for the task to complete.
        try
        {
            final boolean timeoutFlag =
                doneSignal.await(mAllowedTransitionTime.toNanos(),
                                 TimeUnit.NANOSECONDS);

            executionTime =
                Duration.between(startTime, Instant.now());

            // Did we timeout waiting for the task to complete?
            if (!timeoutFlag)
            {
                final IllegalStateException statex =
                    new IllegalStateException(
                        "timed out waiting for transition to complete");

                // Yes, treat this as a failure and move agent
                // back to begin state.
                agentInfo.state(stepName,
                                StepState.COMPLETED_FAILED,
                                mBeginState,
                                executionTime,
                                statex);

                throw (statex);
            }

            if (!task.isSuccessful())
            {
                final IllegalStateException statex =
                    new IllegalStateException(
                        String.format(
                            "transition failed (step %s)",
                            stepName),
                        task.caughtException());

                agentInfo.state(stepName,
                                StepState.COMPLETED_FAILED,
                                mBeginState,
                                executionTime,
                                statex);

                throw (statex);
            }

            sLogger.info(
                "{}: {} transition from {} to {} successfully completed.",
                stepName,
                mAgentName,
                mBeginState,
                mEndState);

            agentInfo.state(stepName,
                            StepState.COMPLETED_SUCCESS,
                            mEndState,
                            executionTime,
                            null);
        }
        catch (InterruptedException interrupt)
        {
            final IllegalStateException statex =
                new IllegalStateException(
                    String.format(
                        "transition interrupted (step %s)",
                        stepName),
                    interrupt);

            executionTime =
                Duration.between(startTime, Instant.now());

            // Treat an interrupt as a failure.
            agentInfo.state(stepName,
                            StepState.COMPLETED_FAILED,
                            mBeginState,
                            executionTime,
                            statex);

            throw (statex);
        }
    } // end of execute(EfsActivator)

    /**
     * Returns intermediate state between begin and end states.
     * @param beginState expected agent begin state.
     * @param endState target agent end state.
     * @return intermediate state.
     */
    private static EfsAgentState findIntermediateState(final EfsAgentState beginState,
                                                       final EfsAgentState endState)
    {
        final EfsAgentState retval;

        retval =
            switch (beginState)
            {
                case STOPPED -> EfsAgentState.STARTING;
                case STAND_BY ->
                    (endState == EfsAgentState.ACTIVE ?
                     EfsAgentState.ACTIVATING :
                     EfsAgentState.STOPPING);
                default -> EfsAgentState.DEACTIVATING;
            }; // That leaves the active state.

        return (retval);
    } // end of findIntermediateState(EfsAgentState,EfsAgentState)

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Provides ability to programmatically define a
     * {@link WorkflowStep} instance. Requires definition of a
     * unique {@code IEfsActivateAgent} name, agent begin and
     * end states, and maximum allowed agent transition time.
     * <p>
     * A workflow step builder instance is accessed via
     * {@link WorkflowStep#builder()}.
     * </p>
     * <p>
     * A builder may be used in combination with a loaded
     * typesafe {@link WorkflowStepConfig} bean to create a
     * workflow step instance.
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
         * Step applies to this named agent.
         */
        private String mAgentName;

        /**
         * Expected agent initial state.
         */
        private EfsAgentState mBeginState;

        /**
         * Agent target state. Must be adjacent to
         * {@link #mBeginState begin state}.
         */
        private EfsAgentState mEndState;

        /**
         * Time allowed to agent to transition from begin to
         * end state.
         */
        private Duration mAllowedTransitionTime;

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
         * Sets {@link IEfsActivateAgent agent} name.
         * @param name agent name.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code name} is either {@code null} or an empty
         * string.
         */
        public Builder agentName(final String name)
        {
            if (Strings.isNullOrEmpty(name))
            {
                throw (
                    new IllegalArgumentException(
                        "name is either null or an empty string"));
            }

            mAgentName = name;

            return (this);
        } // end of agentName(String)

        /**
         * Sets expected agent begin state.
         * @param state agent begin state.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code state} is {@code null}.
         */
        public Builder beginState(final EfsAgentState state)
        {
            mBeginState =
                Objects.requireNonNull(state, "state is null");

            return (this);
        } // end of beginState(EfsAgentState)

        /**
         * Sets target agent end state.
         * @param state agent end state.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code state} is {@code null}.
         */
        public Builder endState(final EfsAgentState state)
        {
            mEndState =
                Objects.requireNonNull(state, "state is null");

            return (this);
        } // end of endState(EfsAgentState)

        /**
         * Sets time agent is allowed to complete the transition
         * from begin to end state.
         * @param time allowed agent transition time.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code time} is {@code null}.
         * @throws IllegalArgumentException
         * if {@code time} &le; zero.
         */
        public Builder allowedTransitionTime(final Duration time)
        {
            Objects.requireNonNull(time, "time is null");

            if (time.compareTo(Duration.ZERO) <= 0)
            {
                throw (
                    new IllegalArgumentException(
                        "time <= zero"));
            }

            mAllowedTransitionTime = time;

            return (this);
        } // end of allowedTransitionTime(Duration)

        /**
         * Fills in builder with given workflow step
         * configuration.
         * @param config workflow configuration.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if required non-{@code null} field is set to
         * {@code null}.
         * @throws IllegalArgumentException
         * if a field is set to an invalid value.
         */
        public Builder set(final WorkflowStepConfig config)
        {
            return (this.agentName(config.getAgent())
                        .beginState(config.getBeginState())
                        .endState(config.getEndState())
                        .allowedTransitionTime(
                            config.getAllowedTransitionTime()));
        } // end of set(int, WorkflowStepConfig)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Returns new workflow step based on this builder's
         * settings.
         * @return new workflow step.
         * @throws ValidationException
         * if {@code this Builder} has an incomplete or invalid
         * setting.
         */
        public WorkflowStep build()
        {
            final Validator problems = new Validator();

            problems.requireNotNull(mAgentName, "agentName")
                    .requireNotNull(mBeginState, "beginState")
                    .requireNotNull(mEndState, "endState")
                    .requireNotNull(mAllowedTransitionTime,
                                    "allowedTransitionTime")
                    .requireTrue((mBeginState != null &&
                                  mEndState != null &&
                                  mBeginState.isAdjacent(mEndState)),
                                 "beginState",
                                 "not adjacent to " + mEndState)
                    .throwException(WorkflowStep.class);

            return (new WorkflowStep(this));
        } // end of build()
    } // end of class Builder

    /**
     * Executes agent transition on a dispatcher thread,
     * capturing any exception which causes the transition to
     * fail.
     */
    private final class ActivateTask
        implements Runnable
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Name of workflow step executing this task.
         */
        private final String mStepName;

        /**
         * Post transition update to this agent.
         */
        private final IEfsActivateAgent mAgent;

        /**
         * Decrement when transition completes.
         */
        private final CountDownLatch mDoneSignal;

        /**
         * If agent's transition throws an exception, store that
         * exception here.
         */
        private Throwable mException;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private ActivateTask(final String stepName,
                             final IEfsActivateAgent agent,
                             final CountDownLatch doneSignal)
        {
            mStepName = stepName;
            mAgent = agent;
            mDoneSignal = doneSignal;
        } // end of ActivateTask(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Runnable Interface Implementation.
        //

        /**
         * Executes agent transition method.
         */
        @Override
        public void run()
        {
            sLogger.trace(
                "{}: performing {} transition from {} to {} beginning.",
                mStepName,
                mAgent.name(),
                mBeginState,
                mEndState);

            try
            {
                mTransition.invokeExact(mAgent);

                sLogger.trace(
                    "{}: {} transition from {} to {} ended.",
                    mStepName,
                    mAgent.name(),
                    mBeginState,
                    mEndState);
            }
            catch (Throwable tex)
            {
                mException = tex;

                sLogger.trace(
                    "{}: {} transition from {} to {} failed.",
                    mStepName,
                    mAgent.name(),
                    mBeginState,
                    mEndState,
                    tex);
            }

            mDoneSignal.countDown();
        } // end of run()

        //
        // end of Runnable Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns {@code true} if activation transition
         * successfully completed and {@code false} otherwise.
         * @return {@code true} if transition successfully
         * completed.
         */
        public boolean isSuccessful()
        {
            return (mException == null);
        } // end of isSuccessful()

        /**
         * Returns exception associated with a transition failure
         * and {@code null} if transition succeeded.
         * @return failed transition exception.
         */
        @Nullable public Throwable caughtException()
        {
            return (mException);
        } // end of caughtException()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of class ActivateTask
} // end of class WorkflowStep
