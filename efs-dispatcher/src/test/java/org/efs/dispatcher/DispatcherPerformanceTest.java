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
import org.efs.dispatcher.EfsDispatcher.DispatcherType;
import org.efs.dispatcher.config.ThreadType;
import org.efs.logging.AsyncLoggerFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 * Unit used to profile dispatcher performance.
 *
 * @author charlesr
 */

@SuppressWarnings({"java:S5977"})
public final class DispatcherPerformanceTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String BLOCKING_DISPATCHER =
        "dispatcher-blocking";
    private static final String SPINNING_DISPATCHER =
        "dispatcher-spinning";
    private static final String SPIN_PARK_DISPATCHER =
        "dispatcher-spin+park";
    private static final String SPIN_YIELD_DISPATCHER =
        "dispatcher-spin+yield";
    private static final String MULTI_SPINNING_DISPATCHER =
        "dispatcher-multi-spinning";
    private static final String MULTI_SPIN_PARK_DISPATCHER =
        "dispatcher-multi-spin+park";

    private static final int BLOCKING_NUM_THREADS = 4;
    private static final int DISPATCHER_NUM_THREADS = 1;
    private static final int DISPATCHER_PRIORITY =
        Thread.MAX_PRIORITY;
    private static final long DISPATCHER_SPIN_LIMIT =
        3_000_000L;
    private static final Duration DISPATCHER_PARK_TIME =
        Duration.ofNanos(500L);

    private static final int MAX_EVENT_QUEUE_SIZE = 8_192;
    private static final int MAX_RUN_QUEUE_SIZE = 1_024;

    private static final String AGENT_NAME = "PerformanceAgent";

    private static final int MAX_EVENTS = 1_000_000;
    private static final long TEST_DELAY = 100L;
    private static final TimeUnit TEST_TIME_UNIT =
        TimeUnit.MICROSECONDS;

    //-----------------------------------------------------------
    // Statics.
    //

    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger();

    //-----------------------------------------------------------
    // Locals.
    //

    private PerformanceAgent mAgent;

    // Multi-agent, multi-dispatcher test.
    private int mAgentCount;
    private ProducerAgent mProducer;
    private PerformanceAgent[] mAgents;

