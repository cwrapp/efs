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
package org.efs.timer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;
import org.efs.dispatcher.config.ThreadType;
import org.efs.logging.AsyncLoggerFactory;
import org.efs.timer.EfsScheduledExecutor.CancelReason;
import org.efs.timer.EfsScheduledExecutor.IEfsTimer;
import org.efs.timer.EfsScheduledExecutor.TimerState;
import org.efs.timer.EfsScheduledExecutor.TimerType;
import org.efs.timer.config.EfsScheduledExecutorConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 *
 * @author charlesr
 */

public final class ScheduledExecutorTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String TIMER_NAME_PREFIX = "timer-";
    private static final long SPIN_LIMIT = 2_500_000L;
    private static final Duration PARK_TIME =
        Duration.ofNanos(500L);

    private static final String EXECUTOR_NAME_PREFIX =
        "executor-";
    private static final String AGENT_NAME_PREFIX = "agent-";

    //
    // Dispatcher names.
    //

    private static final String BLOCKING_DISPATCHER =
        "exec-test-blocking-dispatcher";
    private static final String SPINNING_DISPATCHER =
        "exec-test-spinning-dispatcher";
    private static final String SPIN_PARK_DISPATCHER =
        "exec-test-spinpark-dispatcher";
    private static final String SPIN_YIELD_DISPATCHER =
        "exec-test-spinyield-dispatcher";
    private static final String TEST_AGENT_DISPATCHER =
        "exec-test-agent-dispatcher";

    /**
     * Each bucket represents this many nanoseconds-worth of
     * data.
     */
    private static final long BUCKET_SIZE = 1_000L; // nanos.

    /**
     * Latencies &ge; to this value are stored in one bucket.
     */
    private static final long MAX_BUCKET = 50_000L; // nanos

    /**
     * The number of buckets is the maximum bucket time limit
     * divided by the bucket time size plus one.
     */
    private static final int BUCKET_COUNT =
        ((int) (MAX_BUCKET / BUCKET_SIZE) + 1);

    //-----------------------------------------------------------
    // Statics.
    //

    private static int sExecutorIndex = 0;
    private static int sAgentIndex = 0;

    private static EfsScheduledExecutorConfig sBlockingConfig;
    private static EfsScheduledExecutorConfig sSpinningConfig;
    private static EfsScheduledExecutorConfig sSpinParkConfig;
    private static EfsScheduledExecutorConfig sSpinYieldConfig;

    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger();

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void setUpClass()
    {
        // Create a spinning dispatcher so timer events are
        // delivered with minimal delay.

        sBlockingConfig = new EfsScheduledExecutorConfig();
        sBlockingConfig.setExecutorName("BlockingExecutor");
        sBlockingConfig.setThreadType(ThreadType.BLOCKING);
        sBlockingConfig.setPriority(Thread.NORM_PRIORITY);

        sSpinningConfig = new EfsScheduledExecutorConfig();
        sSpinningConfig.setExecutorName("SpinningExectuor");
        sSpinningConfig.setThreadType(ThreadType.SPINNING);
        sSpinningConfig.setPriority(Thread.MAX_PRIORITY);

        sSpinParkConfig = new EfsScheduledExecutorConfig();
        sSpinParkConfig.setExecutorName("SpinParkExectur");
        sSpinParkConfig.setThreadType(ThreadType.SPINPARK);
        sSpinParkConfig.setPriority(Thread.MAX_PRIORITY - 1);
        sSpinParkConfig.setSpinLimit(SPIN_LIMIT);
        sSpinParkConfig.setParkTime(PARK_TIME);

        sSpinYieldConfig = new EfsScheduledExecutorConfig();
        sSpinYieldConfig.setExecutorName("SpinYieldExecutor");
        sSpinYieldConfig.setThreadType(ThreadType.SPINYIELD);
        sSpinYieldConfig.setPriority(Thread.MAX_PRIORITY - 2);
        sSpinYieldConfig.setSpinLimit(SPIN_LIMIT);

        // Create dispatchers used in this test.
        final long spinLimit = 1_000_000L;
        final Duration parkTime = Duration.ofNanos(500L);
        final int eventQueueCapacity = 512;
        final int runQueueCapacity = 8;

        (EfsDispatcher.builder(BLOCKING_DISPATCHER))
            .threadType(ThreadType.BLOCKING)
            .numThreads(1)
            .priority(Thread.MAX_PRIORITY)
            .dispatcherType(EfsDispatcher.DispatcherType.EFS)
            .eventQueueCapacity(eventQueueCapacity)
            .maxEvents(eventQueueCapacity)
            .runQueueCapacity(runQueueCapacity)
            .build();

        (EfsDispatcher.builder(SPINNING_DISPATCHER))
            .threadType(ThreadType.SPINNING)
            .numThreads(1)
            .priority(Thread.MAX_PRIORITY)
            .dispatcherType(EfsDispatcher.DispatcherType.EFS)
            .eventQueueCapacity(eventQueueCapacity)
            .maxEvents(eventQueueCapacity)
            .runQueueCapacity(runQueueCapacity)
            .build();

        (EfsDispatcher.builder(SPIN_PARK_DISPATCHER))
            .threadType(ThreadType.SPINPARK)
            .numThreads(1)
            .priority(Thread.MAX_PRIORITY)
            .spinLimit(spinLimit)
            .parkTime(parkTime)
            .dispatcherType(EfsDispatcher.DispatcherType.EFS)
            .eventQueueCapacity(eventQueueCapacity)
            .maxEvents(eventQueueCapacity)
            .runQueueCapacity(runQueueCapacity)
            .build();

        (EfsDispatcher.builder(SPIN_YIELD_DISPATCHER))
            .threadType(ThreadType.SPINYIELD)
            .numThreads(1)
            .priority(Thread.MAX_PRIORITY)
            .spinLimit(spinLimit)
            .dispatcherType(EfsDispatcher.DispatcherType.EFS)
            .eventQueueCapacity(eventQueueCapacity)
            .maxEvents(eventQueueCapacity)
            .runQueueCapacity(runQueueCapacity)
            .build();

        (EfsDispatcher.builder(TEST_AGENT_DISPATCHER))
            .threadType(ThreadType.SPINNING)
            .numThreads(1)
            .priority(Thread.MAX_PRIORITY)
            .dispatcherType(EfsDispatcher.DispatcherType.EFS)
            .eventQueueCapacity(eventQueueCapacity)
            .maxEvents(eventQueueCapacity)
            .runQueueCapacity(runQueueCapacity)
            .build();
    } // end of setUpClass()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    //
    // Error Tests
    //

    @Test
    public void createAndRetrieveScheduledExecutor()
    {
        final String executorName = nextExecutorName();
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(sBlockingConfig)
                .executorName(executorName)
                .build();

        assertThat(executor.isRunning()).isTrue();
        assertThat(EfsScheduledExecutor.isExecutor("fubar"))
            .isFalse();
        assertThat(EfsScheduledExecutor.isExecutor(executorName))
            .isTrue();

        try
        {
            EfsScheduledExecutor.getExecutor(null);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage("name is null or an empty string");
        }

        final EfsScheduledExecutor executorOut =
            EfsScheduledExecutor.getExecutor(executorName);

        assertThat(executorOut).isSameAs(executor);

        executor.shutdown();

        assertThat(executor.isRunning()).isFalse();
    } // end of createAndRetrieveScheduledExecutor()

    //
    // Schedule failure tests.
    //

    @Test
    public void singleTimerNegativeDelay()
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(sBlockingConfig)
                .executorName(nextExecutorName())
                .build();
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration delay = Duration.ofSeconds(-5L);

        try
        {
            executor.schedule(timerName,
                              agent::onTimeout,
                              agent,
                              delay);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(EfsScheduledExecutor.NEGATIVE_DELAY);
        }
        finally
        {
            executor.shutdown();
        }
    } // end of singleTimerNegativeDelay()

    @Test
    public void singleTimerUnregisteredAgent()
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(sBlockingConfig)
                .executorName(nextExecutorName())
                .build();
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration delay = Duration.ofSeconds(5L);

        try
        {
            executor.schedule(timerName,
                              agent::onTimeout,
                              agent,
                              delay);
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex)
                .hasMessage(
                    agent.name() +
                    EfsScheduledExecutor.UNREGISTERED_AGENT);
        }
        finally
        {
            executor.shutdown();
        }
    } // end of singleTimerUnregisteredAgent()

    @Test
    public void singleTimerExecutorShutdown()
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(sBlockingConfig)
                .executorName(nextExecutorName())
                .build();
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration delay = Duration.ofSeconds(5L);

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);
        executor.shutdown();

        try
        {
            executor.schedule(timerName,
                              agent::onTimeout,
                              agent,
                              delay);
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex)
                .hasMessage(EfsScheduledExecutor.EXEC_SHUT_DOWN);
        }
        finally
        {
            EfsDispatcher.deregister(agent);
        }
    } // end of singleTimerExecutorShutdown()

    @Test
    public void fixedRateTimerNegativeInitialDelay()
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(sBlockingConfig)
                .executorName(nextExecutorName())
                .build();
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration initialDelay = Duration.ofSeconds(-5L);
        final Duration period = Duration.ofSeconds(10L);

        try
        {
            executor.scheduleAtFixedRate(timerName,
                                         agent::onTimeout,
                                         agent,
                                         initialDelay,
                                         period);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(
                    EfsScheduledExecutor.NEGATIVE_INIT_DELAY);
        }
        finally
        {
            executor.shutdown();
        }
    } // end of fixedRateTimerNegativeInitialDelay()

    @Test
    public void fixedRateTimerZeroPeriod()
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(sBlockingConfig)
                .executorName(nextExecutorName())
                .build();
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration initialDelay = Duration.ofSeconds(5L);
        final Duration period = Duration.ZERO;

        try
        {
            executor.scheduleAtFixedRate(timerName,
                                         agent::onTimeout,
                                         agent,
                                         initialDelay,
                                         period);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(
                    EfsScheduledExecutor.NEGATIVE_PERIOD);
        }
        finally
        {
            executor.shutdown();
        }
    } // end of fixedRateTimerZeroPeriod()

    @Test
    public void fixedRateTimerUnregisteredAgent()
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(sBlockingConfig)
                .executorName(nextExecutorName())
                .build();
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration initialDelay = Duration.ofSeconds(5L);
        final Duration period = Duration.ofSeconds(10L);

        try
        {
            executor.scheduleAtFixedRate(timerName,
                                         agent::onTimeout,
                                         agent,
                                         initialDelay,
                                         period);
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex)
                .hasMessage(
                    agent.name() +
                    EfsScheduledExecutor.UNREGISTERED_AGENT);
        }
        finally
        {
            executor.shutdown();
        }
    } // end of fixedRateTimerUnregisteredAgent()

    @Test
    public void fixedRateTimerExecutorShutdown()
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(sBlockingConfig)
                .executorName(nextExecutorName())
                .build();
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration initialDelay = Duration.ofSeconds(5L);
        final Duration period = Duration.ofSeconds(10L);

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);
        executor.shutdown();

        try
        {
            executor.scheduleAtFixedRate(timerName,
                                         agent::onTimeout,
                                         agent,
                                         initialDelay,
                                         period);
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex)
                .hasMessage(EfsScheduledExecutor.EXEC_SHUT_DOWN);
        }
        finally
        {
            EfsDispatcher.deregister(agent);
        }
    } // end of fixedRateTimerExecutorShutdown()

    @Test
    public void fixedDelayTimerNegativeInitialDelay()
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(sBlockingConfig)
                .executorName(nextExecutorName())
                .build();
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration initialDelay = Duration.ofSeconds(-5L);
        final Duration delay = Duration.ofSeconds(10L);

        try
        {
            executor.scheduleWithFixedDelay(timerName,
                                            agent::onTimeout,
                                            agent,
                                            initialDelay,
                                            delay);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(
                    EfsScheduledExecutor.NEGATIVE_INIT_DELAY);
        }
        finally
        {
            executor.shutdown();
        }
    } // end of fixedDelayTimerNegativeInitialDelay()

    @Test
    public void fixedDelayTimerZeroDelay()
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(sBlockingConfig)
                .executorName(nextExecutorName())
                .build();
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration initialDelay = Duration.ofSeconds(5L);
        final Duration delay = Duration.ofSeconds(-10L);

        try
        {
            executor.scheduleWithFixedDelay(timerName,
                                            agent::onTimeout,
                                            agent,
                                            initialDelay,
                                            delay);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(
                    EfsScheduledExecutor.NEGATIVE_REPEAT_DELAY);
        }
        finally
        {
            executor.shutdown();
        }
    } // end of fixedDelayTimerZeroDelay()

    @Test
    public void fixedDelayTimerUnregisteredAgent()
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(sBlockingConfig)
                .executorName(nextExecutorName())
                .build();
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration initialDelay = Duration.ofSeconds(5L);
        final Duration delay = Duration.ofSeconds(10L);

        try
        {
            executor.scheduleWithFixedDelay(timerName,
                                            agent::onTimeout,
                                            agent,
                                            initialDelay,
                                            delay);
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex)
                .hasMessage(
                    agent.name() +
                    EfsScheduledExecutor.UNREGISTERED_AGENT);
        }
        finally
        {
            executor.shutdown();
        }
    } // end of fixedDelayTimerUnregisteredAgent()

    @Test
    public void fixedDelayTimerExecutorShutdown()
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(sBlockingConfig)
                .executorName(nextExecutorName())
                .build();
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration initialDelay = Duration.ofSeconds(5L);
        final Duration delay = Duration.ofSeconds(10L);

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);
        executor.shutdown();

        try
        {
            executor.scheduleWithFixedDelay(timerName,
                                            agent::onTimeout,
                                            agent,
                                            initialDelay,
                                            delay);
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex)
                .hasMessage(EfsScheduledExecutor.EXEC_SHUT_DOWN);
        }
        finally
        {
            EfsDispatcher.deregister(agent);
        }
    } // end of fixedDelayTimerExecutorShutdown()

    @Test
    public void startMultipleSingleTimersThenStopExecutor()
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(sBlockingConfig)
                .executorName(nextExecutorName())
                .build();
        final int numTimers = 4;
        final CountDownLatch signal =
            new CountDownLatch(numTimers);
        final String timerNamePrefix = "multiple-timers-";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration delay = Duration.ofSeconds(10L);
        int index;
        String timerName;

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);

        for (index = 0; index < numTimers; ++index)
        {
            timerName = timerNamePrefix + index;
            executor.schedule(timerName,
                              agent::onTimeout,
                              agent,
                              delay);
        }

        executor.shutdown();
        EfsDispatcher.deregister(agent);
    } // end of startMultipleSingleTimersThenStopExecutor()

    //
    // Blocking scheduled executor.
    //

    @Test
    public void singleTimerBlockingExecutor()
    {
        runSingleTimer(sBlockingConfig);
    } // end of singleTimerBlockingExecutor()

    @Test
    public void multipleTimersBlockingExecutor()
    {
        runMultipleTimers(sBlockingConfig);
    } // end of multipleTimersBlockingExecutor()

    @Test
    public void multipleTimersEarlierBlockingExecutor()
    {
        runMultipleTimersEarlier(sBlockingConfig);
    } // end of multipleTimersEarlierBlockingExecutor()

    @Test
    public void cancelTimerBlockingExecutor()
    {
        runCancelTimer(sBlockingConfig);
    } // end of cancelTimerBlockingExecutor()

    @Test
    public void singleFixRateTimerBlockingExecutor()
    {
        runSingleFixRateTimer(sBlockingConfig);
    } // end of singleFixRateTimerBlockingExecutor()

    @Test
    public void singleFixDelayTimerBlockingExecutor()
    {
        runSingleFixDelayTimer(sBlockingConfig);
    } // end of singleFixDelayTimerBlockingExecutor()

    @Test
    public void mixedTimersBlockingExecutor()
    {
        runMixedTimers(sBlockingConfig);
    } // end of mixedTimersBlockingExecutor()

    //
    // Spinning scheduled executor.
    //

    @Test
    public void singleTimerSpinningExecutor()
    {
        runSingleTimer(sSpinningConfig);
    } // end of singleTimerSpinningExecutor()

    @Test
    public void multipleTimersSpinningExecutor()
    {
        runMultipleTimers(sSpinningConfig);
    } // end of multipleTimersSpinningExecutor()

    @Test
    public void multipleTimersEarlierSpinningExecutor()
    {
        runMultipleTimersEarlier(sSpinningConfig);
    } // end of multipleTimersEarlierSpinningExecutor()

    @Test
    public void cancelTimerSpinningExecutor()
    {
        runCancelTimer(sSpinningConfig);
    } // end of cancelTimerSpinningExecutor()

    @Test
    public void singleFixRateTimerSpinningExecutor()
    {
        runSingleFixRateTimer(sSpinningConfig);
    } // end of singleFixRateTimerSpinningExecutor()

    @Test
    public void singleFixDelayTimerSpinningExecutor()
    {
        runSingleFixDelayTimer(sSpinningConfig);
    } // end of singleFixDelayTimerSpinningExecutor()

    @Test
    public void mixedTimersSpinningExecutor()
    {
        runMixedTimers(sSpinningConfig);
    } // end of mixedTimersSpinningExecutor()

    //
    // Spin+park scheduled executor.
    //

    @Test
    public void singleTimerSpinParkExecutor()
    {
        runSingleTimer(sSpinParkConfig);
    } // end of singleTimerSpinParkExecutor()

    @Test
    public void multipleTimersSpinParkExecutor()
    {
        runMultipleTimers(sSpinParkConfig);
    } // end of multipleTimersSpinParkExecutor()

    @Test
    public void multipleTimersEarlierSpinParkExecutor()
    {
        runMultipleTimersEarlier(sSpinParkConfig);
    } // end of multipleTimersEarlierSpinParkExecutor()

    @Test
    public void cancelTimerSpinParkExecutor()
    {
        runCancelTimer(sSpinParkConfig);
    } // end of cancelTimerSpinParkExecutor()

    @Test
    public void singleFixRateTimerSpinParkExecutor()
    {
        runSingleFixRateTimer(sSpinParkConfig);
    } // end of singleFixRateTimerSpinParkExecutor()

    @Test
    public void singleFixDelayTimerSpinParkExecutor()
    {
        runSingleFixDelayTimer(sSpinParkConfig);
    } // end of singleFixDelayTimerSpinParkExecutor()

    @Test
    public void mixedTimersSpinParkExecutor()
    {
        runMixedTimers(sSpinParkConfig);
    } // end of mixedTimersSpinParkExecutor()

    //
    // Spin+yield scheduled executor.
    //

    @Test
    public void singleTimerSpinYieldExecutor()
    {
        runSingleTimer(sSpinYieldConfig);
    } // end of singleTimerSpinYieldExecutor()

    @Test
    public void multipleTimersSpinYieldExecutor()
    {
        runMultipleTimers(sSpinYieldConfig);
    } // end of multipleTimersSpinYieldExecutor()

    @Test
    public void multipleTimersEarlierSpinYieldExecutor()
    {
        runMultipleTimersEarlier(sSpinYieldConfig);
    } // end of multipleTimersEarlierSpinYieldExecutor()

    @Test
    public void cancelTimerSpinYieldExecutor()
    {
        runCancelTimer(sSpinYieldConfig);
    } // end of cancelTimerSpinYieldExecutor()

    @Test
    public void singleFixRateTimerSpinYieldExecutor()
    {
        runSingleFixRateTimer(sSpinYieldConfig);
    } // end of singleFixRateTimerSpinYieldExecutor()

    @Test
    public void singleFixDelayTimerSpinYieldExecutor()
    {
        runSingleFixDelayTimer(sSpinYieldConfig);
    } // end of singleFixDelayTimerSpinYieldExecutor()

    @Test
    public void mixedTimersSpinYieldExecutor()
    {
        runMixedTimers(sSpinYieldConfig);
    } // end of mixedTimersSpinYieldExecutor()

    //
    // Performance tests.
    //
    // Blocking scheduled executor.
    //


    @Disabled
    @Test
    public void fixRatePerformanceBlockingExecutorBlockingDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  BLOCKING_DISPATCHER,
                                  sBlockingConfig);
    } // end of fixRatePerformanceBlockingExecutorBlockingDispatcher()

    @Disabled
    @Test
    public void fixRatePerformanceBlockingExecutorSpinningDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  SPINNING_DISPATCHER,
                                  sBlockingConfig);
    } // end of fixRatePerformanceBlockingExecutorSpinningDispatcher()

    @Disabled
    @Test
    public void fixRatePerformanceBlockingExecutorSpinParkDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  SPIN_PARK_DISPATCHER,
                                  sBlockingConfig);
    } // end of fixRatePerformanceBlockingExecutorSpinParkDispatcher()

    @Disabled
    @Test
    public void fixRatePerformanceBlockingExecutorSpinYieldDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  SPIN_YIELD_DISPATCHER,
                                  sBlockingConfig);
    } // end of fixRatePerformanceBlockingExecutorSpinYieldDispatcher()

    //
    // Spinning scheduled executor.
    //

    @Disabled
    @Test
    public void fixRatePerformanceSpinningExecutorBlockingDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  BLOCKING_DISPATCHER,
                                  sSpinningConfig);
    } // end of fixRatePerformanceSpinningExecutorBlockingDispatcher()

    @Disabled
    @Test
    public void fixRatePerformanceSpinningExecutorSpinningDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  SPINNING_DISPATCHER,
                                  sSpinningConfig);
    } // end of fixRatePerformanceSpinningExecutorSpinningDispatcher()

    @Disabled
    @Test
    public void fixRatePerformanceSpinningExecutorSpinParkDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  SPIN_PARK_DISPATCHER,
                                  sSpinningConfig);
    } // end of fixRatePerformanceSpinningExecutorSpinParkDispatcher()

    @Disabled
    @Test
    public void fixRatePerformanceSpinningExecutorSpinYieldDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  SPIN_YIELD_DISPATCHER,
                                  sSpinningConfig);
    } // end of fixRatePerformanceSpinningExecutorSpinYieldDispatcher()

    //
    // Spin+park scheduled executor.
    //

    @Disabled
    @Test
    public void fixRatePerformanceSpinParkExecutorBlockingDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  BLOCKING_DISPATCHER,
                                  sSpinParkConfig);
    } // end of fixRatePerformanceSpinParkExecutorBlockingDispatcher()

    @Disabled
    @Test
    public void fixRatePerformanceSpinParkExecutorSpinningDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  SPINNING_DISPATCHER,
                                  sSpinParkConfig);
    } // end of fixRatePerformanceSpinParkExecutorSpinningDispatcher()

    @Disabled
    @Test
    public void fixRatePerformanceSpinParkExecutorSpinParkDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  SPIN_PARK_DISPATCHER,
                                  sSpinParkConfig);
    } // end of fixRatePerformanceSpinParkExecutorSpinParkDispatcher()

    @Disabled
    @Test
    public void fixRatePerformanceSpinParkExecutorSpinYieldDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  SPIN_YIELD_DISPATCHER,
                                  sSpinParkConfig);
    } // end of fixRatePerformanceSpinParkExecutorSpinYieldDispatcher()

    //
    // Spin+yield scheduled executor.
    //

    @Disabled
    @Test
    public void fixRatePerformanceSpinYieldExecutorBlockingDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  BLOCKING_DISPATCHER,
                                  sSpinYieldConfig);
    } // end of fixRatePerformanceSpinYieldExecutorBlockingDispatcher()

    @Disabled
    @Test
    public void fixRatePerformanceSpinYieldExecutorSpinningDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  SPINNING_DISPATCHER,
                                  sSpinYieldConfig);
    } // end of fixRatePerformanceSpinYieldExecutorSpinningDispatcher()

    @Disabled
    @Test
    public void fixRatePerformanceSpinYieldExecutorSpinParkDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  SPIN_PARK_DISPATCHER,
                                  sSpinYieldConfig);
    } // end of fixRatePerformanceSpinYieldExecutorSpinParkDispatcher()

    @Disabled
    @Test
    public void fixRatePerformanceSpinYieldExecutorSpinYieldDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  SPIN_YIELD_DISPATCHER,
                                  sSpinYieldConfig);
    } // end of fixRatePerformanceSpinYieldExecutorSpinYieldDispatcher()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private void runSingleTimer(final EfsScheduledExecutorConfig config)
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(config)
                .executorName(nextExecutorName())
                .build();
        final int timerId = 0;
        final Duration expiration = Duration.ofMillis(500L);
        final int numTimers = 1;
        final CountDownLatch signal =
            new CountDownLatch(numTimers);
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);
        agent.setTimer(timerId, expiration);

        try
        {
            signal.await((expiration.toMillis() * 2L),
                         TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(agent.timerCount()).isEqualTo(numTimers);
        assertThat(agent.expirationCount()).isEqualTo(numTimers);
        assertThat(agent.isDone(timerId)).isTrue();
        assertThat(agent.cancelReason(timerId))
            .isEqualTo(CancelReason.SINGLE_SHOT_TIMER_EXPIRED);
        assertThat(agent.cancelException(timerId)).isNull();
    } // end of runSingleTimer(EfsScheduledExecutorConfig)

    private void runMultipleTimers(final EfsScheduledExecutorConfig config)
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(config)
                .executorName(nextExecutorName())
                .build();
        final Duration[] expirations =
        {
            Duration.ofMillis(500L),
            Duration.ofSeconds(1L)
        };
        final int numTimers = expirations.length;
        final CountDownLatch signal =
            new CountDownLatch(numTimers);
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        int timerId;

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);

        for (timerId = 0; timerId < numTimers; ++timerId)
        {
            agent.setTimer(timerId, expirations[timerId]);

            await().atMost(expirations[timerId].dividedBy(2L));
        }

        timerId = (numTimers - 1);

        try
        {
            signal.await(
                (expirations[timerId].toMillis() * 5L),
                TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(agent.timerCount()).isEqualTo(numTimers);
        assertThat(agent.expirationCount()).isEqualTo(numTimers);

        for (timerId = 0; timerId < numTimers; ++timerId)
        {
            assertThat(agent.isDone(timerId)).isTrue();
            assertThat(agent.cancelReason(timerId))
                .isEqualTo(CancelReason.SINGLE_SHOT_TIMER_EXPIRED);
            assertThat(agent.cancelException(timerId)).isNull();
        }
    } // end of runMultipleTimers(EfsScheduledExecutorConfig)

    private void runMultipleTimersEarlier(final EfsScheduledExecutorConfig config)
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(config)
                .executorName(nextExecutorName())
                .build();
        final Duration[] expirations =
        {
            Duration.ofSeconds(1L),
            Duration.ofMillis(500L)
        };
        final int numTimers = expirations.length;
        final CountDownLatch signal =
            new CountDownLatch(numTimers);
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        int timerId;

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);

        for (timerId = 0; timerId < numTimers; ++timerId)
        {
            agent.setTimer(timerId, expirations[timerId]);

            await().atMost(Duration.ofMillis(100L));
        }

        try
        {
            signal.await(
                (expirations[0].toMillis() * 5L),
                TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(agent.timerCount()).isEqualTo(numTimers);
        assertThat(agent.expirationCount()).isEqualTo(numTimers);

        for (timerId = 0; timerId < numTimers; ++timerId)
        {
            assertThat(agent.isDone(timerId)).isTrue();
            assertThat(agent.cancelReason(timerId))
                .isEqualTo(CancelReason.SINGLE_SHOT_TIMER_EXPIRED);
            assertThat(agent.cancelException(timerId)).isNull();
        }
    } // end of runMultipleTimersEarlier(...)

    private void runCancelTimer(final EfsScheduledExecutorConfig config)
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(config)
                .executorName(nextExecutorName())
                .build();
        final Duration[] expirations =
        {
            Duration.ofMillis(500L),
            Duration.ofSeconds(2L)
        };
        final int numTimers = expirations.length;
        final CountDownLatch signal =
            new CountDownLatch(numTimers);
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        int timerId = 0;

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);

        agent.setTimer(timerId, expirations[timerId]);

        await().atMost(expirations[timerId].dividedBy(2L));

        // Set second timer and then cancel first.
        timerId = 1;
        agent.setTimer(timerId, expirations[timerId]);
        agent.cancelTimer(0);

        try
        {
            signal.await((expirations[timerId].toMillis() * 5L),
                         TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(agent.timerCount()).isEqualTo(numTimers);
        assertThat(agent.expirationCount()).isEqualTo(1);
        assertThat(agent.isCanceled(0)).isTrue();
    } // end of runCancelTimer(EfsScheduledExecutorConfig)

    private void runSingleFixRateTimer(final EfsScheduledExecutorConfig config)
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(config)
                .executorName(nextExecutorName())
                .build();
        final int timerId = 0;
        final int numExpirations = 10;
        final Duration initialDelay = Duration.ofMillis(200L);
        final Duration period = Duration.ofMillis(100L);
        final long waitTime =
            ((initialDelay.toMillis() +
              (period.toMillis() * (numExpirations - 1))) * 2L);
        final CountDownLatch signal =
            new CountDownLatch(numExpirations);
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);
        agent.setFixedRateTimer(timerId, initialDelay, period);

        try
        {
            signal.await(waitTime, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        agent.cancelTimer(timerId);

        // One repeat timer scheduled, multiple expirations.
        assertThat(agent.timerCount()).isEqualTo(1);
        assertThat(agent.expirationCount())
            .isEqualTo(numExpirations);
        assertThat(agent.isCanceled(timerId)).isTrue();
    } // end of runSingleFixRateTimer(EfsScheduledExecutorConfig)

    private void runSingleFixDelayTimer(final EfsScheduledExecutorConfig config)
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(config)
                .executorName(nextExecutorName())
                .build();
        final int timerId = 0;
        final int numExpirations = 10;
        final Duration initialDelay = Duration.ofMillis(200L);
        final Duration period = Duration.ofMillis(100L);
        final long waitTime =
            ((initialDelay.toMillis() +
              (period.toMillis() * (numExpirations - 1))) * 2L);
        final CountDownLatch signal =
            new CountDownLatch(numExpirations);
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);
        agent.setFixedDelayTimer(timerId, initialDelay, period);

        try
        {
            signal.await(waitTime, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        agent.cancelTimer(timerId);

        // One repeat timer scheduled, multiple expirations.
        assertThat(agent.timerCount()).isEqualTo(1);
        assertThat(agent.expirationCount())
            .isEqualTo(numExpirations);
        assertThat(agent.isCanceled(timerId)).isTrue();
    } // end of runSingleFixDelayTimer(...)

    private void runMixedTimers(final EfsScheduledExecutorConfig config)
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(config)
                .executorName(nextExecutorName())
                .build();
        final int numExpirations = 30;
        final Duration[] singleShotExpirations =
        {
            Duration.ofMillis(500L),
            Duration.ofSeconds(1L)
        };
        final long waitTime = 10_000L; // milliseconds.
        final Duration initialDelay = Duration.ofMillis(200L);
        final Duration delay = Duration.ofMillis(100L);
        final Duration period = Duration.ofMillis(50L);
        final CountDownLatch signal =
            new CountDownLatch(numExpirations);
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);

        agent.setTimer(0, singleShotExpirations[0]);
        agent.setFixedRateTimer(1, initialDelay, period);
        agent.setFixedDelayTimer(2, initialDelay, delay);

        await().atMost(Duration.ofMillis(500L));

        agent.setTimer(3, singleShotExpirations[1]);

        try
        {
            signal.await(waitTime, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        agent.cancelTimer(1);
        agent.cancelTimer(2);

        assertThat(agent.timerCount()).isEqualTo(4);
        assertThat(agent.expirationCount())
            .isGreaterThanOrEqualTo(numExpirations);
        assertThat(agent.isDone(0)).isTrue();
        assertThat(agent.isCanceled(1)).isTrue();
        assertThat(agent.isCanceled(2)).isTrue();
        assertThat(agent.isDone(3)).isTrue();
    } // end of runMixedTimers(EfsScheduledExecutorConfig)

    private void runFixRatePerformanceTest(final int numExpirations,
                                           final String dispatcherName,
                                           final EfsScheduledExecutorConfig config)
    {
        final EfsScheduledExecutor executor =
            (EfsScheduledExecutor.builder())
                .set(config)
                .executorName(nextExecutorName())
                .build();
        final int timerId = 0;
        final Duration initialDelay = Duration.ofNanos(200_000L);
        final Duration period = Duration.ofNanos(100_000L);
        final long waitTime =
            (((initialDelay.toNanos() +
               (period.toNanos() * (numExpirations - 1))) * 2L) /
             100_000L);
        final CountDownLatch signal =
            new CountDownLatch(numExpirations);
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Instant startTime;
        final Instant stopTime;
        final Duration runTime;

        EfsDispatcher.register(agent, dispatcherName);

        startTime = Instant.now();
        agent.setFixedRateTimer(timerId, initialDelay, period);

        try
        {
            signal.await(waitTime, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        stopTime = Instant.now();
        runTime = Duration.between(startTime, stopTime);

        agent.cancelTimer(timerId);

        sLogger.info(
            "\n{} scheduled executor, {} dispatcher.",
            config.getThreadType(),
            dispatcherName);

        sLogger.info(
            "\nRun time: {}\n\n", formatDuration(runTime));

        sLogger.info(agent.generateResults());
    } // end of runFixRatePerformanceTest(...)

    private String nextExecutorName()
    {
        final String retval =
            EXECUTOR_NAME_PREFIX + sExecutorIndex;

        ++sExecutorIndex;

        return (retval);
    } // end of nextExecutorName()

    private String nextAgentName()
    {
        final String retval = AGENT_NAME_PREFIX + sAgentIndex;

        ++sAgentIndex;

        return (retval);
    } // end of nextAgentName()

    private String formatDuration(final Duration d)
    {
        final StringBuilder retval = new StringBuilder();
        final int hours = d.toHoursPart();
        final int minutes = d.toMinutesPart();
        final int seconds = d.toSecondsPart();
        final int millisecs = d.toMillisPart();

        if (hours > 0)
        {
            retval.append(hours).append(':');
        }

        if (hours > 0 || minutes > 0)
        {
            retval.append(minutes).append(':');
        }

        retval.append(seconds).append('.')
              .append(millisecs);

        return (retval.toString());
    } // end of formatDuration(Duration)

//---------------------------------------------------------------
// Inner classes.
//

    private static final class TimerAgent
        implements IEfsAgent
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private final String mName;
        private final CountDownLatch mSignal;
        private final EfsScheduledExecutor mExecutor;
        private final Map<Integer, IEfsTimer> mTimers;

        private final AtomicInteger mTimerCounter;
        private final AtomicInteger mExpirationCounter;
        private final List<Long> mDeltas;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private TimerAgent(final String name,
                           final CountDownLatch signal,
                           final EfsScheduledExecutor executor)
        {
            mName = name;
            mSignal = signal;
            mExecutor = executor;
            mTimers = new HashMap<>();

            mTimerCounter = new AtomicInteger();
            mExpirationCounter = new AtomicInteger();
            mDeltas = new ArrayList<>();
        } // end of TimerAgent()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // IEfsAgent Interface Implementation.
        //

        @Override
        public String name()
        {
            return (mName);
        } // end of name()

        //
        // end of IEfsAgent Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        public int timerCount()
        {
            return (mTimerCounter.get());
        } // end of timerCount()

        public int expirationCount()
        {
            return (mExpirationCounter.get());
        } // end of expirationCount()

        public boolean isDone(final int id)
        {
            final IEfsTimer timer = mTimers.get(id);

            return (timer == null || timer.isDone());
        } // end of isExpired(int)

        public boolean isCanceled(final int id)
        {
            final IEfsTimer timer = mTimers.get(id);

            return (timer != null && timer.isCanceled());
        } // end of isCanceled(int)

        @Nullable public CancelReason cancelReason(final int id)
        {
            final IEfsTimer timer = mTimers.get(id);

            return (timer == null ?
                    null :
                    timer.cancelReason());
        } // end of cancelReason(int)

        @Nullable public Exception cancelException(final int id)
        {
            final IEfsTimer timer = mTimers.get(id);

            return (timer == null ?
                    null :
                    timer.cancelException());
        } // end of cancelException(int)

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Puts a single-shot timer in place.
         * @param delay timer delay.
         */
        private void setTimer(final int id,
                              final Duration delay)
        {
            if (!mTimers.containsKey(id))
            {
                final String timerName = TIMER_NAME_PREFIX + id;
                final IEfsTimer timer =
                    mExecutor.schedule(
                        timerName,
                        this::onTimeout,
                        this,
                        delay);
                final String text =
                    String.format("[type=%s, state=%s",
                                   TimerType.SINGLE_SHOT,
                                   TimerState.ACTIVE);

                mTimerCounter.incrementAndGet();
                mTimers.put(id, timer);

                assertThat(timer.timerName())
                    .isEqualTo(timerName);
                assertThat(timer.timerState())
                    .isEqualTo(TimerState.ACTIVE);
                assertThat(timer.timerType())
                    .isEqualTo(TimerType.SINGLE_SHOT);
                assertThat(timer.isCanceled()).isFalse();
                assertThat(timer.cancelException()).isNull();
                assertThat(timer.isRepeating()).isFalse();
                assertThat(timer.isDone()).isFalse();
                assertThat(timer.toString()).startsWith(text);
            }
        } // end of setTimer(int, Duration)

        private void setFixedRateTimer(final int id,
                                       final Duration initialDelay,
                                       final Duration period)
        {
            if (!mTimers.containsKey(id))
            {
                final String timerName = TIMER_NAME_PREFIX + id;
                final IEfsTimer timer =
                    mExecutor.scheduleAtFixedRate(
                        timerName,
                        this::onTimeout,
                        this,
                        initialDelay,
                        period);
                final String text =
                    String.format("[type=%s, state=%s",
                                   TimerType.REPEAT_FIXED_RATE,
                                   TimerState.ACTIVE);

                mTimerCounter.incrementAndGet();
                mTimers.put(id, timer);

                assertThat(timer.timerName())
                    .isEqualTo(timerName);
                assertThat(timer.timerState())
                    .isEqualTo(TimerState.ACTIVE);
                assertThat(timer.timerType())
                    .isEqualTo(TimerType.REPEAT_FIXED_RATE);
                assertThat(timer.isCanceled()).isFalse();
                assertThat(timer.cancelException()).isNull();
                assertThat(timer.isRepeating()).isTrue();
                assertThat(timer.isDone()).isFalse();
                assertThat(timer.toString()).startsWith(text);
            }
        } // end of setFixedRateTimer(int, Duration, Duration)

        private void setFixedDelayTimer(final int id,
                                        final Duration initialDelay,
                                        final Duration delay)
        {
            if (!mTimers.containsKey(id))
            {
                final String timerName = TIMER_NAME_PREFIX + id;
                final IEfsTimer timer =
                    mExecutor.scheduleWithFixedDelay(
                        timerName,
                        this::onTimeout,
                        this,
                        initialDelay,
                        delay);
                final String text =
                    String.format("[type=%s, state=%s",
                                   TimerType.REPEAT_FIXED_DELAY,
                                   TimerState.ACTIVE);

                mTimerCounter.incrementAndGet();
                mTimers.put(id, timer);

                assertThat(timer.timerName())
                    .isEqualTo(timerName);
                assertThat(timer.timerState())
                    .isEqualTo(TimerState.ACTIVE);
                assertThat(timer.timerType())
                    .isEqualTo(TimerType.REPEAT_FIXED_DELAY);
                assertThat(timer.isCanceled()).isFalse();
                assertThat(timer.cancelException()).isNull();
                assertThat(timer.isRepeating()).isTrue();
                assertThat(timer.isDone()).isFalse();
                assertThat(timer.toString()).startsWith(text);
            }
        } // end of setFixedDelayTimer(int, Duration, Duration)

        private void cancelTimer(final int id)
        {
            if (mTimers.containsKey(id))
            {
                final IEfsTimer timer = mTimers.get(id);

                if (!timer.isDone())
                {
                    try
                    {
                        timer.close();
                    }
                    catch (Exception jex)
                    {
                        sLogger.warn("{} cancel failed.",
                                     timer.timerName(),
                                     jex);
                    }
                }
            }
        } // end of cancelTimer(int)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Event Handlers.
        //

        private void onTimeout(final EfsTimerEvent timerEvent)
        {
            final long delay =
                (System.nanoTime() - timerEvent.expiration());

            mExpirationCounter.incrementAndGet();
            mDeltas.add(delay);

            sLogger.debug(
                "{}: received timer {} expiration; delay={} nanos.",
                mName,
                timerEvent.timerName(),
                delay);

            mSignal.countDown();
        } // end of onTimeout(EfsTimerEvent)

        //
        // end of Event Handlers.
        //-------------------------------------------------------

        private String generateResults()
        {
            final Formatter output = new Formatter();
            final int eventCount = mDeltas.size();
            final Bucket[] buckets = new Bucket[BUCKET_COUNT];
            final int maxIndex = (BUCKET_COUNT - 1);
            long latency;
            long minLatency;
            long maxLatency;
            long averageLatency;
            long sum = 0L;
            final int median = (eventCount / 2);
            final int p75 = (int) (eventCount * .75d);
            final int p90 = (int) (eventCount * .9d);
            final int p95 = (int) (eventCount * .95d);
            final int p99 = (int) (eventCount * .99d);
            int i;
            int bucketIndex;
            long bi;
            long bnext;

            for (i = 0, bi = 0L, bnext = BUCKET_SIZE;
                 i < maxIndex;
                 ++i, bi = bnext, bnext += BUCKET_SIZE)
            {
                buckets[i] = new Bucket(bi, bnext);
            }

            buckets[maxIndex] = new Bucket(MAX_BUCKET, 0L);

            Collections.sort(mDeltas);

            minLatency = Long.MAX_VALUE;
            maxLatency = -1L;

            for (i = 0; i < eventCount; ++i)
            {
                latency = mDeltas.get(i);

                sum += latency;

                if (latency < minLatency)
                {
                    minLatency = latency;
                }

                if (latency > maxLatency)
                {
                    maxLatency = latency;
                }

                if (latency >= MAX_BUCKET)
                {
                    bucketIndex = maxIndex;
                }
                else
                {
                    bucketIndex = (int) (latency / BUCKET_SIZE);
                }

                buckets[bucketIndex].increment();
            }

            averageLatency = (sum / eventCount);

            output.format("%nTimeouts received: %,d%n", eventCount);
            output.format("Latency:%n");
            output.format(
                "       minimum= %,11d nanoseconds%n", minLatency);
            output.format(
                "        median= %,11d nanoseconds.%n",
                mDeltas.get(median));
            output.format(
                "75%% percentile= %,11d nanoseconds%n",
                mDeltas.get(p75));
            output.format(
                "90%% percentile= %,11d nanoseconds%n",
                mDeltas.get(p90));
            output.format(
                "95%% percentile= %,11d nanoseconds%n",
                mDeltas.get(p95));
            output.format(
                "99%% percentile= %,11d nanoseconds%n",
                mDeltas.get(p99));
            output.format(
                "       maximum= %,11d nanoseconds.%n",
                maxLatency);
            output.format(
                "       average= %,11d nanoseconds.%n%n",
                averageLatency);

            output.format("Latency intervals:%n");
            for (i = 0; i < BUCKET_COUNT; ++i)
            {
                output.format("%s%n", buckets[i]);
            }

        return (output.toString());
        } // end of generateResults()
    } // end of class TimerAgent

    private static final class Bucket
    {

    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private final long mTime;
        private final long mMaxTime;
        private int mCount;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Bucket(final long time, final long maxTime)
        {
            mTime = time;
            mMaxTime = maxTime;
            mCount = 0;
        } // end of Bucket(long, long)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        @Override
        public String toString()
        {
            return (
                String.format(
                    "[%,10d, %,10d) %,7d items.",
                    mTime,
                    mMaxTime,
                    mCount));
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        public long time()
        {
            return (mTime);
        } // end of time()

        public int count()
        {
            return (mCount);
        } // end of count()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        public void increment()
        {
            ++mCount;
        } // end of increment()

        //
        // end of Set Methods.
        //-------------------------------------------------------
    } // end of class Bucket
} // end of class ScheduledExecutorTest
