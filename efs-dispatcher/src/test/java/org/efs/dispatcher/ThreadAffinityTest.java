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

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import net.openhft.affinity.AffinityLock;
import net.openhft.affinity.AffinityStrategies;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.efs.dispatcher.config.ThreadAffinity;
import org.efs.dispatcher.config.ThreadAffinityConfig;
import org.efs.dispatcher.config.ThreadAffinityConfig.AffinityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class ThreadAffinityTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    //-----------------------------------------------------------
    // Statics.
    //

    private static List<Integer> sAvailableCpus;

    //-----------------------------------------------------------
    // Locals.
    //

    private AffinityLock mAffinityLock;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void setUpClass()
    {
        final int numCpus =
            (Runtime.getRuntime()).availableProcessors();
        int cpuId;

        sAvailableCpus = new ArrayList<>(numCpus - 2);

        for (cpuId = 2; cpuId < numCpus; ++cpuId)
        {
            sAvailableCpus.add(cpuId);
        }
    } // end of setUpClass()

    @AfterEach
    public void tearDown()
    {
        if (mAffinityLock != null)
        {
            final Integer cpuId = mAffinityLock.cpuId();

            sAvailableCpus.remove(cpuId);
            mAffinityLock.release();
            mAffinityLock = null;
        }
    } // end of tearDown()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void aquireLockNullConfig()
    {
        final ThreadAffinityConfig config = null;

        assertThatThrownBy(
            () -> ThreadAffinity.acquireLock(config))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(ThreadAffinity.NULL_CONFIG);
    } // end of aquireLockNullConfig()

    @Test
    public void acquireLockNullLock()
    {
        final AffinityLock affinityLock = null;
        final ThreadAffinityConfig config =
            createAnyCoreAffinity(false);

        assertThatThrownBy(
            () -> ThreadAffinity.acquireLock(affinityLock,
                                             config))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(ThreadAffinity.NULL_LOCK);
    } // end of acquireLockNullLock()

    @Disabled
    @Test
    public void acquireLockNullConfig2()
    {
        final ThreadAffinityConfig config = null;
        final AffinityLock affinityLock =
            ThreadAffinity.acquireLock(
                createAnyCoreAffinity(false));

        assertThatThrownBy(
            () -> ThreadAffinity.acquireLock(affinityLock,
                                             config))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(ThreadAffinity.NULL_CONFIG);
    } // end of acquireLockNullConfig2()

    @Test
    public void aquireLockAnyCore()
    {
        final ThreadAffinityConfig config =
            createAnyCoreAffinity(false);

        mAffinityLock = ThreadAffinity.acquireLock(config);

        assertThat(mAffinityLock).isNotNull();
    } // end of aquireLockAnyCore()

    @Test
    public void acquireLockAnyCpu()
    {
        final ThreadAffinityConfig config =
            createAnyCpuAffinity(false, false);

        mAffinityLock = ThreadAffinity.acquireLock(config);

        assertThat(mAffinityLock).isNotNull();
    } // end of acquireLockAnyCpu()

    @Test
    public void acquireLockCpuId()
    {
        final ThreadAffinityConfig config =
            createCpuIdAffinity(false, false);

        try
        {
            mAffinityLock = ThreadAffinity.acquireLock(config);

            assertThat(mAffinityLock).isNotNull();
        }
        catch (Exception jex)
        {
            // Ignore.
        }
    } // end of acquireLockCpuId()

    @Test
    public void acquireLockCpuLastMinus()
    {
        try
        {
            final ThreadAffinityConfig config =
                createCpuLastMinusAffinity(false, false);

            mAffinityLock = ThreadAffinity.acquireLock(config);

            assertThat(mAffinityLock).isNotNull();
        }
        catch (IllegalStateException statex)
        {
            // Ignore.
        }
    } // end of acquireLockCpuLastMinus()

    @Test
    public void acquireLockAndBind()
    {
        final ThreadAffinityConfig config =
            createAnyCoreAffinity(true);

        try
        {
            mAffinityLock = ThreadAffinity.acquireLock(config);
        }
        catch (Exception jex)
        {
            // Ignore.
            jex.printStackTrace(System.err);
        }
    } // end of acquireLockAndBind()

    @Test
    public void aquireLockCpuStrategiesWithoutLock()
    {
        final ThreadAffinityConfig config =
            createCpuStrategiesAffinity(false, false);

        assertThatThrownBy(
            () -> ThreadAffinity.acquireLock(config))
            .isInstanceOfAny(IllegalStateException.class)
            .hasMessage(
                "affinity lock acquisition using strategies requires an existing lock");
    } // end of aquireLockCpuStrategiesWithoutLock()

    @Test
    public void acquireLockCpuStrategiesNullLock()
    {
        final AffinityLock affinityLock = null;
        final ThreadAffinityConfig config =
            createCpuStrategiesAffinity(false, false);

        assertThatThrownBy(
            () -> ThreadAffinity.acquireLock(affinityLock,
                                             config))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("lock is null");
    } // end of acquireLockCpuStrategiesNullLock()

    @Test
    public void acquireLockCpuStrategiesWrongType()
    {
        final AffinityLock affinityLock =
            ThreadAffinity.acquireLock(
                createCpuIdAffinity(false, false));
        final ThreadAffinityConfig config =
            createAnyCoreAffinity(false);

        assertThatThrownBy(
            () -> ThreadAffinity.acquireLock(affinityLock,
                                             config))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                "invalid affinity type ANY_CORE, must be CPU_STRATEGIES");
    } // end of acquireLockCpuStrategiesWrongType()

    @Test
    public void acquireLockCpuStrategies()
    {
        try
        {
            final AffinityLock affinityLock =
                ThreadAffinity.acquireLock(
                    createCpuIdAffinity(false, false));
            final ThreadAffinityConfig config =
                createCpuStrategiesAffinity(false, false);

            mAffinityLock =
                ThreadAffinity.acquireLock(affinityLock, config);

            assertThat(mAffinityLock).isNotNull();

            sAvailableCpus.remove((Integer) affinityLock.cpuId());
            affinityLock.release();
        }
        catch (IllegalStateException statex)
        {
            // Ignore.
        }
    } // end of acquireLockCpuStrategies()

    @Test
    public void acquireLockCpuStrategiesWithBind()
    {
        try
        {
            final AffinityLock affinityLock =
                ThreadAffinity.acquireLock(
                    createAnyCoreAffinity(false));
            final ThreadAffinityConfig config =
                createCpuStrategiesAffinity(true, true);

            mAffinityLock =
                ThreadAffinity.acquireLock(affinityLock, config);
        }
        catch (Exception jex)
        {
            // Ignore.
        }
    } // end of acquireLockCpuStrategiesWithBind()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private static ThreadAffinityConfig createAnyCoreAffinity(final boolean bindFlag)
    {
        final ThreadAffinityConfig retval =
            new ThreadAffinityConfig();

        retval.setAffinityType(AffinityType.ANY_CORE);
        retval.setBindFlag(bindFlag);

        return (retval);
    } // end of createAnyCoreAffinity(boolean)

    private static ThreadAffinityConfig createAnyCpuAffinity(final boolean bindFlag,
                                                             final boolean wholeCore)
    {
        final ThreadAffinityConfig retval =
            new ThreadAffinityConfig();

        retval.setAffinityType(AffinityType.ANY_CPU);
        retval.setBindFlag(bindFlag);
        retval.setWholeCoreFlag(wholeCore);

        return (retval);
    } // end of createAnyCpuAffinity(boolean, boolean)

    private static ThreadAffinityConfig createCpuIdAffinity(final boolean bindFlag,
                                                            final boolean wholeCore)
    {
        final int cpuId = sAvailableCpus.removeFirst();
        final ThreadAffinityConfig retval =
            new ThreadAffinityConfig();

        retval.setAffinityType(AffinityType.CPU_ID);
        retval.setCpuId(cpuId);
        retval.setBindFlag(bindFlag);
        retval.setWholeCoreFlag(wholeCore);

        return (retval);
    } // end of createCpuIdAffinity(boolean, boolean)

    private static ThreadAffinityConfig createCpuLastMinusAffinity(final boolean bindFlag,
                                                                   final boolean wholeCore)
    {
        final ThreadAffinityConfig retval =
            new ThreadAffinityConfig();

        retval.setAffinityType(AffinityType.CPU_LAST_MINUS);
        retval.setLastMinusOffset(4);
        retval.setBindFlag(bindFlag);
        retval.setWholeCoreFlag(wholeCore);

        return (retval);
    } // end of createCpuLastMinusAffinity(boolean, boolean)

    private static ThreadAffinityConfig createCpuStrategiesAffinity(final boolean bindFlag,
                                                                    final boolean wholeCore)
    {
        final AffinityType affinityType =
            AffinityType.CPU_STRATEGIES;
        final List<AffinityStrategies> strategies =
            ImmutableList.of(
                AffinityStrategies.SAME_CORE,
                AffinityStrategies.SAME_SOCKET,
                AffinityStrategies.ANY);
        final ThreadAffinityConfig retval =
            new ThreadAffinityConfig();

        retval.setAffinityType(affinityType);
        retval.setStrategies(strategies);
        retval.setBindFlag(bindFlag);
        retval.setWholeCoreFlag(wholeCore);

        return (retval);
    } // end of createCpuStrategiesAffinity(boolean, boolean)
} // end of class ThreadAffinityTest