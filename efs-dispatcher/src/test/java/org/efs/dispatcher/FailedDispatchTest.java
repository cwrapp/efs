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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.dispatcher.EfsDispatcher.DispatcherType;
import org.efs.dispatcher.config.ThreadType;
import org.efs.event.IEfsEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class FailedDispatchTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String DISPATCHER_NAME =
        "test-dispatcher-201";
    private static final ThreadType THREAD_TYPE =
        ThreadType.BLOCKING;
    private static final int THREAD_COUNT = 1;
    private static final int THREAD_PRIORITY = 3;
    private static final int EVENT_QUEUE_CAPACITY = 32;
    private static final int RUN_QUEUE_CAPACITY = 1;
    private static final int MAX_EVENTS = 32;

    private static final String AGENT_NAME_PREFIX =
        "test-agent-";

    //-----------------------------------------------------------
    // Locals.
    //

    private TestAgent mAgent0;
    private TestAgent mAgent1;
    private BadTestAgent mBadAgent;

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
            EfsDispatcher.builder(DISPATCHER_NAME);

        builder.threadType(THREAD_TYPE)
               .numThreads(THREAD_COUNT)
               .priority(THREAD_PRIORITY)
               .dispatcherType(DispatcherType.EFS)
               .eventQueueCapacity(EVENT_QUEUE_CAPACITY)
               .runQueueCapacity(RUN_QUEUE_CAPACITY)
               .maxEvents(MAX_EVENTS)
               .build();
    } // end of setUpClass()

    @AfterAll
    public static void tearDownClass()
    {
        EfsDispatcher.stopDispatchers();
        EfsDispatcher.clearDispatchers();
    } // end of tearDownClass()

    @BeforeEach
    public void setUp()
    {
        int agentIndex = 200;
        String agentName = AGENT_NAME_PREFIX + agentIndex;

        mAgent0 = new TestAgent(agentName);

        ++agentIndex;
        agentName = AGENT_NAME_PREFIX + agentIndex;
        mAgent1 = new TestAgent(agentName);

        ++agentIndex;
        agentName = AGENT_NAME_PREFIX + agentIndex;
        mBadAgent = new BadTestAgent(agentName);

        EfsDispatcher.register(mAgent0, DISPATCHER_NAME);
        EfsDispatcher.register(mAgent1, DISPATCHER_NAME);
        EfsDispatcher.register(mBadAgent, DISPATCHER_NAME);
    } // end of setUp()

    @AfterEach
    public void tearDown()
    {
        EfsDispatcher.deregister(mAgent0);
        EfsDispatcher.deregister(mAgent1);
        EfsDispatcher.deregister(mBadAgent);
    } // end of tearDown()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void failedDispatchTest()
    {
        final int numDispatches = 10;
        int i;
        final TestEvent event = new TestEvent(0);

        for (i = 0; i < numDispatches; ++i)
        {
            EfsDispatcher.dispatch(
                mAgent0::onEvent, event, mAgent0);
            EfsDispatcher.dispatch(
                mAgent1::onEvent, event, mAgent1);
        }

        final List<EfsAgent.AgentStats> agentStats =
            EfsAgent.runTimeStats();
        long totalMisses = 0L;

        assertThat(agentStats).isNotNull();
        assertThat(agentStats).isNotEmpty();
        assertThat(agentStats).hasSizeGreaterThanOrEqualTo(2);

        for (EfsAgent.AgentStats as : agentStats)
        {
            totalMisses += as.getMissedDispatchCount();
        }

        assertThat(totalMisses).isGreaterThan(0L);
    } // end of failedDispatchTest()

    @Test
    public void agentThrowsException()
    {
        final CountDownLatch doneSignal = mBadAgent.doneSignal();
        final TestEvent event = new TestEvent(1);

        EfsDispatcher.dispatch(
            mBadAgent::onEvent, event, mBadAgent);

        try
        {
            doneSignal.await();
        }
        catch (InterruptedException interrupt)
        {}
    } // end of agentThrowsException()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

//---------------------------------------------------------------
// Inner classes.
//

    private static final class TestAgent
        implements IEfsAgent
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private final String mName;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private TestAgent(final String name)
        {
            mName = name;
        } // end of TestAgent(String)

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
        // Event Methods.
        //

        private void onEvent(final TestEvent event)
        {}

        //
        // end of Event Methods.
        //-------------------------------------------------------
    } // end of class TestAgent

    private static final class BadTestAgent
        implements IEfsAgent
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private final String mName;
        private final CountDownLatch mDoneSignal;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private BadTestAgent(final String name)
        {
            mName = name;
            mDoneSignal = new CountDownLatch(1);
        } // end of BadTestAgent(String)

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

        public CountDownLatch doneSignal()
        {
            return (mDoneSignal);
        } // end of doneSignal()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Event Methods.
        //

        private void onEvent(final TestEvent event)
        {
            mDoneSignal.countDown();

            throw (new RuntimeException("BOOM!"));
        } // end of onEvent(TestEvent)

        //
        // end of Set Methods.
        //-------------------------------------------------------
    } // end of class BadTestAgent

    private static final class TestEvent
        implements IEfsEvent
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private final int mIndex;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private TestEvent(final int index)
        {
            mIndex = index;
        } // end of TestEvent(int)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        public int index()
        {
            return (mIndex);
        } // end of index()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of class TestEvent
} // end of class FailedDispatchTest