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

import com.google.common.base.Strings;
import jakarta.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.openhft.affinity.AffinityLock;
import net.sf.eBus.util.Validator;
import static org.efs.dispatcher.EfsDispatcherThreadAbstract.MAX_AGENT_STATS;
import static org.efs.dispatcher.EfsDispatcherThreadAbstract.NO_EFS_AGENT;
import org.efs.dispatcher.config.ThreadAffinity;
import org.efs.dispatcher.config.ThreadAffinityConfig;
import org.efs.dispatcher.config.ThreadAffinityConfig.AffinityType;
import org.efs.dispatcher.config.ThreadType;

/**
 * Base implementation for a dispatcher thread that runs efs
 * agents and manages their execution lifecycle.
 * <p>
 * Concrete dispatcher-thread subclasses implement the thread
 * scheduling behavior for a particular execution model while
 * sharing the common infrastructure in this class for agent
 * selection, affinity management, run state tracking, and
 * statistics gathering. Each thread participates in the
 * dispatcher queue and processes one or more ready agents under
 * the configured execution policy.
 * </p>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@SuppressWarnings({"java:S2134"})
public abstract class EfsDispatcherThreadAbstract
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

    /**
     * Used to create a no-op but non-{@code null} affinity lock.
     */
    private static final String NO_OP_AFFINITY_LOCK = "none";

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
    @Nullable
    private static volatile AffinityLock sPreviousLock = null;

    /**
     * Acquire this lock prior to setting thread affinity.
     */
    private static final Lock sAffinityAccessLock =
        new ReentrantLock();

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Dispatcher thread type.
     */
    protected final ThreadType mThreadType;

    /**
     * Thread affinity configuration. Used to associate thread
     * with a CPU. Set to {@code null} if the dispatcher
     * thread has no CPU affinity.
     */
    @Nullable protected final ThreadAffinityConfig mAffinity;

    /**
     * Dispatcher thread continues running while this flag is
     * {@code true}.
     */
    protected volatile boolean mRunFlag;

    //
    // Performance statistics.
    //

    /**
     * Contains latest performance statistics for this
     * dispatcher thread.
     */
    protected final DispatcherThreadStats mStats;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new dispatcher thread using the supplied builder
     * configuration.
     * <p>
     * The builder must contain valid thread metadata, including
     * the thread name, execution type, priority, and optional
     * affinity settings. The constructor initializes the base
     * thread state, stores the configuration, and applies the
     * default runtime attributes required for a managed efs
     * dispatcher thread.
     * </p>
     * @param builder contains valid dispatcher thread settings.
     */
    protected EfsDispatcherThreadAbstract(final ThreadBuilder<?, ?> builder)
    {
        super (builder.mThreadName);

        mThreadType = builder.mThreadType;
        mAffinity = builder.mAffinity;
        mStats = new DispatcherThreadStats();
        mRunFlag = true;

        // Note: these settings *must* be done in the
        // constructor.
        this.setPriority(builder.mPriority);
        this.setDaemon(true);
    } // end of EfsDispatcherThreadAbstract(ThreadBuilder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

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
    public final ThreadType threadType()
    {
        return (mThreadType);
    } // end of threadType()

    /**
     * Returns thread affinity configuration. May return
     * {@code null}.
     * @return thread affinity configuration.
     */
    @Nullable
    public final ThreadAffinityConfig affinity()
    {
        return (mAffinity);
    } // end of affinity()

    /**
     * Returns this dispatcher thread's performance statistics.
     * <p>
     * <strong>Note:</strong> the returned object is a live view
     * of thread performance statistics and may be updated
     * concurrently while the caller accesses these statistics.
     * The returned {@link DispatcherThreadStats} are
     * <em>approximate</em> and not meant to be a strictly
     * accurate view of thread performance.
     * </p>
     * @return dispatcher thread performance statistics.
     *
     * @see DispatcherThreadStats
     */
    public final DispatcherThreadStats performanceStats()
    {
        return (mStats);
    } // end of performanceStats()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Returns acquired affinity lock using this thread's
     * configured affinity type.
     * <p>
     * Note: caller guarantees that {@code mAffinity} is not
     * {@code null}.
     * </p>
     * @return acquired affinity lock.
     */
    @SuppressWarnings ({"java:S2696", "java:S3776"})
    protected final AffinityLock setAffinity()
    {
        final AffinityType affinityType =
            mAffinity.getAffinityType();
        final AffinityLock retval;

        sAffinityAccessLock.lock();
        try
        {
            retval =
                switch (affinityType)
                {
                    case NO_OP ->
                        AffinityLock.acquireLock(
                            NO_OP_AFFINITY_LOCK);

                    case CPU_STRATEGIES ->
                        ThreadAffinity.acquireLock(
                            sPreviousLock, mAffinity);

                    default ->
                        ThreadAffinity.acquireLock(mAffinity);
                };

                if (affinityType != AffinityType.NO_OP)
                {
                    sPreviousLock = retval;
                }
        }
        finally
        {
            sAffinityAccessLock.unlock();
        }

        return (retval);
    } // end of setAffinity()

    /**
     * If given affinity lock is not {@code null}, then release
     * it now for re-use.
     * @param affinityLock release this affinity lock.
     */
    @SuppressWarnings ({"java:S2696"})
    protected final void releaseAffinity(@Nullable final AffinityLock affinityLock)
    {
        // Did this thread acquire an cpu affinity lock?
        if (affinityLock != null)
        {
            // Yes, close affinity lock.
            sAffinityAccessLock.lock();
            try (affinityLock)
            {
                // If previous lock references this lock, then
                // clear the reference.
                if (sPreviousLock == affinityLock)
                {
                    sPreviousLock = null;
                }
            }
            finally
            {
                sAffinityAccessLock.unlock();
            }
        }
    } // end of releaseAffinity(AffinityLock)

    /**
     * Sets run flag to {@code false} which eventually stops
     * this dispatcher thread. This method may be overridden to
     * extend shutdown processing but overriding method
     * <em>must</em> call {@code super.shutdown()}.
     */
    /* package */ void shutdown()
    {
        mRunFlag = false;
        this.interrupt();
    } // end of shutdown()

    //
    // end of Set Methods.
    //-----------------------------------------------------------

