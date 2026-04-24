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

package org.efs.bus;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.config.ThreadType;
import org.efs.event.EfsTopicKey;
import org.efs.logging.AsyncLoggerFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 * Unit test profiling {@code EfsEventBus} performance.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class BusPerformanceTest
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

    private static final String DISPATCHER_NAME_FORMAT = "%s-%s";
    private static final int DISPATCHER_NUM_THREADS = 1;
    private static final int DISPATCHER_PRIORITY =
        Thread.MAX_PRIORITY;
    private static final long DISPATCHER_SPIN_LIMIT =
        3_000_000L;
    private static final Duration DISPATCHER_PARK_TIME =
        Duration.ofNanos(500L);

    private static final String BUS_NAME = "performance-bus";

    private static final int MAX_EVENT_QUEUE_SIZE = 1_024;
    private static final int MAX_RUN_QUEUE_SIZE = 64;

    private static final int MAX_EVENTS = 10_000_000;
    private static final long TEST_DELAY = 10L;
    private static final TimeUnit TEST_TIME_UNIT =
        TimeUnit.MICROSECONDS;

    private static final int ROUTER_CHILD_COUNT = 5;

    private static final String PINGER_AGENT = "ping";
    /* package */ static final String PONGER_AGENT = "pong";
    private static final String ROUTER_AGENT = "pong-router";
    private static final String REPLIER_AGENT = "pong-replier";

    private static final String PING_TOPIC = "/test/ping!";
    private static final EfsTopicKey<PerformanceEvent> PING_KEY =
        EfsTopicKey.getKey(PerformanceEvent.class, PING_TOPIC);
    private static final String PONG_TOPIC = "/test/pong!";
    private static final EfsTopicKey<PerformanceEvent> PONG_KEY =
        EfsTopicKey.getKey(PerformanceEvent.class, PONG_TOPIC);

    //-----------------------------------------------------------
    // Statics.
    //

    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(BusPerformanceTest.class);

