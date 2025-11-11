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

import com.google.common.base.Strings;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import javax.annotation.Nullable;
import net.openhft.affinity.AffinityLock;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import org.efs.dispatcher.config.ThreadAffinity;
import org.efs.dispatcher.config.ThreadAffinityConfig;
import org.efs.dispatcher.config.ThreadAffinityConfig.AffinityType;
import org.efs.dispatcher.config.ThreadType;
import static org.efs.dispatcher.config.ThreadType.BLOCKING;
import static org.efs.dispatcher.config.ThreadType.SPINNING;
import static org.efs.dispatcher.config.ThreadType.SPINPARK;
import org.efs.logging.AsyncLoggerFactory;
import org.jctools.queues.atomic.AtomicReferenceArrayQueue;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

/**
 * A dispatcher thread watches a given
 * {@link java.util.concurrent.ConcurrentLinkedQueue run queue}
 * for {@link EfsAgent} instances ready to run, attempting to
 * acquire the next ready agent. When this thread successfully
 * acquires a agent, it has the agent execute its pending
 * events until either 1) the agent has no more events or 2) the
 * agent exhausts its maximum allowed event limit.
 * <p>
 * Each dispatcher has a configurable run-time maximum event
 * (defaults to {@link #DEFAULT_MAX_EVENTS}). When a agent
 * exhausts this limit <em>and</em> still has events to process,
 * the agent is placed at the end of the run queue and the
 * agent's event limit is replenished.
 </p>
 * <p>
 * A agent instance is referenced by only one dispatcher thread
 * at a time. This means a agent is effectively single-threaded
 * even though over time it may be dispatched by multiple,
 * different threads.
 * </p>
 * <p>
 * <strong>Note:</strong> this <em>only</em> applies to efs
 * dispatcher threads. Non-dispatcher threads may still access
 * a agent object at the same time.
 * </p>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsDispatcherThread
    extends Thread
{
//---------------------------------------------------------------
// Member enums.
//

    /**
     * A dispatcher thread is either not running, idle (waiting
     * for an available efs agent) or busy (running an efs
     * agent).
     */
    public enum DispatcherThreadState
    {
        /**
         * Dispatcher thread is not running due to it not yet
         * being started.
         */
        NOT_STARTED,

        /**
         * Dispatcher thread is waiting for the next
         * available efs agent to run.
         */
        IDLE,

        /**
         * Dispatcher thread is busy running efs agent events.
         */
        BUSY
    } // end of enum DispatcherThreadState

