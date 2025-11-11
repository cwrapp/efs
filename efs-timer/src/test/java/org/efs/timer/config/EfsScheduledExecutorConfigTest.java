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
package org.efs.timer.config;

import com.typesafe.config.ConfigException;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.dispatcher.config.ThreadType;
import org.junit.jupiter.api.Test;

/**
 *
 * @author charlesr
 */
public final class EfsScheduledExecutorConfigTest
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
    public void nullExecutorName()
    {
        final String executorName = null;
        final EfsScheduledExecutorConfig config =
            new EfsScheduledExecutorConfig();

        try
        {
            config.setExecutorName(executorName);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("name is null or an empty string");
        }
    } // end of nullExecutorName()

    @Test
    public void emptyExecutorName()
    {
        final String executorName = "";
        final EfsScheduledExecutorConfig config =
            new EfsScheduledExecutorConfig();

        try
        {
            config.setExecutorName(executorName);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("name is null or an empty string");
        }
    } // end of emptyExecutorName()

    @Test
    public void nullThreadType()
    {
        final ThreadType type = null;
        final EfsScheduledExecutorConfig config =
            new EfsScheduledExecutorConfig();

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
    public void priorityTooSmall()
    {
        final int priority = (Thread.MIN_PRIORITY - 1);
        final EfsScheduledExecutorConfig config =
            new EfsScheduledExecutorConfig();

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
        final EfsScheduledExecutorConfig config =
            new EfsScheduledExecutorConfig();

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
        final EfsScheduledExecutorConfig config =
            new EfsScheduledExecutorConfig();

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
        final EfsScheduledExecutorConfig config =
            new EfsScheduledExecutorConfig();

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
        final EfsScheduledExecutorConfig config =
            new EfsScheduledExecutorConfig();

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
    public void configSuccess()
    {
        final String executorName = "TestExecutor";
        final ThreadType threadType = ThreadType.SPINPARK;
        final int priority = Thread.MAX_PRIORITY;
        final long spinLimit = 2_000_000L;
        final Duration parkTime = Duration.ofNanos(500L);
        final EfsScheduledExecutorConfig config =
            new EfsScheduledExecutorConfig();

        config.setExecutorName(executorName);
        config.setThreadType(threadType);
        config.setPriority(priority);
        config.setSpinLimit(spinLimit);
        config.setParkTime(parkTime);

        assertThat(config.getExecutorName())
            .isEqualTo(executorName);
        assertThat(config.getThreadType()).isEqualTo(threadType);
        assertThat(config.getPriority()).isEqualTo(priority);
        assertThat(config.getSpinLimit()).isEqualTo(spinLimit);
        assertThat(config.getParkTime()).isEqualTo(parkTime);
        assertThat(config.toString()).isNotEmpty();
    } // end of configSuccess()

    //
    // end of JUnit Test Methods.
    //-----------------------------------------------------------
} // end of class EfsScheduledExecutorConfigTest
