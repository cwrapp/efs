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

        try
        {
            EfsDispatcher.builder(dispatcherName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(EfsDispatcher.INVALID_NAME);
        }
    } // end of builderNullDispatcherName()

    @Test
    public void builderEmptyDispatcherName()
    {
        final String dispatcherName = "";

        try
        {
            EfsDispatcher.builder(dispatcherName);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(jex)
                .hasMessage(EfsDispatcher.INVALID_NAME);
        }
    } // end of builderEmptyDispatcherName()

    @Test
    public void builderZeroThreadCount()
    {
        final String dispatcherName = generateDispatcherName();
        final int numThreads = 0;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        try
        {
            builder.numThreads(numThreads);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(jex)
                .hasMessage("numThreads <= zero");
        }
    } // end of builderZeroThreadCount()

    @Test
    public void builderNullThreadType()
    {
        final String dispatcherName = generateDispatcherName();
        final ThreadType type = null;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        try
        {
            builder.threadType(type);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(NullPointerException.class);
            assertThat(jex).hasMessage("type is null");
        }
    } // end of builderNullThreadType()

    @Test
    public void builderPiorityLessThanMin()
    {
        final String dispatcherName = generateDispatcherName();
        final int priority = (Thread.MIN_PRIORITY - 1);
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        try
        {
            builder.priority(priority);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(jex).hasMessage("priority out of bounds");
        }
    } // end of builderPiorityLessThanMin()

    @Test
    public void builderPriorityGreaterThanMax()
    {
        final String dispatcherName = generateDispatcherName();
        final int priority = (Thread.MAX_PRIORITY + 1);
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        try
        {
            builder.priority(priority);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(jex).hasMessage("priority out of bounds");
        }
    } // end of builderPriorityGreaterThanMax()

    @Test
    public void builderZeroSpinLimit()
    {
        final String dispatcherName = generateDispatcherName();
        final long limit = 0L;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        try
        {
            builder.spinLimit(limit);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(jex).hasMessage("limit <= zero");
        }
    } // end of builderZeroSpinLimit()

    @Test
    public void builderNullParkTime()
    {
        final String dispatcherName = generateDispatcherName();
        final Duration time = null;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        try
        {
            builder.parkTime(time);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(NullPointerException.class);
            assertThat(jex).hasMessage("time is null");
        }
    } // end of builderNullParkTime()

    @Test
    public void builderNegativeParkTime()
    {
        final String dispatcherName = generateDispatcherName();
        final Duration time = Duration.ZERO;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        try
        {
            builder.parkTime(time);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(jex).hasMessage("time <= zero");
        }
    } // end of builderNegativeParkTime()

    @Test
    public void builderNullDispatcherType()
    {
        final String dispatcherName = generateDispatcherName();
        final DispatcherType type = null;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        try
        {
            builder.dispatcherType(type);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(NullPointerException.class);
            assertThat(jex).hasMessage("type is null");
        }
    } // end of builderNullDispatcherType()

    @Test
    public void builderNegativeEventQueueCapacity()
    {
        final String dispatcherName = generateDispatcherName();
        final int capacity = -1;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        try
        {
            builder.eventQueueCapacity(capacity);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(jex).hasMessage("capacity < 2");
        }
    } // end of builderNegativeEventQueueCapacity()

    @Test
    public void builderZeroRunQueueCapacity()
    {
        final String dispatcherName = generateDispatcherName();
        final int capacity = 0;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        try
        {
            builder.runQueueCapacity(capacity);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(jex).hasMessage("capacity <= zero");
        }
    } // end of builderZeroRunQueueCapacity()

    @Test
    public void builderZeroMaxEvents()
    {
        final String dispatcherName = generateDispatcherName();
        final int maxEvents = 0;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        try
        {
            builder.maxEvents(maxEvents);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(jex).hasMessage("maxEvents <= zero");
        }
    } // end of builderZeroMaxEvents()

    @Test
    public void builderNullDispatcher()
    {
        final String dispatcherName = generateDispatcherName();
        final Consumer<Runnable> dispatcher = null;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        try
        {
            builder.dispatcher(dispatcher);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(NullPointerException.class);
            assertThat(jex).hasMessage("dispatcher is null");
        }
    } // end of builderNullDispatcher()

    @Test
    public void builderInvalidSettings()
    {
        final String dispatcherName = generateDispatcherName();
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(dispatcherName);

        try
        {
            builder.dispatcherType(DispatcherType.EFS).build();
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(ValidationException.class);
        }
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
        final List<EfsAgent.AgentStats> runStats =
            EfsAgent.runTimeStats();

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

        assertThat(runStats).isNotNull();
        assertThat(runStats).isNotEmpty();

        try
        {
            EfsDispatcher.register(
                sEfsAgentEmptyName, dispatcherName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(EfsDispatcher.MISSING_AGENT_NAME);
        }

        try
        {
            EfsDispatcher.register(sEfsAgent, "");
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(EfsDispatcher.INVALID_NAME);
        }

        try
        {
            EfsDispatcher.register(sEfsAgent, "fubar");
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage("unknown dispatcher \"fubar\"");
        }

        EfsDispatcher.clearAgents();
        EfsDispatcher.register(sEfsAgent, dispatcherName);

        assertThat(EfsDispatcher.isRegistered(sEfsAgent))
            .isTrue();
        assertThat(EfsDispatcher.agent("fubar")).isNull();
        assertThat(EfsDispatcher.agent(EFS_AGENT_NAME))
            .isEqualTo(sEfsAgent);

        try
        {
            EfsDispatcher.register(sEfsAgent, dispatcherName);
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex)
                .hasMessage(
                    String.format(
                        "efs agent \"%s\" currently registered",
                        EFS_AGENT_NAME));
        }

        try
        {
            EfsDispatcher.dispatcher(null);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(EfsDispatcher.NULL_AGENT);
        }

        assertThat(EfsDispatcher.dispatcher(sEfsAgent))
            .isEqualTo(dispatcherName);

        try
        {
            EfsDispatcher.dispatch(null, sEfsAgent);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(EfsDispatcher.NULL_TASK);
        }

        try
        {
            EfsDispatcher.dispatch(
                () -> System.out.println("Do it!"), null);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(EfsDispatcher.NULL_AGENT);
        }

        try
        {
            EfsDispatcher.dispatch(
                () -> System.out.println("Do it!"),
                sEfsAgentNotRegistered);
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex)
                .hasMessage(
                    String.format(
                        "efs agent %s not registered",
                        EFS_AGENT_NOT_REGISTERED_NAME));
        }

        EfsDispatcher.dispatch(
            () -> System.out.println("Do it!"), sEfsAgent);

        EfsDispatcher.deregister(null);

        assertThat(EfsDispatcher.isRegistered(sEfsAgent))
            .isTrue();

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

        try
        {
            EfsDispatcher.builder(dispatcherName);
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex).
                hasMessage(
                    "dispatcher " +
                    dispatcherName +
                    " already exists");
        }
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

        try
        {
            EfsDispatcher.getDispatcher(dispatcherName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage("name is null or an empty string");
        }
    } // end of getDispatcherNullName()

    @Test
    public void getDispatcherEmptyName()
    {
        final String dispatcherName = "";

        try
        {
            EfsDispatcher.getDispatcher(dispatcherName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage("name is null or an empty string");
        }
    } // end of getDispatcherEmptyName()

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