//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * The default run maximum events is {@value}.
     */
    public static final int DEFAULT_MAX_EVENTS = 4;

    /**
     * Set efs agent name to {@value} when dispatcher thread is
     * idle.
     */
    public static final String NO_EFS_AGENT = "(idle)";

    /**
     * Collect at most {@value} agent statistics.
     */
    public static final int MAX_AGENT_STATS = 1_024;

    /**
     * Bit mask matching {@link #MAX_AGENT_STATS}.
     */
    private static final int MAX_AGENT_MASK = 0x3ff;

    /**
     * Timestamps are reported in {@value}.
     */
    private static final String NANO_UNIT = "nanos";

    /**
     * Event count is reported in {@value}.
     */
    private static final String EVENT_UNIT = "events";

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * If dispatcher has multiple dispatcher threads
     * <em>and</em> defined thread affinity, then this is the
     * affinity lock assigned to the previous dispatcher
     * thread. This lock is used when affinity type is a
     * CPU selection strategy.
     */
    private static AffinityLock sPreviousLock = null;

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger();

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Dispatcher thread type.
     */
    private final ThreadType mThreadType;

    /**
     * This thread takes ready efs clients from this run
     * queue
     */
    private final Queue<EfsAgent> mRunQueue;

    /**
     * Use this method to extract clients from
     * {@link #mRunQueue}.
     */
    private final IPollInterface<EfsAgent> mPollMethod;

    /**
     * Spin limit used when {@link #spinParkPoll()} or
     * {@link #spinYieldPoll()} poll method is used.
     */
    private final long mSpinLimit;

    /**
     * Nanosecond park time used when
     * {@link #spinParkPoll()} poll method is used.
     */
    private final long mParkTime;

    /**
     * Thread affinity configuration. Used to associate thread
     * with a CPU. Set to {@code null} if the dispatcher
     * thread has no CPU affinity.
     */
    @Nullable private final ThreadAffinityConfig mAffinity;

    /**
     * Maximum number of events a agent may run per call out.
     */
    private final int mMaxEvents;

    /**
     * Dispatcher thread continues running while this flag is
     * {@code true}.
     */
    private volatile boolean mRunFlag;

    //
    // Performance statistics.
    //

    /**
     * Contains latest performance statistics for this
     * dispatcher thread.
     */
    private final DispatcherThreadStats mStats;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new efs dispatcher thread instance based on
     * builder settings.
     * @param builder contains dispatcher thread settings.
     */
    private EfsDispatcherThread(final Builder builder)
    {
        super (builder.mThreadName);

        mRunQueue = builder.mRunQueue;
        mThreadType = builder.mThreadType;

        mPollMethod = switch (mThreadType)
                      {
                          case BLOCKING -> this::blockingPoll;
                          case SPINNING -> this::spinningPoll;
                          case SPINPARK -> this::spinParkPoll;
                          default -> this::spinYieldPoll;
                      };
        mSpinLimit = builder.mSpinLimit;
        mParkTime = (builder.mParkTime).toNanos();
        mAffinity = builder.mAffinity;
        mMaxEvents = builder.mMaxEvents;
        mRunFlag = true;

        mStats = new DispatcherThreadStats();

        // Note: these settings *must* be done in the
        // constructor.
        this.setPriority(builder.mPriority);
        this.setDaemon(true);
    } // end of EfsDispatcherThread(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Thread Method Overrides.
    //

    /**
     * A dispatcher thread continues processing ready
     * efs clients until the JVM exits. On each iteration,
     * this thread acquires the next available agent. Then
     * iterates over the agent's event list, delivering each
     * event in turn. This continues until the agent either has no
     * more events or has used up its maximum event limit. The
     * agent is put back on the run queue and this thread goes
     * back to the loop's top, acquiring next available agent.
     * <p>
     * Because an efs agent is posted to only one, unique
     * run queue and each {@code Dispatcher} thread works
     * with only one run queue, once a dispatcher thread
     * acquires an efs agent, it is guaranteed that no
     * other <em>dispatcher</em> thread has access to the
     * agent. Therefore, from an efs perspective, efs
     * agent access is single-threaded.
     * </p>
     * <p>
     * (Non-dispatcher threads may still access an agent
     * simultaneously as a dispatcher thread. This is an
     * application design decision and requires proper
     * synchronization.)
     * </p>
     */
    @SuppressWarnings ({"java:S2696", "java:S3776"})
    @Override
    public void run()
    {
        final String name = this.getName();
        EfsAgent agent;
        long readyTime;
        long busyStart;
        long busyStop;
        long busyTime;
        int eventCount;

        mStats.startTime(Instant.now());
        mStats.updateState(DispatcherThreadState.IDLE,
                           NO_EFS_AGENT);

        // If dispatcher is configured for thread affinity, then
        // put that affinity in place here.
        if (mAffinity != null)
        {
            setAffinity();
        }

        sLogger.debug("{}: running.", name);
        sLogger.trace("{}: polling run queue {}.",
                      name,
                      mRunQueue);

        // Note: once started, a dispatcher thread runs until
        // JVM is stopped - or the world comes to an end,
        // whichever happens first.
        while (mRunFlag)
        {
            // Get the next agent.
            // Note: poll *never* returns null.
            agent = mPollMethod.poll();

            mStats.updateState(DispatcherThreadState.BUSY,
                               agent.agentName());
            busyStart = System.nanoTime();

            sLogger.trace("{}: processing agent {}.",
                          name,
                          agent.agentName());


            // Have the agent process its events.
            eventCount = agent.processEvents();

            busyStop = System.nanoTime();
            readyTime =
                (busyStart - agent.getAndClearReadyTimestamp());
            busyTime = (busyStop - busyStart);

            // Agent finished with its event processing.
            mStats.updateState(DispatcherThreadState.IDLE,
                               NO_EFS_AGENT);
            mStats.updateAgentStats(readyTime,
                                    busyTime,
                                    eventCount);
        }
    } // end of run()

    //
    // end of Thread Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    @SuppressWarnings({"java:S1192"})
    @Override
    public String toString()
    {
        final StringBuilder output = new StringBuilder();

        output.append('[').append(getName())
              .append(", type=").append(mThreadType)
              .append(", state=").append(mStats.threadState());

        if (mThreadType == ThreadType.SPINPARK ||
            mThreadType == ThreadType.SPINYIELD)
        {
            output.append(", spin limit=").append(mSpinLimit);

            if (mThreadType == ThreadType.SPINPARK)
            {
                output.append(", park time=").append(mParkTime);
            }
        }

        return (output.append(']').toString());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns dispatcher thread type.
     * @return thread type.
     */
    public ThreadType threadType()
    {
        return (mThreadType);
    } // end of threadType()

    /**
     * Returns thread maximum events per agent call out.
     * @return maximum events per agent call out.
     */
    public int maxEvents()
    {
        return (mMaxEvents);
    } // end of maxEvents()

    /**
     * Returns thread affinity configuration. May return
     * {@code null}.
     * @return thread affinity configuration.
     */
    @Nullable
    public ThreadAffinityConfig affinity()
    {
        return (mAffinity);
    } // end of affinity()

    /**
     * Returns spin limit for spin+park/spin+yield thread type.
     * Otherwise returns zero.
     * @return spin+park/spin+yield spin limit.
     */
    public long spinLimit()
    {
        return (mSpinLimit);
    } // end of spinLimit()

    /**
     * Returns nanosecond park time for spin+park thread type.
     * Otherwise returns zero.
     * @return nanosecond park time.
     */
    public long parkTime()
    {
        return (mParkTime);
    } // end of parkTime()

    /**
     * Returns this dispatcher threads performance statistics.
     * The returned object is immutable from the
     * @return dispatcher thread performance statistics.
     *
     * @see DispatcherThreadStats
     */
    public DispatcherThreadStats performanceStats()
    {
        return (mStats);
    } // end of performanceStats()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Queue Polling Methods.
    //
    // Following method implement IPollInterface. Method used is
    // based on ThreadType.
    // Note: the only difference between blocking, spinning, etc.
    // thread types is how the run queue is polled. Everything
    // else in the run() method is the same.
    //

    /**
     * Returns the next available agent from the run queue,
     * blocking until the agent arrives.
     * @return next available agent. Does <em>not</em> return
     * {@code null}.
     */
    @NonNull private EfsAgent blockingPoll()
    {
        EfsAgent retval = null;

        while (retval == null)
        {
            try
            {
                retval =
                    ((BlockingQueue<EfsAgent>)
                        mRunQueue).take();
            }
            catch (InterruptedException interrupt)
            {
                // Pass this interrupt through.
                this.interrupt();
            }
        }

        return (retval);
    } // end of blockingPoll()

    /**
     * Actively spins calling
     * {@link ConcurrentLinkedQueue#poll()} to extract the
     * next available agent from the run queue.
     * @return next available agent. Does <em>not</em> return
     * {@code null}.
     */
    @NonNull private EfsAgent spinningPoll()
    {
        EfsAgent retval = null;

        while (retval == null)
        {
            retval = mRunQueue.poll();
        }

        return (retval);
    } // end of spinningPoll()

    /**
     * Spins a fixed number of times calling
     * {@link ConcurrentLinkedQueue#poll()} to extract the
     * next available agent from the run queue. When the
     * spin limit is reached, then parks for a fixed number
     * of nanoseconds.
     * @return next available agent. Does <em>not</em> return
     * {@code null}.
     */
    @NonNull private EfsAgent spinParkPoll()
    {
        long counter = mSpinLimit;
        EfsAgent retval = null;

        while (retval == null)
        {
            // Spin limit reached?
            if (counter == 0)
            {
                // Yes. Take a nap before continuing.
                LockSupport.parkNanos(mParkTime);
                counter = mSpinLimit;
            }

            retval = mRunQueue.poll();
            --counter;
        }

        return (retval);
    } // end of spinParkPoll()

    /**
     * Spins a fixed number of times calling
     * {@link ConcurrentLinkedQueue#poll()} to extract the
     * next available agent from the run queue. When the
     * spin limit is reached, then this Dispatcher thread
     * yields.
     * @return next available agent. Does <em>not</em> return
     * {@code null}.
     */
    @SuppressWarnings ("CallToThreadYield")
    @NonNull private EfsAgent spinYieldPoll()
    {
        long counter = mSpinLimit;
        EfsAgent retval = null;

        while (retval == null)
        {
            // Spin limit reached?
            if (counter == 0)
            {
                // Yes. Take a nap before continuing.
                Thread.yield();

                counter = mSpinLimit;
            }

            retval = mRunQueue.poll();
            --counter;
        }

        return (retval);
    } // end of spinYieldPoll()

    //
    // end of Queue Polling Methods.
    //-----------------------------------------------------------

    /**
     * Sets run flag to {@code false} which eventually stops
     * this dispatcher thread.
     */
    /* package */ void shutdown()
    {
        mRunFlag = false;
        this.interrupt();
    } // end of shutdown()

    /**
     * Returns a new dispatcher thread builder instance.
     * @return dispatcher thread builder.
     */
    /* package */ static Builder builder()
    {
        return (new Builder());
    } // end of Builder()

    @SuppressWarnings ({"java:S2696", "java:S3776"})
    private void setAffinity()
    {
        // Is this a strategy affinity?
        if (mAffinity.getAffinityType() ==
                AffinityType.CPU_STRATEGIES)
        {
            // Yes. Use the previous lock when selecting
            // this next lock.
            sPreviousLock =
                ThreadAffinity.acquireLock(
                    sPreviousLock, mAffinity);
        }
        // No. Apply the CPU selection independent of
        // previous lock.
        else
        {
            sPreviousLock =
                ThreadAffinity.acquireLock(mAffinity);
        }
    } // end of setAffinity()

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Use to create a new dispatcher thread for a given efs
     * agent run queue.
     * <p>
     * This method is package private to restrict access to
     * {@link EfsDispatcher}.
     * </p>
     */
    /* package */ static final class Builder
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private String mThreadName;
        private ThreadType mThreadType;
        private long mSpinLimit;
        private Duration mParkTime;
        private int mPriority;
        private ThreadAffinityConfig mAffinity;
        private int mMaxEvents;
        private Queue<EfsAgent> mRunQueue;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Builder()
        {
            mPriority = Thread.NORM_PRIORITY;
            mSpinLimit = 0;
            mParkTime = Duration.ZERO;
        } // end of builder()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets dispatcher thread name.
         * @param threadName dispatcher thread name.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code mThreadName} is either {@code null} or an
         * empty string.
         */
        /* package */ Builder threadName(final String threadName)
        {
            if (Strings.isNullOrEmpty(threadName))
            {
                throw (
                    new IllegalArgumentException(
                        "threadName is either null or an empty string"));
            }

            mThreadName = threadName;

            return (this);
        } // end of mThreadName(String)

        /**
         * Sets dispatcher thread type.
         * @param threadType dispatcher thread type.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code threadType} is {@code null}.
         */
        /* package */ Builder threadType(final ThreadType threadType)
        {
            mThreadType =
                Objects.requireNonNull(
                    threadType, "threadType is null");

            return (this);
        } // end of threadType(ThreadType)

        /**
         * Sets the thread priority. Must be
         * &ge; {@link Thread#MIN_PRIORITY} and &le;
         * {@link Thread#MAX_PRIORITY}. If not set, then defaults
         * to {@link Thread#NORM_PRIORITY}.
         * @param priority assigned thread priority for scheduled
         * executor thread.
         * @return {@code this} dispatcher builder.
         * @throws IllegalArgumentException
         * if {@code priority} &lt; {@code Threads.MIN_PRIORITY}
         * or &gt; {@code Thread.MAX_PRIORITY}.
         */
        /* package */ Builder priority(final int priority)
        {
            if (priority < Thread.MIN_PRIORITY ||
                priority > Thread.MAX_PRIORITY)
            {
                throw (
                    new IllegalArgumentException(
                        "priority out of bounds"));
            }

            mPriority = priority;

            return (this);
        } // end of priority(int)

        /**
         * Sets {@link ThreadType#SPINPARK} or
         * {@link ThreadType#SPINYIELD} spin limit. This setting
         * is ignored for any other dispatcher thread type.
         * @param limit spin limit.
         * @return {@code this} dispatcher builder.
         * @throws IllegalArgumentException
         * if {@code limit} &lt; zero.
         */
        /* package */ Builder spinLimit(final long limit)
        {
            if (limit < 0L)
            {
                throw (
                    new IllegalArgumentException(
                        "limit < zero"));
            }

            mSpinLimit = limit;

            return (this);
        } // end of spinLimit(long)

        /**
         * Sets {@link ThreadType#SPINPARK spin+park} park time
         * limit. This setting is ignored for any other
         * dispatcher thread type.
         * @param time park time limit.
         * @return {@code this} dispatcher builder.
         * @throws NullPointerException
         * if {@code time} is {@code null}
         * @throws IllegalArgumentException
         * if {@code time} is &lt; zero.
         */
        /* package */ Builder parkTime(final Duration time)
        {
            Objects.requireNonNull(time, "time is null");

            if (time.isNegative())
            {
                throw (
                    new IllegalArgumentException(
                        "time < zero"));
            }

            mParkTime = time;

            return (this);
        } // end of parkTime(Duration)

        /**
         * Sets optional thread affinity configuration.
         * @param affinity thread affinity configuration. May be
         * {@code null}.
         * @return {@code this} dispatcher builder.
         */
        /* package */ Builder affinity(@Nullable final ThreadAffinityConfig affinity)
        {
            mAffinity = affinity;

            return (this);
        } // end of affinity(ThreadAffinityConfig)

        /**
         * Sets run maxEvents assigned to each efs agent.
         * @param maxEvents maximum number of events which can be
         * run at a time.
         * @return {@code this} dispatcher builder.{@code this} dispatcher builder.
         * @throws IllegalArgumentException
         * if {@code maxEvents} is &le; zero.
         */
        /* package */ Builder maxEvents(final int maxEvents)
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
         * Sets efs agent queue monitored by this thread.
         * @param queue efs agent run queue.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code queue} is {@code null}.
         */
        /* package */ Builder runQueue(final Queue<EfsAgent> queue)
        {
            mRunQueue =
                Objects.requireNonNull(queue, "queue is null");

            return (this);
        } // end of runQueue(Queue<>)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Returns a new efs dispatcher thread using this
         * builder's current settings.
         * @return efs dispatcher thread.
         */
        /* package */ EfsDispatcherThread build()
        {
            validate();

            return (new EfsDispatcherThread(this));
        } // end of build()

        /**
         * Validates {@code EfsDispatcherThread} builder
         * settings. This validation is "fail slow" meaning
         * that a single validation call will determine all
         * configuration errors.
         * @throws ValidationException
         * if {@this Builder} instance contains one or more
         * invalid settings.
         */
        @SuppressWarnings ({"java:S1067"})
        private void validate()
        {
            final Validator problems = new Validator();

            problems.requireNotNull(mThreadName, "threadName")
                    .requireNotNull(mThreadType, "threadType")
                    .requireTrue((mMaxEvents > 0),
                                 "maxEvents",
                                 Validator.NOT_SET)
                    .requireNotNull(mRunQueue, "runQueue")
                    .requireTrue(
                        ((mThreadType != ThreadType.SPINPARK &&
                          mThreadType != ThreadType.SPINYIELD) ||
                         mSpinLimit > 0L),
                        "spinLimit",
                        "not set for spin+park/spin+yield thread type")
                    .requireTrue(
                        (mThreadType != ThreadType.SPINPARK ||
                         (mParkTime != null &&
                          mParkTime.isPositive())),
                        "parkTime",
                        "not set for spin+park thread type")
                    .requireTrue(((mThreadType == BLOCKING &&
                                   mRunQueue instanceof BlockingQueue) ||
                                  (mThreadType != BLOCKING &&
                                   mRunQueue instanceof AtomicReferenceArrayQueue)),
                                 "runQueue",
                                 "does not match thread type")
                    .throwException(EfsDispatcherThread.class);
        }
    } // end of class Builder

    /**
     * Contains the following dispatcher thread measurements
     * (all times are in nanoseconds):
     * <ul>
     *   <li>
     *     dispatcher thread start time,
     *   </li>
     *   <li>
     *     name of agent currently executing events (set to
     *     {@link #NO_EFS_AGENT} if no there is no executing
     *     agent),
     *   </li>
     *   <li>
     *     number of agents run by this thread,
     *   </li>
     *   <li>
     *     amount of time those agents spent on dispatcher ready
     *     queue,
     *   </li>
     *   <li>
     *     amount of time agents spend processing events, and
     *   </li>
     *   <li>
     *     number of events agents processed per run.
     *   </li>
     * </ul>
     * <p>
     * This object is mutable in that the dispatcher thread
     * updates the data overtime. It is expected that an
     * application will monitor this object to track the
     * thread's performance particularly agent time spent on
     * the dispatcher ready queue, agent time spent processing
     * events, and the number of events processed. If these
     * values are increasing over time, this shows a problem
     * with dispatcher and its threads unable to keep up with
     * inbound event delivery.
     * </p>
     *
     * @see EfsDispatcherThread.AgentStats
     */
    public final class DispatcherThreadStats
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Dispatcher thread start timestamp.
         */
        private volatile Instant mStartTime;

        /**
         * Current dispatcher thread state.
         */
        private volatile DispatcherThreadState mState;

        /**
         * efs agent name currently running on dispatcher thread.
         * If there is not agent running, then returns
         * {@link #NO_EFS_AGENT}.
         */
        private volatile String mAgentName;

        /**
         * Total number of efs agents run on this dispatcher thread.
         */
        private long mAgentRunCount;

        /**
         * Tracks time agent spent on its dispatcher's queue
         * before being removed by a dispatcher thread.
         */
        private final AgentStats mAgentReadyTime;

        /**
         * Tracks time agent spent processing events.
         */
        private final AgentStats mAgentRunTime;

        /**
         * Tracks number of events an agent processed on each
         * callout.
         */
        private final AgentStats mAgentEvent;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        @SuppressWarnings({"java:S107"})
        private DispatcherThreadStats()
        {
            mState = DispatcherThreadState.NOT_STARTED;
            mAgentName = NO_EFS_AGENT;
            mAgentRunCount = 0L;
            mAgentReadyTime = new AgentStats("agent ready deltas",
                                              NANO_UNIT);
            mAgentRunTime = new AgentStats("agent run deltas",
                                           NANO_UNIT);
            mAgentEvent = new AgentStats("agent event counts",
                                         EVENT_UNIT);
        } // end of DispatcherThreadStats(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        /**
         * Returns dispatcher thread information as text.
         * @return message field as text.
         */
        @Override
        public String toString()
        {
            final StringBuilder retval = new StringBuilder();

            return (retval.append("[thread=").append(getName())
                          .append(", start time=")
                          .append(mStartTime)
                          .append(", state=").append(mState)
                          .append(", agent=").append(mAgentName)
                          .append(", run count=")
                          .append(mAgentRunCount)
                          .append(",\n")
                          .append(mAgentReadyTime)
                          .append('\n')
                          .append(mAgentRunTime)
                          .append('\n')
                          .append(mAgentRunCount)
                          .append(']')
                          .toString());
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns dispatcher thread name.
         * @return thread name.
         */
        public String threadName()
        {
            return (getName());
        } // end of threadName()

        /**
         * Returns dispatcher thread start time. Returns
         * {@code null} if not yet started.
         * @return dispatcher thread start time.
         */
        @Nullable public Instant startTime()
        {
            return (mStartTime);
        } // end of startTime()

        /**
         * Returns dispatcher thread current state.
         * @return thread state.
         */
        public DispatcherThreadState threadState()
        {
            return (mState);
        } // end of threadState()

        /**
         * Returns currently executing agent's name. If thread
         * state is not running, then returns
         * {@link #NO_EFS_AGENT}.
         * @return currently executing agent's name or
         * {@link #NO_EFS_AGENT} if there is no executing agent.
         */
        public String agentName()
        {
            return (mAgentName);
        } // end of agentName()

        /**
         * Returns total dispatcher thread run time from its
         * start time until now.
         * @return total thread run time.
         */
        public Duration totalRunTime()
        {
            return (Duration.between(mStartTime, Instant.now()));
        } // end of totalRunTime()

        /**
         * Returns total number of agents run.
         * @return total agent run count.
         */
        public long agentRunCount()
        {
            return (mAgentRunCount);
        } // end of agentRunCount()

        /**
         * Returns agent statistics with respect to how long
         * agents spent on dispatcher ready queue before running.
         * @return agent ready queue time statistics.
         */
        public AgentStats agentReadyTimeStats()
        {
            return (mAgentReadyTime);
        } // end of agentReadyTimeStats()

        /**
         * Returns agent statistics with respect to how long
         * agents spent processing events.
         * @return agent run time statistics.
         */
        public AgentStats agentRunTimeStats()
        {
            return (mAgentRunTime);
        } // end of agentRunTimeStats()

        /**
         * Returns agent statistics with respect to how many
         * events agents processed per run.
         * @return agent event processing statistics.
         */
        public AgentStats agentEventStats()
        {
            return (mAgentEvent);
        } // end of agentEventStats()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets dispatcher thread start time.
         * @param timestamp thread start time.
         */
        private void startTime(final Instant timestamp)
        {
            mStartTime = timestamp;
        } // end of startTime(Instant)

        /**
         * Updates dispatcher thread state and agent name.
         * @param state thread state.
         * @param agentName currently running agent name.
         */
        private void updateState(final DispatcherThreadState state,
                                 final String agentName)
        {
            mState = state;
            mAgentName = agentName;
        } // end of updateState(DispatcherThreadState)

        /**
         * Updates agent statistics pertaining to 1) how long
         * agent was on the dispatcher run queue, 2) how long the
         * agent was busy processing events, and 3) how many
         * events agent processed. Also increments dispatcher
         * thread agent run count.
         * @param readyTime nanosecond time agent spent on run
         * queue.
         * @param runTime nanosecond time agent spent processing
         * events.
         * @param eventCount number of events agent processed.
         */
        private void updateAgentStats(final long readyTime,
                                      final long runTime,
                                      final int eventCount)
        {
            ++mAgentRunCount;
            mAgentReadyTime.addAgentStat(readyTime);
            mAgentRunTime.addAgentStat(runTime);
            mAgentEvent.addAgentStat(eventCount);
        } // end of updateAgentStats(long, long, int)

        //
        // end of Set Methods.
        //-------------------------------------------------------
    } // end of class DispatcherThreadStats

    /**
     * This class is used to track agent performance for a
     * variety of statistics: time spent on dispatcher ready
     * queue, time spent processing events, and number of event
     * processed per run. These statistics are on a per
     * dispatcher thread basis.
     * <p>
     * Note: the data stored in an {@code AgentStats} instance is
     * "live" which means that the performance data is update by
     * the dispatcher thread over time. It is recommended that
     * an application routinely monitor dispatcher these
     * statistics over time to detect dispatcher performance
     * degradation.
     * </p>
     *
     * @see DispatcherThreadStats
     */
    public final class AgentStats
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Unique name identifying these statistics.
         */
        private final String mStatsName;

        /**
         * Agent statistics are in these units.
         */
        private final String mUnit;

        /**
         * Number of agent stats inserted into {@link #mStats}.
         */
        private volatile int mCount;

        /**
         * Raw stats used to calculate the moving average.
         */
        private final long[] mStats;

        /**
         * Insert next datum into {@link #mStats} at this
         * index.
         */
        private volatile int mNextIndex;

        /**
         * Moving average of agent stats.
         */
        private volatile long mMovingAverage;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates
         * @param statsName
         * @param unit
         */
        private AgentStats(final String statsName,
                           final String unit)
        {
            mStatsName = statsName;
            mUnit = unit;
            mCount = 0;
            mStats = new long[MAX_AGENT_STATS];
            mNextIndex = 0;
            mMovingAverage = 0L;
        } // end of AgentStat(String, String)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        @Override
        public String toString()
        {
            final long[] stats = stats();
            final String retval;

            if (stats.length == 0)
            {
                retval = "(no agent statistics to report)";
            }
            else
            {
                final int p50 = (int) (mCount * 0.5d);
                final int p75 = (int) (mCount * 0.75d);
                final int p90 = (int) (mCount * 0.9d);
                final int p95 = (int) (mCount * 0.95d);
                final int p99 = (int) (mCount * 0.99d);

                try (final Formatter output = new Formatter())
                {
                    output.format(
                        "%s %s stats:%n", getName(), mStatsName);
                    output.format(" min: %,d %s%n",
                                  stats[0],
                                  mUnit);
                    output.format(" max: %,d %s%n",
                                  stats[mCount - 1],
                                  mUnit);
                    output.format(" med: %,d %s%n",
                                  stats[p50],
                                  mUnit);
                    output.format(" 75%%: %,d %s%n",
                                  stats[p75],
                                  mUnit);
                    output.format(" 90%%: %,d %s%n",
                                  stats[p90],
                                  mUnit);
                    output.format(" 95%%: %,d %s%n",
                                  stats[p95],
                                  mUnit);
                    output.format(" 99%%: %,d %s%n",
                                  stats[p99],
                                  mUnit);
                    output.format(" avg: %,d %s",
                                  mMovingAverage,
                                  mUnit);

                    retval = output.toString();
                }
            }

            return (retval);
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns dispatcher thread's name.
         * @return thread name.
         */
        public String threadName()
        {
            return (getName());
        } // end of threadName()

        /**
         * Returns agent statistic's name.
         * @return statistic name.
         */
        public String statsName()
        {
            return (mStatsName);
        } // end of statsName()

        /**
         * Returns unit name for stored data.
         * @return data unit name.
         */
        public String unit()
        {
            return (mUnit);
        } // end of unit()

        /**
         * Returns number of agents run on this thread.
         * @return agents run o this thread.
         */
        public int agentRunCount()
        {
            return (mCount);
        } // end of agentRunCount()

        /**
         * Returns copy of the collected agent statistics
         * containing only those data points collected
         * so far. Array is sorted from minimum value to maximum.
         * <p>
         * Maximum array size is {@link #MAX_AGENT_STATS}.
         * </p>
         * @return sorted copy of collected agent data points.
         */
        public long[] stats()
        {
            final long[] retval = Arrays.copyOf(mStats, mCount);

            Arrays.sort(retval);

            return (retval);
        } // end of stats()

        /**
         * Returns current moving average.
         * @return agent stats moving average.
         */
        public long movingAverage()
        {
            return (mMovingAverage);
        } // end of movingAverage()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Adds next datum
         * @param datum
         */
        private void addAgentStat(final long datum)
        {
            // Make sure datum is valid.
            if (datum >= 0L)
            {
                final long removedValue = mStats[mNextIndex];
                final long ma =
                    ((mMovingAverage * mCount) - removedValue);

                mStats[mNextIndex] = datum;

                mCount = (Math.min((mCount + 1), MAX_AGENT_STATS));
                mNextIndex = ((mNextIndex + 1) & MAX_AGENT_MASK);

                mMovingAverage = ((ma + datum) / mCount);
            }
        } // end of addAgentStat(long)

        //
        // end of Set Methods.
        //-------------------------------------------------------
    } // end of class AgentStat

    /**
     * Allows various methods to be substituted for
     * a {@link Queue#poll()}.
     *
     * @param <T> the {@code Queue} item type.
     */
    @FunctionalInterface
    private interface IPollInterface<T>
    {
        /**
         * Returns item removed from the queue's head. Note
         * that this method never returns a {@code null} value.
         * @return the queue's head.
         */
        @NonNull T poll();
    } // end of interface IPollInterface
} // end of class EfsDispatcherThread
