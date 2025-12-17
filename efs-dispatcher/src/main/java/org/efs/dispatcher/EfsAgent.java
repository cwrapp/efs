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

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import org.efs.event.IEfsEvent;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;
import org.jctools.queues.atomic.MpscAtomicArrayQueue;
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
{
//---------------------------------------------------------------
// Member Enums.
//

    /**
     * Defines efs agent run states. The agent run state changes
     * as events are posted to or removed from the agent event
     * queue.
     */
    public enum RunState
    {
        /**
         * efs agent has no pending events. Is not be on run
         * queue.
         */
        IDLE,

        /**
         * efs agent has pending events. Will be on run queue,
         * waiting for a dispatcher thread to execute its oldest
         * pending event.
         */
        READY,

        /**
         * efs agent is processing its oldest event. May or may
         * not have pending events. Once the processing is
         * completed, agent is placed back on the run queue
         * if it has pending events.
         */
        RUNNING
    } // end of enum RunState

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
        LoggerFactory.getLogger(EfsAgent.class);

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * efs agent encapsulated in this agent.
     */
    private final IEfsAgent mAgent;

    /**
     * Post this agent when it is ready to run to this
     * dispatcher for later execution.
     */
    private final IEfsDispatcher mDispatcher;

    /**
     * Set to {@code true} when underlying agent is registered
     * with its dispatcher and {@code false} when not. When an
     * agent is no longer registered, any enqueued events are
     * no longer delivered to the agent.
     */
    private volatile boolean mIsRegistered;

    //
    // Executor data members.
    //

    /**
     * efs agent's pending callback events. If this queue is not
     * empty and agent run state is not
     * {@link RunState#RUNNING}, then this agent will be in
     * the dispatch table. When the encapsulated application
     * instance is finalized, this queue is cleared.
     * <p>
     * Set to {@code null} when another thread (like JavaFX GUI
     * thread) is used to dispatch events.
     * </p>
     */
    private final Queue<Runnable> mEvents;

    /**
     * When {@code true}, agent is either on the run queue and
     * ready to run or running. Agent may only be placed on run
     * queue when false.
     */
    private final AtomicBoolean mOnRunQueue;

    /**
     * An {@code EfsAgent} may continue running on a
     * {@link EfsDispatcherThread} as long as the agent has
     * pending events limited by this many events.
     */
    private final int mMaxEvents;

    /**
     * This agent's current run state. This value is updated
     * when new events are dispatched and when pending events are
     * executed.
     */
    private volatile RunState mRunState;

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
    private volatile long mReadyTimestamp;

    //
    // Execution statistics.
    //

    /**
     * Minimum nanoseconds spent processing messages.
     */
    private long mMinimumRunTime;

    /**
     * Maximum nanoseconds spent processing messages.
     */
    private long mMaximumRunTime;

    /**
     * Total nanoseconds spent processing messages.
     */
    private long mTotalRunTime;

    /**
     * Number of times this agent has been on core.
     */
    private long mRunCount;

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
        mAgent = builder.mAgent;
        mIsRegistered = true;
        mMaxEvents = builder.mMaxEvents;
        mOnRunQueue = new AtomicBoolean();
        mDispatcher = builder.mDispatcher;
        mEvents =
            createEventQueue(builder.mEventQueueCapacity,
                             mDispatcher.threadCount());

        mRunState = RunState.IDLE;
        mReadyTimestamp = 0L;

        mMinimumRunTime = 0L;
        mMaximumRunTime = 0L;
        mTotalRunTime = 0L;
        mRunCount = 0L;
    } // end of EfsAgent(Builder)

    //
    // end of Constructors.
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
     *     agent maximum event count,
     *   </li>
     *   <li>
     *     assigned dispatcher name,
     *   </li>
     *   <li>
     *     agent state, and
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
        return (
            String.format(
                "[%s max events=%,d, dispatcher=%s, run state=%s]",
                mAgent.name(),
                mMaxEvents,
                mDispatcher.name(),
                mRunState));
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns encapsulated efs agent.
     * @return encapsulated efs agent
     */
    public IEfsAgent agent()
    {
        return (mAgent);
    } // end of agent()

    /**
     * Returns efs agent name.
     * @return efs agent name.
     */
    public String agentName()
    {
        return (mAgent.name());
    } // end of agentName()

    /**
     * Returns efs agent's maximum allowed events processed per
     * run.
     * @return agent maximum allowed events.
     */
    public int maxEvents()
    {
        return (mMaxEvents);
    } // end oof maxEvents()

    /**
     * Returns current agent run state.
     * @return agent run state.
     */
    public RunState runState()
    {
        return (mRunState);
    } // end of runState()

    /**
     * Returns nanosecond timestamp when efs agent last entered
     * ready state. Sets ready timestamp to zero before
     * returning.
     * @return efs agent ready state timestamp.
     */
    public long getAndClearReadyTimestamp()
    {
        final long retval = mReadyTimestamp;

        mReadyTimestamp = 0L;

        return (retval);
    } // end of readyTimestamp()

    /**
     * Returns this agent's associated efs dispatcher instance.
     * @return efs dispatcher.
     */
    /* package */ IEfsDispatcher dispatcher()
    {
        return (mDispatcher);
    } // end of dispatcher()

    /**
     * Returns an efs agent information instance based on this
     * agent's settings.
     * @return efs agent information instance.
     */
    public AgentStats generateRunStats()
    {
        return (new AgentStats(agentName(),
                               mEvents.size(),
                               mMinimumRunTime,
                               mMaximumRunTime,
                               mTotalRunTime,
                               mRunCount,
                               mDispatcher.name(),
                               mMaxEvents));
    } // end of generateRunStats()

    /**
     * Returns run time statistics immutable list for extant efs
     * objects. Returns an empty list if there are no currently
     * registered efs objects.
     * <p>
     * The following is an example output of the returned list:
     * </p>
     * <pre><code>ConnectionPublisher
    min run time: 1,364 nanos
    max run time: 32,743,678 nanos
  total run time: 34,189,949 nanos
       run count: 4
    avg run time: 8,547,487 nanos
      dispatcher: general
      max events: 16

MulticastConnectionPublisher
    min run time: 613 nanos
    max run time: 751,792 nanos
  total run time: 763,513 nanos
       run count: 3
    avg run time: 254,504 nanos
      dispatcher: general
      max events: 15

PingPong Main
    min run time: 10,541 nanos
    max run time: 3,700,790 nanos
  total run time: 3,711,331 nanos
       run count: 2
    avg run time: 1,855,665 nanos
      dispatcher: general
      max events: 4

Ping! Pong! Timer
    min run time: 1,260 nanos
    max run time: 9,877,401 nanos
  total run time: 10,195,402 nanos
       run count: 5
    avg run time: 2,039,080 nanos
      dispatcher: general
      max events: 32

Pinger
    min run time: 61 nanos
    max run time: 33,913,494 nanos
  total run time: 953,601,532 nanos
       run count: 338,447
    avg run time: 2,817 nanos
      dispatcher: ping
      max events: 32

Ponger
    min run time: 164 nanos
    max run time: 4,439,180 nanos
  total run time: 926,228,288 nanos
       run count: 132,905
    avg run time: 6,969 nanos
      dispatcher: pong
      max events: 32</code></pre>
     * @return run time statistics list.
     */
    public static List<AgentStats> runTimeStats()
    {
        final ImmutableList.Builder<AgentStats> builder =
            ImmutableList.builder();

        EfsDispatcher.agents().forEach(
            a -> builder.add(a.generateRunStats()));

        return (builder.build());
    } // end of runTimeStats()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets efs agent's run state to given value.
     * @param state run state.
     */
    private void runState(final RunState state)
    {
        if (sLogger.isTraceEnabled())
        {
            sLogger.trace("{}: run state set to {}.",
                          mAgent.name(),
                          state);
        }

        mRunState = state;
    } // end of runState(RunState)

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    /**
     * Marks this agent as no longer registered. Any pending
     * events will not be forwarded to agent.
     */
    /* package */ void deregister()
    {
        mIsRegistered = false;

        // Clear out undelivered events.
        mEvents.clear();
    } // end of deregister()

    /**
     * Posts event callback consumer and event to event queue.
     * The callback and event are encapsulated in an event
     * callback used to call
     * {@link Consumer#accept(Object) callback.accept(event)}.
     * @param <E> event type being dispatched to agent.
     * @param callback consumer instance used to pass
     * {@code event} to agent.
     * @param event pass this event to agent.
     * @throws IllegalStateException
     * if event queue is full preventing {@code event} from being
     * enqueued.
     */
    /* package */ <E extends IEfsEvent> void dispatch(final Consumer<E> callback,
                                                      final E event)
    {
        dispatch(new EventTask<>(event, callback));
    } // end of dispatch(Consumer<>, E)

    /**
     * Posts task to run queue. If run queue was initially empty,
     * then this agent is posted to its dispatcher's run queue.
     * @param task post this task to agent event queue.
     * @throws IllegalStateException
     * if event queue is full preventing {@code task} from being
     * enqueued.
     */
    /* package */ void dispatch(final Runnable task)
    {
        // Is the event queue full?
        if (!mEvents.offer(task))
        {
            throw (
                new IllegalStateException(
                    String.format(
                        "failed to add %s task to event queue; task will not be run",
                        (task.getClass()).getSimpleName())));
        }
        // This agent has at least one event to run so put it on
        // the dispatcher run queue.
        else
        {
            postToRunQueue();
        }
    } // end of dispatch(Runnable)

    /**
     * Updates agent run-time statistics based on the latest
     * run.
     * @param runTime latest nanosecond run-time.
     */
    /* package */ void updateRunStats(final long runTime)
    {
        if (runTime > 0L)
        {
            if (mMinimumRunTime == 0 ||
                runTime < mMinimumRunTime)
            {
                mMinimumRunTime = runTime;
            }

            if (runTime > mMaximumRunTime)
            {
                mMaximumRunTime = runTime;
            }

            mTotalRunTime += runTime;
            ++mRunCount;
        }
    } // end of updateRunStats(long)

    /**
     * Process either all enqueued events or until either
     * agent is no longer registered or {@link #mMaxEvents} limit
     * is reached. Returns number of processed events.
     * @return processed event count.
     */
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
            // Yes, there is a event to delivery.
            // So, deliver it from all evil.
            // forwardEvent catches any agent-thrown exceptions,
            // so a try-catch block is not needed here.
            ++retval;
            startTime = System.nanoTime();
            task.run();
            timeUsed = (System.nanoTime() - startTime);
            --eventsRemaining;
            runTime += timeUsed;
        }

        updateRunStats(runTime);

        // Mark this agent as idle and then attempt to put it
        // back on the run queue.
        runState(RunState.IDLE);
        mOnRunQueue.set(false);

        postToRunQueue();

        return (retval);
    } // end of processEvents()

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
    private void postToRunQueue()
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
            mReadyTimestamp = System.nanoTime();

            // No. Place this agent on the run queue.
            mDispatcher.dispatch(this);
        }
        // Else if this agent is either stopped or currently on
        // the run queue or running, so nothing has changed.
        // If this agent is currently running, then when
        // the event completes, the agent will be posted
        // back to the run queue.
    } // end of postToRunQueue()

    /**
     * Creates an event queue with given capacity based on
     * dispatcher thread count. If dispatcher has only one
     * thread, then a {@code MpscAtomicArrayQueue} is returned;
     * otherwise a {@code MpmcAtomicArrayQueue}.
     * <p>
     * Note: if dispatcher has multiple threads, then event queue
     * <em>must</em> have an bounded capacity. This means that
     * if event queue is set to unbounded, it is changed to
     * {@link #UNBOUNDED_MULTI_THREAD_CAPACITY}.
     * </p>
     * @param capacity event queue capacity.
     * @param threadCount dispatcher thread count.
     * @return event queue for given capacity and dispatcher
     * thread count.
     */
    private static Queue<Runnable> createEventQueue(final int capacity,
                                                    final int threadCount)
    {
        final Queue<Runnable> retval;

        if (threadCount == 1)
        {
            retval = new MpscAtomicArrayQueue<>(capacity);
        }
        else
        {
            retval = new MpmcAtomicArrayQueue<>(capacity);
        }

        return (retval);
    } // end of createEventQueue(int, int)

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Contains agent run time statistics which includes:
     * <ul>
     *   <li>
     *     minimum run time,
     *   </li>
     *   <li>
     *     maximum run time,
     *   </li>
     *   <li>
     *     total run time,
     *   </li>
     *   <li>
     *     average run time, and
     *   </li>
     *   <li>
     *     number of times posted to a dispatcher thread.
     *   </li>
     * </ul>
     * <p>
     * Note: all times are in nanoseconds.
     * </p>
     *
     * @see #generateRunStats()
     * @see #runTimeStats()
     */
    public static final class AgentStats
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * efs agent name.
         */
        private final String mAgentName;

        /**
         * Agent event queue size as of this report.
         */
        private final int mEventQueueSize;

        /**
         * Minimum nanoseconds spent processing messages.
         */
        private final long mMinimumRunTime;

        /**
         * Maximum nanoseconds spent processing messages.
         */
        private final long mMaximumRunTime;

        /**
         * Total nanoseconds spent processing messages.
         */
        private final long mTotalRunTime;

        /**
         * Number of times this agent has been on core.
         */
        private final long mRunCount;

        /**
         * Dispatcher responsible for running this object.
         */
        private final String mDispatcherName;

        /**
         * Dispatcher's maximum allowed events per agent call out.
         */
        private final int mMaxEvents;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        @SuppressWarnings({"java:S107"})
        private AgentStats(final String agentName,
                           final int eventQueueSize,
                           final long minRunTime,
                           final long maxRunTime,
                           final long totalRunTime,
                           final long runCount,
                           final String dispatcherName,
                           final int maxEvents)
        {
            mAgentName = agentName;
            mEventQueueSize = eventQueueSize;
            mMinimumRunTime = minRunTime;
            mMaximumRunTime = maxRunTime;
            mTotalRunTime = totalRunTime;
            mRunCount = runCount;
            mDispatcherName = dispatcherName;
            mMaxEvents = maxEvents;
        } // end of AgentStats(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        /**
         * Returns efs agent's run time statistics as text.
         * @return textual representation of run time
         * statistics.
         */
        @Override
        public String toString()
        {
            final long avgRunTime =
                (mRunCount == 0L ?
                 0L :
                 (mTotalRunTime / mRunCount));

            return (
                String.format(
                    "%s%nevent queue size: %,d" +
                    "%n    min run time: %,d nanos" +
                    "%n    max run time: %,d nanos" +
                    "%n  total run time: %,d nanos" +
                    "%n       run count: %,d" +
                    "%n    avg run time: %,d nanos" +
                    "%n      dispatcher: %s" +
                    "%n      max events: %,d",
                    mAgentName,
                    mEventQueueSize,
                    mMinimumRunTime,
                    mMaximumRunTime,
                    mTotalRunTime,
                    mRunCount,
                    avgRunTime,
                    mDispatcherName,
                    mMaxEvents));
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        public String getAgentName()
        {
            return (mAgentName);
        } // end of getAgentName()

        public int getEventQueueSize()
        {
            return (mEventQueueSize);
        } // end of getEventQueueSize()

        public long getMinimumRunTime()
        {
            return (mMinimumRunTime);
        } // end of getMinimumRunTime()

        public long getMaximumRunTime()
        {
            return (mMaximumRunTime);
        } // end of getMaximumRunTime()

        public long getTotalRunTime()
        {
            return (mTotalRunTime);
        } // end of getTotalRunTime()

        public long getRunCount()
        {
            return (mRunCount);
        } // end of getRunCount()

        public String getDispatcherName()
        {
            return (mDispatcherName);
        } // end of getDispatcherName()

        public int getMaxEvents()
        {
            return (mMaxEvents);
        } // end of getMaxEvents()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of class AgentStats

    /**
     * {@code EfsAgent} builder used by {@link EfsDispatcher}
     * to create an agent instance encapsulating a
     * {@link IEfsAgent} instance.
     */
    /* package */ static final class Builder
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * efs agent encapsulated in agent.
         */
        private IEfsAgent mAgent;

        /**
         * Maximum number of events per agent callout.
         */
        private int mMaxEvents;

        /**
         * efs agent is associated with this dispatcher.
         */
        private IEfsDispatcher mDispatcher;

        /**
         * Maximum allowed {@code IEfsAgent} events queue
         * capacity.
         */
        private int mEventQueueCapacity;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Builder()
        {
            mEventQueueCapacity = 0;
        } // end of Builder()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets the encapsulated agent to which events are
         * posted.
         * @param target encapsulated agent.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code target} is {@code null}.
         */
        public Builder agent(final IEfsAgent target)
        {
            mAgent =
                Objects.requireNonNull(target, "target is null");

            return (this);
        } // end of agent(IEfsAgent)

        /**
         * Sets dispatcher used to post events to
         * encapsulated agent.
         * @param dispatcher post events to encapsulated agent
         * using this dispatcher.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code dispatcher} is {@code null}.
         */
        public Builder dispatcher(final IEfsDispatcher dispatcher)
        {
            mDispatcher =
                Objects.requireNonNull(
                    dispatcher, "dispatcher is null");

            return (this);
        } // end of dispatcher(IEfsDispatcher)

        /**
         * Set maximum allowed events per dispatch.
         * @param maxEvents maximum number of events per
         * dispatch.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code maxEvents} &le; zero.
         */
        public Builder maxEvents(final int maxEvents)
        {
            if (maxEvents <= 0)
            {
                throw (
                    new IllegalArgumentException(
                        "maxEvents <= zero"));
            }

            mMaxEvents = maxEvents;

            return (this);
        } // end of maxEvents(int)

        /**
         * Sets agent event queue capacity.
         * @param capacity event queue capacity.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code capacity} &le; zero.
         */
        public Builder eventQueueCapacity(final int capacity)
        {
            if (capacity <= 0)
            {
                throw (
                    new IllegalArgumentException(
                        "capacity <= zero"));
            }

            mEventQueueCapacity = capacity;

            return (this);
        } // end of eventQueueCapacity(int)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Returns newly constructed efs agent based on this
         * builder's settings.
         * @return efs agent constructed from this builder's
         * settings.
         * @throws ValidationException
         * if this builder contains one or more invalid settings.
         */
        public EfsAgent build()
        {
            validate();

            return (new EfsAgent(this));
        } // end of build()

        /**
         * Validates that required fields are set.
         */
        private void validate()
        {
            final Validator problems = new Validator();

            problems.requireNotNull(mAgent, "agent")
                    .requireTrue((mMaxEvents > 0),
                                 "maxEvents",
                                 Validator.NOT_SET)
                    .requireNotNull(mDispatcher, "dispatcher")
                    .requireTrue((mEventQueueCapacity > 0),
                                 "eventQueueCapacity",
                                 Validator.NOT_SET)
                    .throwException(EfsAgent.class);
        } // end of validate()
    } // end of class Builder

    /**
     * Task used to deliver an event to an efs agent using a
     * {@code Consumer} callback.
     *
     * @param <E> event class.
     */
    private final class EventTask<E extends IEfsEvent>
        implements Runnable
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Deliver this event to efs agent.
         */
        private final E mEvent;

        /**
         * Deliver event using this lambda expression.
         */
        private final Consumer<E> mCallback;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates an event delivery task for given event and
         * lambda expression.
         * @param event deliver this event.
         * @param callback consumer lambda expression used to
         * deliver this event.
         */
        private EventTask(final E event,
                          final Consumer<E> callback)
        {
            mEvent = event;
            mCallback = callback;
        } // end of EventTask(IEfsEvent, Consumer<>)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Runnable Interface Impelementation.
        //

        /**
         * Passes event to agent using the configured consumer.
         */
        @Override
        public void run()
        {
            try
            {
                mCallback.accept(mEvent);
            }
            catch (Exception jex)
            {
                sLogger.warn(
                    "{}: exception when forwarding {} event\n{}.",
                    mAgent.name(),
                    (mEvent.getClass()).getName(),
                    mEvent,
                    jex);
            }
        } // end of run()

        //
        // end of Runnable Interface Impelementation.
        //-------------------------------------------------------
    } // end of class EventTask
} // end of class EfsAgent
