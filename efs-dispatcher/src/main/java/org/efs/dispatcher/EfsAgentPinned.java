//
// Copyright 2026 Charles W. Rapp
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

import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This efs agent is associated with a single, busy spin
 * dispatcher thread and that dispatcher thread has only that one
 * agent. Therefore, the dispatcher thread has no run queue and
 * this agent is constantly checking is event queue. The
 * dispatcher thread has a core affinity configuration.
 * <p>
 * Pinned dispatchers are an advanced feature, requiring
 * knowledge of isolated cores and thread affinity for cores. It
 * is recommended that this feature be used with care.
 * </p>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

/* package */ final class EfsAgentPinned
    extends EfsAgentAbstract
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Logging subsystem interface. A synchronous logger is used
     * due to {@link #dispatch(Runnable)} logging a failed
     * dispatch may result in an infinite recursion of failed
     * dispatches because {@code AsyncLogger} uses dispatch
     * itself.
     */
    private static final Logger sLogger =
        LoggerFactory.getLogger(EfsAgentPinned.class);

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Signals when this pinned agent is up and running.
     * Dispatcher starts waits on this signal before returning.
     */
    private final CountDownLatch mStartSignal;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of EfsAgentPinned.
     */
    private EfsAgentPinned(final Builder builder)
    {
        super (builder);

        mStartSignal = new CountDownLatch(1);
    } // end of EfsAgentPinned(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Abstract Method Implementations.
    //

    /**
     * Agent remains in a busy spin loop, repeatedly attempting
     * to retrieve next event for delivery to encapsulated
     * agent.
     * <p>
     * <em>Note:</em> it is fully expected that this method will
     * not return until the agent's dispatcher is closed.
     * </p>
     * @return processed event count.
     */
    @Override
    /* package */ int processEvents()
    {
        Runnable task;
        long runTime = 0L;
        long startTime;
        long timeUsed;
        int retval = 0;

        sLogger.debug("{}: pinned agent processing events.",
                      agentName());

        // Agent is ready to receive events.
        mStartSignal.countDown();

        runState(RunState.RUNNING);

        while (mIsRegistered)
        {
            // Get the next task from the event queue.
            task = mEvents.poll();

            // Was a task retrieved?
            if (task != null)
            {
                // Yes, execute the task - with extreme
                // predjudice.
                ++retval;
                mQueueSize.decrement();
                startTime = System.nanoTime();
                task.run();
                timeUsed = (System.nanoTime() - startTime);
                runTime += timeUsed;

                updateRunStats(runTime);
            }
        }

        // Now that the agent is de-registered, remove any events
        // still on its event queue.
        mEvents.clear();
        mQueueSize.reset();

        runState(RunState.IDLE);

        sLogger.debug(
            "{}: pinned agent stopped, {} events processed.",
            agentName(),
            retval);

        return (retval);
    } // end of processEvents()

    //
    // end of Abstract Method Implementations.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns a single line of text containing:
     * <ul>
     *   <li>
     *     efs agent name,
     *   </li>
     *   <li>
     *     assigned dispatcher name, and
     *   </li>
     *   <li>
     *     efs agent run state.
     *   </li>
     * </ul>
     * @return text containing agent configuration and state.
     */
    @Override
    public String toString()
    {
        return (String.format("[%s dispatcher=%s, run state=%s]",
                              mAgent.name(),
                              mDispatcher.name(),
                              runState()));
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns signal used to inform parent dispatcher that this
     * pinned agent is started.
     * @return pinned agent start signal.
     */
    /* package */ CountDownLatch startSignal()
    {
        return (mStartSignal);
    } // end of startSignal()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Returns a new {@code EfsAgentPinned} builder instance.
     * @return new {@code EfsAgentPinned} builder instance.
     */
    /* package */ static Builder builder()
    {
        return (new Builder());
    } // end of builder()

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Builder for creating a pinned efs agent that executes on a
     * dedicated busy-spin dispatcher thread.
     * <p>
     * A pinned agent is associated with a single dispatcher
     * thread and does not use a run queue because that thread is
     * dedicated solely to the agent's event processing. The agent
     * also inherits the dispatcher thread's core affinity and
     * startup configuration established by the builder.
     * </p>
     */
    public static final class Builder
        extends AgentBuilder<EfsAgentPinned, Builder>
    {
    //-----------------------------------------------------------
    // Member data.
    //

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Builder()
        {
            super (EfsAgentPinned.class);
        } // end of Builder()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Implementations.
        //

        @Override
        protected Builder self()
        {
            return (this);
        } // end of self()

        @Override
        protected EfsAgentPinned buildImpl()
        {
            return (new EfsAgentPinned(this));
        } // end of buildImpl()

        //
        // end of Abstract Method Implementations.
        //-------------------------------------------------------
    } // end of class Builder
} // end of class EfsAgentPinned
