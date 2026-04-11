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

import jakarta.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;
import org.efs.dispatcher.config.ThreadType;
import org.efs.logging.AsyncLoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

    private static final String SPINNING_WHEEL_TIMER =
        "exec-test-spinning-wheel-timer";

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

    private static int sAgentIndex = 0;

    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(
            ScheduledExecutorTest.class);

    //-----------------------------------------------------------
    // Locals.
    //

    private ScheduledExecutorService mService;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void setUpClass()
    {
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

    @BeforeEach
    public void testSetUp()
    {
        mService = Executors.newSingleThreadScheduledExecutor();
    } // end of testSetUp()

    @AfterEach
    public void testCleanup()
    {
        if (!mService.isShutdown())
        {
            mService.shutdown();
        }
    } // end of testCleanup()

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
    public void createScheduledExecutorNullExecutor()
    {
        final ScheduledExecutorService service = null;

        try
        {
            new EfsScheduledExecutor(service);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(EfsScheduledExecutor.NULL_EXECUTOR);
        }
    } // end of createScheduledExecutorNullExecutor()

    @Test
    public void createScheduledExecutorShutdownExecutor()
    {
        mService.shutdownNow();

        try
        {
            new EfsScheduledExecutor(mService);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(
                    EfsScheduledExecutor.INVALID_EXECUTOR);
        }
    } // end of createScheduledExecutorShutdownExecutor()

    @Test
    public void createScheduledExecutor()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);

        assertThat(executor.isRunning()).isTrue();
        assertThat(executor.isShutdown()).isFalse();
        assertThat(executor.isTerminated()).isFalse();
        assertThat(executor.service()).isSameAs(mService);

        executor.shutdown();

        assertThat(executor.isRunning()).isFalse();
        assertThat(executor.isShutdown()).isTrue();
        assertThat(executor.isTerminated()).isTrue();

        final List<Runnable> tasks = executor.shutdownNow();

        assertThat(tasks).isEmpty();
    } // end of createAndRetrieveScheduledExecutor()

    //
    // Schedule failure tests.
    //

    @Test
    public void singleTimerNegativeDelay()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration delay = Duration.ofSeconds(-5L);

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);

        try
        {
            executor.schedule(timerName,
                              null,
                              agent::onTimeout,
                              agent,
                              delay);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(EfsScheduledExecutor.NEGATIVE_DELAY);
        }
    } // end of singleTimerNegativeDelay()

    @Test
    public void singleTimerExcessiveDelay()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration delay =
            Duration.ofSeconds(Long.MAX_VALUE);

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);

        try
        {
            executor.schedule(timerName,
                              null,
                              agent::onTimeout,
                              agent,
                              delay);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(EfsScheduledExecutor.EXCESSIVE_DELAY);
        }
    } // end of singleTimerExcessiveDelay()

    @Test
    public void singleTimerUnregisteredAgent()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration delay = Duration.ofSeconds(5L);

        try
        {
            executor.schedule(timerName,
                              null,
                              agent::onTimeout,
                              agent,
                              delay);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(
                    agent.name() +
                    EfsScheduledExecutor.UNREGISTERED_AGENT);
        }
    } // end of singleTimerUnregisteredAgent()

    @Test
    public void singleTimerExecutorShutdown()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
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
                              null,
                              agent::onTimeout,
                              agent,
                              delay);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(EfsScheduledExecutor.EXEC_SHUT_DOWN);
        }
    } // end of singleTimerExecutorShutdown()

    @Test
    public void fixedRateTimerNegativeInitialDelay()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration initialDelay = Duration.ofSeconds(-5L);
        final Duration period = Duration.ofSeconds(10L);

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);

        try
        {
            executor.scheduleAtFixedRate(timerName,
                                         null,
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
    } // end of fixedRateTimerNegativeInitialDelay()

    @Test
    public void fixedRateTimerExcessiveInitialDelay()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration initialDelay =
            Duration.ofSeconds(Long.MAX_VALUE);
        final Duration period = Duration.ofSeconds(10L);

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);

        try
        {
            executor.scheduleAtFixedRate(timerName,
                                         null,
                                         agent::onTimeout,
                                         agent,
                                         initialDelay,
                                         period);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(
                    EfsScheduledExecutor.EXCESSIVE_INIT_DELAY);
        }
    } // end of fixedRateTimerExcessiveInitialDelay()

    @Test
    public void fixedRateTimerZeroPeriod()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration initialDelay = Duration.ofSeconds(5L);
        final Duration period = Duration.ZERO;

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);

        try
        {
            executor.scheduleAtFixedRate(timerName,
                                         null,
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
    } // end of fixedRateTimerZeroPeriod()

    @Test
    public void fixedRateTimerExcessivePeriod()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration initialDelay = Duration.ofSeconds(5L);
        final Duration period =
            Duration.ofSeconds(Long.MAX_VALUE);

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);

        try
        {
            executor.scheduleAtFixedRate(timerName,
                                         null,
                                         agent::onTimeout,
                                         agent,
                                         initialDelay,
                                         period);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(
                    EfsScheduledExecutor.EXCESSIVE_PERIOD);
        }
    } // end of fixedRateTimerExcessivePeriod()

    @Test
    public void fixedRateTimerUnregisteredAgent()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
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
                                         null,
                                         agent::onTimeout,
                                         agent,
                                         initialDelay,
                                         period);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(
                    agent.name() +
                    EfsScheduledExecutor.UNREGISTERED_AGENT);
        }
    } // end of fixedRateTimerUnregisteredAgent()

    @Test
    public void fixedRateTimerExecutorShutdown()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
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
                                         null,
                                         agent::onTimeout,
                                         agent,
                                         initialDelay,
                                         period);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(EfsScheduledExecutor.EXEC_SHUT_DOWN);
        }
    } // end of fixedRateTimerExecutorShutdown()

    @Test
    public void fixedDelayTimerNegativeInitialDelay()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration initialDelay = Duration.ofSeconds(-5L);
        final Duration delay = Duration.ofSeconds(10L);

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);

        try
        {
            executor.scheduleWithFixedDelay(timerName,
                                            null,
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
    } // end of fixedDelayTimerNegativeInitialDelay()

    @Test
    public void fixedDelayTimerExcessiveInitialDelay()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration initialDelay =
            Duration.ofSeconds(Long.MAX_VALUE);
        final Duration delay = Duration.ofSeconds(10L);

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);

        try
        {
            executor.scheduleWithFixedDelay(timerName,
                                            null,
                                            agent::onTimeout,
                                            agent,
                                            initialDelay,
                                            delay);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(
                    EfsScheduledExecutor.EXCESSIVE_INIT_DELAY);
        }
    } // end of fixedDelayTimerExcessiveInitialDelay()

    @Test
    public void fixedDelayTimerZeroDelay()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration initialDelay = Duration.ofSeconds(5L);
        final Duration delay = Duration.ofSeconds(-10L);

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);

        try
        {
            executor.scheduleWithFixedDelay(timerName,
                                            null,
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
    } // end of fixedDelayTimerZeroDelay()

    @Test
    public void fixedDelayTimerExcessiveDelay()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
        final CountDownLatch signal =
            new CountDownLatch(1);
        final String timerName = "test-timer";
        final TimerAgent agent =
            new TimerAgent(nextAgentName(), signal, executor);
        final Duration initialDelay = Duration.ofSeconds(5L);
        final Duration delay =
            Duration.ofSeconds(Long.MAX_VALUE);

        EfsDispatcher.register(agent, TEST_AGENT_DISPATCHER);

        try
        {
            executor.scheduleWithFixedDelay(timerName,
                                            null,
                                            agent::onTimeout,
                                            agent,
                                            initialDelay,
                                            delay);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(
                    EfsScheduledExecutor.EXCESSIVE_DELAY);
        }
    } // end of fixedDelayTimerExcessiveDelay()

    @Test
    public void fixedDelayTimerUnregisteredAgent()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
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
                                            null,
                                            agent::onTimeout,
                                            agent,
                                            initialDelay,
                                            delay);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(
                    agent.name() +
                    EfsScheduledExecutor.UNREGISTERED_AGENT);
        }
    } // end of fixedDelayTimerUnregisteredAgent()

    @Test
    public void fixedDelayTimerExecutorShutdown()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
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
                                            null,
                                            agent::onTimeout,
                                            agent,
                                            initialDelay,
                                            delay);
        }
        catch (RejectedExecutionException rejex)
        {
            assertThat(rejex)
                .hasMessage(EfsScheduledExecutor.EXEC_SHUT_DOWN);
        }
    } // end of fixedDelayTimerExecutorShutdown()

    @Test
    public void startMultipleSingleTimersThenStopExecutor()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
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
                              null,
                              agent::onTimeout,
                              agent,
                              delay);
        }

        final List<Runnable> tasks =  executor.shutdownNow();
        boolean termFlag = false;

        try
        {
            termFlag =
                executor.awaitTermination(1L, TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(tasks).isNotEmpty();
        assertThat(termFlag).isTrue();

        EfsDispatcher.deregister(agent);
    } // end of startMultipleSingleTimersThenStopExecutor()

    //
    // Scheduling timers test.
    //

    @Test
    public void singleTimerTest()
    {
        runSingleTimer();
    } // end of singleTimerTest()

    @Test
    public void multipleTimersTest()
    {
        runMultipleTimers();
    } // end of multipleTimersTest()

    @Test
    public void multipleTimersEarlierTest()
    {
        runMultipleTimersEarlier();
    } // end of multipleTimersEarlierTest()

    @Test
    public void cancelTimerTest()
    {
        runCancelTimer();
    } // end of cancelTimerTest()

    @Test
    public void singleFixRateTimerTest()
    {
        runSingleFixRateTimer();
    } // end of singleFixRateTimerTest()

    @Test
    public void singleFixDelayTimerTest()
    {
        runSingleFixDelayTimer();
    } // end of singleFixDelayTimerTest()

    @Test
    public void mixedTimersTest()
    {
        runMixedTimers();
    } // end of mixedTimersTest()

    //
    // Performance tests.
    //
    // Note: when running any of the following four tests,
    // edit src/test/resources/logback-test.xml and make sure
    // <root level="INFO"> is set. Do not set log level to
    // WARN or ERROR.
    //

    @Disabled
    @Test
    public void fixRatePerformanceBlockingDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  BLOCKING_DISPATCHER);
    } // end of fixRatePerformanceBlockingExecutorBlockingDispatcher()

    @Disabled
    @Test
    public void fixRatePerformanceSpinningDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  SPINNING_DISPATCHER);
    } // end of fixRatePerformanceBlockingExecutorSpinningDispatcher()

    @Disabled
    @Test
    public void fixRatePerformanceSpinParkDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  SPIN_PARK_DISPATCHER);
    } // end of fixRatePerformanceBlockingExecutorSpinParkDispatcher()

    @Disabled
    @Test
    public void fixRatePerformanceBlockingExecutorSpinYieldDispatcher()
    {
        final int expirationCount = 1_000_000;

        runFixRatePerformanceTest(expirationCount,
                                  SPIN_YIELD_DISPATCHER);
    } // end of fixRatePerformanceBlockingExecutorSpinYieldDispatcher()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private void runSingleTimer()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
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
    } // end of runSingleTimer()

    private void runMultipleTimers()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
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
        }
    } // end of runMultipleTimers()

    private void runMultipleTimersEarlier()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
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
        }
    } // end of runMultipleTimersEarlier()

    private void runCancelTimer()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
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
        assertThat(agent.state(0))
            .isEqualTo(Future.State.CANCELLED);
    } // end of runCancelTimer()

    private void runSingleFixRateTimer()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
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
        assertThat(agent.state(timerId))
            .isEqualTo(Future.State.CANCELLED);
    } // end of runSingleFixRateTimer()

    private void runSingleFixDelayTimer()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
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
        assertThat(agent.state(timerId))
            .isEqualTo(Future.State.CANCELLED);
    } // end of runSingleFixDelayTimer()

    private void runMixedTimers()
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
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
        assertThat(agent.state(0))
            .isEqualTo(Future.State.SUCCESS);
        assertThat(agent.isCanceled(1)).isTrue();
        assertThat(agent.state(1))
            .isEqualTo(Future.State.CANCELLED);
        assertThat(agent.isCanceled(2)).isTrue();
        assertThat(agent.state(2))
            .isEqualTo(Future.State.CANCELLED);
        assertThat(agent.isDone(3)).isTrue();
        assertThat(agent.state(3))
            .isEqualTo(Future.State.SUCCESS);
    } // end of runMixedTimers()

    private void runFixRatePerformanceTest(final int numExpirations,
                                           final String dispatcherName)
    {
        final EfsScheduledExecutor executor =
            new EfsScheduledExecutor(mService);
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

        sLogger.info("\n{} dispatcher.", dispatcherName);

        sLogger.info(
            "\nRun time: {}\n\n", formatDuration(runTime));

        sLogger.info(agent.generateResults());
    } // end of runFixRatePerformanceTest(...)

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
        private final Map<Integer, ScheduledFuture<?>> mTimers;

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
            final ScheduledFuture<?> timer = mTimers.get(id);

            return (timer == null || timer.isDone());
        } // end of isExpired(int)

        public boolean isCanceled(final int id)
        {
            final ScheduledFuture<?> timer = mTimers.get(id);

            return (timer != null && timer.isCancelled());
        } // end of isCanceled(int)

        @Nullable public Future.State state(final int id)
        {
            final ScheduledFuture<?> timer = mTimers.get(id);

            return (timer == null ? null : timer.state());
        } // end of state(int)

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
                final ScheduledFuture<?> timer =
                    mExecutor.schedule(
                        null,
                        timerName,
                        this::onTimeout,
                        this,
                        delay);

                mTimerCounter.incrementAndGet();
                mTimers.put(id, timer);
                assertThat(timer.isCancelled()).isFalse();
                assertThat(timer.isDone()).isFalse();
                assertThat(timer.state())
                    .isEqualTo(Future.State.RUNNING);
            }
        } // end of setTimer(int, Duration)

        private void setFixedRateTimer(final int id,
                                       final Duration initialDelay,
                                       final Duration period)
        {
            if (!mTimers.containsKey(id))
            {
                final String timerName = TIMER_NAME_PREFIX + id;
                final ScheduledFuture<?> timer =
                    mExecutor.scheduleAtFixedRate(
                        timerName,
                        null,
                        this::onTimeout,
                        this,
                        initialDelay,
                        period);

                mTimerCounter.incrementAndGet();
                mTimers.put(id, timer);

                assertThat(timer.state())
                    .isEqualTo(Future.State.RUNNING);
                assertThat(timer.isCancelled()).isFalse();
                assertThat(timer.isDone()).isFalse();
            }
        } // end of setFixedRateTimer(int, Duration, Duration)

        private void setFixedDelayTimer(final int id,
                                        final Duration initialDelay,
                                        final Duration delay)
        {
            if (!mTimers.containsKey(id))
            {
                final String timerName = TIMER_NAME_PREFIX + id;
                final ScheduledFuture<?> timer =
                    mExecutor.scheduleWithFixedDelay(
                        timerName,
                        null,
                        this::onTimeout,
                        this,
                        initialDelay,
                        delay);

                mTimerCounter.incrementAndGet();
                mTimers.put(id, timer);

                assertThat(timer.state())
                    .isEqualTo(Future.State.RUNNING);
                assertThat(timer.isCancelled()).isFalse();
                assertThat(timer.isDone()).isFalse();
            }
        } // end of setFixedDelayTimer(int, Duration, Duration)

        private void cancelTimer(final int id)
        {
            if (mTimers.containsKey(id))
            {
                final ScheduledFuture<?> timer = mTimers.get(id);

                if (!timer.isDone())
                {
                    try
                    {
                        timer.cancel(true);
                    }
                    catch (Exception jex)
                    {
                        sLogger.warn("{}: timer cancel failed.",
                                     mName,
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
                (System.nanoTime() - timerEvent.dispatchTimestamp());

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
