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

package org.efs.activator;

import com.google.common.collect.ImmutableList;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.EfsDispatcher.DispatcherType;
import org.efs.dispatcher.config.ThreadType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies that only one {@code AgentInfo} is created
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@ExtendWith (MockitoExtension.class)
public final class ConcurrentActivationTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String AGENT_NAME = "test-agent";
    private static final String WORKFLOW_NAME = "test-workflow";
    private static final String NAME_METHOD = "name";
    private static final int THREAD_COUNT = 100;
    private static final int TASKS_PER_THREAD_COUNT = 200;

    // Dispatcher attributes.
    private static final String DISPATCHER_NAME =
        "test-dispatcher";
    private static final DispatcherType DISPATCHER_TYPE =
        DispatcherType.EFS;
    private static final ThreadType THREAD_TYPE =
        ThreadType.BLOCKING;
    private static final int NUM_DISPATCHER_THREADS = 1;
    private static final int THREAD_PRIORITY = 5;
    private static final int EVENT_QUEUE_SIZE = 65536;
    private static final int RUN_QUEUE_SIZE = 128;
    private static final int MAX_EVENTS = 4;

    /**
     * Wait this long for test to complete.
     */
    private static final Duration WAIT_TIME =
        Duration.ofMinutes(1L);


    //-----------------------------------------------------------
    // Statics.
    //

    private static Workflow sWorkflow;

    //-----------------------------------------------------------
    // Locals.
    //

    private IEfsActivateAgent mActivateAgent;
    private EfsActivator mActivator;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void setUpClass()
    {
        sWorkflow = mock(Workflow.class);
        when(sWorkflow.name()).thenReturn(WORKFLOW_NAME);

        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(DISPATCHER_NAME);

        builder.dispatcherType(DISPATCHER_TYPE)
               .threadType(THREAD_TYPE)
               .numThreads(NUM_DISPATCHER_THREADS)
               .priority(THREAD_PRIORITY)
               .eventQueueCapacity(EVENT_QUEUE_SIZE)
               .runQueueCapacity(RUN_QUEUE_SIZE)
               .maxEvents(MAX_EVENTS)
               .build();
    } // end of setUpClass()

    @BeforeEach
    public void setUp()
    {
        mActivateAgent =
            mock(IEfsActivateAgent.class,
                 invocation ->
                 {
                     if (NAME_METHOD.equals((invocation.getMethod()).getName()))
                     {
                         return (AGENT_NAME);
                     }

                     return(
                         Mockito.RETURNS_DEFAULTS.answer(
                             invocation));
                 });
        when(mActivateAgent.name()).thenReturn(AGENT_NAME);

        final List<Workflow> workflows =
            ImmutableList.of(sWorkflow);
        final EfsActivator.Builder builder =
            EfsActivator.builder();

        mActivator = builder.workflows(workflows).build();
    } // end of setUp()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    @SuppressWarnings({"unchecked"})
    public void concurrentFindAgentTest()
        throws NoSuchFieldException,
               IllegalAccessException
    {
        EfsDispatcher.register(mActivateAgent,
                               DISPATCHER_NAME);

        final ExecutorService executor =
            Executors.newFixedThreadPool(THREAD_COUNT);
        final List<Future<Boolean>> futures =
            new ArrayList<>();
        final CountDownLatch startSignal =
            new CountDownLatch(1);
        final CountDownLatch doneSignal =
            new CountDownLatch(THREAD_COUNT);
        boolean waitFlag = false;

        // Create the
        for (int i = 0; i < THREAD_COUNT; ++i)
        {
            futures.add(
                executor.submit(
                    () ->
                    {
                        try
                        {
                            // Have all threads wait and then
                            // execute this task at the same time
                            // so as to validate that
                            // EfsActivator.findAgent does *not*
                            // create multiple AgentInfo
                            // instances for the same agent.
                            startSignal.await();

                            for (int i1 = 0;
                                 i1 < TASKS_PER_THREAD_COUNT;
                                 ++i1)
                            {
                                // The following call executes
                                // EfsActivator.findAgent.
                                mActivator.agentState(
                                    AGENT_NAME);
                            }

                            return (true);
                        }
                        catch (Throwable tex)
                        {
                            throw (
                                new RuntimeException(tex));
                        }
                        finally{
                            // Decrement the done signal
                            // whether test was successful
                            // or not.
                            doneSignal.countDown();
                        }
                    }));
        }

        // Start test threads running and then wait for them
        // to complete.
        startSignal.countDown();

        try
        {
            waitFlag =
                doneSignal.await(WAIT_TIME.toMillis(),
                                 TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException interrupt)
        {}

        assertThat(waitFlag).isTrue();

        // Collect exceptions.
        for (Future<Boolean> f : futures)
        {
            try
            {
                // Will throw exception if task had an
                // exception.
                f.get();
            }
            catch (InterruptedException |
                   ExecutionException jex)
            {
                // DEBUG
                (jex.getCause()).printStackTrace(System.err);

                fail(
                    "Concurrent task failed with exception " +
                    jex.getCause());
            }
        }

        // Inspect private EfsActivator.mAgents map using
        // reflection.
        final Field agentsField =
            EfsActivator.class.getDeclaredField("mAgents");
        final Map<String, ?> agentsMap;

        agentsField.setAccessible(true);
        agentsMap =
            (Map<String, ?>) agentsField.get(mActivator);

        // Agents map must contain a single entry for
        // activator agent.
        assertThat(agentsMap).isNotNull();
        assertThat(agentsMap).containsKey(AGENT_NAME);
        assertThat(agentsMap).hasSize(1);

        final Object agentInfo = agentsMap.get(AGENT_NAME);

        assertThat(agentInfo).isNotNull();

        final Field agentField =
            (agentInfo.getClass()).getDeclaredField("mAgent");
        final IEfsActivateAgent storedAgent;

        agentField.setAccessible(true);
        storedAgent =
            (IEfsActivateAgent) agentField.get(agentInfo);

        assertThat(storedAgent.name()).isEqualTo(AGENT_NAME);
    } // end of concurrentFindAgentTest()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------
} // end of class ConcurrentActivationTest