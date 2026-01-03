//
// Copyright 2024 Charles W. Rapp
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

package org.efs.timer;

import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.annotation.concurrent.Immutable;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;
import org.efs.dispatcher.config.ThreadType;
import org.efs.event.IEfsEvent;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * Executes given timer, {@link IEfsAgent} pairs at a specified
 * time. When task timer expires, timer event is dispatched to
 * {@code IEfsAgent} via
 * {@link EfsDispatcher#dispatch(Consumer, IEfsEvent, IEfsAgent)}.
 * <p>
 * Schedule methods are used to create timers with various
 * delays, returning a {@link ScheduledFuture} which can
 * be used to cancel the timer or check its status. Methods
 * {@link #scheduleAtFixedRate(String, Consumer, IEfsAgent, Duration, Duration)}
 * and
 * {@link #scheduleWithFixedDelay(String, Consumer, IEfsAgent, Duration, Duration)}
 * create and execute timer tasks which run periodically until
 * canceled.
 * </p>
 * <p>
 * Unlike {@code java.util.concurrent.ScheduledExecutorService},
 efs scheduled service does <em>not</em> support delay or
 * period &lt; zero. A repeating fixed delay or repeating fixed
 * rate period must be &gt; zero. A zero single-shot delay or
 * initial delay must be &ge; zero.
 * </p>
 * <p>
 * All scheduled methods accept <em>relative</em> delays and
 * periods as arguments and not absolute times or date.
 * </p>
 * <h2>Creating an EfsScheduledTimer</h2>
 * An {@code EfsScheduledTimer} is created by first creating
 * an {@link ScheduledExecutorService} and then passing that
 * service to
 * {@link EfsScheduledExecutor#EfsScheduledExecutor(ScheduledExecutorService)}.
 * Note that the {@code ScheduledExecutorService} instance must
 * not be {@code null} or shut down.
 * <p>
 * It is possible to use this {@code ScheduledExecutorService}
 * instance for other tasks and even shutdown this service
 * independently of the {@code EfsScheduledExecutor} instance
 * encapsulating this service. This is because
 * {@code EfsScheduledExecutor} tracks the
 * {@code ScheduledExecutorService} status.
 * </p>
 * <h2>Usage Example</h2>
 This code example assumes that an efs scheduled service was
 previously created.
 <pre><code>import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExectorService;
import java.util.concurrent.ScheduledFuture;
import org.efs.dispatcher.IEfsAgent
import org.efs.timer.EfsScheduledExecutor;
import org.efs.timer.EfsTimerEvent;

public class MyAgent implements IEfsAgent {
    private static final String UPDATE_TIMER_NAME = "update-timer";

    // Verify that at least one update is received over the past minute.
    private static final Duration TIMER_DELAY = Duration.ofMinutes(1L);

    private EfsScheduledExecutor mExecutor;
    private Instant mLatestUpdate;
    private ScheduledFuture&lt;?&gt; mUpdateTimer;

    public MyAgent() {
    }

    public void startup() {
        // Do some work at a fixed delay.
        mExecutor = EfsScheduledExecutor(Executors.newSingleThreadScheduledExecutor());

        mUpdateTimer = service.scheduleWithFixedDelay(UPDATE_TIMER_NAME, this::onTimeout, this, TIMER_DELAY, TIMER_DELAY);
    }

    public void shutdown() {
        stopTimer();
    }

    private void onUpdate(final AppUpdate update) {
        mLatestUpdate = update.timestampAsInstant();

        // Do update work here.
        ...
    }

    private void onTimeout() {
        final Duration delta = Duration.between(mLatestUpdate, Instant.now());

        // Did an update arrive within the last minute?
        if (delta.compareTo(TIMER_DELAY) &gt; 0) {
            // Yes. Take necessary actions.
            ...
        }
    }

    private void stopTimer() {
        if (mUpdateTimer != null) {
            try {
                mTimer.cancel();
            } catch (Exception jex) {
                // Do nothing.
            } finally {
                mTimer = null;
                mLatestUpdate = null;
            }
        }
    }
}</code></pre>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@SuppressWarnings({"java:S1452"})
public final class EfsScheduledExecutor
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Use command line option {@code -D}{@value}{@code=<file>}
     * to specify file containing a typesafe HOCON {a form of
     * JSON} configuration for one or more's
     * {@code EfsScheduledExcutor}s.
     */
    public static final String SCHEDULER_CONFIG_OPTION =
        "org.efs.timer.configFile";

    //
    // Default values.
    //

    /**
     * Default scheduled service thread type is
{@link ThreadType#BLOCKING}.
     */
    public static final ThreadType DEFAULT_THREAD_TYPE =
        ThreadType.BLOCKING;

    /**
     * Default scheduled service thread has
{@link Thread#NORM_PRIORITY} priority.
     */
    public static final int DEFAULT_EXECUTOR_PRIORITY =
        Thread.NORM_PRIORITY;

    //
    // Error text.
    //

    /**
     * Null scheduled service {@code NullPointerException}
     * message is {@value}.
     */
    public static final String NULL_EXECUTOR = "executor is null";

    /**
     * Invalid service {@code IllegalArgumentException} message
     * is {@value}.
     */
    public static final String INVALID_EXECUTOR =
        "executor is shutdown";

    /**
     * Invalid thread type {@code NullPointerException}
     * message is {@value}.
     */
    public static final String NULL_TYPE = "type is null";

    /**
     * Null callback lambda {@code NullPointerException}
     * message is {@value}.
     */
    public static final String NULL_CALLBACK =
        "callback is null";

    /**
     * Invalid efs agent {@code NullPointerException} message
     * is {@value}.
     */
    public static final String NULL_AGENT = "agent is null";

    /**
     * Invalid delay {@code NullPointerException} message
     * is {@value}.
     */
    public static final String NULL_DELAY = "delay is null";

    /**
     * Invalid initial delay {@code NullPointerException} message
     * is {@value}.
     */
    public static final String NULL_INIT_DELAY =
        "initial delay is null";

    /**
     * Invalid period {@code NullPointerException} message is
     * {@value}.
     */
    public static final String NULL_PERIOD = "period is null";

    /**
     * Invalid negative delay {@code IllegalArgumentException}
     * message is {@value}.
     */
    public static final String NEGATIVE_DELAY = "delay < zero";

    /**
     * Invalid negative initial delay
     * {@code IllegalArgumentException} message is {@value}.
     */
    public static final String NEGATIVE_INIT_DELAY =
        "initial delay < zero";

    /**
     * Invalid period {@code IllegalArgumentException}
     * message is {@value}.
     */
    public static final String NEGATIVE_PERIOD =
        "period <= zero";

    /**
     * Invalid repeat delay {@code IllegalArgumentException}
     * message is {@value}.
     */
    public static final String NEGATIVE_REPEAT_DELAY =
        "delay <= zero";

    /**
     * Unregistered agent {@code IllegalStateException}
     * message is {@value}.
     */
    public static final String UNREGISTERED_AGENT =
        " is not registered with a dispatcher";

    /**
     * Shut down scheduler {@code IllegalStateException}
     * message is {@value}.
     */
    public static final String EXEC_SHUT_DOWN =
        "scheduled executor is shut down";

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
     * Encapsulated thread pool service
     */
    private final ScheduledExecutorService mExecutor;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new efs scheduled service instance for the
given Java scheduled service.
     * @param executor encapsulated Java scheduled service
service.
     * @throws NullPointerException
     * if {@code service} is {@code null}.
     * @throws IllegalArgumentException
     * if {@code service} is shut down.
     */
    public EfsScheduledExecutor(final ScheduledExecutorService executor)
    {
        Objects.requireNonNull(executor, NULL_EXECUTOR);

        if (executor.isShutdown())
        {
            throw (
                new IllegalArgumentException(INVALID_EXECUTOR));
        }

        mExecutor = executor;
    } // end of EfsScheduledExecutor()

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns {@code true} if this service has <em>not</em>
     * been shut down.
     * @return {@code true} if this service is running.
     */
    public boolean isRunning()
    {
        return (!mExecutor.isShutdown());
    } // end of isRunning()

    /**
     * Returns {@code true} if this service has been shut down.
     * @return {@code true} if this service has been shut down
     */
    public boolean isShutdown()
    {
        return (mExecutor.isShutdown());
    } // end of isShutdown()

    /**
     * Returns {@code true} if all tasks have completed following
     * shut down. Note that {@code isTerminated} is never
     * {@code true} unless either {@code shutdown} or
     * {@code shutdownNow} was called first.
     * @return {@code true} if all tasks have completed following
     * shut down.
     */
    public boolean isTerminated()
    {
        return (mExecutor.isTerminated());
    } // end of isTerminated()

    /**
     * Returns encapsulated Java scheduled executor service.
     * @return encapsulated Java scheduled executor service.
     */
    public ScheduledExecutorService service()
    {
        return (mExecutor);
    } // end of service()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Initiates an orderly shutdown in which previously
     * submitted tasks are executed, but no new tasks will be
     * accepted. Invocation has no additional effect if already
     * shut down. Shuts down this executor thread which results
     * in all currently scheduled timers being canceled.
     * <p>
     * This method does not wait for previously submitted tasks
     * to complete execution. Use {@link #awaitTermination} to do
     * that.
     * </p>
     * @throws SecurityException
     * if a security manager exists and shutting down this
     * {@code ExecutorService} may manipulate threads that caller
     * is not permitted to modify because it does not hold
     * {@code RuntimePermission("modifyThread")}, or the security
     * manager's {@code checkAccess} method denies access.
     */
    public void shutdown()
    {
        // Is this scheduled service currently running?
        if (!mExecutor.isShutdown())
        {
            // Yes. Shut down the encapsulated scheduled
            // service.
            mExecutor.shutdown();
        }
    } // end of shutdown()

    /**
     * Attempts to stop all actively executing tasks and halts
     * processing of waiting tasks, and returns list of tasks
     * awaiting execution.
     * <p>
     * This method does not wait for actively executing tasks to
     * terminate. Use {@link #awaitTermination} to do that.
     * </p>
     * @return list of tasks that never commenced execution. This
     * list may be empty but never {@code null}.
     * @throws SecurityException
     * if a security manager exists and shutting down this
     * {@code ExecutorService} may manipulate threads that caller
     * is not permitted to modify because it does not hold
     * {@code RuntimePermission("modifyThread")}, or the security
     * manager's {@code checkAccess} method denies access.
     */
    @Nonnull public List<Runnable> shutdownNow()
    {
        final List<Runnable> retval;

        // Is this scheduled service currently running?
        if (mExecutor.isShutdown())
        {
            // No. Return an empty runnable tasks list.
            retval = ImmutableList.of();
        }
        else
        {
            // Yes. Now shut down the encapsulated scheduled
            // service.
            retval = mExecutor.shutdownNow();
        }

        return (retval);
    } // end of shutdownNow()

    /**
     * Blocks until all tasks have completed execution after a
     * shutdown request, or the timeout occurs, or the current
     * thread is interrupted, whichever happens first.
     * @param timeout maximum wait time.
     * @param unit {@code timeout} time unit.
     * @return {@code true} if this service terminated and
{@code false} if the timeout elapsed before termination
     * @throws InterruptedException
     * if interrupted while waiting.
     */
    public boolean awaitTermination(final long timeout,
                                    final TimeUnit unit)
        throws InterruptedException
    {
        return (mExecutor.awaitTermination(timeout, unit));
    } // end of awaitTermination(long, TimeUnit)

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Schedule Methods.
    //

    /**
     * Submits a single-shot timer which expires after the given
     * delay.
     * @param timerName timer name meaningful to caller. This
     * name may be {@code null} or an empty string.
     * @param callback execute this task when timer expires.
     * @param agent dispatch timer expiration event to this
     * agent's queue.
     * @param delay timer expires after this delay.
     * @return a {@code ScheduledFuture} representing pending
     * completion of the task and whose {@code get()} method
     * returns {@code null} upon completion.
     * @throws NullPointerException
     * if {@code callback}, {@code agent}, or {@code delay} is
     * {@code null}.
     * @throws RejectedExecutionException
     * if {@code delay} &lt; zero, this service is shut down, or
if {@code agent} is not registered with a dispatcher.
     */
    public ScheduledFuture<?> schedule(@Nullable final String timerName,
                                       final Consumer<EfsTimerEvent> callback,
                                       final IEfsAgent agent,
                                       final Duration delay)
    {
        final EfsTimerTask task;
        final ScheduledFuture<?> retval;

        // Make sure parameters are not null.
        Objects.requireNonNull(callback, NULL_CALLBACK);
        Objects.requireNonNull(agent, NULL_AGENT);
        Objects.requireNonNull(delay, NULL_DELAY);

        if (!EfsDispatcher.isRegistered(agent))
        {
            throw (
                new RejectedExecutionException(
                    agent.name() + UNREGISTERED_AGENT));
        }

        if (mExecutor.isShutdown())
        {
            throw (
                new RejectedExecutionException(EXEC_SHUT_DOWN));
        }

        sLogger.debug(
            "scheduling single shot timer, delay={}, agent={}.",
            delay,
            agent.name());

        // Create timer task and schedule with service.
        task =
            new EfsTimerTask(timerName, callback, agent, this);
        retval = mExecutor.schedule(task,
                                    delay.toNanos(),
                                    TimeUnit.NANOSECONDS);

        return (retval);
    } // end of scheduleAtFixedRate(Runnable,IEfsAgent,Duration)

    /**
     * Submits a periodic action that becomes enabled first after
     * initial delay, and subsequently with the given period;
     * that is, executions will commence after initialDelay, then
     * initialDelay + period, then initialDelay + 2 * period, and
     * so on.
     * <p>
     * The given timer will continue to be indefinitely executed
     * until one of the following occurs:
     * </p>
     * <ul>
     *   <li>
     *     The timer is explicitly canceled via the returned
     *     {@link ScheduledFuture} instance.
     *   <li>
    The service is terminated which results in all
    scheduled tasks being canceled.
  </li>
     *   <li>
     *     The task's execution results in a thrown exception.
     *   </li>
     * </ul>
     * Once a timer is canceled, subsequent executions are
     * suppressed and {@link ScheduledFuture#isDone() isDone}
     * returns {@code true}.
     * <p>
     * If any callback execution takes longer than its period,
     * then subsequent executions may start late but will not
     * result in multiple scheduled expirations.
     * </p>
     * @param timerName timer name meaningful to caller. This
     * name may be {@code null} or an empty string.
     * @param callback execute this task when timer expires.
     * @param agent dispatch timer expiration event to this
     * agent's queue.
     * @param initialDelay timer first expiration after this
     * delay.
     * @param period timer subsequent expirations after this
     * period.
     * @return a {@code ScheduledFuture} representing pending
     * completion of the task and whose {@code get()} method
     * returns {@code null} upon completion.
     * @throws NullPointerException
     * if {@code callback}, {@code agent}, {@code initialDelay},
     * or {@code period} is {@code null}.
     * @throws RejectedExecutionException
     * if {@code initialDelay} &lt; zero, {@code period} &le;
zero, this service is shut down, or if {@code agent} is
     * not registered with a dispatcher.
     */
    public ScheduledFuture<?> scheduleAtFixedRate(@Nullable final String timerName,
                                                  final Consumer<EfsTimerEvent> callback,
                                                  final IEfsAgent agent,
                                                  final Duration initialDelay,
                                                  final Duration period)
    {
        final EfsTimerTask task;
        final ScheduledFuture<?> retval;

        // Make sure parameters are not null.
        Objects.requireNonNull(callback, NULL_CALLBACK);
        Objects.requireNonNull(agent, NULL_AGENT);
        Objects.requireNonNull(
            initialDelay, NULL_INIT_DELAY);
        Objects.requireNonNull(period, NULL_PERIOD);

        // Make sure period is > zero.
        if (period.compareTo(Duration.ZERO) <= 0)
        {
            throw (
                new RejectedExecutionException(NEGATIVE_PERIOD));
        }

        if (!EfsDispatcher.isRegistered(agent))
        {
            throw (
                new RejectedExecutionException(
                    agent.name() + UNREGISTERED_AGENT));
        }

        if (mExecutor.isShutdown())
        {
            throw (
                new RejectedExecutionException(EXEC_SHUT_DOWN));
        }

        sLogger.debug(
            "scheduling fix rate timer, initial delay: {}, period: {}, agent={}.",
            initialDelay,
            period,
            agent.name());

        // Create timer task and store in timer priority queue.
        task =
            new EfsTimerTask(timerName, callback, agent, this);
        retval =
            mExecutor.scheduleAtFixedRate(task,
                                          initialDelay.toNanos(),
                                          period.toNanos(),
                                          TimeUnit.NANOSECONDS);

        return (retval);
    } // end of scheduleAtFixedRate(...)

    /**
     * Submits a periodic task which expires for the first time
     * after the initial delay and then repeatedly with the given
     * delay between the termination of the previous expiration
     * and the commencement of the next. This means that callback
     * execution time does not impact scheduling the subsequent
     * expirations. When the callback completes, the next
     * expiration is current time plus delay.
     * <p>
     * The given timer will continue to be indefinitely
     * executed until one of the following occurs:
     * </p>
     * <ul>
     *   <li>
     *     The timer is explicitly canceled via the returned
     *     {@link ScheduledFuture} instance.
     *   </li>
     *   <li>
    The service is terminated which results in all
    scheduled tasks being canceled.
  </li>
     *   <li>
     *     The callback's execution results in a thrown
     *     exception.
     *   </li>
     * </ul>
     * Once a timer is canceled, subsequent executions are
     * suppressed and {@link ScheduledFuture#isDone() isDone}
     * returns {@code true}.
     * @param timerName timer name meaningful to caller. This
     * name may be {@code null} or an empty string.
     * @param callback execute this task when timer expires.
     * @param agent dispatch timer event to this agent's event
     * queue.
     * @param initialDelay timer first expiration after this
     * delay.
     * @param delay timer subsequence expirations after this
     * delay.
     * @return a {@code ScheduledFuture} representing pending
     * completion of the task and whose {@code get()} method
     * returns {@code null} upon completion.
     * @throws NullPointerException
     * if {@code callback}, {@code agent}, {@code initialDelay},
     * or {@code delay} is {@code null}.
     * @throws RejectedExecutionException
     * if {@code initialDelay} &lt; zero, {@code delay} &le;
zero, this service is shut down, or if {@code agent} is
     * not registered with a dispatcher.
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(@Nullable final String timerName,
                                                     final Consumer<EfsTimerEvent> callback,
                                                     final IEfsAgent agent,
                                                     final Duration initialDelay,
                                                     final Duration delay)
    {
        final EfsTimerTask task;
        final ScheduledFuture<?> retval;

        // Make sure parameters are not null.
        Objects.requireNonNull(callback, NULL_CALLBACK);
        Objects.requireNonNull(agent, NULL_AGENT);
        Objects.requireNonNull(
            initialDelay, NULL_INIT_DELAY);
        Objects.requireNonNull(delay, NULL_DELAY);

        // Make sure repeating delay is > zero.
        if (delay.compareTo(Duration.ZERO) <= 0)
        {
            throw (
                new RejectedExecutionException(
                    NEGATIVE_REPEAT_DELAY));
        }

        if (!EfsDispatcher.isRegistered(agent))
        {
            throw (
                new RejectedExecutionException(
                    agent.name() + UNREGISTERED_AGENT));
        }

        if (mExecutor.isShutdown())
        {
            throw (
                new RejectedExecutionException(EXEC_SHUT_DOWN));
        }

        sLogger.debug(
            "scheduling fix delay timer, initial delay: {}, delay: {}, agent={}.",
            initialDelay,
            delay,
            agent.name());

        // Create timer task and store in timer priority queue.
        task =
            new EfsTimerTask(timerName, callback, agent, this);
        retval =
            mExecutor.scheduleWithFixedDelay(
                task,
                initialDelay.toNanos(),
                delay.toNanos(),
                TimeUnit.NANOSECONDS);

        return (retval);
    } // end of scheduleWithFixedDelay(...)

    //
    // end of Schedule Methods.
    //-----------------------------------------------------------

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * This task is used to deliver an {@link EfsTimerEvent}
     * to the {@link IEfsAgent} which scheduled this task. This
     * task is inactive when scheduled executor is shut down.
     */
    @Immutable
    private static final class EfsTimerTask
        implements Runnable
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * User-specified timer name. May be {@code null} or an
         * empty string.
         */
        @Nullable private final String mTimerName;

        /**
         * Post timer event to this agent callback.
         */
        private final Consumer<EfsTimerEvent> mCallback;

        /**
         * Post timer event to this agent.
         */
        private final IEfsAgent mAgent;

        /**
         * This task is executed by {@link EfsScheduledExecutor}.
         */
        private final EfsScheduledExecutor mExecutor;

//-----------------------------------------------------------
// Member methods.
//
        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates a new timer task used to deliver timer events
         * to agent using specified callback.
         * @param timerName optional user-defined timer name.
         * @param callback deliver timer event to this method.
         * @param agent deliver timer event to this agent.
         * @param executor efs scheduled service creating this
task.
         */
        private EfsTimerTask(@Nullable final String timerName,
                             final Consumer<EfsTimerEvent> callback,
                             final IEfsAgent agent,
                             final EfsScheduledExecutor executor)
        {
            mTimerName = timerName;
            mCallback = callback;
            mAgent = agent;
            mExecutor = executor;
        } // end of EfsTimerTask(String, Consumer, IEfsAgent)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Runnable Interface Implementation.
        //

        /**
         * Dispatches a timer event to agent and callback
         * <em>if</em> this timer task is still active. Otherwise
         * does nothing.
         */
        @Override
        public void run()
        {
            // Is service still active?
            if (!mExecutor.isShutdown())
            {
                // Yes. Create the timer event based on
                // configured timer name and current nanosecond
                // time.
                final EfsTimerEvent timerEvent =
                    new EfsTimerEvent(
                        mTimerName, System.nanoTime());

                EfsDispatcher.dispatch(
                    mCallback, timerEvent, mAgent);
            }
        } // end of run()

        //
        // end of Runnable Interface Implementation.
        //-------------------------------------------------------
    } // end of class EfsTimerTask
} // end of class EfsScheduledExecutor
