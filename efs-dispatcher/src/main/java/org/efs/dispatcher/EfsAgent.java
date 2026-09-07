//
// Copyright 2025, 2026 Charles W. Rapp
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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.sf.eBus.util.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code EfsAgent} is the link between
 * {@link IEfsDispatcher dispatchers} and
 * {@link IEfsAgent agents}. {@code EfsAgent} maintains a strong
 * reference to an {@code IEfsAgent} agent, handles the agent's
 * event queue, and places the agent on the dispatcher's
 * agent run queue as appropriate.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@SuppressWarnings ({"java:S3011"})
/* package */ final class EfsAgent
    extends EfsAgentAbstract
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    //
    // Exception messages.
    //

    /**
     * {@code NullPointerException} thrown when target is
     * {@code null} has message {@value}.
     */
    public static final String NULL_TARGET = "target is null";

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
        LoggerFactory.getLogger(EfsAgent.class);

    //-----------------------------------------------------------
    // Locals.
    //

    //
    // Executor data members.
    //

    /**
     * When {@code true}, agent is either on the run queue and
     * ready to run or running. Agent may only be placed on run
     * queue when false.
     */
    private final AtomicBoolean mOnRunQueue;

    /**
     * Marks time this {@code EfsAgent} either:
     * <ul>
     *   <li>
     *     transitioned to ready state when an event is added
     *     to an empty {@link #mEvents} queue, or
     *   </li>
     *   <li>
     *     duration efs agent spent in ready state waiting for
     *     access to a dispatcher thread.
     *   </li>
     * </ul>
     * This timestamp is used to detect when a runnable agent
     * is denied access to a dispatcher thread beyond the
     * monitor time limit.
     */
    private final AtomicLong mReadyTimestamp;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of EfsAgent.
     */
    private EfsAgent(final Builder builder)
    {
        super (builder);

        mOnRunQueue = new AtomicBoolean();
        mReadyTimestamp = new AtomicLong();
    } // end of EfsAgent(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Abstract Method Implementations.
    //

    /**
     * Process either all enqueued events or until either
     * agent is no longer registered or {@link #mMaxEvents} limit
     * is reached. Returns number of processed events. If agent
     * de-registers while leaving enqueued events, event queue
     * is cleared prior to returning.
     * @return processed event count.
     */
    @Override
    /* package */ int processEvents()
    {
        int eventsRemaining = mMaxEvents;
        Runnable task;
        long runTime = 0L;
        long startTime;
        long timeUsed;
        int retval = 0;

        runState(RunState.RUNNING);

        // Continue processing this agent until the
        // agent is either:
        // + no longer registered,
        // + reached maximum event limit, or
        // + runs out of event.
        while (mIsRegistered &&
               eventsRemaining > 0 &&
               (task = mEvents.poll()) != null)
        {
            // Yes, there is an event to deliver.
            // So, deliver it from all evil.
            // forwardEvent catches any agent-thrown exceptions,
            // so a try-catch block is not needed here.
            ++retval;
            mQueueSize.decrement();
            startTime = System.nanoTime();
            task.run();
            timeUsed = (System.nanoTime() - startTime);
            --eventsRemaining;
            runTime += timeUsed;
        }

        updateRunStats(runTime);

        // Is this agent now de-registered?
        if (!mIsRegistered)
        {
            // Yes. Remove any events still on its event queue.
            mEvents.clear();
            mQueueSize.reset();
        }

        // Mark this agent as idle and then attempt to put it
        // back on the run queue.
        runState(RunState.IDLE);
        mOnRunQueue.set(false);

        postToRunQueue();

        return (retval);
    } // end of processEvents()

    //
    // end of Abstract Method Implementations.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // EfsAgentAbstract Method Overrides.
    //

    /**
     * Marks this agent as no longer registered. Any pending
     * events will not be forwarded to agent.
     *
     * @see EfsDispatcher#deregister(IEfsAgent)
     */
    @Override
    /* package */ void deregister()
    {
        super.deregister();

        // Is this agent on its run queue?
        if (!mOnRunQueue.get())
        {
            // No. Clear out undelivered events.
            mEvents.clear();
            mQueueSize.reset();
        }
        // Else the event queue will be cleared when this agent
        // stops processing events.
    } // end of deregister()

    /**
     * Posts task to agent event queue. If event queue is
     * initially empty, then this agent is posted to its
     * dispatcher's run queue.
     * @param task post this task to agent event queue.
     * @throws IllegalStateException
     * if event queue is full preventing {@code task} from being
     * enqueued.
     *
     * @see EfsDispatcher#dispatch(Runnable, IEfsAgent)
     */
    @Override
    /* package */ void dispatch(final Runnable task)
    {
        super.dispatch(task);

        // If we reach here, that means task was successfully
        // posted to the event queue and event queue size
        // incremented. Clear to post this agent to run queue.
        postToRunQueue();
    } // end of dispatch(Runnable)

    //
    // end of EfsAgentAbstract Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns nanosecond timestamp when efs agent last entered
     * ready state. Sets ready timestamp to zero before
     * returning.
     * @return efs agent ready state timestamp.
     */
    public long getAndClearReadyTimestamp()
    {
        return (mReadyTimestamp.getAndSet(0L));
    } // end of readyTimestamp()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Returns a new {@code EfsAgent} builder instance.
     * @return new {@code EfsAgent} builder instance.
     */
    /* package */ static Builder builder()
    {
        return (new Builder());
    } // end of builder()

    /**
     * Posts this agent to dispatcher run queue if:
     * <ul>
     *   <li>
     *     agent is still active,
     *   </li>
     *   <li>
     *     has tasks to run, and
     *   </li>
     *   <li>
     *     is not already on the run queue or running.
     *   </li>
     * </ul>
     */
    /* package */ void postToRunQueue()
    {
        // Is this agent still registered?
        // Does it have events to deliver?
        // Is this agent currently on the run queue or running?
        if (mIsRegistered &&
            !mEvents.isEmpty() &&
            mOnRunQueue.compareAndSet(false, true))
        {
            if (sLogger.isTraceEnabled())
            {
                sLogger.trace("{}: adding to {} run queue.",
                              mAgent.name(),
                              mDispatcher.name());
            }

            // Mark this agent as in the ready state and
            // timestamp when this occurred.
            runState(RunState.READY);
            mReadyTimestamp.set(System.nanoTime());

            // No. Place this agent on the run queue.
            try
            {
                mDispatcher.dispatch(this);
            }
            catch (Exception jex)
            {
                // Dispatcher run queue is full so agent is not
                // on the run queue. Make note of this.
                // The only way this is corrected is when another
                // event is posted to agent.
                mOnRunQueue.set(false);
                mMissedDispatchCount.increment();

                sLogger.warn(
                    "{}: failed to post this agent to {} dispatcher due to run queue overflow.",
                    mAgent.name(),
                    mDispatcher.name(),
                    jex);
            }
        }
        // Else if this agent is either de-registered or
        // currently on the run queue or running, so nothing has
        // changed. If this agent is currently running, then when
        // the event completes, the agent will be posted back to
        // the run queue.
    } // end of postToRunQueue()

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * {@code EfsAgent} builder used by {@link EfsDispatcher}
     * to create an agent instance encapsulating a
     * {@link IEfsAgent} instance.
     */
    /* package */ static final class Builder
        extends AgentBuilder<EfsAgent, Builder>
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
            super (EfsAgent.class);
        } // end of

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Implementations.
        //

        /**
         * Returns {@code this Builder} reference.
         * @return {@code this Builder} reference.
         */
        @Override
        protected Builder self()
        {
            return (this);
        } // end of self()

        /**
         * Returns a new {@code EfsAgent} instance constructed
         * from this builder's settings.
         * @return new {@code EfsAgent} instance.
         */
        @Override
        protected EfsAgent buildImpl()
        {
            return (new EfsAgent(this));
        } // end of buildImpl()

        //
        // end of Abstract Method Implementations.
        //-------------------------------------------------------

        /**
         * Extends {@link AgentBuilder#validate(Validator)}
         * checking if maximum events limit is set.
         * @param problems builder validator.
         * @return {@code problems}.
         */
        @Override
        protected Validator validate(final Validator problems)
        {
            return (super.validate(problems)
                         .requireTrue((mMaxEvents > 0),
                                      "maxEvents",
                                      Validator.NOT_SET));
        } // end of validate(Validator)

    } // end of class Builder
} // end of class EfsAgent
