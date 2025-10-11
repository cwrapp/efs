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

import com.google.common.collect.ImmutableList;
import com.typesafe.config.ConfigException;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.dispatcher.config.ThreadType;
import org.efs.timer.EfsScheduledExecutor;
import org.junit.jupiter.api.Test;

/**
 *
 * @author charlesr
 */

public final class EfsScheduledExecutorsTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    //
    // Configuration file names.
    //

    private static final String NO_EXECUTORS_FILE_NAME =
        "./src/test/resources/no-executors.conf";
    private static final String DUPLICATE_EXECUTORS_FILE_NAME =
        "./src/test/resources/duplicate-executors.conf";
    private static final String ALL_EXECUTORS_FILE_NAME =
        "./src/test/resources/all-executors.conf";

    //-----------------------------------------------------------
    // Statics.
    //

    private static final List<String> sNoExecutors =
        ImmutableList.of();
    private static final List<String> sAllExecutors =
        ImmutableList.of("TestExecutor-0",
                         "TestExecutor-1",
                         "TestExecutor-2",
                         "TestExecutor-3",
                         "TestExecutor-4");

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void noExecutorsFile()
    {
        final String fn = NO_EXECUTORS_FILE_NAME;

        EfsScheduledExecutor.loadScheduledExecutorsConfig(fn);

        assertThat(EfsScheduledExecutor.executorNames())
            .containsAll(sNoExecutors);
    } // end of noExecutorsFile()

    @Test
    public void duplicateExecutors()
    {
        final String fn = DUPLICATE_EXECUTORS_FILE_NAME;

        try
        {
            EfsScheduledExecutor.loadScheduledExecutorsConfig(fn);
        }
        catch (ConfigException configex)
        {
            assertThat(configex)
                .isInstanceOf(ConfigException.class);
        }
    } // end of duplicateExecutors()

    @Test
    public void executorsFileSuccesslLoad()
    {
        final String fn = ALL_EXECUTORS_FILE_NAME;

        EfsScheduledExecutor.loadScheduledExecutorsConfig(fn);

        assertThat(EfsScheduledExecutor.executorNames())
            .containsAll(sAllExecutors);
    } // end of executorsFileSuccesslLoad()

    @Test
    public void createExecutorsConfig()
    {
        final String executorName = "test-executor";
        final ThreadType threadType = ThreadType.SPINPARK;
        final int priority = Thread.MAX_PRIORITY;
        final long spinLimit = 2_000_000L;
        final Duration parkTime = Duration.ofNanos(500L);
        final EfsScheduledExecutorConfig executorConfig =
            createExecutorConfig(executorName,
                                 threadType,
                                 priority,
                                 spinLimit,
                                 parkTime);
        final String text =
            "[executors={\n" + executorConfig + "}";
        final List<EfsScheduledExecutorConfig> executors =
            ImmutableList.of(executorConfig);
        final EfsScheduledExecutorsConfig config =
            new EfsScheduledExecutorsConfig();

        config.setExecutors(executors);

        assertThat(config.getExecutors()).containsAll(executors);
        assertThat(config.toString()).isEqualTo(text);
    } // end of createExecutorsConfig()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private static EfsScheduledExecutorConfig createExecutorConfig(final String name,
                                                                   final ThreadType threadType,
                                                                   final int priority,
                                                                   final long spinLimit,
                                                                   final Duration parkTime)
    {
        final EfsScheduledExecutorConfig retval =
            new EfsScheduledExecutorConfig();

        retval.setExecutorName(name);
        retval.setThreadType(threadType);
        retval.setPriority(priority);
        retval.setSpinLimit(spinLimit);
        retval.setParkTime(parkTime);

        return (retval);
    } // end of createExecutorConfig(...)
} // end of class EfsScheduledExecutorsTest
