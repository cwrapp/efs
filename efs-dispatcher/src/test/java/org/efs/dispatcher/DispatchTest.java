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

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.dispatcher.EfsDispatcher.DispatcherStats;
import org.efs.dispatcher.EfsDispatcher.DispatcherType;
import org.efs.dispatcher.EfsDispatcherThread.DispatcherThreadStats;
import org.efs.dispatcher.config.ThreadType;
import org.efs.logging.AsyncLoggerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 * Exercises event dispatch on a small scale.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class DispatchTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String DISPATCHER_NAME =
        "test-dispatcher-101";
    private static final ThreadType THREAD_TYPE =
        ThreadType.BLOCKING;
    private static final int THREAD_COUNT = 2;
    private static final int THREAD_PRIORITY = 3;
    private static final int MAX_EVENTS = 4;

    private static final String AGENT_NAME = "test-agent-101";
    private static final int EVENT_COUNT = 100;
    private static final int EVENT_DELAY = 100; // milliseconds.
    private static final TimeUnit DELAY_UNIT =
        TimeUnit.MILLISECONDS;

    //-----------------------------------------------------------
    // Statics.
    //

    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger();

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Produces events posted to agent.
     */
    private ProducerAgent mProducer;

    /**
     * Consumers posted events.
     */
    private PerformanceAgent mAgent;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void setUpClass()
    {
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder();

        builder.dispatcherName(DISPATCHER_NAME)
               .threadType(THREAD_TYPE)
               .numThreads(THREAD_COUNT)
               .priority(THREAD_PRIORITY)
               .dispatcherType(DispatcherType.EFS)
               .eventQueueCapacity(8)
               .runQueueCapacity(1)
               .maxEvents(MAX_EVENTS)
               .build();
    } // end of setUpClass()

    @AfterAll
    public static void tearDownClass()
    {
        EfsDispatcher.clearDispatchers();
    } // end of tearDownClass()

    @BeforeEach
    public void setUp()
    {
        final CountDownLatch doneSignal =
            new CountDownLatch(EVENT_COUNT);

        mAgent = new PerformanceAgent(AGENT_NAME,
                                      EVENT_COUNT,
                                      doneSignal);
        mProducer = new ProducerAgent(EVENT_COUNT,
                                      EVENT_DELAY,
                                      DELAY_UNIT,
                                      mAgent);

        EfsDispatcher.register(mAgent, DISPATCHER_NAME);
        EfsDispatcher.register(mProducer, DISPATCHER_NAME);
    } // end of setUp()

    @AfterEach
    public void tearDown()
    {
        EfsDispatcher.deregister(mAgent);
        EfsDispatcher.deregister(mProducer);
    } // end of tearDown()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void dispatcherStatsNullName()
    {
        final String dispatcherName = null;

        try
        {
            EfsDispatcher.performanceStats(dispatcherName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(EfsDispatcher.INVALID_NAME);
        }
    } // end of dispatcherStatsNullName()

    @Test
    public void dispatcherStatsEmptyName()
    {
        final String dispatcherName = "";

        try
        {
            EfsDispatcher.performanceStats(dispatcherName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(EfsDispatcher.INVALID_NAME);
        }
    } // end of dispatcherStatsEmptyName()

    @Test
    public void dispatcherStatsUnknownDispatcher()
    {
        final String dispatcherName = "snafu";

        try
        {
            EfsDispatcher.performanceStats(dispatcherName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage("name is null or an empty string");
        }
    } // end of dispatcherStatsUnknownDispatcher()

    @Test
    public void dispatchTest()
    {
        final CountDownLatch doneSignal = mAgent.doneSignal();

        mAgent.start();
        mProducer.start();

        try
        {
            doneSignal.await();
        }
        catch (InterruptedException interrupt)
        {}

        mAgent.stop();
        mProducer.stop();

        assertThat(doneSignal.getCount()).isZero();
        assertThat(mAgent.eventCount()).isEqualTo(EVENT_COUNT);
        assertThat(mProducer.eventCount())
            .isBetween((EVENT_COUNT - 2), EVENT_COUNT);

        final DispatcherStats stats =
            EfsDispatcher.performanceStats(DISPATCHER_NAME);

        assertThat(stats).isNotNull();

        final DispatcherThreadStats[] threadStats =
            stats.dispatcherThreadStats();
        final int agentRunCount = stats.totalAgentRunCount();
        final int expectedRunCount = (EVENT_COUNT * 2);
        final String expectedText =
            String.format(
                "[dispatcher=%s, thread type=%s, thread count=%d, max events=%d, agent run count=",
                DISPATCHER_NAME,
                THREAD_TYPE,
                THREAD_COUNT,
                MAX_EVENTS);

        assertThat(stats.dispatcherName())
            .isEqualTo(DISPATCHER_NAME);
        assertThat(stats.maxEvents()).isEqualTo(MAX_EVENTS);
        assertThat(stats.threadType()).isEqualTo(THREAD_TYPE);
        assertThat(threadStats.length).isEqualTo(THREAD_COUNT);
        assertThat(agentRunCount)
            .isBetween((expectedRunCount - 2), expectedRunCount);
        assertThat(stats.toString()).startsWith(expectedText);

        final EfsDispatcher.AgentStats agentReadyStats =
            stats.agentReadyTimeStats();

        assertThat(agentReadyStats.statsName())
            .isEqualTo("agent ready deltas");
        assertThat(agentReadyStats.unit()).isEqualTo("nanos");
        assertThat(agentReadyStats.agentRunCount())
            .isGreaterThan(0);
        assertThat((agentReadyStats.stats()).length)
            .isGreaterThan(0);
        assertThat(agentReadyStats.average()).isGreaterThan(0L);
        assertThat(agentReadyStats.toString()).isNotEmpty();

        final DispatcherThreadStats tstats = threadStats[0];
        final String threadName =
            String.format("[[Cold-0]%s]", DISPATCHER_NAME);

        assertThat(tstats.threadName())
            .isEqualTo(threadName);
        assertThat(tstats.startTime()).isNotNull();
        assertThat(tstats.totalRunTime())
            .isGreaterThan(Duration.ZERO);

        final EfsDispatcherThread.AgentStats agentStats =
            tstats.agentReadyTimeStats();

        assertThat(agentStats.threadName())
            .isEqualTo(threadName);
        assertThat(agentStats.movingAverage())
            .isGreaterThan(0L);
        assertThat(agentStats.toString()).isNotEmpty();
    } // end of dispatchTest()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------
} // end of class DispatchTest