//---------------------------------------------------------------
// Member methods.
//

    /**
     * Main method needed to run performance test.
     * @param args
     */
    public static void main(final String[] args)
    {}

    //-----------------------------------------------------------
    // JUnit Test Methods.
    //

    //
    // NOTE: Turn off TRACE logging before running any of these
    // performance tests.
    //

    @Disabled
    @Test
    public void performanceTestBlocking()
    {
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(BLOCKING_DISPATCHER);
        final int eventCount = MAX_EVENTS;
        final long delay = TEST_DELAY;
        final TimeUnit timeUnit = TEST_TIME_UNIT;

        // Blocking dispatcher.
        builder.numThreads(BLOCKING_NUM_THREADS)
               .threadType(ThreadType.BLOCKING)
               .priority(DISPATCHER_PRIORITY)
               .dispatcherType(DispatcherType.EFS)
               .eventQueueCapacity(MAX_EVENT_QUEUE_SIZE)
               .runQueueCapacity(MAX_RUN_QUEUE_SIZE)
               .maxEvents(MAX_EVENT_QUEUE_SIZE)
               .build();

        runTest(BLOCKING_DISPATCHER,
                eventCount,
                delay,
                timeUnit);
    } // end of performanceTestBlocking()

    @Disabled
    @Test
    public void performanceTestSpinning()
    {
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(SPINNING_DISPATCHER);
        final int eventCount = MAX_EVENTS;
        final long delay = TEST_DELAY;
        final TimeUnit timeUnit = TEST_TIME_UNIT;

        // Spinning dispatcher.
        builder.numThreads(DISPATCHER_NUM_THREADS)
               .threadType(ThreadType.SPINNING)
               .priority(DISPATCHER_PRIORITY)
               .dispatcherType(DispatcherType.EFS)
               .eventQueueCapacity(MAX_EVENT_QUEUE_SIZE)
               .runQueueCapacity(MAX_RUN_QUEUE_SIZE)
               .maxEvents(MAX_EVENT_QUEUE_SIZE)
               .build();

        runTest(SPINNING_DISPATCHER,
                eventCount,
                delay,
                timeUnit);
    } // end of performanceTestSpinning()

    @Disabled
    @Test
    public void performanceTestSpinPark()
    {
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(SPIN_PARK_DISPATCHER);
        final int eventCount = MAX_EVENTS;
        final long delay = TEST_DELAY;
        final TimeUnit timeUnit = TEST_TIME_UNIT;

        // Spin+park dispatcher.
        builder.numThreads(DISPATCHER_NUM_THREADS)
               .threadType(ThreadType.SPINPARK)
               .priority(DISPATCHER_PRIORITY)
               .spinLimit(DISPATCHER_SPIN_LIMIT)
               .parkTime(DISPATCHER_PARK_TIME)
               .dispatcherType(DispatcherType.EFS)
               .eventQueueCapacity(MAX_EVENT_QUEUE_SIZE)
               .runQueueCapacity(MAX_RUN_QUEUE_SIZE)
               .maxEvents(MAX_EVENT_QUEUE_SIZE)
               .build();

        runTest(SPIN_PARK_DISPATCHER,
                eventCount,
                delay,
                timeUnit);
    } // end of performanceTestSpinPark()

    @Disabled
    @Test
    public void performanceTestSpinYield()
    {
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(SPIN_YIELD_DISPATCHER);
        final int eventCount = MAX_EVENTS;
        final long delay = TEST_DELAY;
        final TimeUnit timeUnit = TEST_TIME_UNIT;

        // Spin+yield dispatcher.
        builder.numThreads(DISPATCHER_NUM_THREADS)
               .threadType(ThreadType.SPINYIELD)
               .priority(DISPATCHER_PRIORITY)
               .spinLimit(DISPATCHER_SPIN_LIMIT)
               .dispatcherType(DispatcherType.EFS)
               .eventQueueCapacity(MAX_EVENT_QUEUE_SIZE)
               .runQueueCapacity(MAX_RUN_QUEUE_SIZE)
               .maxEvents(MAX_EVENT_QUEUE_SIZE)
               .build();

        runTest(SPIN_YIELD_DISPATCHER,
                eventCount,
                delay,
                timeUnit);
    } // end of performanceTestSpinYield()

    @Disabled
    @Test
    public void performanceTestMultiAgentMultiDispatcher()
    {
        final int numEvents = 5_000_000;
        final CountDownLatch doneSignal =
            new CountDownLatch(numEvents);
        final int eventCount;
        final int maxEvents = MAX_EVENT_QUEUE_SIZE;
        EfsDispatcher.Builder builder;
        int i;
        String agentName;

        sLogger.info(
            "Multi-agent, multi-dispatcher performance test ...");

        // Create multi-threaded spinning and spin+park
        // dispatchers.

        // Spinning dispatcher.
        sLogger.info("Creating {}.", MULTI_SPINNING_DISPATCHER);
        builder =
            EfsDispatcher.builder(MULTI_SPINNING_DISPATCHER);
        builder.numThreads(5)
               .threadType(ThreadType.SPINNING)
               .priority(DISPATCHER_PRIORITY)
               .dispatcherType(DispatcherType.EFS)
               .eventQueueCapacity(MAX_EVENT_QUEUE_SIZE)
               .runQueueCapacity(MAX_RUN_QUEUE_SIZE)
               .maxEvents(maxEvents)
               .build();

        // Spin+park dispatcher.
        sLogger.info("Creating {}.", MULTI_SPIN_PARK_DISPATCHER);
        builder =
            EfsDispatcher.builder(MULTI_SPIN_PARK_DISPATCHER);
        builder.numThreads(5)
               .threadType(ThreadType.SPINPARK)
               .priority(DISPATCHER_PRIORITY)
               .spinLimit(DISPATCHER_SPIN_LIMIT)
               .parkTime(DISPATCHER_PARK_TIME)
               .dispatcherType(DispatcherType.EFS)
               .eventQueueCapacity(MAX_EVENT_QUEUE_SIZE)
               .runQueueCapacity(MAX_RUN_QUEUE_SIZE)
               .maxEvents(maxEvents)
               .build();

        // Blocking dispatcher.
        sLogger.info("Creating {}.", BLOCKING_DISPATCHER);
        builder = EfsDispatcher.builder(BLOCKING_DISPATCHER);
        builder.numThreads(5)
               .threadType(ThreadType.BLOCKING)
               .priority(DISPATCHER_PRIORITY)
               .dispatcherType(DispatcherType.EFS)
               .eventQueueCapacity(MAX_EVENT_QUEUE_SIZE)
               .runQueueCapacity(MAX_RUN_QUEUE_SIZE)
               .maxEvents(maxEvents)
               .build();

        mAgentCount = 60;
        mAgents = new PerformanceAgent[mAgentCount];
        eventCount = (numEvents / mAgentCount);

        // Create multiple performance agents.
        for (i = 0; i < mAgentCount; ++i)
        {
            agentName = AGENT_NAME + "-" + i;
            mAgents[i] = new PerformanceAgent(agentName,
                                              eventCount,
                                              doneSignal);
        }

        mProducer = new ProducerAgent(eventCount,
                                      TEST_DELAY,
                                      TEST_TIME_UNIT,
                                      mAgents,
                                      true,
                                      1,
                                      5);

        // Assign the first 20 to multi-spinning dispatcher,
        // the next 30 to multip-spin+park dispatcher, and the
        // last 10 to blocking.
        final int fastAgents0 = 10;
        final int fastAgents1 = 40;
        final int slowAgents = 10;
        int numAgents;

        // Register spinning, sping+park, and blocking agents.
        for (i = 0, numAgents = (i + fastAgents0);
             i < numAgents;
             ++i)
        {
            EfsDispatcher.register(
                mAgents[i], MULTI_SPINNING_DISPATCHER);
        }

        for (numAgents = (i + fastAgents1); i < numAgents; ++i)
        {
            EfsDispatcher.register(
                mAgents[i], MULTI_SPIN_PARK_DISPATCHER);
        }

        for (numAgents = (i + slowAgents); i < numAgents; ++i)
        {
            EfsDispatcher.register(
                mAgents[i], BLOCKING_DISPATCHER);
        }

        // Now start the agents.
        for (i = 0; i < mAgentCount; ++i)
        {
            mAgents[i].start();
        }

        EfsDispatcher.register(mProducer, BLOCKING_DISPATCHER);
        mProducer.start();

        try
        {
            doneSignal.await();
        }
        catch (InterruptedException interrupt)
        {}

        // Stop agents.
        for (i = 0; i < mAgentCount; ++i)
        {
            mAgents[i].stop();
        }

        mProducer.stop();

        sLogger.info("... test completed.");

        // De-register agents.
        for (i = 0; i < mAgentCount; ++i)
        {
            EfsDispatcher.deregister(mAgents[i]);
        }

        // Dump results.
        sLogger.info("PerformanceAgent results:");
        for (i = 0; i < mAgentCount; ++i)
        {
            sLogger.info(mAgents[i].generateResults());
        }

        sLogger.info("ProducerAgent results:");
        sLogger.info(mProducer.generateResults());

        System.out.format(
            "%s performance stats:%n%s%n",
            MULTI_SPINNING_DISPATCHER,
            EfsDispatcher.performanceStats(
                MULTI_SPINNING_DISPATCHER));
        System.out.format(
            "%n%s performance stats:%n%s%n",
            MULTI_SPIN_PARK_DISPATCHER,
            EfsDispatcher.performanceStats(
                MULTI_SPIN_PARK_DISPATCHER));
        System.out.format(
            "%n%s performance stats:%n%s%n",
            BLOCKING_DISPATCHER,
            EfsDispatcher.performanceStats(
                BLOCKING_DISPATCHER));
    } // end of performanceTestMultiAgentMultiDispatcher()

    //
    // end of JUnit Test Methods.
    //-----------------------------------------------------------

    private void runTest(final String dispatcherName,
                         final int eventCount,
                         final long delay,
                         final TimeUnit timeUnit)
    {
        final CountDownLatch doneSignal =
            new CountDownLatch(eventCount);

        sLogger.info("\n\nTesting {}.", dispatcherName);

        mAgent = new PerformanceAgent(AGENT_NAME,
                                      eventCount,
                                      doneSignal);
        mProducer = new ProducerAgent(eventCount,
                                      delay,
                                      timeUnit,
                                      mAgent);

        EfsDispatcher.register(mAgent, dispatcherName);
        EfsDispatcher.register(mProducer, dispatcherName);

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

        EfsDispatcher.deregister(mAgent);
        EfsDispatcher.deregister(mProducer);

        sLogger.info("PerformanceAgent results:");
        sLogger.info(mAgent.generateResults());
        sLogger.info("ProducerAgent results:");
        sLogger.info(mProducer.generateResults());

        System.out.format(
            "%s performance stats:%n%s%n",
            dispatcherName,
            EfsDispatcher.performanceStats(dispatcherName));
    } // end of runTest(EfsDispatcher, int, long, TimeUnit)
} // end of class DispatcherPerformanceTest
