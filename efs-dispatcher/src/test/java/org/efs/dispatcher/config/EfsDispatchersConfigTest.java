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

import com.google.common.collect.ImmutableList;
import com.typesafe.config.ConfigException;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.dispatcher.EfsDispatcher;
import org.junit.jupiter.api.Test;

/**
 * Exercises efs dispatcher configuration from files.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsDispatchersConfigTest
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

    private static final String NO_DISPATCHERS_FILE_NAME =
        "./src/test/resources/no-dispatchers.conf";
    private static final String IRREGULAR_DISPATCHERS_FILE_NAME =
        "/etc";
    private static final String UNREADABLE_DISPATCHERS_FILE_NAME =
        "/etc/sudoers";
    private static final String DUPLICATE_DISPATCHERS_FILE_NAME =
        "./src/test/resources/duplicate-dispatchers.conf";
    private static final String ALL_DISPATCHERS_FILE_NAME =
        "./src/test/resources/all-dispatchers.conf";

    //-----------------------------------------------------------
    // Statics.
    //

    private static final List<String> sAllDispatchers =
        ImmutableList.of("TestDispatcher-0",
                         "TestDispatcher-1",
                         "TestDispatcher-2",
                         "TestDispatcher-3");

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void nullNameDispatchersFile()
    {
        final String fileName = null;

        try
        {
            EfsDispatcher.loadDispatchersConfigFile(fileName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(
                    "fileName is either null or an empty string");
        }
    } // end of nullNameDispatchersFile()

    @Test
    public void emptyNameDispatchersFile()
    {
        final String fileName = "";

        try
        {
            EfsDispatcher.loadDispatchersConfigFile(fileName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(
                    "fileName is either null or an empty string");
        }
    } // end of emptyNameDispatchersFile()

    @Test
    public void noDispatchersFile()
    {
        final String fileName = NO_DISPATCHERS_FILE_NAME;

        try
        {
            EfsDispatcher.loadDispatchersConfigFile(fileName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(
                    "\"" + fileName + "\" does not exist");
        }
    } // end of noDispatchersFile()

    @Test
    public void irregularDispatchersFile()
    {
        final String fileName = IRREGULAR_DISPATCHERS_FILE_NAME;

        try
        {
            EfsDispatcher.loadDispatchersConfigFile(fileName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(
                    "\"" + fileName + "\" not regular file");
        }
    } // end of loadDispatcherConfigFileNotRegularFile()

    @Test
    public void unreadableDispatchersFile()
    {
        final String fileName = UNREADABLE_DISPATCHERS_FILE_NAME;

        try
        {
            EfsDispatcher.loadDispatchersConfigFile(fileName);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(
                    "\"" + fileName + "\" unreadable");
        }
    } // end of unreadableDispatchersFile()

    @Test
    public void duplicateDispatchersFile()
    {
        final String fileName = DUPLICATE_DISPATCHERS_FILE_NAME;

        try
        {
            EfsDispatcher.loadDispatchersConfigFile(fileName);
        }
        catch (Exception jex)
        {
            assertThat(jex)
                .isInstanceOf(ConfigException.class);
        }
    } // end of duplicateDispatchersFile()

    @Test
    public void dispatchersFileSuccessfulLoad()
    {
        final String fileName = ALL_DISPATCHERS_FILE_NAME;

        EfsDispatcher.loadDispatchersConfigFile(fileName);

        assertThat(EfsDispatcher.dispatcherNames())
            .containsAll(sAllDispatchers);
    } // end of dispatchersFileSuccessfulLoad()

    @Test
    public void createDispatchersConfig()
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
        final EfsDispatcherConfig dispatcherConfig =
            createDispatcher(dispatcherName,
                             threadType,
                             numThreads,
                             priority,
                             spinLimit,
                             parkTime,
                             eventQueueCapacity,
                             runQueueCapacity,
                             maxEvents);
        final List<EfsDispatcherConfig> dispatchers =
            ImmutableList.of(dispatcherConfig);
        final String text =
            "[dispatchers={\n" + dispatcherConfig + "}";
        final EfsDispatchersConfig config =
            new EfsDispatchersConfig();

        config.setDispatchers(dispatchers);

        assertThat(config.getDispatchers())
            .containsAll(dispatchers);
        assertThat(config.toString()).isEqualTo(text);
    } // end of createDispatchersConfig()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private static EfsDispatcherConfig createDispatcher(final String name,
                                                        final ThreadType threadType,
                                                        final int threadCount,
                                                        final int priority,
                                                        final long spinLimit,
                                                        final Duration parkTime,
                                                        final int eventQueueCapacity,
                                                        final int runQueueCapacity,
                                                        final int maxEvents)
    {
        final EfsDispatcherConfig retval =
            new EfsDispatcherConfig();

        retval.setDispatcherName(name);
        retval.setThreadType(threadType);
        retval.setNumThreads(threadCount);
        retval.setPriority(priority);
        retval.setSpinLimit(spinLimit);
        retval.setParkTime(parkTime);
        retval.setEventQueueCapacity(eventQueueCapacity);
        retval.setRunQueueCapacity(runQueueCapacity);
        retval.setMaxEvents(maxEvents);

        return (retval);
    } // end of createDispatcher();
} // end of class EfsDispatchersConfigTest
