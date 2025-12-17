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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigBeanFactory;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.openhft.affinity.AffinityLock;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import org.efs.dispatcher.EfsDispatcher;
import static org.efs.dispatcher.EfsDispatcher.DEFAULT_PARK_TIME;
import static org.efs.dispatcher.EfsDispatcher.DEFAULT_SPIN_LIMIT;
import org.efs.dispatcher.IEfsAgent;
import org.efs.dispatcher.config.ThreadAffinity;
import org.efs.dispatcher.config.ThreadAffinityConfig;
import org.efs.dispatcher.config.ThreadType;
import static org.efs.dispatcher.config.ThreadType.BLOCKING;
import static org.efs.dispatcher.config.ThreadType.SPINNING;
import static org.efs.dispatcher.config.ThreadType.SPINYIELD;
import org.efs.logging.AsyncLoggerFactory;
import org.efs.timer.config.EfsScheduledExecutorConfig;
import static org.efs.timer.config.EfsScheduledExecutorConfig.EXECUTOR_NAME_KEY;
import static org.efs.timer.config.EfsScheduledExecutorConfig.PARK_TIME_KEY;
import static org.efs.timer.config.EfsScheduledExecutorConfig.SPIN_LIMIT_KEY;
import org.efs.timer.config.EfsScheduledExecutorsConfig;
import org.slf4j.Logger;

