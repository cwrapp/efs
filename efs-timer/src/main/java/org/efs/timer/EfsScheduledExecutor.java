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

import jakarta.annotation.Nullable;
import java.time.Duration;
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
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * Executes given timer, {@link IEfsAgent} pairs at a specified
 * time. When task timer expires, timer event is dispatched to
 * {@code IEfsAgent} via
 * {@link EfsDispatcher#dispatch(Consumer, org.efs.dispatcher.IEfsEvent, IEfsAgent)}.
 * <p>
 * Schedule methods are used to create timers with various
 * delays, returning a {@link ScheduledFuture} which can
 * be used to cancel the timer or check its status. Methods
 * {@link #scheduleAtFixedRate(String, Object, Consumer, IEfsAgent, Duration, Duration)}
 * and
 * {@link #scheduleWithFixedDelay(String, Object, Consumer, IEfsAgent, Duration, Duration)}
 * create and execute timer tasks which run periodically until
 * canceled.
 * </p>
 * <p>
 * Unlike {@code java.util.concurrent.ScheduledExecutorService},
 * efs scheduled service does <em>not</em> support delay or
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
 * {@code EfsScheduledExecutor} uses a user-provided
 * {@code ScheduledExecutorService} instance. The user is
 * responsible for creating, maintaining, and terminating this
 * executor service. {@code EfsScheduleExecutor} assumes that
 * its encapsulated executor service is available when scheduling
 * callback tasks. This means that
 * {@code RejectedExecutionException} may be thrown when calling
 * {@code EfsScheduleExecutor} schedule methods. Caller is
 * responsible for handling these exceptions when thrown.
 * </p>
 * <p>
 * By encapsulating a user-provided
 * {@code ScheduledExecutorService}, {@code EfsScheduledExecutor}
 * allows the user to select the executor service implementation.
 * </p>
 * <p>
 * Because this {@code ScheduledExecutorService} is owned by the
 * {@code EfsScheduledExecutor} user it is possible to use this
 * executor service for other tasks.
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

        // No data is forwarded to onTimeout.
        mUpdateTimer = service.scheduleWithFixedDelay(UPDATE_TIMER_NAME, null, this::onTimeout, this, TIMER_DELAY, TIMER_DELAY);
    }

    public void shutdown() {
        stopTimer();
    }

    private void onUpdate(final AppUpdate update) {
        mLatestUpdate = update.timestampAsInstant();

        // Do update work here.
        ...
    }

    private void onTimeout(final EfsTimerEvent timerEvent) {
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
                mUpdateTimer.cancel();
            } catch (Exception jex) {
                // Do nothing.
            } finally {
                mUpdateTimer = null;
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
     * {@code EfsScheduledExecutor}s.
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
     * {@code null} scheduled service  message is {@value}.
     */
    public static final String NULL_EXECUTOR = "executor is null";

    /**
     * Invalid executor service message is {@value}.
     */
    public static final String INVALID_EXECUTOR =
        "executor is shutdown";

    /**
     * Invalid thread type message is {@value}.
     */
    public static final String NULL_TYPE = "type is null";

    /**
     * {@code null} callback lambda message is {@value}.
     */
    public static final String NULL_CALLBACK =
        "callback is null";

    /**
     * {@code null} efs agent message is {@value}.
     */
    public static final String NULL_AGENT = "agent is null";

    /**
     * {@code null} delay message is {@value}.
     */
    public static final String NULL_DELAY = "delay is null";

    /**
     * {@code null} initial delay message is {@value}.
     */
    public static final String NULL_INIT_DELAY =
        "initial delay is null";

    /**
     * Invalid {@code null} period message is {@value}.
     */
    public static final String NULL_PERIOD = "period is null";

    /**
     * Invalid negative delay message is {@value}.
     */
    public static final String NEGATIVE_DELAY = "delay < zero";

    /**
     * Invalid delay exceeding long size message is {@value}.
     */
    public static final String EXCESSIVE_DELAY =
        "delay > Long.MAX_VALUE";

    /**
     * Invalid negative initial delay message is {@value}.
     */
    public static final String NEGATIVE_INIT_DELAY =
        "initial delay < zero";

    /**
     * Invalid initial delay exceeding long size message is
     * {@value}.
     */
    public static final String EXCESSIVE_INIT_DELAY =
        "initial delay > Long.MAX_VALUE";

    /**
     * Invalid negative period  message is {@value}.
     */
    public static final String NEGATIVE_PERIOD =
        "period <= zero";

    /**
     * Invalid period exceeding long size message is {@value}.
     */
    public static final String EXCESSIVE_PERIOD =
        "period > Long.MAX_VALUE";

    /**
     * Negative repeat delay message is {@value}.
     */
    public static final String NEGATIVE_REPEAT_DELAY =
        "delay <= zero";

    /**
     * Unregistered agent message is {@value}.
     */
    public static final String UNREGISTERED_AGENT =
        " is not registered with a dispatcher";

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(EfsScheduledExecutor.class);

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
     * given Java scheduled service.
     * @param executor encapsulated Java scheduled service
     * service.
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
    // Schedule Methods.
    //

    /**
     * Submits a single-shot timer which expires after the given
     * delay.
     * @param timerName timer name meaningful to caller. This
     * name may be {@code null} or an empty string.
     * @param datum user-provided data which will be forwarded
     * to {@code callback} in {@link EfsTimerEvent}. May be
     * {@code null}.
     * @param callback execute this task when timer expires.
     * @param agent dispatch timer dispatchTimestamp event to this
     * agent's queue.
     * @param delay timer expires after this delay.
     * @return a {@code ScheduledFuture} representing pending
     * completion of the task and whose {@code get()} method
     * returns {@code null} upon completion.
     * @throws NullPointerException
     * if {@code callback}, {@code agent}, or {@code delay} is
     * {@code null}.
     * @throws RejectedExecutionException
     * if:
     * <ul>
     *   <li>
     *     {@code delay} &lt; zero or &gt; {@link Long#MAX_VALUE}
     *     nanoseconds,
     *   </li>
     *   <li>
     *     this service is shut down,
     *   </li>
     *   <li>
     *     {@code agent} is not registered with a dispatcher, or
     *   </li>
     *   <li>
     *     {@link ScheduledExecutorService#schedule(Runnable, long, TimeUnit) underlying executor}
     *     throws this exception.
     *   </li>
     * </ul>
     */
    public ScheduledFuture<?> schedule(@Nullable final String timerName,
                                       @Nullable final Object datum,
                                       final Consumer<EfsTimerEvent> callback,
                                       final IEfsAgent agent,
                                       final Duration delay)
    {
        final long nanosDelay;
        final EfsTimerTask task;
        final ScheduledFuture<?> retval;

        // Make sure parameters are not null.
        Objects.requireNonNull(callback, NULL_CALLBACK);
        Objects.requireNonNull(agent, NULL_AGENT);
        Objects.requireNonNull(delay, NULL_DELAY);

        // Make sure delay is >= zero.
        if (delay.compareTo(Duration.ZERO) < 0)
        {
            throw (
                new RejectedExecutionException(NEGATIVE_DELAY));
        }

        try
        {
            nanosDelay = delay.toNanos();
        }
        catch (ArithmeticException arthex)
        {
            throw (
                new RejectedExecutionException(
                    EXCESSIVE_DELAY, arthex));
        }

        if (!EfsDispatcher.isRegistered(agent))
        {
            throw (
                new RejectedExecutionException(
                    agent.name() + UNREGISTERED_AGENT));
        }

        sLogger.debug(
            "scheduling single shot timer, delay={}, agent={}, timer={}.",
            delay,
            agent.name(),
            timerName);

        // Create timer task and schedule with service.
        task =
            new EfsTimerTask(timerName, datum, callback, agent);
        retval = mExecutor.schedule(task,
                                    nanosDelay,
                                    TimeUnit.NANOSECONDS);

        return (retval);
    } // end of schedule(...)

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
     *   </li>
     *   <li>
     *     The service is terminated which results in all
     *     scheduled tasks being canceled.
     *   </li>
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
     * @param datum user-provided data which will be forwarded
     * to {@code callback} in {@link EfsTimerEvent}. May be
     * {@code null}.
     * @param callback execute this task when timer expires.
     * @param agent dispatch timer dispatchTimestamp event to this
     * agent's queue.
     * @param initialDelay timer first dispatchTimestamp after this
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
     * if:
     * <ul>
     *   <li>
     *     {@code initialDelay} &lt; zero or &gt;
     *     {@link Long#MAX_VALUE} nanoseconds,
     *   </li>
     *   <li>
     *     {@code period} &le; zero or &gt;
     *     {@link Long#MAX_VALUE} nanoseconds,
     *   </li>
     *   <li>
     *     this service is shut down,
     *   </li>
     *   <li>
     *     {@code agent} is not registered with a dispatcher, or
     *   </li>
     *   <li>
     *     {@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit) underlying executor}
     *     throws this exception.
     *   </li>
     * </ul>
     */
    public ScheduledFuture<?> scheduleAtFixedRate(@Nullable final String timerName,
                                                  @Nullable final Object datum,
                                                  final Consumer<EfsTimerEvent> callback,
                                                  final IEfsAgent agent,
                                                  final Duration initialDelay,
                                                  final Duration period)
    {
        final long nanosInitDelay;
        final long nanosPeriod;
        final EfsTimerTask task;
        final ScheduledFuture<?> retval;

        // Make sure parameters are not null.
        Objects.requireNonNull(callback, NULL_CALLBACK);
        Objects.requireNonNull(agent, NULL_AGENT);
        Objects.requireNonNull(
            initialDelay, NULL_INIT_DELAY);
        Objects.requireNonNull(period, NULL_PERIOD);

        // Make sure initial delay is >= zero.
        if (initialDelay.compareTo(Duration.ZERO) < 0)
        {
            throw (
                new RejectedExecutionException(
                    NEGATIVE_INIT_DELAY));
        }

        try
        {
            nanosInitDelay = initialDelay.toNanos();
        }
        catch (ArithmeticException arthex)
        {
            throw (
                new RejectedExecutionException(
                    EXCESSIVE_INIT_DELAY, arthex));
        }

        try
        {
            nanosPeriod = period.toNanos();
        }
        catch (ArithmeticException arthex)
        {
            throw (
                new RejectedExecutionException(
                    EXCESSIVE_PERIOD, arthex));
        }

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

        sLogger.debug(
            "scheduling fixed rate timer, initial delay: {}, period: {}, agent={}, timer={}.",
            initialDelay,
            period,
            agent.name(),
            timerName);

        // Create timer task and store in timer priority queue.
        task =
            new EfsTimerTask(timerName, datum, callback, agent);
        retval =
            mExecutor.scheduleAtFixedRate(task,
                                          nanosInitDelay,
                                          nanosPeriod,
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
     *     The service is terminated which results in all
     *     scheduled tasks being canceled.
     *   </li>
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
     * @param datum user-provided data which will be forwarded
     * to {@code callback} in {@link EfsTimerEvent}. May be
     * {@code null}.
     * @param callback execute this task when timer expires.
     * @param agent dispatch timer event to this agent's event
     * queue.
     * @param initialDelay timer first dispatchTimestamp after this
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
     * <ul>
     *   <li>
     *     if {@code initialDelay} &lt; zero or &gt;
     *     {@link Long#MAX_VALUE} nanoseconds,
     *   </li>
     *   <li>
     *     {@code delay} &le; zero or &gt;
     *     {@link Long#MAX_VALUE} nanoseconds,
     *   </li>
     *   <li>
     *     this service is shut down,
     *   </li>
     *   <li>
     *     {@code agent} is not registered with a dispatcher,
     *   </li>
     *   <li>
     *     {@link ScheduledExecutorService#scheduleWithFixedDelay(Runnable, long, long, TimeUnit) underlying executor}
     *     throws this exception.
     *   </li>
     * </ul>
     *
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(@Nullable final String timerName,
                                                     @Nullable final Object datum,
                                                     final Consumer<EfsTimerEvent> callback,
                                                     final IEfsAgent agent,
                                                     final Duration initialDelay,
                                                     final Duration delay)
    {
        final long nanosInitDelay;
        final long nanosDelay;
        final EfsTimerTask task;
        final ScheduledFuture<?> retval;

        // Make sure parameters are not null.
        Objects.requireNonNull(callback, NULL_CALLBACK);
        Objects.requireNonNull(agent, NULL_AGENT);
        Objects.requireNonNull(
            initialDelay, NULL_INIT_DELAY);
        Objects.requireNonNull(delay, NULL_DELAY);

        // Make sure initial delay is >= zero.
        if (initialDelay.compareTo(Duration.ZERO) < 0)
        {
            throw (
                new RejectedExecutionException(
                    NEGATIVE_INIT_DELAY));
        }

        try
        {
            nanosInitDelay = initialDelay.toNanos();
        }
        catch (ArithmeticException arthex)
        {
            throw (
                new RejectedExecutionException(
                    EXCESSIVE_INIT_DELAY, arthex));
        }

        // Make sure repeating delay is > zero.
        if (delay.compareTo(Duration.ZERO) <= 0)
        {
            throw (
                new RejectedExecutionException(
                    NEGATIVE_REPEAT_DELAY));
        }

        try
        {
            nanosDelay = delay.toNanos();
        }
        catch (ArithmeticException arthex)
        {
            throw (
                new RejectedExecutionException(
                    EXCESSIVE_DELAY, arthex));
        }

        if (!EfsDispatcher.isRegistered(agent))
        {
            throw (
                new RejectedExecutionException(
                    agent.name() + UNREGISTERED_AGENT));
        }

        sLogger.debug(
            "scheduling fixed delay timer, initial delay: {}, delay: {}, agent={}, timer={}.",
            initialDelay,
            delay,
            agent.name(),
            timerName);

        // Create timer task and store in timer priority queue.
        task =
            new EfsTimerTask(timerName, datum, callback, agent);
        retval =
            mExecutor.scheduleWithFixedDelay(
                task,
                nanosInitDelay,
                nanosDelay,
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
     * <p>
     * Note: once a timer task begin executing, it cannot be
     * stopped but must continue until completion.
     * </p>
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
         * User-specified datum forwarded in timer event.
         */
        @Nullable private final Object mDatum;

        /**
         * Post timer event to this agent callback.
         */
        private final Consumer<EfsTimerEvent> mCallback;

        /**
         * Post timer event to this agent.
         */
        private final IEfsAgent mAgent;

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
         * @param datum optional user-provided datum.
         * @param callback deliver timer event to this method.
         * @param agent deliver timer event to this agent.
         * @param executor efs scheduled service creating this
         * task.
         */
        private EfsTimerTask(@Nullable final String timerName,
                             @Nullable final Object datum,
                             final Consumer<EfsTimerEvent> callback,
                             final IEfsAgent agent)
        {
            mTimerName = timerName;
            mDatum = datum;
            mCallback = callback;
            mAgent = agent;
        } // end of EfsTimerTask(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Runnable Interface Implementation.
        //

        /**
         * Dispatches a timer event to agent and callback.
         */
        @Override
        public void run()
        {
            final EfsTimerEvent timerEvent =
                new EfsTimerEvent(
                    mTimerName, mDatum, System.nanoTime());

            EfsDispatcher.dispatch(
                mCallback, timerEvent, mAgent);
        } // end of run()

        //
        // end of Runnable Interface Implementation.
        //-------------------------------------------------------
    } // end of class EfsTimerTask
} // end of class EfsScheduledExecutor
