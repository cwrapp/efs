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
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import net.sf.eBus.util.ValidationException;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.dispatcher.EfsDispatcherThread.DispatcherThreadState;
import org.efs.dispatcher.EfsDispatcherThread.DispatcherThreadStats;
import org.efs.dispatcher.config.ThreadType;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests building dispatcher threads.
 *
 * @author charlesr
 */

public class EfsDispatcherThreadTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String TEST_THREAD_NAME = "abc-123";
    private static final int TEST_THREAD_PRIORITY =
        Thread.MAX_PRIORITY;
    private static final int TEST_MAX_EVENTS = 8;
    private static final long TEST_SPIN_LIMIT = 2_500_000L;
    private static final Duration TEST_PARK_TIME =
        Duration.ofNanos(250L);
    private static final int TEST_RUN_QUEUE_CAPACITY = 64;

    //-----------------------------------------------------------
    // Statics.
    //

    private static Queue<EfsAgent> sBlockingQueue;
    private static Queue<EfsAgent> sNonBlockingQueue;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void setUpClass()
    {
        sBlockingQueue =
            new LinkedBlockingQueue<>(TEST_RUN_QUEUE_CAPACITY);
        sNonBlockingQueue =
            new MpmcAtomicArrayQueue<>(TEST_RUN_QUEUE_CAPACITY);
    } // end of setUpClass()

    //
    // end of JUnit Test Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Test Methods.
    //

    @Test
    public void builderNullThreadName()
    {
        final String threadName = null;
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

        try
        {
            builder.threadName(threadName);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(jex)
                .hasMessage("threadName is either null or an empty string");
        }
    } // end of builderNullThreadName()

    @Test
    public void builderEmptyThreadName()
    {
        final String threadName = "";
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

        try
        {
            builder.threadName(threadName);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(jex)
                .hasMessage("threadName is either null or an empty string");
        }
    } // end of builderEmptyThreadName()

    @Test
    public void builderNullThreadType()
    {
        final ThreadType threadType = null;
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

        try
        {
            builder.threadType(threadType);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(NullPointerException.class);
            assertThat(jex).hasMessage("threadType is null");
        }
    } // end of builderNullThreadType()

    @Test
    public void builderPriorityLessThanMin()
    {
        final int priority = (Thread.MIN_PRIORITY - 1);
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

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
    } // end of builderPriorityLessThanMin()

    @Test
    public void builderPriorityGreaterThanMax()
    {
        final int priority = (Thread.MAX_PRIORITY + 1);
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

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
    public void builderNegativeSpinLimit()
    {
        final long spinLimit = -1L;
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

        try
        {
            builder.spinLimit(spinLimit);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(jex).hasMessage("limit < zero");
        }
    } // end of builderNegativeSpinLimit()

    @Test
    public void builderNullParkTime()
    {
        final Duration parkTime = null;
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

        try
        {
            builder.parkTime(parkTime);
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
        final Duration parkTime = Duration.ofNanos(-1L);
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

        try
        {
            builder.parkTime(parkTime);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(jex).hasMessage("time < zero");
        }
    } // end of builderNegativeParkTime()

    @Test
    public void builderZeroMaxEvents()
    {
        final int maxEvents = 0;
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

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
    public void builderNullRunQueue()
    {
        final Queue<EfsAgent> runQueue = null;
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

        try
        {
            builder.runQueue(runQueue);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(NullPointerException.class);
            assertThat(jex).hasMessage("queue is null");
        }
    } // end of builderNullRunQueue()

    @Test
    public void builderThreadTypeNotSet()
    {
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

        try
        {
            builder.build();
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(ValidationException.class);
            assertThat(jex)
                .hasMessageContainingAll(
                    "threadName: not set",
                    "threadType: not set",
                    "maxEvents: not set");
        }
    } // end of builderThreadTypeNotSet()

    @Test
    public void builderInvalidBlockingSettings()
    {
        final String threadName = TEST_THREAD_NAME;
        final ThreadType threadType = ThreadType.BLOCKING;
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

        try
        {
            builder.threadName(threadName)
                   .threadType(threadType)
                   .build();
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(ValidationException.class);
            assertThat(jex)
                .hasMessageContainingAll(
                    "maxEvents: not set",
                    "runQueue: not set");
        }
    } // end of builderInvalidBlockingSettings()

    @Test
    public void builderBlockingRunQueueMismatch()
    {
        final String threadName = TEST_THREAD_NAME;
        final ThreadType threadType = ThreadType.BLOCKING;
        final int priority = TEST_THREAD_PRIORITY;
        final int maxEvents = TEST_MAX_EVENTS;
        final Queue<EfsAgent> runQueue = new LinkedList<>();
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

        try
        {
            builder.threadName(threadName)
                   .threadType(threadType)
                   .priority(priority)
                   .maxEvents(maxEvents)
                   .runQueue(runQueue)
                   .build();
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(ValidationException.class);
            assertThat(jex)
                .hasMessageContainingAll(
                    "runQueue: does not match thread type");
        }
    } // end of builderBlockingRunQueueMismatch()

    @Test
    public void builderInvalidSpinningSettings()
    {
        final String threadName = TEST_THREAD_NAME;
        final ThreadType threadType = ThreadType.SPINNING;
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

        try
        {
            builder.threadName(threadName)
                   .threadType(threadType)
                   .build();
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(ValidationException.class);
            assertThat(jex)
                .hasMessageContainingAll(
                    "maxEvents: not set",
                    "runQueue: not set");
        }
    } // end of builderInvalidSpinningSettings()

    @Test
    public void builderInvalidSpinYieldSettings()
    {
        final String threadName = "abc-123";
        final ThreadType threadType = ThreadType.SPINYIELD;
        final int maxEvents = TEST_MAX_EVENTS;
        final Queue<EfsAgent> runQueue = sBlockingQueue;
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

        try
        {
            builder.threadName(threadName)
                   .threadType(threadType)
                   .maxEvents(maxEvents)
                   .runQueue(runQueue)
                   .build();
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(ValidationException.class);
            assertThat(jex)
                .hasMessageContaining(
                    "spinLimit: not set for spin+park/spin+yield thread type");
        }
    } // end of builderInvalidSpinYieldSettings()

    @Test
    public void builderInvalidSpinParkSettings()
    {
        final String threadName = "abc-123";
        final ThreadType threadType = ThreadType.SPINPARK;
        final int maxEvents = TEST_MAX_EVENTS;
        final long spinLimit = TEST_SPIN_LIMIT;
        final Queue<EfsAgent> runQueue = sBlockingQueue;
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

        try
        {
            builder.threadName(threadName)
                   .threadType(threadType)
                   .maxEvents(maxEvents)
                   .runQueue(runQueue)
                   .spinLimit(spinLimit)
                   .build();
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(ValidationException.class);
            assertThat(jex)
                .hasMessageContaining(
                    "parkTime: not set for spin+park thread type");
        }
    } // end of builderInvalidSpinParkSettings()

    @Test
    public void builderBlockingThreadSuccess()
    {
        final String threadName = TEST_THREAD_NAME;
        final ThreadType threadType = ThreadType.BLOCKING;
        final int priority = TEST_THREAD_PRIORITY;
        final int maxEvents = TEST_MAX_EVENTS;
        final Queue<EfsAgent> runQueue = sBlockingQueue;
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();
        final EfsDispatcherThread dthread =
            builder.threadName(threadName)
                   .threadType(threadType)
                   .priority(priority)
                   .maxEvents(maxEvents)
                   .runQueue(runQueue)
                   .build();

        assertThat(dthread).isNotNull();
        assertThat(dthread.getName()).isEqualTo(threadName);
        assertThat(dthread.threadType()).isEqualTo(threadType);
        assertThat(dthread.getPriority()).isEqualTo(priority);
        assertThat(dthread.maxEvents()).isEqualTo(maxEvents);
        assertThat(dthread.affinity()).isNull();
        assertThat(dthread.isAlive()).isFalse();
        assertThat(dthread.isDaemon()).isTrue();
    } // end of builderBlockingThreadSuccess()

    @Test
    public void builderSpinningThreadSuccess()
    {
        final String threadName = TEST_THREAD_NAME;
        final ThreadType threadType = ThreadType.SPINNING;
        final int priority = TEST_THREAD_PRIORITY;
        final int maxEvents = TEST_MAX_EVENTS;
        final Queue<EfsAgent> runQueue = sNonBlockingQueue;
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();
        final EfsDispatcherThread dthread =
            builder.threadName(threadName)
                   .threadType(threadType)
                   .priority(priority)
                   .maxEvents(maxEvents)
                   .runQueue(runQueue)
                   .build();

        assertThat(dthread).isNotNull();
        assertThat(dthread.getName()).isEqualTo(threadName);
        assertThat(dthread.threadType()).isEqualTo(threadType);
        assertThat(dthread.getPriority()).isEqualTo(priority);
        assertThat(dthread.maxEvents()).isEqualTo(maxEvents);
        assertThat(dthread.affinity()).isNull();
        assertThat(dthread.isAlive()).isFalse();
        assertThat(dthread.isDaemon()).isTrue();
    } // end of builderSpinningThreadSuccess()

    @Test
    public void builderSpinYieldThreadSuccess()
    {
        final String threadName = TEST_THREAD_NAME;
        final ThreadType threadType = ThreadType.SPINYIELD;
        final int priority = TEST_THREAD_PRIORITY;
        final int maxEvents = TEST_MAX_EVENTS;
        final long spinLimit = TEST_SPIN_LIMIT;
        final Queue<EfsAgent> runQueue = sNonBlockingQueue;
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();
        final EfsDispatcherThread dthread =
            builder.threadName(threadName)
                   .threadType(threadType)
                   .priority(priority)
                   .maxEvents(maxEvents)
                   .runQueue(runQueue)
                   .spinLimit(spinLimit)
                   .build();

        assertThat(dthread).isNotNull();
        assertThat(dthread.getName()).isEqualTo(threadName);
        assertThat(dthread.threadType()).isEqualTo(threadType);
        assertThat(dthread.getPriority()).isEqualTo(priority);
        assertThat(dthread.maxEvents()).isEqualTo(maxEvents);
        assertThat(dthread.spinLimit()).isEqualTo(spinLimit);
        assertThat(dthread.affinity()).isNull();
        assertThat(dthread.isAlive()).isFalse();
        assertThat(dthread.isDaemon()).isTrue();
    } // end of builderSpinYieldThreadSuccess()

    @Test
    public void builderSpinParkThreadSuccess()
    {
        final String threadName = TEST_THREAD_NAME;
        final ThreadType threadType = ThreadType.SPINPARK;
        final int priority = TEST_THREAD_PRIORITY;
        final int maxEvents = TEST_MAX_EVENTS;
        final long spinLimit = TEST_SPIN_LIMIT;
        final Duration parkTime = TEST_PARK_TIME;
        final Queue<EfsAgent> runQueue = sNonBlockingQueue;
        final String text =
            String.format(
                "[%s, type=%s, state=%s, spin limit=%s, park time=%d]",
                threadName,
                threadType,
                DispatcherThreadState.NOT_STARTED,
                spinLimit,
                parkTime.toNanos());
        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();
        final EfsDispatcherThread dthread =
            builder.threadName(threadName)
                   .threadType(threadType)
                   .priority(priority)
                   .maxEvents(maxEvents)
                   .runQueue(runQueue)
                   .spinLimit(spinLimit)
                   .parkTime(parkTime)
                   .build();

        assertThat(dthread).isNotNull();
        assertThat(dthread.getName()).isEqualTo(threadName);
        assertThat(dthread.threadType()).isEqualTo(threadType);
        assertThat(dthread.getPriority()).isEqualTo(priority);
        assertThat(dthread.maxEvents()).isEqualTo(maxEvents);
        assertThat(dthread.spinLimit()).isEqualTo(spinLimit);
        assertThat(dthread.parkTime())
            .isEqualTo(parkTime.toNanos());
        assertThat(dthread.affinity()).isNull();
        assertThat(dthread.isAlive()).isFalse();
        assertThat(dthread.isDaemon()).isTrue();
        assertThat(dthread.toString()).isEqualTo(text);

        final DispatcherThreadStats threadStats =
            dthread.performanceStats();
        final String dispatcherText =
            """
            [thread=abc-123, start time=null, state=NOT_STARTED, agent=(idle), run count=0,
            (no agent statistics to report)
            (no agent statistics to report)
            0]""";

        assertThat(threadStats.threadName())
            .isEqualTo(threadName);
        assertThat(threadStats.agentRunCount()).isZero();
        assertThat(threadStats.agentName())
            .isEqualTo(EfsDispatcherThread.NO_EFS_AGENT);
        assertThat(threadStats.threadState())
            .isEqualTo(DispatcherThreadState.NOT_STARTED);
        assertThat(threadStats.toString())
            .isEqualTo(dispatcherText);
    } // end of builderSpinParkThreadSuccess()

    //
    // end of JUnit Test Methods.
    //-----------------------------------------------------------
} // end of class EfsDispatcherThreadTest