/**
 * Executes given timer, {@link IEfsAgent} pairs at a specified
 * time. When task timer expires, timer event is dispatched to
 * {@code IEfsAgent} via
 * {@link EfsDispatcher#dispatch(Consumer, IEfsEvent, IEfsAgent)}.
 * <p>
 * Schedule methods are used to create timers with various
 * delays, returning a {@link IEfsTimer timer object} which can
 * be used to cancel the timer or check its status. Methods
 * {@link #scheduleAtFixedRate(String, Consumer, IEfsAgent, Duration, Duration)}
 * and
 * {@link #scheduleWithFixedDelay(String, Consumer, IEfsAgent, Duration, Duration)}
 * create and execute timer tasks which run periodically until
 * canceled.
 * </p>
 * <p>
 * Unlike {@code java.util.concurrent.ScheduledExecutorService},
 * efs scheduled executor does <em>not</em> support delay or
 * period &lt; zero. A repeating fixed delay or repeating fixed
 * rate period must be &gt; zero. A zero single-shot delay or
 * initial delay must be &ge; zero.
 * </p>
 * <p>
 * All scheduled methods accept <em>relative</em> delays and
 * periods as arguments and not absolute times or date.
 * </p>
 * <h2>Creating an EScheduledTimer</h2>
 * efs scheduled timers can be created in two ways: one at start
 * up by defining them in
 * {@code -Dorg.efs.timer.configFile=<config file> scheduledExecutors}
 * block or programmatically using
 * {@code EfsScheduleExecutor.Builder} to create a
 * {@code EfsScheduledExecutor} instance. See
 * {@link org.efs.timer.config.EfsScheduledExecutorConfig}
 * for details on configuring an efs scheduled executor.
 * <p>
 * Note: efs scheduled executors are required to have a unique
 * name. This allows efs scheduled executor instances to be
 * retrieved using {@link #getExecutor(String)}. An attempt to
 * create an executor with a named equaling an existing
 * executor's will result in an exception.
 * <h2>Usage Example</h2>
 * This code example assumes that an efs scheduled executor
 * named "fast-timer" has already been created.
 * <pre><code>import java.time.Duration;
import java.time.Instant;
import org.efs.dispatcher.IEfsAgent
import org.efs.timer.EfsScheduledExecutor;
import org.efs.timer.EfsScheduledExecutor.IEfsTimer;
import org.efs.timer.EfsTimerEvent;

public class MyAgent implements IEfsAgent {
    private static final String EXECUTOR_NAME = "fast-timer";
    private static final String UPDATE_TIMER_NAME = "update-timer";

    // Verify that at least one update is received over the past minute.
    private static final Duration TIMER_DELAY = Duration.ofMinutes(1L);

    private Instant mLatestUpdate;
    private IEfsTimer mUpdateTimer;

    public MyAgent() {
    }

    public void startup() {
        // Do some work at a fixed delay.
        final EfsScheduledExecutor executor = EfsScheduledExecutor.getExecutor(EXECUTOR_NAME);

        mUpdateTimer = executor.scheduleWithFixedDelay(UPDATE_TIMER_NAME, this::onTimeout, this, TIMER_DELAY, TIMER_DELAY);
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
                mTimer.close();
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
 * @see IEfsTimer
 * @see EfsScheduledExecutorConfig
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public abstract class EfsScheduledExecutor
    extends Thread
{
//---------------------------------------------------------------
// Member enums.
//

    /**
     * An efs-scheduled timer may be in one of three states:
     * <ul>
     *   <li>
     *     waiting for an active timer to expire,
     *   </li>
     *   <li>
     *     timer has expired and is no longer active, and
     *   </li>
     *   <li>
     *     timer is canceled, will never expire, and is no longer
     *     active.
     *   </li>
     * </ul>
     */
    public enum TimerState
    {
        /**
         * Waiting for active timer to expire.
         */
        ACTIVE (true),

        /**
         * Timer successfully reached expiration and is no longer
         * active.
         */
        EXPIRED (false),

        /**
         * Timer is canceled, will never expire, and is no longer
         * active.
         */
        CANCELED (false);

    //-----------------------------------------------------------
    // Member data.
    //


        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Set to {@code true} if state means timer has yet to
         * expire or be canceled.
         */
        private final boolean mIsActive;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private TimerState(final boolean isActive)
        {
            mIsActive = isActive;
        } // end of TimerState(boolean)

        //
        // end of Constructors
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns {@code true} if timer is in a state yet to
         * expire or be canceled.
         * @return {@code true} if timer is active, capable
         * of expiring, and forwarding timer event to agent.
         */
        public boolean isActive()
        {
            return (mIsActive);
        } // end of isActive()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of enum TimerState

    /**
     * Enumerates supported timer type: single shot, repeated at
     * a fixed rate, and repeated at a fixed delay.
     */
    public enum TimerType
    {
        /**
         * Timer expires only once and then is permanently
         * inactive.
         */
        SINGLE_SHOT (false),

        /**
         * Timer repeats at a fixed rate based on when the timer
         * was initially scheduled. Subsequent expirations will
         * be scheduled on the initial timestamp + (n * period).
         * This timer is best used for "alarm clock" timers which
         * need to expire at a certain wall clock time.
         */
        REPEAT_FIXED_RATE (true),

        /**
         * Timer repeats at fixed delay based on when timer
         * expires. Subsequence expirations will be scheduled
         * on the current time + periodic delay. This timer is
         * best used for events which must occur at a fixed delay
         * independent of wall clock time.
         */
        REPEAT_FIXED_DELAY (true);

    //-----------------------------------------------------------
    // Member data.
    //


        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Set to {@code true} if this is a repeating timer;
         * {@code false} if single shot.
         */
        private final boolean mIsRepeating;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private TimerType(final boolean isRepeating)
        {
            mIsRepeating = isRepeating;
        } // end of TimerType(boolean)

        //
        // end of Constructors
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns {@code true} if this is a repeating timer.
         * @return {@code true} if this is a repeating timer.
         */
        public boolean isRepeating()
        {
            return (mIsRepeating);
        } // end of isRepeating()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of enum TimerType

    /**
     * Provides reason for a timer no longer running.
     */
    public enum CancelReason
    {
        /**
         * Timer is still running.
         */
        NOT_CANCELED,

        /**
         * A single-shot timer is no longer running due to its
         * sole expiration.
         */
        SINGLE_SHOT_TIMER_EXPIRED,

        /**
         * Timer canceled due to user request.
         */
        USER_REQUEST,

        /**
         * Timer canceled due to associated timer task throwing
         * an exception.
         */
        TASK_EXCEPTION,

        /**
         * Timer canceled due to associated executor shut down.
         */
        EXECUTOR_SHUTDOWN
    } // end of enum CancelReason

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
     * Default scheduled executor thread type is
     * {@link ThreadType#BLOCKING}.
     */
    public static final ThreadType DEFAULT_THREAD_TYPE =
        ThreadType.BLOCKING;

    /**
     * Default scheduled executor thread has
     * {@link Thread#NORM_PRIORITY} priority.
     */
    public static final int DEFAULT_EXECUTOR_PRIORITY =
        Thread.NORM_PRIORITY;

    //
    // Error text.
    //

    /**
     * Invalid name {@code IllegalArgumentException} message is
     * {@value}.
     */
    public static final String INVALID_NAME =
        "name is either null or an empty string";

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
     * Maps unique scheduled executor name to its instance.
     */
    private static final Map<String, EfsScheduledExecutor> sScheduledExecutors =
        new ConcurrentHashMap<>();

    /**
     * If there are multiple scheduled executor threads
     * <em>and</em> defined thread affinity, then this is the
     * affinity lock assigned to the previous executor
     * thread. This lock is used when affinity type is a
     * CPU selection strategy.
     */
    private static AffinityLock sPreviousLock = null;

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger();

    // Class static initialization.
    static
    {
        final String configFile =
            System.getProperty(SCHEDULER_CONFIG_OPTION);

        // Was a -D option provided for scheduled executor
        // configuration file?
        if (!Strings.isNullOrEmpty(configFile))
        {
            // Yes. Load the file and then scheduled executors
            // from configuration.
            try
            {
                loadScheduledExecutorsConfig(configFile);
            }
            catch (Exception jex)
            {
                sLogger.error(
                    "attempt to load EfsScheduledExecutorsConfig from {} failed",
                    configFile,
                    jex);
            }
        }
    } // end of class static initialization.

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Contains active timers yet to expire.
     */
    protected final ConcurrentSkipListSet<IEfsTimer> mTimers;

    /**
     * Continue running while this flag is {@code true}.
     */
    protected volatile boolean mRunFlag;

    /**
     * Thread affinity configuration. Used to associate thread
     * with a CPU. Set to {@code null} if the scheduled executor
     * thread has no CPU affinity.
     */
    @Nullable private final ThreadAffinityConfig mAffinity;

    /**
     * Decrement this signal when thread is started.
     */
    private CountDownLatch mStartupSignal;

    /**
     * Decrement this signal when thread is stopped.
     */
    private final CountDownLatch mShutdownSignal;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new efs scheduled executor instance.
     * @param builder contains scheduled executor configuration.
     */
    protected EfsScheduledExecutor(final Builder builder)
    {
        super (builder.mExecutorName);

        mAffinity = builder.mAffinity;
        mStartupSignal = builder.mStartupSignal;
        mShutdownSignal = builder.mShutdownSignal;

        mTimers = new ConcurrentSkipListSet<>();
        mRunFlag = false;
    } // end of EfsScheduledExecutor(...)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Abstract Method Declarations.
    //

    /**
     * Adds a new efs timer to timers set in such a way as to
     * notify {@link #waitForExpiration(EfsTimerImpl)} <em>if</em>
     * the new timer expires before the current next-to-expire
     * timer.
     * @param timer new timer to be added to timers set.
     */
    protected abstract void addTimer(EfsTimerImpl timer);

    /**
     * Removes given timer from timers set in such a way as to
     * notify {@link #waitForExpiration(EfsTimerImpl)} <em>if</em>
     * removed timer is currently next to expire.
     * @param timer timer removed from timers set.
     */
    protected abstract void removeTimer(EfsTimerImpl timer);

    /**
     * Returns next efs timer scheduled to expire. Will return
     * {@code null} if this scheduled executor is stopped.
     * @return next timer scheduled to expire or {@code null} if
     * executor is stopped.
     */
    protected abstract @Nullable EfsTimerImpl pollTimer();

    /**
     * Returns {@code true} if given efs timer expired and
     * {@code false} if the routine was preempted by either this
     * scheduled timer stopping or a new timer was scheduled and
     * is due to expire before this timer.
     * @param timer next timer to expire.
     * @return {@code true} if {@code timer} expires and
     * {@code false} if not.
     */
    protected abstract boolean waitForExpiration(EfsTimerImpl timer);

    //
    // end of Abstract Method Declarations.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Thread Method Overrides.
    //

    @Override
    public final void run()
    {
        final String name = this.getName();
        EfsTimerImpl timer;

        // If dispatcher is configured for thread affinity, then
        // put that affinity in place here.
        if (mAffinity != null)
        {
            setAffinity();
        }

        mRunFlag = true;

        // Decrement start up signal to let creator know this
        // thread is up and running.
        mStartupSignal.countDown();
        mStartupSignal = null;

        sLogger.debug("{}: running.", name);

        // Continue processing timers until this scheduled
        // executor is stopped.
        while (mRunFlag)
        {
            // Get the next scheduled timer to expire. Wait if
            // necessary when there are no scheduled timers.
            timer = pollTimer();

            // Wait for timer to expire or executor to shut down.
            // Note: pollTimer() returns null when run flag is
            // false.
            if (timer != null &&
                mRunFlag &&
                waitForExpiration(timer))
            {
                dispatchExpiredTimers();
            }
        }

        // Cancel all remaining timers and remove from timers
        // set.
        cancelAllTimers();

        // Decrement shutdown signal to let shutdown() method
        // know this thread is dead.
        mShutdownSignal.countDown();
    } // end of run()

    //
    // end of Thread Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns {@code true} if this efs scheduled executor is
     * running and {@code false} if shutdown.
     * @return {@code true} if this executor is running.
     */
    public boolean isRunning()
    {
        return (mRunFlag);
    } // end of isRunning()

    /**
     * Returns {@code true} if efs scheduled executor with the
     * given name exists and {@code false} otherwise.
     * @param name efs scheduled executor name.
     * @return {@code true} if efs scheduled executor named
     * {@code name} exists.
     */
    public static boolean isExecutor(final String name)
    {
        return (sScheduledExecutors.containsKey(name));
    } // end of isExecutor(String)

    /**
     * Returns scheduled executor associated with the given name.
     * Returns {@code null} if no scheduled executor is
     * associated with the name.
     * @param name scheduled executor name.
     * @return scheduled executor named {@code name}.
     * @throws IllegalArgumentException
     * if {@code name} is {@code null} or an empty string.
     */
    public static @Nullable EfsScheduledExecutor getExecutor(final String name)
    {
        if (Strings.isNullOrEmpty(name))
        {
            throw (
                new IllegalArgumentException(
                    "name is null or an empty string"));
        }

        return (sScheduledExecutors.get(name));
    } // end of getExecutor(String)

    /**
     * Returns an immutable copy of executor names.
     * @return executor names list.
     */
    public static List<String> executorNames()
    {
        return (
            ImmutableList.copyOf(sScheduledExecutors.keySet()));
    } // end of executorNames()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Shuts down this executor thread which results in all
     * currently scheduled timers being canceled.
     */
    public void shutdown()
    {
        // Remove executor from map so it may no longer be
        // acquired.
        sScheduledExecutors.remove(this.getName());

        // Set run flag to false and then wake up this thread
        // so it returns to main run() while loop.
        mRunFlag = false;
        signalShutdown();

        // Wait here for thread to stop.
        try
        {
            mShutdownSignal.await();
        }
        catch (InterruptedException interrupt)
        {}
    } // end of shutdown()

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Schedule Methods.
    //

    /**
     * Submits a single-shot task which expires after the given
     * delay. This timer's expiration will not be reported if
     * canceled prior to expiration detection.
     * @param timerName timer name meaningful to caller. This
     * name may be {@code null} or an empty string.
     * @param callback execute this task when timer expires.
     * @param agent dispatch timer expiration event to this
     * agent's queue.
     * @param delay timer expires after this delay.
     * @return an {@code IEfsTimer} instance which can be used to
     * cancel the timer prior to execution.
     * @throws NullPointerException
     * if {@code task}, {@code eobject}, or {@code delay} is
     * {@code null}.
     * @throws RejectedExecutionException
     * if {@code delay} &lt; zero.
     * @throws IllegalStateException
     * if either {@code agent} is not registered with a
     * dispatcher or this executor is shut down.
     */
    public IEfsTimer schedule(@Nullable final String timerName,
                              final Consumer<EfsTimerEvent> callback,
                              final IEfsAgent agent,
                              final Duration delay)
    {
        final long expiration;
        final SingleShotTimer retval;

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

        if (!EfsDispatcher.isRegistered(agent))
        {
            throw (
                new IllegalStateException(
                    agent.name() + UNREGISTERED_AGENT));
        }

        if (!mRunFlag)
        {
            throw (new IllegalStateException(EXEC_SHUT_DOWN));
        }

        sLogger.debug(
            "{}: scheduling single shot timer, delay={}, agent={}.",
            getName(),
            delay,
            agent.name());

        expiration = (System.nanoTime() + delay.toNanos());

        // Create timer task and store in timer priority queue.
        retval = new SingleShotTimer(timerName,
                                     callback,
                                     agent,
                                     expiration);
        addTimer(retval);

        return (retval);
    } // end of scheduleAtFixedRate(Runnable,IEfsAgent,Duration)

    /**
     * Submits a periodic task which expires for the first time
     * after the initial delay and then repeatedly at the
     * periodic rate. In other words, expirations are
     * {@code initialDelay}, then {@code initialDelay + period},
     * then {@code initialDelay + (2 * period)}, and so on.
     * <p>
     * The given timer will continue to be indefinitely executed
     * until one of the following occurs:
     * </p>
     * <ul>
     *   <li>
     *     The timer is explicitly canceled via the returned
     *     {@link IEfsTimer} instance.
     *   <li>
     *     The executor is terminated which results in all
     *     scheduled tasks being canceled.
     *   </li>
     *   <li>
     *     The task's execution results in a thrown exception.
     *   </li>
     * </ul>
     * Once a timer is canceled, subsequent executions are
     * suppressed and {@link IEfsTimer#isDone() isDone} returns
     * {@code true}.
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
     * @return {@code IEfsTimer} instance which can be used to
     * cancel the timer prior to execution.
     */
    public IEfsTimer scheduleAtFixedRate(@Nullable final String timerName,
                                         final Consumer<EfsTimerEvent> callback,
                                         final IEfsAgent agent,
                                         final Duration initialDelay,
                                         final Duration period)
    {
        final long expiration;
        final FixedRateTimer retval;

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

        // Make sure period is > zero.
        if (period.compareTo(Duration.ZERO) <= 0)
        {
            throw (
                new RejectedExecutionException(NEGATIVE_PERIOD));
        }

        if (!EfsDispatcher.isRegistered(agent))
        {
            throw (
                new IllegalStateException(
                    agent.name() + UNREGISTERED_AGENT));
        }

        if (!mRunFlag)
        {
            throw (new IllegalStateException(EXEC_SHUT_DOWN));
        }

        sLogger.debug(
            "{}: scheduling fix rate timer, initial delay: {}, period: {}, agent={}.",
            getName(),
            initialDelay,
            period,
            agent.name());

        expiration =
            (System.nanoTime() + initialDelay.toNanos());

        // Create timer task and store in timer priority queue.
        retval = new FixedRateTimer(timerName,
                                    callback,
                                    agent,
                                    expiration,
                                    period);
        addTimer(retval);

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
     *     {@link IEfsTimer} instance.
     *   </li>
     *   <li>
     *     The executor is terminated which results in all
     *     scheduled tasks being canceled.
     *   </li>
     *   <li>
     *     The callback's execution results in a thrown
     *     exception.
     *   </li>
     * </ul>
     * Once a timer is canceled, subsequent executions are
     * suppressed and {@link IEfsTimer#isDone() isDone} returns
     * {@code true}.
     * @param timerName timer name meaningful to caller. This
     * name may be {@code null} or an empty string.
     * @param callback execute this task when timer expires.
     * @param agent dispatch timer event to this agent's event
     * queue.
     * @param initialDelay timer first expiration after this
     * delay.
     * @param delay timer subsequence expirations after this
     * delay.
     * @return {@code IEfsTimer} instance which can be used to
     * cancel the timer prior to execution.
     */
    public IEfsTimer scheduleWithFixedDelay(@Nullable final String timerName,
                                            final Consumer<EfsTimerEvent> callback,
                                            final IEfsAgent agent,
                                            final Duration initialDelay,
                                            final Duration delay)
    {
        final long expiration;
        final FixedDelayTimer retval;

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
                new IllegalStateException(
                    agent.name() + UNREGISTERED_AGENT));
        }

        if (!mRunFlag)
        {
            throw (new IllegalStateException(EXEC_SHUT_DOWN));
        }

        sLogger.debug(
            "{}: scheduling fix delay timer, initial delay: {}, delay: {}, agent={}.",
            getName(),
            initialDelay,
            delay,
            agent.name());

        expiration =
            (System.nanoTime() + initialDelay.toNanos());

        // Create timer task and store in timer priority queue.
        retval = new FixedDelayTimer(timerName,
                                     callback,
                                     agent,
                                     expiration,
                                     delay);
        addTimer(retval);

        return (retval);
    } // end of scheduleWithFixedDelay(...)

    //
    // end of Schedule Methods.
    //-----------------------------------------------------------

    /**
     * Returns a new {@link Builder} instance. It is highly
     * recommended that {@code builder()} be called for each
     * {@code EfsScheduledExecutor} created.
     * @return a new {code Builder} instance.
     */
    public static Builder builder()
    {
        return (new Builder());
    } // end of builder()

    /**
     * Loads each of the schedule executors contained in
     * typesafe configuration file.
     * @param fileName contains scheduled executor
     * configurations.
     * @throws ConfigException
     * if {@code fileName} contains an invalid scheduled executor
     * configuration.
     */
    public static void loadScheduledExecutorsConfig(final String fileName)
    {
        final File configFile = new File(fileName);
        final Config configSource =
            ConfigFactory.parseFile(configFile);
        final EfsScheduledExecutorsConfig sec =
            ConfigBeanFactory.create(
                configSource.resolve(),
                EfsScheduledExecutorsConfig.class);

        for (EfsScheduledExecutorConfig c : sec.getExecutors())
        {
            builder().set(c).build();
        }
    } // end of loadScheduledExecutors(String)

    /**
     * Returns next timer scheduled to expire or {@code null} if
     * there are no scheduled timers.
     * @return next timer scheduled to expire.
     */
    protected final @Nullable EfsTimerImpl nextTimer()
    {
        EfsTimerImpl retval = null;

        try
        {
            retval = (EfsTimerImpl) mTimers.first();
        }
        catch (NoSuchElementException elemex)
        {
            // Return null;
        }

        return (retval);
    } // end of nextTimer()

    /**
     * Dispatches all expired timers in the timers set. Removes
     * any expired or canceled timers found along the way.
     */
    private void dispatchExpiredTimers()
    {
        final List<EfsTimerImpl> expiredTimers = new ArrayList<>();
        final Iterator<IEfsTimer> timerIt = mTimers.iterator();
        EfsTimerImpl timer;
        long nanoTime;
        boolean continueFlag = true;

        sLogger.debug("{}: dispatching expired timers.",
                      getName());

        while (continueFlag && timerIt.hasNext())
        {
            timer = (EfsTimerImpl) timerIt.next();
            nanoTime = System.nanoTime();
            continueFlag = (timer.expiration() < nanoTime);

            // Is this timer active?
            if (timer.isDone())
            {
                // No. Remove from the timers set. This timer
                // was canceled just prior to expiration and not
                // yet removed from the timers set.
                timerIt.remove();
            }
            // Is this timer expired?
            else if (continueFlag)
            {
                // Yes, this timer is expired. Remove this timer
                // from the timers set. If it is a repeating
                // timer it will be placed back into the set when
                // it is rescheduled.
                timerIt.remove();

                // Dispatch timer task.
                timer.dispatch();

                // Add timer to expired timers list.
                expiredTimers.add(timer);
            }
        }

        // Need to reschedule timers outside of the above loop
        // since rescheduling updates the timers set - which
        // above loop is iterating over.
        for (EfsTimerImpl t : expiredTimers)
        {
            if (t.reschedule())
            {
                addTimer(t);
            }
        }
    } // end of dispatchExpiredTimers()

    /**
     * Cancels all active timers and clears the timers set.
     */
    private void cancelAllTimers()
    {
        final List<IEfsTimer> timers = new ArrayList<>(mTimers);
        EfsTimerImpl eTimer;

        mTimers.clear();

        for (IEfsTimer t : timers)
        {
            eTimer = (EfsTimerImpl) t;

            if (!eTimer.isDone())
            {
                eTimer.setTimerState(
                    TimerState.CANCELED,
                    CancelReason.EXECUTOR_SHUTDOWN,
                    null);
            }
        }
    } // end of cancelAllTimers()

    /**
     * Interrupts this thread in case timer is currently parked.
     */
    private void signalShutdown()
    {
        this.interrupt();
    } // end of signalShutdown()

    /**
     * Sets thread affinity based on the non-{@code null}
     * affinity configuration.
     */
    @SuppressWarnings ({"java:S2696", "java:S3776"})
    private void setAffinity()
    {
        // Is this a strategy affinity?
        if (mAffinity.getAffinityType() ==
                ThreadAffinityConfig.AffinityType.CPU_STRATEGIES)
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
     * Builder for constructing {@link EfsDispatcher} instance.
     * <table class="builder-properties">
     *   <caption>EfsScheduledExecutor Properties</caption>
     *   <tr>
     *     <th>Name</th>
     *     <th>Type</th>
     *     <th>Required?</th>
     *     <th>Default Value</th>
     *     <th>Description</th>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">executor name</td>
     *     <td>{@code String}</td>
     *     <td>Yes</td>
     *     <td>NA</td>
     *     <td>
     *       Unique executor name within JVM. May not be
     *       {@code null} or an empty string.
     *     </td>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">thread type</td>
     *     <td>{@link ThreadType ThreadType}</td>
     *     <td>No</td>
     *     <td>
     *       {@link #DEFAULT_THREAD_TYPE DEFAULT_THREAD_TYPE}
     *     </td>
     *     <td>
     *       Specifies whether this thread is blocking, spinning,
     *       spin+park, or spin+yield.
     *     </td>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">thread priority</td>
     *     <td>{@code int}</td>
     *     <td>No</td>
     *     <td>{@link #DEFAULT_EXECUTOR_PRIORITY DEFAULT_EXECUTOR_PRIORITY}</td>
     *     <td>
     *       Each scheduled executor thread is set to this
     *       priority. Values must be
     *       {@link Thread#MIN_PRIORITY} &le; priority &le;
     *       {@link Thread#MAX_PRIORITY}.
     *     </td>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">spin limit</td>
     *     <td>{@code long}</td>
     *     <td>
     *       No
     *     </td>
     *     <td>{@link EfsDispatcher#DEFAULT_SPIN_LIMIT DEFAULT_SPIN_LIMIT}</td>
     *     <td>
     *       Each dispatcher thread spins this many times
     *       attempting to acquire next runnable agent before
     *       parking or yield. Property only used if thread type
     *       is spin+park or spin+yield.
     *     </td>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">park time</td>
     *     <td>{@code Duration}</td>
     *     <td>No</td>
     *     <td>{@link EfsDispatcher#DEFAULT_PARK_TIME DEFAULT_PARK_TIME}</td>
     *     <td>
     *       Each dispatcher thread parks this amount of time
     *       before spinning on agent queue again. Property only
     *       used if thread type is spin+park.
     *     </td>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">affinity</td>
     *     <td>{@link ThreadAffinityConfig}</td>
     *     <td>No</td>
     *     <td>{@code null}</td>
     *     <td>
     *       Thread affinity for dispatcher subordinate threads.
     *       Ignored if dispatcher type is special.
     *     </td>
     *   </tr>
     * </table>
     * <h2>Example Building EfsScheduledExecutor</h2>
     * <pre><code>import java.time.Duration;
import org.efs.timer.EfsScheduledExecutor;

    // EfsScheduledExecutor.Builder automatically stores executor
    // instance in scheduled executor map using its unique name
    // as key and start executor.
    // Therefore it is not required to place executor instance
    // in a local variable since that executor instance can be
    // readily accessed by its name via
    // EfsScheduleExecutor.getExecutor(name).
    (EfsScheduledExecutor.builder()).executorName("FastExecutor")
                                    .threadType(ThreadType.SPINNING)
                                    .priority(Thread.MAX_PRIORITY)
                                    // See {@link ThreadAffinityConfig}.
                                    .threadAffinity(sFastAffinity)
                                    .build();
    (EfsScheduledExecutor.builder()).executorName("MediumExecutor")
                                    .threadType(ThreadType.SPINPARK)
                                    .priority(7)
                                    .spinLimit(1_000_000L)
                                    .parkTime(Duration.ofNanos(1_000L)
                                    .build();
    (EfsScheduledExecutor.builder()).executorName("SlowExecutor")
                                    .threadType(ThreadType.BLOCKING)
                                    .priority(Thread.MIN_PRIORITY)
                                    .build();</code></pre>
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
         * Unique scheduled executor name.
         */
        private String mExecutorName;

        /**
         * Executor thread type. Defaults to
         * {@link #DEFAULT_THREAD_TYPE}.
         */
        private ThreadType mThreadType;

        /**
         * Scheduled executor thread priority.
         */
        private int mPriority;

        /**
         * Spin limit used for spin+park or spin+yield executor
         * thread.
         */
        protected long mSpinLimit;

        /**
         * Park time used for spin+park executor thread.
         */
        protected Duration mParkTime;

        /**
         * Optional thread affinity configuration.
         */
        @Nullable private ThreadAffinityConfig mAffinity;

        /**
         * Decremented when scheduled executor thread is started.
         */
        private CountDownLatch mStartupSignal;

        /**
         * Decremented when scheduled executor thread is stopped.
         */
        private CountDownLatch mShutdownSignal;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates scheduled executor builder with default
         * settings.
         */
        private Builder()
        {
            mThreadType = DEFAULT_THREAD_TYPE;
            mPriority = DEFAULT_EXECUTOR_PRIORITY;
            mSpinLimit = DEFAULT_SPIN_LIMIT;
            mParkTime = DEFAULT_PARK_TIME;
        } // end of Builder()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets unique executor name.
         * @param name unique executor name.
         * @return {@code this} scheduled executor builder.
         * @throws IllegalArgumentException
         * if {@code name} is {@code null} or an empty string.
         */
        public Builder executorName(final String name)
        {
            if (Strings.isNullOrEmpty(name))
            {
                throw (
                    new IllegalArgumentException(
                        INVALID_NAME));
            }

            mExecutorName = name;

            return (this);
        } // end of executorName(String)

        /**
         * Sets scheduled executor thread type.
         * @param type thread type.
         * @return {@code this} scheduled executor builder.
         * @throws NullPointerException
         * if {@code type} is {@code null}.
         */
        public Builder threadType(final ThreadType type)
        {
            mThreadType =
                Objects.requireNonNull(type, NULL_TYPE);

            return (this);
        } // end of threadType(ThreadType)

        /**
         * Sets the thread priority. Must be
         * &ge; {@link Thread#MIN_PRIORITY} and &le;
         * {@link Thread#MAX_PRIORITY}. If not set, then defaults
         * to {@link Thread#NORM_PRIORITY}.
         * @param priority assigned thread priority for scheduled
         * executor thread.
         * @return {@code this} scheduled executor builder.
         * @throws IllegalArgumentException
         * if {@code priority} &lt; {@code Threads.MIN_PRIORITY}
         * or &gt; {@code Thread.MAX_PRIORITY}.
         */
        public Builder priority(final int priority)
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
         * is ignored for any other scheduled executor thread
         * type.
         * @param limit spin limit.
         * @return {@code this} scheduled executor builder.
         * @throws IllegalArgumentException
         * if {@code limit} &le; zero.
         */
        public Builder spinLimit(final long limit)
        {
            if (limit <= 0L)
            {
                throw (
                    new IllegalArgumentException(
                        "limit <= zero"));
            }

            mSpinLimit = limit;

            return (this);
        } // end of spinLimit(long)

        /**
         * Sets {@link ThreadType#SPINPARK spin+park} park time
         * limit. This setting is ignored for any other
         * scheduled executor thread type.
         * @param time park time limit.
         * @return {@code this} scheduled executor builder.
         * @throws NullPointerException
         * if {@code time} is {@code null}.
         * @throws IllegalArgumentException
         * if {@code time} is &le; zero.
         */
        public Builder parkTime(final Duration time)
        {
            Objects.requireNonNull(time, "time is null");

            if (time.compareTo(Duration.ZERO) <= 0)
            {
                throw (
                    new IllegalArgumentException(
                        "time <= zero"));
            }

            mParkTime = time;

            return (this);
        } // end of parkTime(Duration)

        /**
         * Sets optional thread affinity to the given
         * configuration list. Thread affinity should be
         * considered when using spinning thread type.
         * @param affinity thread affinity configuration.
         * @return {@code this} scheduled executor builder.
         */
        public Builder threadAffinity(@Nullable final ThreadAffinityConfig affinity)
        {
            mAffinity = affinity;

            return (this);
        } // end of threadAffinity(ThreadAffinityConfig)

        /**
         * Configures this efs scheduled executor builder as per
         * given configuration.
         * @param config efs scheduled executor configuration.
         * @return {@code this} scheduled executor builder.
         */
        public Builder set(final EfsScheduledExecutorConfig config)
        {
            final ThreadType threadType = config.getThreadType();

            executorName(config.getExecutorName());
            threadType(threadType);
            priority(config.getPriority());
            threadAffinity(config.getAffinity());

            if (threadType == ThreadType.SPINPARK ||
                threadType == ThreadType.SPINYIELD)
            {
                spinLimit(config.getSpinLimit());

                if (threadType == ThreadType.SPINPARK)
                {
                    parkTime(config.getParkTime());
                }
            }

            return (this);
        } // end of set(EfsScheduledExecutorConfig)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Returns efs scheduled executor based on builder's
         * current settings.
         * @return new efs scheduled executor instance.
         * @throws ValidationException
         * if {@code this Builder} instance contains incomplete
         * or invalid settings.
         */
        public EfsScheduledExecutor build()
        {
            EfsScheduledExecutor retval;

            validate();

            mStartupSignal = new CountDownLatch(1);
            mShutdownSignal = new CountDownLatch(1);

            retval =
                switch (mThreadType)
                {
                    case BLOCKING ->
                        new EfsScheduledExecutorBlocking(this);
                    case SPINNING ->
                        new EfsScheduledExecutorSpinning(this);
                    case SPINYIELD ->
                        new EfsScheduledExecutorSpinYield(this);
                    default ->
                        new EfsScheduledExecutorSpinPark(this);
                };

            sScheduledExecutors.put(mExecutorName, retval);

            // Get the scheduled executor thread up and running
            // before returning.
            retval.setPriority(mPriority);
            retval.start();

            try
            {
                mStartupSignal.await();
            }
            catch (InterruptedException interrupt)
            {}

            return (retval);
        } // end of build()

        /**
         * Validates scheduled executor builder settings.
         * This validation is "fail slow" meaning that a single
         * validation call will determine all configuration
         * errors.
         * @throws ValidationException
         * if {@this Builder} instance contains one or more
         * invalid settings.
         */
        @SuppressWarnings ({"java:S1067"})
        private void validate()
        {
            final Validator problems = new Validator();

            problems.requireNotNull(mExecutorName,
                                    EXECUTOR_NAME_KEY)
                    .requireTrue(
                        // If executor name not set, then
                        // above check will catch that. This
                        // check is to make sure name is unique.
                        (mExecutorName == null ||
                         !sScheduledExecutors.containsKey(mExecutorName)),
                        EXECUTOR_NAME_KEY,
                        "\"" +
                        mExecutorName +
                        "\" is not unique")
                    .requireTrue(
                        ((mThreadType != ThreadType.SPINPARK &&
                          mThreadType != ThreadType.SPINYIELD) ||
                         mSpinLimit > 0L),
                         SPIN_LIMIT_KEY,
                         "not set for spin+park or spin+yield thread type")
                    .requireTrue(
                        (mThreadType != ThreadType.SPINPARK ||
                         (mParkTime != null &&
                          mParkTime.isPositive())),
                        PARK_TIME_KEY,
                        "not set for spin+park thread type")
                    .throwException(EfsScheduledExecutor.class);
        } // end of validate()
    } // end of class Builder

    /**
     * A scheduled timer task which may be active, expired, or
     * canceled. Once a timer is expired or canceled, it will
     * never expire again.
     */
    public static interface IEfsTimer
        extends AutoCloseable,
                Comparable<IEfsTimer>
    {
        /**
         * Returns user-defined timer name. May be {@code null}
         * or an empty string.
         * @return user-defined timer name.
         */
        @Nullable String timerName();

        /**
         * Returns timer current state.
         * @return timer current state.
         *
         * @see #isCanceled()
         * @see #isDone()
         */
        TimerState timerState();

        /**
         * Returns timer type.
         * @return timer type.
         */
        TimerType timerType();

        /**
         * Returns remaining delay until this timer expires. If
         * timer is either expired (and not a repeating timer) or
         * if timer is cancelled, then returns
         * {@link Duration#ZERO}.
         * @return remaining delay until timer expires.
         */
        Duration delay();

        /**
         * Returns {@code true} if this task was successfully
         * canceled prior to expiration. Note that this task
         * may be inactive due to expiration.
         * @return {@code true} if task is canceled.
         *
         * @see #timerState()
         * @see #isDone()
         */
        boolean isCanceled();

        /**
         * Returns reason why timer is no longer running.
         * @return timer cancellation reason.
         */
        CancelReason cancelReason();

        /**
         * Returns exception associated with
         * {@link #cancelReason()} returning
         * {@link CancelReason#TASK_EXCEPTION}. Returns
         * {@code null} for any other cancel reason.
         * @return task exception.
         */
        @Nullable Exception cancelException();

        /**
         * Return {@code true} if timer is repeating.
         * @return {@code true} if timer is repeating.
         */
        boolean isRepeating();

        /**
         * Returns {@code true} if this task is no longer active.
         * This could be due to either expiration or
         * cancellation, there is no distinction between the
         * two.
         * @return {@code true} if task is no longer active.
         *
         * @see #timerState()
         * @see #isCanceled()
         */
        boolean isDone();
    } // end of interface IEfsTimer

    /**
     * Implements {@IEfsTimer} instance and contains necessary
     * information to execute expire timer task on the associated
     * efs client task queue and cancel timer.
     */
    /* package */ abstract class EfsTimerImpl
        implements IEfsTimer
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Specific timer type.
         */
        protected final TimerType mType;

        /**
         * Current timer state.
         */
        protected final AtomicReference<TimerState> mTimerState;

        /**
         * Timer set to expire at this nanosecond time based on
         * {@code System.nanoTime()}. This data member is not
         * {@code final} because it is updated by repeat timers.
         */
        protected long mExpiration;

        /**
         * User-defined timer name. May be {@code null} or an
         * empty string.
         */
        @Nullable private final String mTimerName;

        /**
         * When timer expires, execute this task.
         */
        private final Consumer<EfsTimerEvent> mCallback;

        /**
         * efs agent setting this timer.
         */
        private final IEfsAgent mAgent;

        /**
         * Reason for timer no longer running.
         */
        private CancelReason mCxlReason;

        /**
         * Timer task exception is thrown.
         */
        private Exception mCxlException;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        protected EfsTimerImpl(final String timerName,
                               final Consumer<EfsTimerEvent> callback,
                               final IEfsAgent agent,
                               final long expiration,
                               final TimerType type)
        {
            mTimerName = timerName;
            mCallback = callback;
            mAgent = agent;
            mExpiration = expiration;
            mType = type;
            mTimerState =
                new AtomicReference<>(TimerState.ACTIVE);
        } // end of ETimerImple(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Declarations.
        //

        /**
         * Reschedules a repeat timer. Returns {@code true} if
         * this is a repeat timer and {@code false} if
         * single-shot.
         * @return {@code true} if timer re-scheduled.
         */
        protected abstract boolean reschedule();

        //
        // end of Abstract Method Declarations.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // IEfsTimer Interface Implementation.
        //

        @Override
        public final String timerName()
        {
            return (mTimerName);
        } // end of timerName()

        @Override
        public final TimerState timerState()
        {
            return (mTimerState.get());
        } // end of timerState()

        @Override
        public final TimerType timerType()
        {
            return (mType);
        } // end of timerType()

        /**
         * Returns active timer expiration. Returns
         * {@link Duration#ZERO} if timer is inactive.
         * @return time until timer expiration.
         */
        @Override
        public final Duration delay()
        {
            final long delta = (mExpiration - System.nanoTime());
            final Duration retval;

            // Is this timer expired or canceled?
            if (mTimerState.get() != TimerState.ACTIVE ||
                delta <= 0L)
            {
                // Yes. Return zero.
                retval = Duration.ZERO;
            }
            else
            {
                retval = Duration.ofNanos(delta);
            }

            return (retval);
        } // end of delay()

        @Override
        public final boolean isCanceled()
        {
            return (mTimerState.get() == TimerState.CANCELED);
        } // end of isCanceled()

        @Override
        public final boolean isRepeating()
        {
            return (mType.isRepeating());
        } // end of isRepeating()

        @Override
        public final boolean isDone()
        {
            return (!((mTimerState.get()).isActive()));
        } // end of isDone()

        /**
         * Cancels running timer
         * @throws Exception
         */
        @Override
        public final void close()
            throws Exception
        {
            // Was this timer still active when canceled?
            if (mTimerState.compareAndSet(TimerState.ACTIVE,
                                          TimerState.CANCELED))
            {
                sLogger.debug(
                    "Closing {} timer",
                    mType);

                // Yes. Remove this timer from timer set.
                removeTimer(this);

                setTimerState(TimerState.CANCELED,
                              CancelReason.USER_REQUEST,
                              null);
            }
        } // end of close()

        @Override
        public final CancelReason cancelReason()
        {
            return (mCxlReason);
        } // end of cancelReason()

        @Override
        public final @Nullable Exception cancelException()
        {
            return (mCxlException);
        } // end of cancelException()

        //
        // end of IEfsTimer Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Comparable Interface Implementation.
        //

        /**
         * Returns value &lt;, equal to, or greater than zero
         * based on whether {@code this IEfsTimer}'s expiration is
         * &lt;, equal to; or greater than {@code timer}'s
         * expiration.
         * @param timer compared timer.
         * @return integer values &lt;, equal to, or &gt; zero.
         */
        @Override
        public final int compareTo(final IEfsTimer timer)
        {
            final EfsTimerImpl eTimer = (EfsTimerImpl) timer;

            return (
                Long.compare(mExpiration, eTimer.expiration()));
        } // end of compareTo(IEfsTimer)

        //
        // end of Comparable Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        @Override
        public String toString()
        {
            return (
                String.format(
                    "[type=%s, state=%s, delay=%,d]",
                    mType,
                    mTimerState.get(),
                    expiration()));
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns timer's expiration in nanosecond with respect
         * to {@code System.nanoTime()}.
         * @return timer nanosecond expiration.
         */
        /* package */ long expiration()
        {
            return (mExpiration);
        } // end of expiration()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets timer to given state.
         * @param state next timer state.
         * @param reason reason timer is canceled.
         * @param optional exception associated with timer
         * cancellation.
         */
        protected void setTimerState(final TimerState state,
                                     final CancelReason reason,
                                     final @Nullable Exception cxlException)
        {
            mTimerState.set(state);
            mCxlReason = reason;
            mCxlException = cxlException;
        } // end of setTimerState(TimerState)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Dispatches timer event to agent via callback lambda.
         */
        private void dispatch()
        {
            if (mTimerState.get() == TimerState.ACTIVE)
            {
                final EfsTimerEvent timerEvent =
                    new EfsTimerEvent(mTimerName, mExpiration);

                sLogger.debug(
                    "Dispatching {} timer event to agent {}.",
                    mType,
                    mAgent.name());

                // Put the timer within another timer task so if
                // timer throws an exception, the exception can
                // be caught and timer canceled.
                try
                {
                    EfsDispatcher.dispatch(
                        mCallback, timerEvent, mAgent);
                }
                catch (IllegalStateException statex)
                {
                    sLogger.warn(
                        "Failed to dispatch timer event.",
                        statex);
                }
            }
        } // end of dispatch()
    } // end of class EfsTimerImpl

    private final class SingleShotTimer
        extends EfsTimerImpl
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

        /**
         * Creates a new single-shot timer with the given
         * parameters.
         * @param timerName optional user-defined timer name.
         * @param callback timer expiration callback.
         * @param agent agent scheduling the timer.
         * @param expiration expiration time in nanoseconds.
         */
        private SingleShotTimer(@Nullable final String timerName,
                                final Consumer<EfsTimerEvent> callback,
                                final IEfsAgent agent,
                                final long expiration)
        {
            super (timerName,
                   callback,
                   agent,
                   expiration,
                   TimerType.SINGLE_SHOT);
        } // end of SingleShotTimer(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Implementations.
        //

        /**
         * Does nothing since single-shot timers are not
         * rescheduled.
         * @return {@code false}.
         */
        @SuppressWarnings ({"java:S1186"})
        @Override
        protected boolean reschedule()
        {
            // Mark this timer as expired.
            setTimerState(TimerState.EXPIRED,
                          CancelReason.SINGLE_SHOT_TIMER_EXPIRED,
                          null);

            return (false);
        } // end of reschedule()

        //
        // end of Abstract Method Implementations.
        //-------------------------------------------------------
    } // end of class SingleShotTimer

    private final class FixedRateTimer
        extends EfsTimerImpl
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Repeat expirations are based on this expiration,
         * period, and expiration count.
         */
        private final long mInitialExpiration;

        /**
         * Subsequent expirations occur at this nanosecond
         * period.
         */
        private final long mPeriod;

        /**
         * Tracks number of times this timer has expired. Used
         * to calculate next expiration.
         */
        private int mExpirationCount;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates a new fixed rate timer with the given
         * parameters.
         * @param timerName optional user-defined timer name.
         * @param callback timer expiration callback.
         * @param agent agent scheduling the timer.
         * @param expiration initial expiration time in
         * nanoseconds.
         * @param period timer fixed rate period.
         */
        private FixedRateTimer(@Nullable final String timerName,
                               final Consumer<EfsTimerEvent> callback,
                               final IEfsAgent agent,
                               final long expiration,
                               final Duration period)
        {
            super (timerName,
                   callback,
                   agent,
                   expiration,
                   TimerType.REPEAT_FIXED_RATE);

            mInitialExpiration = expiration;
            mPeriod = period.toNanos();
            mExpirationCount = 0;
        } // end of FixedRateTimer(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Implementations.
        //

        /**
         * Schedules the next expiration at a fixed rate.
         * @return {@code true} if timer active and expiration
         * rescheduled and {@code false} if timer is not active.
         */
        @SuppressWarnings ({"java:S1186"})
        @Override
        protected boolean reschedule()
        {
            final boolean retcode =
                (mTimerState.get()).isActive();

            // Is this timer still active?
            if (retcode)
            {
                // Yes. Set the next expiration.
                ++mExpirationCount;
                mExpiration =
                    (mInitialExpiration +
                     (mPeriod * mExpirationCount));
            }

            return (retcode);
        } // end of reschedule()

        //
        // end of Abstract Method Implementations.
        //-------------------------------------------------------
    } // end of class FixedRateTimer

    private final class FixedDelayTimer
        extends EfsTimerImpl
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Subsequent expirations occur at this nanosecond
         * period.
         */
        private final long mPeriod;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates a new fixed delay timer for given parameters.
         * @param timerName optional user-defined timer name.
         * @param callback timer expiration callback.
         * @param agent agent scheduling the timer.
         * @param expiration initial expiration time in
         * nanoseconds.
         * @param period timer fixed delay period.
         */
        private FixedDelayTimer(@Nullable final String timerName,
                                final Consumer<EfsTimerEvent> callback,
                                final IEfsAgent agent,
                                final long expiration,
                                final Duration period)
        {
            super (timerName,
                   callback,
                   agent,
                   expiration,
                   TimerType.REPEAT_FIXED_DELAY);

            mPeriod = period.toNanos();
        } // end of FixedDelayTimer(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Implementations.
        //

        /**
         * Schedules the next expiration at a fixed rate.
         * @return {@code true} if timer active and expiration
         * rescheduled and {@code false} if timer is not active.
         */
        @SuppressWarnings ({"java:S1186"})
        @Override
        protected boolean reschedule()
        {
            final boolean retcode =
                (mTimerState.get()).isActive();

            // Is this timer still active?
            if (retcode)
            {
                // Yes. Set the next expiration based on the
                // current nanosecond time.
                mExpiration = (System.nanoTime() + mPeriod);
            }

            return (retcode);
        } // end of reschedule()

        //
        // end of Abstract Method Implementations.
        //-------------------------------------------------------
    } // end of class FixedDelayTimer
} // end of class EfsScheduledExecutor
