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
package org.efs.dispatcher.config;

import com.typesafe.config.ConfigException;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.config.ThreadAffinityConfig.AffinityType;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@code EfsDispatcherConfig} typesafe bean.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class EfsDispatcherConfigTest
{
//---------------------------------------------------------------
// Member data.
//

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Test Methods.
    //

    @Test
    public void nullDispatcherName()
    {
        final String dispatcherName = null;
        final EfsDispatcherConfig config =
            new EfsDispatcherConfig();

        try
        {
            config.setDispatcherName(dispatcherName);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("name is null or an empty string");
        }
    } // end of nullDispatcherName()

    @Test
    public void emptyDispatcherName()
    {
        final String dispatcherName = "";
        final EfsDispatcherConfig config =
            new EfsDispatcherConfig();

        try
        {
            config.setDispatcherName(dispatcherName);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("name is null or an empty string");
        }
    } // end of emptyDispatcherName()

    @Test
    public void nullThreadType()
    {
        final ThreadType type = null;
        final EfsDispatcherConfig config =
            new EfsDispatcherConfig();

        try
        {
            config.setThreadType(type);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("type is null");
        }
    } // end of nullThreadType()

    @Test
    public void zeroThreadCount()
    {
        final int numThreads = 0;
        final EfsDispatcherConfig config =
            new EfsDispatcherConfig();

        try
        {
            config.setNumThreads(numThreads);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("numThreads <= zero");
        }
    } // end of zeroThreadCount()

    @Test
    public void priorityTooSmall()
    {
        final int priority = (Thread.MIN_PRIORITY - 1);
        final EfsDispatcherConfig config =
            new EfsDispatcherConfig();

        try
        {
            config.setPriority(priority);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("priority out of bounds");
        }
    } // end of priorityTooSmall()

    @Test
    public void priorityTooBig()
    {
        final int priority = (Thread.MAX_PRIORITY + 1);
        final EfsDispatcherConfig config =
            new EfsDispatcherConfig();

        try
        {
            config.setPriority(priority);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("priority out of bounds");
        }
    } // end of priorityTooBig()

    @Test
    public void zeroSpinLimit()
    {
        final long spinLimit = 0L;
        final EfsDispatcherConfig config =
            new EfsDispatcherConfig();

        try
        {
            config.setSpinLimit(spinLimit);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("spinLimit <= zero");
        }
    } // end of zeroSpinLimit()

    @Test
    public void nullParkTime()
    {
        final Duration parkTime = null;
        final EfsDispatcherConfig config =
            new EfsDispatcherConfig();

        try
        {
            config.setParkTime(parkTime);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("parkTime is null");
        }
    } // end of nullParkTime()

    @Test
    public void zeroParkTime()
    {
        final Duration parkTime = Duration.ZERO;
        final EfsDispatcherConfig config =
            new EfsDispatcherConfig();

        try
        {
            config.setParkTime(parkTime);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("parkTime <= zero");
        }
    } // end of zeroParkTime()

    @Test
    public void eventQueueCapacityTooSmall()
    {
        final int capacity =
            (EfsDispatcher.MIN_EVENT_QUEUE_SIZE - 1);
        final EfsDispatcherConfig config =
            new EfsDispatcherConfig();

        try
        {
            config.setEventQueueCapacity(capacity);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("capacity < " +
                          EfsDispatcher.MIN_EVENT_QUEUE_SIZE);
        }
    } // end of eventQueueCapacityTooSmall()

    @Test
    public void zeroRunQueueCapacity()
    {
        final int capacity = 0;
        final EfsDispatcherConfig config =
            new EfsDispatcherConfig();

        try
        {
            config.setRunQueueCapacity(capacity);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("capacity <= zero");
        }
    } // end of zeroRunQueueCapacity()

    @Test
    public void zeroMaxEvents()
    {
        final int maxEvents = 0;
        final EfsDispatcherConfig config =
            new EfsDispatcherConfig();

        try
        {
            config.setMaxEvents(maxEvents);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("maxEvents <= zero");
        }
    } // end of zeroMaxEvents()

    @Test
    public void configSuccess()
    {
        final String dispatcherName = "TestDispatcher";
        final ThreadType threadType = ThreadType.SPINPARK;
        final int numThreads = 2;
        final int priority = Thread.MAX_PRIORITY;
        final long spinLimit = 2_000_000L;
        final Duration parkTime = Duration.ofNanos(500L);
        final int eventQueueCapacity = 128;
        final int runQueueCapacity = 8;
        final int maxEvents = 8;
        final ThreadAffinityConfig affinityConfig =
            createAffinityConfig();
        final EfsDispatcherConfig config =
            new EfsDispatcherConfig();

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

        assertThat(config.getDispatcherName())
            .isEqualTo(dispatcherName);
        assertThat(config.getThreadType()).isEqualTo(threadType);
        assertThat(config.getNumThreads()).isEqualTo(numThreads);
        assertThat(config.getPriority()).isEqualTo(priority);
        assertThat(config.getSpinLimit()).isEqualTo(spinLimit);
        assertThat(config.getParkTime()).isEqualTo(parkTime);
        assertThat(config.getEventQueueCapacity())
            .isEqualTo(eventQueueCapacity);
        assertThat(config.getRunQueueCapacity())
            .isEqualTo(runQueueCapacity);
        assertThat(config.getMaxEvents()).isEqualTo(maxEvents);
        assertThat(config.getAffinity())
            .isSameAs(affinityConfig);
        assertThat(config.toString()).isNotEmpty();
    } // end of configSuccess()

    @Test
    public void findThreadTypeFail()
    {
        final String name = "fubar";
        final ThreadType threadType = ThreadType.find(name);

        assertThat(threadType).isNull();
    } // end of findThreadTypeFail()

    @Test
    public void findThreadTypeSuccess()
    {
        final String name = "spin+park";
        final ThreadType threadType = ThreadType.find(name);

        assertThat(threadType).isNotNull();
        assertThat(threadType).isEqualTo(ThreadType.SPINPARK);
        assertThat(threadType.textName()).isEqualTo(name);
    } // end of findThreadTypeSuccess()

    //
    // end of JUnit Test Methods.
    //-----------------------------------------------------------

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
} // end of class EfsDispatcherConfigTest
