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

import jakarta.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.LockSupport;
import net.openhft.affinity.AffinityLock;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import org.efs.dispatcher.config.ThreadType;
import static org.efs.dispatcher.config.ThreadType.BLOCKING;
import static org.efs.dispatcher.config.ThreadType.SPINNING;
import static org.efs.dispatcher.config.ThreadType.SPINPARK;
import static org.efs.dispatcher.config.ThreadType.SPINYIELD;
import org.efs.logging.AsyncLoggerFactory;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;
import org.jctools.queues.atomic.MpscAtomicArrayQueue;
import org.slf4j.Logger;

/**
 * A dispatcher thread watches a given
 * {@link java.util.Queue run queue} for {@link EfsAgent}
 * instances ready to run, attempting to acquire the next ready
 * agent. When this thread successfully acquires an agent, it has
 * the agent execute its pending events until either 1) the agent
 * has no more events or 2) the agent exhausts its maximum
 * allowed event limit.
 * <p>
 * Each dispatcher has a configurable run-time maximum event
 * (defaults to {@link #DEFAULT_MAX_EVENTS}). When an agent
 * exhausts this limit <em>and</em> still has events to process,
 * the agent is placed at the end of the run queue and the
 * agent's event limit is replenished.
 </p>
 * <p>
 * An agent instance is referenced by only one dispatcher thread
 * at a time. This means an agent is effectively single-threaded
 * even though over time it may be dispatched by multiple,
 * different threads.
 * </p>
 * <p>
 * <strong>Note:</strong> this <em>only</em> applies to efs
 * dispatcher threads. Non-dispatcher threads may still access
 * an agent object at the same time.
 * </p>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsDispatcherThread
    extends EfsDispatcherThreadAbstract
{
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

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(EfsDispatcherThread.class);

    //-----------------------------------------------------------
    // Locals.
    //

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
     * Maximum number of events an agent may run per call out.
     */
    private final int mMaxEvents;

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
        super (builder);

        mRunQueue = builder.mRunQueue;

        mPollMethod = switch (mThreadType)
                      {
                          case BLOCKING -> this::blockingPoll;
                          case SPINNING -> this::spinningPoll;
                          case SPINPARK -> this::spinParkPoll;
                          default -> this::spinYieldPoll;
                      };
        mSpinLimit = builder.mSpinLimit;
        mParkTime = (builder.mParkTime).toNanos();
        mMaxEvents = builder.mMaxEvents;
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
        AffinityLock affinityLock = null;
        EfsAgent agent;

        mStats.startTime(Instant.now());
        mStats.updateState(DispatcherThreadState.IDLE,
                           NO_EFS_AGENT);

        // If dispatcher is configured for thread affinity, then
        // put that affinity in place here.
        if (mAffinity != null)
        {
            affinityLock = setAffinity();
        }

        sLogger.debug("{}: running.", name);

        while (mRunFlag)
        {
            sLogger.trace("{}: polling run queue.",
                          name);

            // Get the next agent.
            agent = mPollMethod.poll();

            // Is this thread still running?
            // Returned agent will be null when this thread is
            // shut down. So if run flag is true, then agent will
            // not be null; if run flag is false, then agent
            // will be null. In short run flag value and agent
            // nullity is an exclusive or condition.
            if (mRunFlag)
            {
                processEvents(name, agent);
            }
        }

        releaseAffinity(affinityLock);

        sLogger.debug("{}: stopped.", name);
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

        if (mThreadType == SPINPARK ||
            mThreadType == SPINYIELD)
        {
            output.append(", spin limit=").append(mSpinLimit);

            if (mThreadType == SPINPARK)
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
     * Returns thread maximum events per agent call out.
     * @return maximum events per agent call out.
     */
    public int maxEvents()
    {
        return (mMaxEvents);
    } // end of maxEvents()

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
     * <p>
     * While {@link BlockingQueue#take()} may return
     * {@code null}, this method continues looping until either a
     * non-{@code null} agent is returned or this dispatcher
     * thread is no longer running.
     * </p>
     * <p>
     * If an interrupt is received while polling the run queue,
     * current thread is re-interrupted so other code may see
     * this interrupt.
     * </p>
     * @return next available agent. Does <em>not</em> return
     * {@code null} while dispatcher thread is still running.
     */
    private EfsAgent blockingPoll()
    {
        EfsAgent retval = null;

        // Keep trying to acquire an agent until either the
        // thread stops or an agent is acquired.
        while (mRunFlag && retval == null)
        {
            try
            {
                retval =
                    ((BlockingQueue<EfsAgent>)
                        mRunQueue).take();
            }
            catch (InterruptedException interrupt)
            {
                (Thread.currentThread()).interrupt();
            }
        }

        return (retval);
    } // end of blockingPoll()

    /**
     * Actively spins calling
     * {@link Queue#poll()} to extract  next available agent from
     * run queue.
     * @return next available agent. Does <em>not</em> return
     * {@code null} while dispatcher thread is still running.
     */
    private EfsAgent spinningPoll()
    {
        EfsAgent retval = null;

        while (mRunFlag && (retval = mRunQueue.poll()) == null)
        {
            Thread.onSpinWait();
        }

        return (retval);
    } // end of spinningPoll()

    /**
     * Spins a fixed number of times calling
     * {@link Queue#poll()} to extract next available agent from
     * run queue. When spin limit is reached, then parks for
     * fixed number of nanoseconds.
     * @return next available agent. Does <em>not</em> return
     * {@code null} while dispatcher thread is still running.
     */
    private EfsAgent spinParkPoll()
    {
        long counter = mSpinLimit;
        EfsAgent retval = null;

        while (mRunFlag && retval == null)
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
     * {@link Queue#poll()} to extract next available agent from
     * run queue. When spin limit is reached, then this
     * Dispatcher thread yields.
     * @return next available agent. Does <em>not</em> return
     * {@code null} while dispatcher thread is still running.
     */
    @SuppressWarnings ("CallToThreadYield")
    private EfsAgent spinYieldPoll()
    {
        long counter = mSpinLimit;
        EfsAgent retval = null;

        while (mRunFlag && retval == null)
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
     * Returns a new dispatcher thread builder instance.
     * @return dispatcher thread builder.
     */
    /* package */ static Builder builder()
    {
        return (new Builder());
    } // end of Builder()

    /**
     * Has given agent process its event queue. Updates thread
     * and agent statistics as a side effect.
     * @param threadName agent is running on this named thread.
     * @param agent agent processing its event queue.
     */
    private void processEvents(final String threadName,
                               final EfsAgent agent)
    {
        final long busyStart;
        int eventCount = 0;

        mStats.updateState(DispatcherThreadState.BUSY,
                           agent.agentName());
        busyStart = System.nanoTime();

        sLogger.trace("{}: processing agent {}.",
                      threadName,
                      agent.agentName());


        // Have the agent process its events.
        try
        {
            eventCount = agent.processEvents();
        }
        catch (Exception jex)
        {
            sLogger.warn(
                "{}: agent {} event processing exception.",
                threadName,
                agent.agentName(),
                jex);
        }
        finally
        {
            final long busyStop = System.nanoTime();
            final long rawReadyTime =
                (busyStart - agent.getAndClearReadyTimestamp());
            final long readyTime =
                (rawReadyTime < 0L ? 0L : rawReadyTime);
            final long busyTime = (busyStop - busyStart);

            // Agent finished with its event processing.
            mStats.updateState(DispatcherThreadState.IDLE,
                               NO_EFS_AGENT);
            mStats.updateAgentStats(readyTime,
                                    busyTime,
                                    eventCount);
        }
    } // end of processEvents(String, EfsAgent)

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
        extends ThreadBuilder<EfsDispatcherThread, Builder>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * If thread type is either {@link ThreadType#SPINPARK}
         * or {@link ThreadType#SPINYIELD}, then iterate this
         * many times trying to extract an agent from the run
         * queue before parking/yielding.
         */
        private long mSpinLimit;

        /**
         * If thread type is {@link ThreadType#SPINPARK}, then
         * park thread for this duration.
         */
        private Duration mParkTime;

        /**
         * Agent run queue.
         */
        private Queue<EfsAgent> mRunQueue;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Builder()
        {
            super (EfsDispatcherThread.class);

            mMaxEvents = DEFAULT_MAX_EVENTS;
            mPriority = Thread.NORM_PRIORITY;
            mSpinLimit = 0;
            mParkTime = Duration.ZERO;
        } // end of builder()

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
        protected EfsDispatcherThread buildImpl()
        {
            return (new EfsDispatcherThread(this));
        } // end of buildImpl()

        //
        // end of Abstract Method Implementations.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

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
                        EfsDispatcher.INVALID_SPIN_LIMIT));
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
            Objects.requireNonNull(
                time, EfsDispatcher.NULL_TIME);

            if (time.isNegative())
            {
                throw (
                    new IllegalArgumentException(
                        EfsDispatcher.INVALID_TIME));
            }

            mParkTime = time;

            return (this);
        } // end of parkTime(Duration)

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
                        EfsDispatcher.INVALID_MAX_EVENTS));
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
                Objects.requireNonNull(
                    queue, EfsDispatcher.NULL_RUN_QUEUE);

            return (this);
        } // end of runQueue(Queue<>)

        //
        // end of Set Methods.
        //-------------------------------------------------------

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
        @Override
        protected Validator validate(final Validator problems)
        {
            return (
                super.validate(problems)
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
                                    (mRunQueue instanceof MpscAtomicArrayQueue ||
                                     mRunQueue instanceof MpmcAtomicArrayQueue))),
                                  "runQueue",
                                  "does not match thread type"));
        } // end of validate(Validator)
    } // end of class Builder

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
         * that this method returns a {@code null} value when
         * dispatcher thread is shut down. While dispatcher
         * thread is running, will not return a {@code null}
         * value.
         * @return queue head.
         */
        @Nullable T poll();
    } // end of interface IPollInterface
} // end of class EfsDispatcherThread