//---------------------------------------------------------------
// Inner classes.
//

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
        private final AtomicReference<Instant> mStartTime;

        /**
         * Current dispatcher thread state.
         */
        private final AtomicReference<DispatcherThreadState> mState;

        /**
         * efs agent name currently running on dispatcher thread.
         * If there is not agent running, then returns
         * {@link #NO_EFS_AGENT}.
         */
        private final AtomicReference<String> mAgentName;

        /**
         * Total number of efs agents run on this dispatcher
         * thread.
         */
        private final AtomicLong mAgentRunCount;

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
            mStartTime = new AtomicReference<>();
            mState =
                new AtomicReference<>(
                    DispatcherThreadState.NOT_STARTED);
            mAgentName = new AtomicReference<>(NO_EFS_AGENT);
            mAgentRunCount = new AtomicLong();
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
                          .append(mStartTime.get())
                          .append(", state=")
                          .append(mState.get())
                          .append(", agent=")
                          .append(mAgentName.get())
                          .append(", run count=")
                          .append(mAgentRunCount.get())
                          .append(",\n  ready time=")
                          .append(mAgentReadyTime)
                          .append(",\n  run time=")
                          .append(mAgentRunTime)
                          .append(",\n  agent event=")
                          .append(mAgentEvent)
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
            return (mStartTime.get());
        } // end of startTime()

        /**
         * Returns dispatcher thread current state.
         * @return thread state.
         */
        public DispatcherThreadState threadState()
        {
            return (mState.get());
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
            return (mAgentName.get());
        } // end of agentName()

        /**
         * Returns total dispatcher thread run time from its
         * start time until now.
         * @return total thread run time.
         */
        public Duration totalRunTime()
        {
            return (
                Duration.between(
                    mStartTime.get(), Instant.now()));
        } // end of totalRunTime()

        /**
         * Returns total number of agents run.
         * @return total agent run count.
         */
        public long agentRunCount()
        {
            return (mAgentRunCount).get();
        } // end of agentRunCount()

        /**
         * Returns agent statistics with respect to how long
         * agents spent on dispatcher ready queue before running.
         * <p>
         * Note: the returned agent statistics are
         * <em>approximate</em> and are not guaranteed to exactly
         * reflect agent performance up to the moment when this
         * method is called.
         * </p>
         * @return agent ready queue time statistics.
         */
        public AgentStats agentReadyTimeStats()
        {
            return (mAgentReadyTime);
        } // end of agentReadyTimeStats()

        /**
         * Returns agent statistics with respect to how long
         * agents spent processing events.
         * <p>
         * Note: the returned agent statistics are
         * <em>approximate</em> and are not guaranteed to exactly
         * reflect agent performance up to the moment when this
         * method is called.
         * </p>
         * @return agent run time statistics.
         */
        public AgentStats agentRunTimeStats()
        {
            return (mAgentRunTime);
        } // end of agentRunTimeStats()

        /**
         * Returns agent statistics with respect to how many
         * events agents processed per run.
         * <p>
         * Note: the returned agent statistics are
         * <em>approximate</em> and are not guaranteed to exactly
         * reflect agent performance up to the moment when this
         * method is called.
         * </p>
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
        protected void startTime(final Instant timestamp)
        {
            mStartTime.set(timestamp);
        } // end of startTime(Instant)

        /**
         * Updates dispatcher thread state and agent name.
         * @param state thread state.
         * @param agentName currently running agent name.
         */
        protected void updateState(final DispatcherThreadState state,
                                   final String agentName)
        {
            mState.set(state);
            mAgentName.set(agentName);
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
        protected void updateAgentStats(final long readyTime,
                                        final long runTime,
                                        final int eventCount)
        {
            mAgentRunCount.incrementAndGet();
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
     * "live" which means that the performance data is updated by
     * the dispatcher thread over time. It is recommended that
     * an application routinely monitor dispatcher these
     * statistics over time to detect dispatcher performance
     * degradation.
     * </p>
     * <p>
     * Also note that this "live" data is approximate and is not
     * meant to be an exactly snapshot of agent performance
     * stats.
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
        private final AtomicInteger mCount;

        /**
         * Raw stats used to calculate the moving average.
         */
        private final long[] mStats;

        /**
         * Insert next datum into {@link #mStats} at this
         * index.
         */
        private final AtomicInteger mNextIndex;

        /**
         * Current sum of all {@link #mStats} values.
         */
        private final AtomicLong mSum;

        /**
         * Moving average of agent stats.
         */
        private final AtomicLong mMovingAverage;

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
            mCount = new AtomicInteger();
            mStats = new long[MAX_AGENT_STATS];
            mNextIndex = new AtomicInteger();
            mSum = new AtomicLong();
            mMovingAverage = new AtomicLong();
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
            final int size = stats.length;
            final String retval;

            if (size == 0)
            {
                retval = "(no agent statistics to report)";
            }
            else
            {
                final int p50 = (int) (size * 0.5d);
                final int p75 = (int) (size * 0.75d);
                final int p90 = (int) (size * 0.9d);
                final int p95 = (int) (size * 0.95d);
                final int p99 = (int) (size * 0.99d);

                try (final Formatter output = new Formatter())
                {
                    output.format("%s %s stats:%n",
                                  getName(),
                                  mStatsName)
                          .format("    min: %,d %s%n",
                                  stats[0],
                                  mUnit)
                          .format("    med: %,d %s%n",
                                  stats[p50],
                                  mUnit)
                          .format("    avg: %,d %s%n",
                                  mMovingAverage.get(),
                                  mUnit)
                          .format("    75%%: %,d %s%n",
                                  stats[p75],
                                  mUnit)
                          .format("    90%%: %,d %s%n",
                                  stats[p90],
                                  mUnit)
                          .format("    95%%: %,d %s%n",
                                  stats[p95],
                                  mUnit)
                          .format("    99%%: %,d %s%n",
                                  stats[p99],
                                  mUnit)
                          .format("    max: %,d %s",
                                  stats[size - 1],
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
            return (mCount.get());
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
            final int statsCount = mCount.get();
            final long[] retval =
                Arrays.copyOf(mStats, statsCount);

            // Do we need to sort this array?
            if (statsCount > 1)
            {
                // Yes.
                Arrays.sort(retval);
            }

            return (retval);
        } // end of stats()

        /**
         * Returns current moving average.
         * @return agent stats moving average.
         */
        public long movingAverage()
        {
            return (mMovingAverage.get());
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
                final int statsIndex = mNextIndex.get();
                final long removedValue = mStats[statsIndex];

                // Update ring buffer.
                mStats[statsIndex] = datum;

                // Update datum count.
                mCount.set((Math.min((mCount.get() + 1),
                                     MAX_AGENT_STATS)));

                // Update sum by subtracting removed value (0 if
                // not previously set) and adding new datum.
                mSum.set((mSum.get() - removedValue) + datum);

                // Advance index and recompute average.
                mNextIndex.set(
                    (statsIndex + 1) & MAX_AGENT_MASK);
                mMovingAverage.set(mSum.get() / mCount.get());
            }
        } // end of addAgentStat(long)

        //
        // end of Set Methods.
        //-------------------------------------------------------
    } // end of class AgentStat

    /**
     * Base builder for concrete dispatcher-thread implementations.
     * <p>
     * This builder captures the configuration shared by all
     * dispatcher-thread types, including the thread name, execution
     * model, priority, optional affinity, and maximum events per
     * agent dispatch. Concrete subclasses provide the specific
     * thread implementation class and return their own builder type
     * from {@link #self()} and {@link #buildImpl()}.
     * </p>
     *
     * @param <A> builder this dispatcher thread instance.
     * @param <B> thread builder type.
     */
    protected abstract static class ThreadBuilder<A extends EfsDispatcherThreadAbstract,
                                                  B extends ThreadBuilder<A, ?>>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Building an instance of this dispatcher thread class.
         */
        protected final Class<A> mThreadClass;

        /**
         * Unique thread name. Used for logging purposes.
         */
        protected String mThreadName;

        /**
         * Thread type defines how thread waits on run queue for
         * runnable agents.
         */
        protected ThreadType mThreadType;

        /**
         * Thread run priority.
         */
        protected int mPriority;

        /**
         * Thread affinity with one or more CPU cores.
         */
        @Nullable protected ThreadAffinityConfig mAffinity;

        /**
         * Maximum number of events which an agent may process
         * per call out.
         */
        protected int mMaxEvents;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Sets thread priority to {@link Thread#NORM_PRIORITY}
         * and maximum allowed events to zero.
         * @param threadClass abstract thread subclass.
         */
        protected ThreadBuilder(final Class<A> threadClass)
        {
            mThreadClass = threadClass;

            mMaxEvents = 0;
            mPriority = Thread.NORM_PRIORITY;
        } // end of ThreadBuilder()

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
         * Sets dispatcher thread name.
         * @param threadName dispatcher thread name.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code mThreadName} is either {@code null} or an
         * empty string.
         */
        /* package */ final B threadName(final String threadName)
        {
            if (Strings.isNullOrEmpty(threadName) ||
                threadName.isBlank())
            {
                throw (
                    new IllegalArgumentException(
                        EfsDispatcher.INVALID_THREAD_NAME));
            }

            mThreadName = threadName;

            return (self());
        } // end of mThreadName(String)

        /**
         * Sets dispatcher thread type.
         * @param threadType dispatcher thread type.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code threadType} is {@code null}.
         */
        /* package */ final B threadType(final ThreadType threadType)
        {
            mThreadType =
                Objects.requireNonNull(
                    threadType, EfsDispatcher.NULL_THREAD_TYPE);

            return (self());
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
        /* package */ final B priority(final int priority)
        {
            if (priority < Thread.MIN_PRIORITY ||
                priority > Thread.MAX_PRIORITY)
            {
                throw (
                    new IllegalArgumentException(
                        EfsDispatcher.INVALID_PRIORITY));
            }

            mPriority = priority;

            return (self());
        } // end of priority(int)

        /**
         * Sets optional thread affinity configuration.
         * @param affinity thread affinity configuration. May be
         * {@code null}.
         * @return {@code this} dispatcher builder.
         */
        /* package */ final B affinity(@Nullable final ThreadAffinityConfig affinity)
        {
            mAffinity = affinity;

            return (self());
        } // end of affinity(ThreadAffinityConfig)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Returns a new efs dispatcher thread using this
         * builder's current settings.
         * @return efs dispatcher thread.
         */
        /* package */ final A build()
        {
            final Validator problems = new Validator();

            validate(problems).throwException(mThreadClass);

            return (buildImpl());
        } // end of build()

        /**
         * Validates the builder settings required to construct a
         * dispatcher thread. Requires thread name and thread
         * type to be set.
         * <p>
         * Concrete subclasses may extend this method to add
         * additional validation rules beyond the base thread name
         * and thread type checks. Any validation failures are
         * recorded in the supplied {@link Validator} and raised by
         * {@link #build()} before the thread is created.
         * </p>
         * @param problems store invalid builder settings into
         * this list.
         * @return {@code problems} containing any validation
         * failures.
         */
        protected Validator validate(final Validator problems)
        {
            return (
                problems.requireNotNull(mThreadName,
                                        "threadName")
                         .requireNotNull(mThreadType,
                                         "threadType"));
        } // end of validate(Validator)
    } // end of class ThreadBuilder
} // end of class EfsDispatcherThreadAbstract
