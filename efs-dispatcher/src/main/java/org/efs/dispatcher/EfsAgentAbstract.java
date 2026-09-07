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

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import javax.annotation.concurrent.Immutable;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import org.efs.event.IEfsEvent;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;
import org.jctools.queues.atomic.MpscAtomicArrayQueue;
import org.jctools.util.Pow2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for {@link EfsAgent} and {@link EfsAgentPinned}
 * classes. Defines those data and method members common to
 * both.
 *
 * @see EfsAgent
 * @see EfsAgentPinned
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

/* package */ abstract class EfsAgentAbstract
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
         * efs agent has no pending events and is not on run
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
    // Constants.
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
        LoggerFactory.getLogger(EfsAgentAbstract.class);

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * efs agent encapsulated in this agent.
     */
    protected final IEfsAgent mAgent;

    /**
     * Post this agent when it is ready to run to this
     * dispatcher for later execution.
     */
    protected final IEfsDispatcher mDispatcher;

    /**
     * Set to {@code true} when underlying agent is registered
     * with its dispatcher and {@code false} when not. When an
     * agent is no longer registered, any enqueued events are
     * no longer delivered to the agent.
     */
    protected volatile boolean mIsRegistered;

    //
    // Executor data members.
    //

    /**
     * efs agent's pending callback events. If this queue is not
     * empty and agent run state is not
     * {@link RunState#RUNNING}, then this agent will be in
     * the dispatch table. When the encapsulated application
     * instance is finalized, this queue is cleared.
     */
    protected final Queue<Runnable> mEvents;

    /**
     * efs agent's event queue capacity.
     */
    protected final int mEventQueueCapacity;

    /**
     * An {@code EfsAgent} may continue running on a
     * {@link EfsDispatcherThread} as long as the agent has
     * pending events limited by this many events.
     */
    protected final int mMaxEvents;

    /**
     * This agent's current run state. This value is updated
     * when new events are dispatched and when pending events are
     * executed.
     */
    private volatile RunState mRunState;

    //
    // Execution statistics.
    //

    /**
     * Tracks number of events on agent's event queue. This
     * number is not expected to be exactly correct but roughly
     * correct.
     */
    protected final LongAdder mQueueSize;

    /**
     * Minimum nanoseconds spent processing messages.
     */
    protected final AtomicLong mMinimumRunTime;

    /**
     * Maximum nanoseconds spent processing messages.
     */
    protected final AtomicLong mMaximumRunTime;

    /**
     * Total nanoseconds spent processing messages.
     */
    protected final LongAdder mTotalRunTime;

    /**
     * Number of times this agent has been on core.
     */
    protected final LongAdder mRunCount;

    /**
     * Number of times attempt to dispatch this agent failed due
     * to run queue overflow.
     */
    protected final LongAdder mMissedDispatchCount;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of EfsAgentAbstract.
     */
    protected EfsAgentAbstract(final AgentBuilder<?, ?> builder)
    {
        mAgent = builder.mAgent;
        mDispatcher = builder.mDispatcher;
        mIsRegistered = true;

        mEventQueueCapacity = builder.mEventQueueCapacity;
        mMaxEvents = builder.mMaxEvents;
        mEvents =
            createEventQueue(mEventQueueCapacity,
                             mDispatcher.threadCount());

        mRunState = RunState.IDLE;

        mQueueSize = new LongAdder();
        mMinimumRunTime = new AtomicLong();
        mMaximumRunTime = new AtomicLong();
        mTotalRunTime = new LongAdder();
        mRunCount = new LongAdder();
        mMissedDispatchCount = new LongAdder();
    } // end of EfsAgentAbstract(AgentBuilder<>)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Abstract Method Declarations.
    //

    /**
     * Process events or tasks on agent event queue, returning
     * total number of events processed by this method call.
     * @return number of events processed by this method call.
     */
    /* package */ abstract int processEvents();

    //
    // end of Abstract Method Declarations.
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
    public final IEfsAgent agent()
    {
        return (mAgent);
    } // end of agent()

    /**
     * Returns efs agent name.
     * @return efs agent name.
     */
    public final String agentName()
    {
        return (mAgent.name());
    } // end of agentName()

    /**
     * Returns event queue capacity.
     * @return event queue capacity.
     */
    public final int eventQueueCapacity()
    {
        return (mEventQueueCapacity);
    } // end of eventQueueCapacity()

    /**
     * Returns efs agent's maximum allowed events processed per
     * run.
     * @return agent maximum allowed events.
     */
    public final int maxEvents()
    {
        return (mMaxEvents);
    } // end oof maxEvents()

    /**
     * Returns {@code true} if this agent is registered with an
     * efs dispatcher and {@code false} otherwise.
     * @return {@code true} if this agent is registered with an
     * efs dispatcher.
     */
    public final boolean isRegistered()
    {
        return (mIsRegistered);
    } // end of isRegistered()

    /**
     * Returns current agent run state.
     * @return agent run state.
     */
    public final RunState runState()
    {
        return (mRunState);
    } // end of runState()

    /**
     * Returns this agent's associated efs dispatcher instance.
     * @return efs dispatcher.
     */
    /* package */ final IEfsDispatcher dispatcher()
    {
        return (mDispatcher);
    } // end of dispatcher()

    /**
     * Returns an efs agent information instance based on this
     * agent's settings.
     * <p>
     * <strong>Note:</strong> the returned stats are
     * <em>approximate</em> and should be used as such.
     * </p>
     * @return efs agent information instance.
     */
    public final AgentStats generateRunStats()
    {
        return (new AgentStats(agentName(),
                               mQueueSize.sum(),
                               mMinimumRunTime.get(),
                               mMaximumRunTime.get(),
                               mTotalRunTime.sum(),
                               mRunCount.sum(),
                               mMissedDispatchCount.sum(),
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
    protected final void runState(final RunState state)
    {
        if (sLogger.isTraceEnabled())
        {
            sLogger.trace(
                "{}: run state set to {}, dispatcher {}.",
                mAgent.name(),
                state,
                mDispatcher.name());
        }

        mRunState = state;
    } // end of runState(RunState)

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    /**
     * Marks this agent as no longer registered. Any pending
     * events will not be forwarded to agent.
     *
     * @see EfsDispatcher#deregister(IEfsAgent)
     */
    /* package */ void deregister()
    {
        mIsRegistered = false;
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
     *
     * @see EfsDispatcher#dispatch(Consumer, IEfsEvent, IEfsAgent)
     */
    /* package */ final <E extends IEfsEvent> void dispatch(final Consumer<E> callback,
                                                            final E event)
    {
        dispatch(new EventTask<>(mAgent.name(), event, callback));
    } // end of dispatch(Consumer<>, E)

    /**
     * Posts task to agent event queue.
     * @param task post this task to agent event queue.
     * @throws IllegalStateException
     * if event queue is full preventing {@code task} from being
     * enqueued.
     *
     * @see EfsDispatcher#dispatch(Runnable, IEfsAgent)
     */
    /* package */ void dispatch(final Runnable task)
    {
        // Is the event queue full?
        if (!mEvents.offer(task))
        {
            throw (
                new IllegalStateException(
                    String.format(
                        "failed to add %s task to %s event queue; task will not be run",
                        (task.getClass()).getSimpleName(),
                        mAgent.name())));
        }
        // This agent has at least one event to run so put it on
        // the dispatcher run queue.
        else
        {
            mQueueSize.increment();
        }
    } // end of dispatch(Runnable)

    /**
     * Updates agent run-time statistics based on the latest
     * run.
     * @param runTime latest nanosecond run-time.
     */
    /* package */ final void updateRunStats(final long runTime)
    {
        if (runTime > 0L)
        {
            mMinimumRunTime.updateAndGet(
                prev -> (prev == 0L ?
                         runTime :
                         Math.min(prev, runTime)));
            mMaximumRunTime.updateAndGet(
                prev -> Math.max(prev, runTime));

            mTotalRunTime.add(runTime);
            mRunCount.increment();
        }
    } // end of updateRunStats(long)

    /**
     * Creates an event queue with given capacity based on
     * dispatcher thread count. If dispatcher has only one
     * thread, then a {@code MpscAtomicArrayQueue} is returned;
     * otherwise a {@code MpmcAtomicArrayQueue}.
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
     * (Note: all times are in nanoseconds.)
     * </p>
     * <p>
     * <strong>Note:</strong> instances contain
     * <em>approximate</em> stats which should be use
     * accordingly.
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
        private final long mEventQueueSize;

        /**
         * Minimum nanoseconds spent processing events.
         */
        private final long mMinimumRunTime;

        /**
         * Maximum nanoseconds spent processing events.
         */
        private final long mMaximumRunTime;

        /**
         * Total nanoseconds spent processing events.
         */
        private final long mTotalRunTime;

        /**
         * Number of times this agent has been on core.
         */
        private final long mRunCount;

        /**
         * Number of times this agent failed to be dispatched
         * due to run queue overflow.
         */
        private final long mMissedDispatchCount;

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
                           final long eventQueueSize,
                           final long minRunTime,
                           final long maxRunTime,
                           final long totalRunTime,
                           final long runCount,
                           final long missedDispatchCount,
                           final String dispatcherName,
                           final int maxEvents)
        {
            mAgentName = agentName;
            mEventQueueSize = eventQueueSize;
            mMinimumRunTime = minRunTime;
            mMaximumRunTime = maxRunTime;
            mTotalRunTime = totalRunTime;
            mRunCount = runCount;
            mMissedDispatchCount = missedDispatchCount;
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
                    "%n missed dispatch: %,d" +
                    "%n    avg run time: %,d nanos" +
                    "%n      dispatcher: %s" +
                    "%n      max events: %,d",
                    mAgentName,
                    mEventQueueSize,
                    mMinimumRunTime,
                    mMaximumRunTime,
                    mTotalRunTime,
                    mRunCount,
                    mMissedDispatchCount,
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

        /**
         * Returns efs agent name.
         * @return efs agent name.
         */
        public String getAgentName()
        {
            return (mAgentName);
        } // end of getAgentName()

        /**
         * Returns agent event queue size as of this report. This
         * size is approximate due to it being a snapshot of an
         * active agent's metrics.
         * @return agent event queue size.
         */
        public long getEventQueueSize()
        {
            return (mEventQueueSize);
        } // end of getEventQueueSize()

        /**
         * Returns minimum nanoseconds spent processing events
         * in a single run. Will be zero if agent has never
         * processed events.
         * @return minimum event processing time in nanoseconds.
         */
        public long getMinimumRunTime()
        {
            return (mMinimumRunTime);
        } // end of getMinimumRunTime()

        /**
         * Returns maximum nanoseconds spent processing events in
         * a single run. Will be zero if agent has never
         * processed events.
         * @return maximum event processing time in nanoseconds.
         */
        public long getMaximumRunTime()
        {
            return (mMaximumRunTime);
        } // end of getMaximumRunTime()

        /**
         * Returns total nanoseconds spent processing events for
         * all runs. Will be zero if agent has never processed
         * events.
         * @return total event processing time in nanoseconds.
         */
        public long getTotalRunTime()
        {
            return (mTotalRunTime);
        } // end of getTotalRunTime()

        /**
         * Return number of times this agent has been on thread
         * processing events.
         * @return agent run count.
         */
        public long getRunCount()
        {
            return (mRunCount);
        } // end of getRunCount()

        /**
         * Returns number of times this agent failed to be
         * dispatched due to run queue overflow.
         * @return missed agent dispatch count.
         */
        public long getMissedDispatchCount()
        {
            return (mMissedDispatchCount);
        } // end of getMissedDispatchCount()

        /**
         * Returns name of dispatcher responsible for running
         * this object.
         * @return agent's dispatcher's name.
         */
        public String getDispatcherName()
        {
            return (mDispatcherName);
        } // end of getDispatcherName()

        /**
         * Returns dispatcher's maximum allowed events per agent
         * run.
         * @return dispatcher's maximum events per agent run.
         */
        public int getMaxEvents()
        {
            return (mMaxEvents);
        } // end of getMaxEvents()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of class AgentStats

    /**
     * Base builder for concrete efs agent implementations.
     * <p>
     * This builder holds the configuration shared by all agent
     * types, including the encapsulated target agent, the
     * dispatcher responsible for delivering events, the maximum
     * events processed per dispatch, and the agent event queue
     * capacity. Concrete subclasses override {@link #self()} and
     * {@link #buildImpl()} to return their specific builder type
     * and construct the target agent instance after validation.
     * </p>
     *
     * @param <A> target efs agent class.
     * @param <B> target efs agent builder class.
     */
    protected abstract static class AgentBuilder<A extends EfsAgentAbstract,
                                                 B extends AgentBuilder<A, ?>>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Building an instance of this agent class.
         */
        protected final Class<A> mAgentClass;

        /**
         * efs agent encapsulated in agent.
         */
        protected IEfsAgent mAgent;

        /**
         * Maximum number of events per agent callout. This value
         * is zero for pinned agents - meaning there is no limit.
         */
        protected int mMaxEvents;

        /**
         * efs agent is associated with this dispatcher.
         */
        protected IEfsDispatcher mDispatcher;

        /**
         * Maximum allowed {@code IEfsAgent} events queue
         * capacity.
         */
        protected int mEventQueueCapacity;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        protected AgentBuilder(final Class<A> agentClass)
        {
            mAgentClass = agentClass;

            mEventQueueCapacity = 0;
        } // end of AgentBuilder(Class)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Declarations.
        //

        /**
         * Returns {@code this} builder instance.
         * @return {@code this} builder instance.
         */
        protected abstract B self();

        /**
         * Returns new efs agent instance based on this builder's
         * settings.
         * @return new efs agent instance.
         */
        protected abstract A buildImpl();

        //
        // end of Abstract Method Declarations.
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
        public B agent(final IEfsAgent target)
        {
            mAgent =
                Objects.requireNonNull(
                    target, EfsDispatcher.NULL_DISPATCH_TARGET);

            return (self());
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
        public B dispatcher(final IEfsDispatcher dispatcher)
        {
            mDispatcher =
                Objects.requireNonNull(
                    dispatcher, EfsDispatcher.NULL_DISPATCHER);

            return (self());
        } // end of dispatcher(IEfsDispatcher)

        /**
         * Set maximum allowed events per dispatch.
         * @param maxEvents maximum number of events per
         * dispatch.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code maxEvents} &le; zero.
         */
        public B maxEvents(final int maxEvents)
        {
            if (maxEvents <= 0)
            {
                throw (
                    new IllegalArgumentException(
                        EfsDispatcher.INVALID_MAX_EVENTS));
            }

            mMaxEvents = maxEvents;

            return (self());
        } // end of maxEvents(int)

        /**
         * Sets agent event queue capacity. If capacity is not a
         * power of 2, then increases capacity to next highest
         * power of 2.
         * @param capacity event queue capacity.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code capacity} &le; zero or next highest power
         * of 2 exceeds2^31.
         */
        public B eventQueueCapacity(final int capacity)
        {
            if (capacity < EfsDispatcher.MIN_QUEUE_SIZE)
            {
                throw (
                    new IllegalArgumentException(
                        EfsDispatcher.INVALID_EVENT_QUEUE_CAPACITY));
            }

            mEventQueueCapacity =
                Pow2.roundToPowerOfTwo(capacity);

            return (self());
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
        /* package */ A build()
        {
            final Validator problems = new Validator();

            validate(problems).throwException(mAgentClass);

            return (buildImpl());
        } // end of build()

        /**
         * Validates that required fields are set.
         */
        protected Validator validate(final Validator problems)
        {
            return (problems.requireNotNull(mAgent, "agent")
                            .requireNotNull(mDispatcher,
                                            "dispatcher")
                            .requireTrue((mEventQueueCapacity > 0),
                                         "eventQueueCapacity",
                                         Validator.NOT_SET));
        } // end of validate()
    } // end of class AgentBuilder

    /**
     * Task used to deliver an event to an efs agent using a
     * {@code Consumer} callback.
     *
     * @param <E> event class.
     */
    @Immutable
    private static final class EventTask<E extends IEfsEvent>
        implements Runnable
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Forwarding task to this agent.
         */
        private final String mAgentName;

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
         * <p>
         * <strong>Note:</strong> caller has validated argument
         * correctness.
         * </p>
         * @param agentName deliver event to this named agent.
         * @param event deliver this event.
         * @param callback consumer lambda expression used to
         * deliver this event.
         */
        private EventTask(final String agentName,
                          final E event,
                          final Consumer<E> callback)
        {
            mAgentName = agentName;
            mEvent = event;
            mCallback = callback;
        } // end of EventTask(String, IEfsEvent, Consumer<>)

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
                    "{}: exception when forwarding {} event.",
                    mAgentName,
                    (mEvent.getClass()).getName(),
                    jex);
            }
        } // end of run()

        //
        // end of Runnable Interface Impelementation.
        //-------------------------------------------------------
    } // end of class EventTask
} // end of class EfsAgentAbstract
