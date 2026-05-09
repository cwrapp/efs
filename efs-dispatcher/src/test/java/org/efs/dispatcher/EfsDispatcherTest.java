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
import java.util.List;
import java.util.function.Consumer;
import net.sf.eBus.util.ValidationException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.efs.dispatcher.EfsDispatcher.DispatcherType;
import org.efs.dispatcher.config.EfsDispatcherConfig;
import org.efs.dispatcher.config.ThreadAffinityConfig;
import org.efs.dispatcher.config.ThreadAffinityConfig.AffinityType;
import org.efs.dispatcher.config.ThreadType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests {@code EfsDispatcher} construction and start up.
 *
 * @author charlesr
 */

@ExtendWith (MockitoExtension.class)
public final class EfsDispatcherTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String EFS_AGENT_NAME = "test-agent-0";
    private static final String EFS_AGENT_NOT_REGISTERED_NAME =
        "test-agent-1";
    private static final String DISPATCHER_NAME_PREFIX =
        "test-dispatcher-";

    //-----------------------------------------------------------
    // Statics.
    //

    private static IEfsAgent sEfsAgent;
    private static IEfsAgent sEfsAgentEmptyName;
    private static IEfsAgent sEfsAgentBlankName;
    private static IEfsAgent sEfsAgentNotRegistered;
    private static int sDispatcherIndex = 0;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void testClassSetup()
    {
        sEfsAgent = mock(IEfsAgent.class);
        when(sEfsAgent.name()).thenReturn(EFS_AGENT_NAME);

        sEfsAgentEmptyName = mock(IEfsAgent.class);
        when(sEfsAgentEmptyName.name()).thenReturn("");

        sEfsAgentBlankName = mock(IEfsAgent.class);
        when(sEfsAgentEmptyName.name()).thenReturn("   ");

        sEfsAgentNotRegistered = mock(IEfsAgent.class);
        when(sEfsAgentNotRegistered.name())
            .thenReturn(EFS_AGENT_NOT_REGISTERED_NAME);
    } // end of testClassSetup()

    @AfterEach
    public void testCleanUp()
    {
        EfsDispatcher.clearDispatchers();
    } // end of testCleanUp()

    //
    // end of JUnit Test Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Test Methods.
    //

    @Test
    public void builderNullDispatcherName()
    {
        final String dispatcherName = null;

        assertThatThrownBy(
            () -> EfsDispatcher.builder(dispatcherName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsDispatcher.INVALID_NAME);
    } // end of builderNullDispatcherName()

    @Test
    public void builderEmptyDispatcherName()
    {
        final String dispatcherName = "";


        assertThatThrownBy(
            () -> EfsDispatcher.builder(dispatcherName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsDispatcher.INVALID_NAME);
    } // end of builderEmptyDispatcherName()

    @Test
    public void builderBlankDispatcherName()
    {
        final String dispatcherName = "   ";

        assertThatThrownBy(
            () -> EfsDispatcher.builder(dispatcherName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsDispatcher.INVALID_NAME);
    } // end of builderBlankDispatcherName()

    @Test
    public void builderZeroThreadCount()
    {
        final String dispatcherName = generateDispatcherName();
        final int numThreads = 0;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        assertThatThrownBy(
            () -> builder.numThreads(numThreads))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsDispatcher.INVALID_NUM_THREADS);
    } // end of builderZeroThreadCount()

    @Test
    public void builderNullThreadType()
    {
        final String dispatcherName = generateDispatcherName();
        final ThreadType type = null;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        assertThatThrownBy(
            () -> builder.threadType(type))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsDispatcher.NULL_TYPE);
    } // end of builderNullThreadType()

    @Test
    public void builderPiorityLessThanMin()
    {
        final String dispatcherName = generateDispatcherName();
        final int priority = (Thread.MIN_PRIORITY - 1);
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        assertThatThrownBy(
            () -> builder.priority(priority))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsDispatcher.INVALID_PRIORITY);
    } // end of builderPiorityLessThanMin()

    @Test
    public void builderPriorityGreaterThanMax()
    {
        final String dispatcherName = generateDispatcherName();
        final int priority = (Thread.MAX_PRIORITY + 1);
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        assertThatThrownBy(
            () -> builder.priority(priority))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsDispatcher.INVALID_PRIORITY);
    } // end of builderPriorityGreaterThanMax()

    @Test
    public void builderZeroSpinLimit()
    {
        final String dispatcherName = generateDispatcherName();
        final long limit = 0L;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        assertThatThrownBy(
            () -> builder.spinLimit(limit))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsDispatcher.INVALID_SPIN_LIMIT);
    } // end of builderZeroSpinLimit()

    @Test
    public void builderNullParkTime()
    {
        final String dispatcherName = generateDispatcherName();
        final Duration time = null;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        assertThatThrownBy(
            () -> builder.parkTime(time))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsDispatcher.NULL_TIME);
    } // end of builderNullParkTime()

    @Test
    public void builderNegativeParkTime()
    {
        final String dispatcherName = generateDispatcherName();
        final Duration time = Duration.ZERO;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        assertThatThrownBy(
            () -> builder.parkTime(time))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsDispatcher.INVALID_TIME);
    } // end of builderNegativeParkTime()

    @Test
    public void builderNullDispatcherType()
    {
        final String dispatcherName = generateDispatcherName();
        final DispatcherType type = null;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        assertThatThrownBy(
            () -> builder.dispatcherType(type))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsDispatcher.NULL_TYPE);
    } // end of builderNullDispatcherType()

    @Test
    public void builderNegativeEventQueueCapacity()
    {
        final String dispatcherName = generateDispatcherName();
        final int capacity = -1;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        assertThatThrownBy(
            () -> builder.eventQueueCapacity(capacity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                EfsDispatcher.INVALID_EVENT_QUEUE_CAPACITY);
    } // end of builderNegativeEventQueueCapacity()

    @Test
    public void builderZeroRunQueueCapacity()
    {
        final String dispatcherName = generateDispatcherName();
        final int capacity = 0;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        assertThatThrownBy(
            () -> builder.runQueueCapacity(capacity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                EfsDispatcher.INVALID_RUN_QUEUE_CAPACITY);
    } // end of builderZeroRunQueueCapacity()

    @Test
    public void builderZeroMaxEvents()
    {
        final String dispatcherName = generateDispatcherName();
        final int maxEvents = 0;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        assertThatThrownBy(
            () -> builder.maxEvents(maxEvents))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsDispatcher.INVALID_MAX_EVENTS);
    } // end of builderZeroMaxEvents()

    @Test
    public void builderNullDispatcher()
    {
        final String dispatcherName = generateDispatcherName();
        final Consumer<Runnable> dispatcher = null;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        assertThatThrownBy(
            () -> builder.dispatcher(dispatcher))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsDispatcher.NULL_DISPATCHER);
    } // end of builderNullDispatcher()

    @Test
    public void builderInvalidSettings()
    {
        final String dispatcherName = generateDispatcherName();
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        assertThatThrownBy(
            () -> builder.dispatcherType(DispatcherType.EFS)
                         .build())
            .isInstanceOf(ValidationException.class);
    } // end of builderInvalidSettings()

    @Test
    public void builderSuccess()
    {
        final String dispatcherName = generateDispatcherName();
        final int numThreads = 8;
        final ThreadType threadType = ThreadType.SPINPARK;
        final int priority = 8;
        final long spinLimit = 2_500_000L;
        final Duration parkTime = Duration.ofNanos(500L);
        final DispatcherType dispatcherType = DispatcherType.EFS;
        final int eventQueueCapacity = 128;
        final int runQueueCapacity = 32;
        final int maxEvents = eventQueueCapacity;
        final ThreadAffinityConfig affinityConfig =
            createAffinityConfig();
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);
        final List<EfsAgent.AgentStats> runStats;
        final EfsDispatcher dispatcher =
            (EfsDispatcher)
                builder.numThreads(numThreads)
                       .threadType(threadType)
                       .priority(priority)
                       .spinLimit(spinLimit)
                       .parkTime(parkTime)
                       .dispatcherType(dispatcherType)
                       .eventQueueCapacity(eventQueueCapacity)
                       .runQueueCapacity(runQueueCapacity)
                       .maxEvents(maxEvents)
                       .threadAffinity(affinityConfig)
                       .build();

        assertThat(dispatcher.dispatcherState())
            .isEqualTo(EfsDispatcher.DispatcherState.STARTED);
        assertThat(dispatcher.threadType())
            .isEqualTo(threadType);
        assertThat(dispatcher.priority()).isEqualTo(priority);
        assertThat(dispatcher.toString())
            .startsWith("[" + dispatcherName);

        assertThat(EfsDispatcher.isDispatcher(dispatcherName))
            .isTrue();
        assertThat(EfsDispatcher.getDispatcher(dispatcherName))
            .isSameAs(dispatcher);

        assertThatThrownBy(
            () -> EfsDispatcher.register(sEfsAgentEmptyName,
                                         dispatcherName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsDispatcher.MISSING_AGENT_NAME);
        assertThatThrownBy(
            () -> EfsDispatcher.register(sEfsAgentBlankName,
                                         dispatcherName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsDispatcher.MISSING_AGENT_NAME);
        assertThatThrownBy(
            () -> EfsDispatcher.register(sEfsAgent, ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsDispatcher.INVALID_NAME);
        assertThatThrownBy(
            () -> EfsDispatcher.register(sEfsAgent, "fubar"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("unknown dispatcher \"fubar\"");

        EfsDispatcher.clearAgents();
        EfsDispatcher.register(sEfsAgent, dispatcherName);

        assertThat(EfsDispatcher.isRegistered(sEfsAgent))
            .isTrue();
        assertThat(EfsDispatcher.agent("fubar")).isNull();
        assertThat(EfsDispatcher.agent(EFS_AGENT_NAME))
            .isEqualTo(sEfsAgent);

        assertThatThrownBy(
            () -> EfsDispatcher.register(sEfsAgent,
                                         dispatcherName))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                String.format(
                    "efs agent \"%s\" currently registered",
                    EFS_AGENT_NAME));
        assertThatThrownBy(
            () -> EfsDispatcher.dispatcher(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsDispatcher.NULL_AGENT);
        assertThat(EfsDispatcher.dispatcher(sEfsAgent))
            .isEqualTo(dispatcherName);
        assertThatThrownBy(
            () -> EfsDispatcher.dispatch(null, sEfsAgent))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsDispatcher.NULL_TASK);
        assertThatThrownBy(
            () ->
                EfsDispatcher.dispatch(
                    () -> System.out.println("Do it!"), null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsDispatcher.NULL_AGENT);

        assertThatThrownBy(
            () ->
                EfsDispatcher.dispatch(
                    () -> System.out.println("Do it!"),
                    sEfsAgentNotRegistered))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                String.format(
                    "efs agent %s not registered",
                    EFS_AGENT_NOT_REGISTERED_NAME));

        EfsDispatcher.dispatch(
            () -> System.out.println("Do it!"), sEfsAgent);

        runStats = EfsAgent.runTimeStats();

        assertThat(runStats).isNotNull();
        assertThat(runStats).isNotEmpty();

        EfsDispatcher.deregister(sEfsAgent);

        assertThat(EfsDispatcher.isRegistered(sEfsAgent))
            .isFalse();
    } // end of builderSuccess()

    @Test
    public void builderDuplicateDispatcher()
    {
        final String dispatcherName = generateDispatcherName();
        final int numThreads = 8;
        final ThreadType threadType = ThreadType.SPINPARK;
        final int priority = 8;
        final long spinLimit = 2_500_000L;
        final Duration parkTime = Duration.ofNanos(500L);
        final DispatcherType dispatcherType = DispatcherType.EFS;
        final int eventQueueCapacity = 128;
        final int runQueueCapacity = 32;
        final int maxEvents = eventQueueCapacity;
        final ThreadAffinityConfig affinityConfig =
            createAffinityConfig();
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        builder.numThreads(numThreads)
               .threadType(threadType)
               .priority(priority)
               .spinLimit(spinLimit)
               .parkTime(parkTime)
               .dispatcherType(dispatcherType)
               .eventQueueCapacity(eventQueueCapacity)
               .runQueueCapacity(runQueueCapacity)
               .maxEvents(maxEvents)
               .threadAffinity(affinityConfig)
               .build();

        // Create a duplicate dispatcher by building a second
        // time.
        assertThatThrownBy(
            () -> builder.build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("dispatcher " +
                        dispatcherName +
                        " already exists");
    } // end of builderDuplicateDispatcher()

    @Test
    public void builderSuccessSpecial()
    {
        final String dispatcherName = "SwingDispatcher";
        final int maxEvents = 8;
        final DispatcherType dispatcherType =
            DispatcherType.SPECIAL;
        final Consumer<Runnable> eventDispatcher =
            javax.swing.SwingUtilities::invokeLater;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        builder.dispatcherType(DispatcherType.SPECIAL)
               .dispatcher(eventDispatcher)
               .maxEvents(maxEvents)
               .build();

        assertThat(dispatcherType.isSpecial()).isTrue();
    } // end of builderSuccessSpecial()

    @Test
    public void builderUsingConfig()
        throws ClassNotFoundException
    {
        final String dispatcherName = generateDispatcherName();
        final int numThreads = 8;
        final ThreadType threadType = ThreadType.SPINPARK;
        final int priority = 8;
        final long spinLimit = 2_500_000L;
        final Duration parkTime = Duration.ofNanos(500L);
        final int eventQueueCapacity = 128;
        final int runQueueCapacity = 32;
        final int maxEvents = eventQueueCapacity;
        final ThreadAffinityConfig affinityConfig =
            createAffinityConfig();
        final EfsDispatcherConfig config =
            new EfsDispatcherConfig();
        final EfsDispatcher.Builder builder;

        config.setDispatcherName(dispatcherName);
        config.setThreadType(threadType);
        config.setNumThreads(numThreads);
        config.setPriority(priority);
        config.setSpinLimit(spinLimit);
        config.setParkTime(parkTime);
        config.setEventQueueCapacity(eventQueueCapacity);
        config.setRunQueueCapacity(runQueueCapacity);
        config.setMaxEvents(maxEvents);
        config.setAffinity(affinityConfig);

        builder =
            EfsDispatcher.builder(config.getDispatcherName());
        builder.set(config).build();
    } // end of builderUsingConfig()

    @Test
    public void getDispatcherNullName()
    {
        final String dispatcherName = null;

        assertThatThrownBy(
            () -> EfsDispatcher.getDispatcher(dispatcherName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsDispatcher.INVALID_NAME);
    } // end of getDispatcherNullName()

    @Test
    public void getDispatcherEmptyName()
    {
        final String dispatcherName = "";

        assertThatThrownBy(
            () -> EfsDispatcher.getDispatcher(dispatcherName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsDispatcher.INVALID_NAME);
    } // end of getDispatcherEmptyName()

    @Test
    public void isDispatcherNullName()
    {
        final String dispatcherName = null;

        assertThatThrownBy(
            () -> EfsDispatcher.isDispatcher(dispatcherName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsDispatcher.INVALID_NAME);
    } // end of isDispatcherNullName()

    @Test
    public void isDispatcherEmptyName()
    {
        final String dispatcherName = "";

        assertThatThrownBy(
            () -> EfsDispatcher.isDispatcher(dispatcherName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(EfsDispatcher.INVALID_NAME);
    } // end of isDispatcherEmptyName()

    //
    // end of JUnit Test Methods.
    //-----------------------------------------------------------

    private static String generateDispatcherName()
    {
        return (DISPATCHER_NAME_PREFIX + sDispatcherIndex++);
    } // end of generateDispatcherName()

    private static ThreadAffinityConfig createAffinityConfig()
    {
        final ThreadAffinityConfig retval =
            new ThreadAffinityConfig();

        retval.setAffinityType(AffinityType.CPU_ID);
        retval.setCpuId(7);
        retval.setBindFlag(true);
        retval.setWholeCoreFlag(true);

        return (retval);
    } // end of createAffinityConfig()
} // end of class EfsDispatcherTest