//---------------------------------------------------------------
// Member methods.
//

    /**
     * Main method needed to run performance test.
     * @param args empty arguments list.
     */
    public static void main(final String[] args)
    {}

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    //
    // Performance tests.
    // Concrete subscriptions.
    //

    @Disabled
    @Test
    public void performanceTestBlocking()
    {
        final String pingDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                BLOCKING_DISPATCHER, PINGER_AGENT);
        final String pongDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                BLOCKING_DISPATCHER, PONGER_AGENT);
        final int eventCount = MAX_EVENTS;
        final long delay = TEST_DELAY;
        final TimeUnit timeUnit = TEST_TIME_UNIT;

        // Blocking dispatchers.
        createDispatcher(pingDispatcher, ThreadType.BLOCKING);
        createDispatcher(pongDispatcher, ThreadType.BLOCKING);

        runTest(pingDispatcher,
                pongDispatcher,
                eventCount,
                delay,
                timeUnit);
    } // end of performanceTestBlocking()

    @Disabled
    @Test
    public void performanceTestSpinning()
    {
        final String pingDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                SPINNING_DISPATCHER, PINGER_AGENT);
        final String pongDispatcher=
            DISPATCHER_NAME_FORMAT.formatted(
                SPINNING_DISPATCHER, PONGER_AGENT);
        final int eventCount = MAX_EVENTS;
        final long delay = TEST_DELAY;
        final TimeUnit timeUnit = TEST_TIME_UNIT;

        // Spinning dispatchers.
        createDispatcher(pingDispatcher, ThreadType.SPINNING);
        createDispatcher(pongDispatcher, ThreadType.SPINNING);

        runTest(pingDispatcher,
                pongDispatcher,
                eventCount,
                delay,
                timeUnit);
    } // end of performanceTestSpinning()

    @Disabled
    @Test
    public void performanceTestSpinPark()
    {
        final String pingDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                SPIN_PARK_DISPATCHER, PINGER_AGENT);
        final String pongDispatcher=
            DISPATCHER_NAME_FORMAT.formatted(
                SPIN_PARK_DISPATCHER, PONGER_AGENT);
        final int eventCount = MAX_EVENTS;
        final long delay = TEST_DELAY;
        final TimeUnit timeUnit = TEST_TIME_UNIT;

        // Spin+park dispatchers.
        createDispatcher(pingDispatcher, ThreadType.SPINPARK);
        createDispatcher(pongDispatcher, ThreadType.SPINPARK);

        runTest(pingDispatcher,
                pongDispatcher,
                eventCount,
                delay,
                timeUnit);
    } // end of performanceTestSpinPark()

    @Disabled
    @Test
    public void performanceTestSpinYield()
    {
        final String pingDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                SPIN_YIELD_DISPATCHER, PINGER_AGENT);
        final String pongDispatcher=
            DISPATCHER_NAME_FORMAT.formatted(
                SPIN_YIELD_DISPATCHER, PONGER_AGENT);
        final int eventCount = MAX_EVENTS;
        final long delay = TEST_DELAY;
        final TimeUnit timeUnit = TEST_TIME_UNIT;

        // Spin+yield dispatchers.
        createDispatcher(pingDispatcher, ThreadType.SPINYIELD);
        createDispatcher(pongDispatcher, ThreadType.SPINYIELD);

        runTest(pingDispatcher,
                pongDispatcher,
                eventCount,
                delay,
                timeUnit);
    } // end of performanceTestSpinYield()

    @Disabled
    @Test
    public void performanceTestMultiAgentMultiDispatcher()
    {
        final EfsEventBus bus =
            EfsEventBus.findOrCreateBus(BUS_NAME);
        final int totalEventCount = 5_000_000;
        final CountDownLatch doneSignal;
        final int maxEvents = MAX_EVENT_QUEUE_SIZE;
        final long delay = 100L;
        final TimeUnit timeUnit = TimeUnit.MICROSECONDS;
        final int pongerCount = 3;
        final int minEventCount = 1;
        final int maxEventCount = 5;
        final PingAgent pinger;
        final PongAgent[] pongers = new PongAgent[pongerCount];
        final int fastAgents0 = 1;
        final int fastAgents1 = 1;
        final int slowAgents =
            (pongerCount - (fastAgents0 + fastAgents1));
        int numAgents;
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
        builder.numThreads(fastAgents0)
               .threadType(ThreadType.SPINNING)
               .priority(DISPATCHER_PRIORITY)
               .dispatcherType(EfsDispatcher.DispatcherType.EFS)
               .eventQueueCapacity(MAX_EVENT_QUEUE_SIZE)
               .runQueueCapacity(MAX_RUN_QUEUE_SIZE)
               .maxEvents(maxEvents)
               .build();

        // Spin+park dispatcher.
        sLogger.info("Creating {}.", MULTI_SPIN_PARK_DISPATCHER);
        builder =
            EfsDispatcher.builder(MULTI_SPIN_PARK_DISPATCHER);
        builder.numThreads(fastAgents1 + 1)
               .threadType(ThreadType.SPINPARK)
               .priority(DISPATCHER_PRIORITY)
               .spinLimit(DISPATCHER_SPIN_LIMIT)
               .parkTime(DISPATCHER_PARK_TIME)
               .dispatcherType(EfsDispatcher.DispatcherType.EFS)
               .eventQueueCapacity(MAX_EVENT_QUEUE_SIZE)
               .runQueueCapacity(MAX_RUN_QUEUE_SIZE)
               .maxEvents(maxEvents)
               .build();

        // Blocking dispatcher.
        sLogger.info("Creating {}.", BLOCKING_DISPATCHER);
        builder = EfsDispatcher.builder(BLOCKING_DISPATCHER);
        builder.numThreads(slowAgents)
               .threadType(ThreadType.BLOCKING)
               .priority(DISPATCHER_PRIORITY)
               .dispatcherType(EfsDispatcher.DispatcherType.EFS)
               .eventQueueCapacity(MAX_EVENT_QUEUE_SIZE)
               .runQueueCapacity(MAX_RUN_QUEUE_SIZE)
               .maxEvents(maxEvents)
               .build();

        // Create multiple performance agents.
        for (i = 0; i < pongerCount; ++i)
        {
            agentName = PONGER_AGENT + "-" + i;
            pongers[i] = new PongAgent(agentName,
                                      bus,
                                      totalEventCount,
                                      PING_KEY,
                                      PONG_KEY);
        }

        pinger = new PingAgent(PINGER_AGENT,
                               bus,
                               totalEventCount,
                               PING_KEY,
                               PONG_KEY,
                               delay,
                               timeUnit,
                               true,
                               minEventCount,
                               maxEventCount,
                               pongerCount);
        doneSignal = pinger.doneSignal();


        // Register spinning, sping+park, and blocking agents.
        for (i = 0, numAgents = (i + fastAgents0);
             i < numAgents;
             ++i)
        {
            EfsDispatcher.register(
                pongers[i], MULTI_SPINNING_DISPATCHER);
        }

        for (numAgents = (i + fastAgents1); i < numAgents; ++i)
        {
            EfsDispatcher.register(
                pongers[i], MULTI_SPIN_PARK_DISPATCHER);
        }

        for (numAgents = (i + slowAgents); i < numAgents; ++i)
        {
            EfsDispatcher.register(
                pongers[i], BLOCKING_DISPATCHER);
        }

        // Now start the agents.
        for (i = 0; i < pongerCount; ++i)
        {
            pongers[i].start();
        }

        EfsDispatcher.register(
            pinger, MULTI_SPIN_PARK_DISPATCHER);
        pinger.start();

        try
        {
            doneSignal.await();
        }
        catch (InterruptedException interrupt)
        {}

        // Stop agents.
        for (i = 0; i < pongerCount; ++i)
        {
            pongers[i].stop();
        }

        pinger.stop();

        sLogger.info("... test completed.");

        // De-register agents.
        for (i = 0; i < pongerCount; ++i)
        {
            EfsDispatcher.deregister(pongers[i]);
        }

        // Dump results.
        sLogger.info("PongAgent results:");
        for (i = 0; i < pongerCount; ++i)
        {
            sLogger.info(pongers[i].generateResults());
        }

        sLogger.info("PingAgent results:");
        sLogger.info(pinger.generateResults());

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

    // Router subscription.

    @Disabled
    @Test
    public void performanceTestRouterBlocking()
    {
        final String pingDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                BLOCKING_DISPATCHER, PINGER_AGENT);
        final String pongDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                BLOCKING_DISPATCHER, ROUTER_AGENT);
        final int eventCount = MAX_EVENTS;
        final int childCount = ROUTER_CHILD_COUNT;
        final long delay = TEST_DELAY;
        final TimeUnit timeUnit = TEST_TIME_UNIT;

        // Blocking dispatchers.
        createDispatcher(pingDispatcher, ThreadType.BLOCKING);
        createDispatcher(pongDispatcher, ThreadType.BLOCKING);

        runRouterTest(pingDispatcher,
                      pongDispatcher,
                      eventCount,
                      childCount,
                      delay,
                      timeUnit);
    } // end of performanceTestRouterBlocking()

    @Disabled
    @Test
    public void performanceTestRouterSpinning()
    {
        final String pingDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                SPINNING_DISPATCHER, PINGER_AGENT);
        final String pongDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                SPINNING_DISPATCHER, ROUTER_AGENT);
        final int eventCount = MAX_EVENTS;
        final int childCount = ROUTER_CHILD_COUNT;
        final long delay = TEST_DELAY;
        final TimeUnit timeUnit = TEST_TIME_UNIT;

        // Spinning dispatchers.
        createDispatcher(pingDispatcher, ThreadType.SPINNING);
        createDispatcher(pongDispatcher, ThreadType.SPINNING);

        runRouterTest(pingDispatcher,
                      pongDispatcher,
                      eventCount,
                      childCount,
                      delay,
                      timeUnit);
    } // end of performanceTestRouterSpinning()

    @Disabled
    @Test
    public void performanceTestRouterSpinPark()
    {
        final String pingDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                SPIN_PARK_DISPATCHER, PINGER_AGENT);
        final String pongDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                SPIN_PARK_DISPATCHER, ROUTER_AGENT);
        final int eventCount = MAX_EVENTS;
        final int childCount = ROUTER_CHILD_COUNT;
        final long delay = TEST_DELAY;
        final TimeUnit timeUnit = TEST_TIME_UNIT;

        // Spin+park dispatchers.
        createDispatcher(pingDispatcher, ThreadType.SPINPARK);
        createDispatcher(pongDispatcher, ThreadType.SPINPARK);

        runRouterTest(pingDispatcher,
                      pongDispatcher,
                      eventCount,
                      childCount,
                      delay,
                      timeUnit);
    } // end of performanceTestRouterSpinPark()

    @Disabled
    @Test
    public void performanceTestRouterSpinYield()
    {
        final String pingDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                SPIN_YIELD_DISPATCHER, PINGER_AGENT);
        final String pongDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                SPIN_YIELD_DISPATCHER, ROUTER_AGENT);
        final int eventCount = MAX_EVENTS;
        final int childCount = ROUTER_CHILD_COUNT;
        final long delay = TEST_DELAY;
        final TimeUnit timeUnit = TEST_TIME_UNIT;

        // Spin+yield dispatchers.
        createDispatcher(pingDispatcher, ThreadType.SPINYIELD);
        createDispatcher(pongDispatcher, ThreadType.SPINYIELD);

        runRouterTest(pingDispatcher,
                      pongDispatcher,
                      eventCount,
                      childCount,
                      delay,
                      timeUnit);
    } // end of performanceTestRouterSpinYield()

    // Reply ponger.

    @Disabled
    @Test
    public void performanceTestReplierBlocking()
    {
        final String pingDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                BLOCKING_DISPATCHER, PINGER_AGENT);
        final String replyDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                BLOCKING_DISPATCHER, REPLIER_AGENT);
        final int eventCount = MAX_EVENTS;
        final long delay = TEST_DELAY;
        final TimeUnit timeUnit = TEST_TIME_UNIT;

        // Blocking dispatchers.
        createDispatcher(pingDispatcher, ThreadType.BLOCKING);
        createDispatcher(replyDispatcher, ThreadType.BLOCKING);

        runReplierTest(pingDispatcher,
                       replyDispatcher,
                       eventCount,
                       delay,
                       timeUnit);
    } // end of performanceTestReplierBlocking()

    @Disabled
    @Test
    public void peformanceTestRepliersSpinning()
    {
        final String pingDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                SPINNING_DISPATCHER, PINGER_AGENT);
        final String replyDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                SPINNING_DISPATCHER, REPLIER_AGENT);
        final int eventCount = MAX_EVENTS;
        final long delay = TEST_DELAY;
        final TimeUnit timeUnit = TEST_TIME_UNIT;

        // Blocking dispatchers.
        createDispatcher(pingDispatcher, ThreadType.SPINNING);
        createDispatcher(replyDispatcher, ThreadType.SPINNING);

        runReplierTest(pingDispatcher,
                       replyDispatcher,
                       eventCount,
                       delay,
                       timeUnit);
    } // end of peformanceTestRepliersSpinning()

    @Disabled
    @Test
    public void performanceTestRepliersSpinPark()
    {
        final String pingDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                SPIN_PARK_DISPATCHER, PINGER_AGENT);
        final String replyDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                SPIN_PARK_DISPATCHER, REPLIER_AGENT);
        final int eventCount = MAX_EVENTS;
        final long delay = TEST_DELAY;
        final TimeUnit timeUnit = TEST_TIME_UNIT;

        // Blocking dispatchers.
        createDispatcher(pingDispatcher, ThreadType.SPINPARK);
        createDispatcher(replyDispatcher, ThreadType.SPINPARK);

        runReplierTest(pingDispatcher,
                       replyDispatcher,
                       eventCount,
                       delay,
                       timeUnit);
    } // end of performanceTestRepliersSpinPark()

    @Disabled
    @Test
    public void performanceTestRepliersSpinYield()
    {
        final String pingDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                SPIN_YIELD_DISPATCHER, PINGER_AGENT);
        final String replyDispatcher =
            DISPATCHER_NAME_FORMAT.formatted(
                SPIN_YIELD_DISPATCHER, REPLIER_AGENT);
        final int eventCount = MAX_EVENTS;
        final long delay = TEST_DELAY;
        final TimeUnit timeUnit = TEST_TIME_UNIT;

        // Blocking dispatchers.
        createDispatcher(pingDispatcher, ThreadType.SPINYIELD);
        createDispatcher(replyDispatcher, ThreadType.SPINYIELD);

        runReplierTest(pingDispatcher,
                       replyDispatcher,
                       eventCount,
                       delay,
                       timeUnit);
    } // end of performanceTestRepliersSpinYield()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private void createDispatcher(final String dispatcherName,
                                  final ThreadType threadType)
    {
        EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        builder.numThreads(DISPATCHER_NUM_THREADS)
               .threadType(threadType)
               .priority(DISPATCHER_PRIORITY)
               .dispatcherType(EfsDispatcher.DispatcherType.EFS)
               .eventQueueCapacity(MAX_EVENT_QUEUE_SIZE)
               .runQueueCapacity(MAX_RUN_QUEUE_SIZE)
               .maxEvents(MAX_EVENT_QUEUE_SIZE)
               .build();
    } // end of createDispatcher(String, ThreadType)

    private void runTest(final String pingDispatcher,
                         final String pongDispatcher,
                         final int eventCount,
                         final long delay,
                         final TimeUnit timeUnit)
    {
        final EfsEventBus bus =
            EfsEventBus.findOrCreateBus(BUS_NAME);
        final PingAgent pinger =
            new PingAgent(PINGER_AGENT,
                          bus,
                          eventCount,
                          PING_KEY,
                          PONG_KEY,
                          delay,
                          timeUnit);
        final PongAgent ponger =
            new PongAgent(PONGER_AGENT,
                          bus,
                          eventCount,
                          PING_KEY,
                          PONG_KEY);
        final CountDownLatch doneSignal = pinger.doneSignal();

        sLogger.info("\n\nTesting {}.", pingDispatcher);

        EfsDispatcher.register(pinger, pingDispatcher);
        EfsDispatcher.register(ponger, pongDispatcher);

        pinger.start();
        ponger.start();

        try
        {
            doneSignal.await();
        }
        catch (InterruptedException interrupt)
        {}

        pinger.stop();
        ponger.stop();

        EfsDispatcher.deregister(pinger);
        EfsDispatcher.deregister(ponger);

        sLogger.info("Pinger results:");
        sLogger.info(pinger.generateResults());
        sLogger.info("Ponger results:");
        sLogger.info(ponger.generateResults());
        sLogger.info(
            "{} performance stats:\n{}\n\n",
            pingDispatcher,
            EfsDispatcher.performanceStats(pingDispatcher));
        sLogger.info(
            "{} performance stats:\n{}\n",
            pongDispatcher,
            EfsDispatcher.performanceStats(pongDispatcher));
    } // end of runTest(String, String, int, long, TimeUnit)

    private void runRouterTest(final String pingDispatcher,
                               final String pongDispatcher,
                               final int eventCount,
                               final int childCount,
                               final long delay,
                               final TimeUnit timeUnit)
    {
        final EfsEventBus bus =
            EfsEventBus.findOrCreateBus(BUS_NAME);
        final PingAgent pinger =
            new PingAgent(PINGER_AGENT,
                          bus,
                          eventCount,
                          PING_KEY,
                          PONG_KEY,
                          delay,
                          timeUnit);
        final PongRouter router =
            new PongRouter(ROUTER_AGENT,
                           bus,
                           pongDispatcher,
                           eventCount,
                           PING_KEY,
                           PONG_KEY,
                           childCount);
        final CountDownLatch doneSignal = pinger.doneSignal();

        sLogger.info("\n\nTesting {}.", pingDispatcher);

        EfsDispatcher.register(pinger, pingDispatcher);
        EfsDispatcher.register(router, pongDispatcher);

        pinger.start();
        router.start();

        try
        {
            doneSignal.await();
        }
        catch (InterruptedException interrupt)
        {}

        pinger.stop();
        router.stop();

        EfsDispatcher.deregister(pinger);
        EfsDispatcher.deregister(router);

        sLogger.info("Pinger results:");
        sLogger.info(pinger.generateResults());
        sLogger.info("Router results:");
        sLogger.info(router.generateResults());
        sLogger.info(
            "{} performance stats:\n{}\n",
            pingDispatcher,
            EfsDispatcher.performanceStats(pingDispatcher));
        sLogger.info(
            "{} performance stats:\n{}\n",
            pongDispatcher,
            EfsDispatcher.performanceStats(pongDispatcher));
    } // end of runRouterTest(...)

    private void runReplierTest(final String pingDispatcher,
                                final String replyDispatcher,
                                final int eventCount,
                                final long delay,
                                final TimeUnit timeUnit)
    {
        final EfsEventBus bus =
            EfsEventBus.findOrCreateBus(BUS_NAME);
        final PingAgent pinger =
            new PingAgent(PINGER_AGENT,
                          bus,
                          eventCount,
                          PING_KEY,
                          PONG_KEY,
                          delay,
                          timeUnit);
        final ReplyAgent replier =
            new ReplyAgent(PONGER_AGENT,
                           bus,
                           eventCount,
                           PING_KEY,
                           PONG_KEY);
        final CountDownLatch doneSignal = pinger.doneSignal();

        sLogger.info("\n\nTesting {}.", pingDispatcher);

        EfsDispatcher.register(pinger, pingDispatcher);
        EfsDispatcher.register(replier, replyDispatcher);

        pinger.start();
        replier.start();

        try
        {
            doneSignal.await();
        }
        catch (InterruptedException interrupt)
        {}

        pinger.stop();
        replier.stop();

        EfsDispatcher.deregister(pinger);
        EfsDispatcher.deregister(replier);

        sLogger.info("Pinger results:");
        sLogger.info(pinger.generateResults());
        sLogger.info("Ponger results:");
        sLogger.info(replier.generateResults());
        sLogger.info(
            "{} performance stats:\n{}\n\n",
            pingDispatcher,
            EfsDispatcher.performanceStats(pingDispatcher));
        sLogger.info(
            "{} performance stats:\n{}\n",
            replyDispatcher,
            EfsDispatcher.performanceStats(replyDispatcher));
    } // end of runReplierTest()
} // end of class BusPerformanceTest