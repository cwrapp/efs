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
import org.efs.dispatcher.config.ThreadAffinity;
import org.efs.dispatcher.config.ThreadAffinityConfig;
import org.efs.dispatcher.config.ThreadAffinityConfig.AffinityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
            ((Runtime.getRuntime()).availableProcessors() - 2);
        int cpuId;

        sAvailableCpus = new ArrayList<>(numCpus);

        for (cpuId = 1; cpuId < numCpus; ++cpuId)
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
    public void aquireLockAnyCore()
    {
        final ThreadAffinityConfig config =
            createAnyCoreAffinity();

        mAffinityLock = ThreadAffinity.acquireLock(config);

        assertThat(mAffinityLock).isNotNull();
    } // end of aquireLockAnyCore()

    @Test
    public void acquireLockAnyCpu()
    {
        final ThreadAffinityConfig config =
            createAnyCpuAffinity();

        mAffinityLock = ThreadAffinity.acquireLock(config);

        assertThat(mAffinityLock).isNotNull();
    } // end of acquireLockAnyCpu()

    @Test
    public void acquireLockCpuId()
    {
        final ThreadAffinityConfig config =
            createCpuIdAffinity();

        mAffinityLock = ThreadAffinity.acquireLock(config);

        assertThat(mAffinityLock).isNotNull();
    } // end of acquireLockCpuId()

    @Test
    public void acquireLockCpuLastMinus()
    {
        try
        {
            final ThreadAffinityConfig config =
                createCpuLastMinusAffinity();

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
            createCpuIdAffinity();

        config.setBindFlag(true);
        config.setWholeCoreFlag(true);

        try
        {
            ThreadAffinity.acquireLock(config);
        }
        catch (Exception jex)
        {
            // Ignore.
        }
    } // end of acquireLockAndBind()

    @Test
    public void aquireLockCpuStrategiesWithoutLock()
    {
        final ThreadAffinityConfig config =
            createCpuStrategiesAffinity();

        try
        {
            ThreadAffinity.acquireLock(config);
        }
        catch (IllegalStateException statex)
        {
            assertThat(statex)
                .hasMessage(
                    "affinity lock acquisition using strategies requires an existing lock");
        }
    } // end of aquireLockCpuStrategiesWithoutLock()

    @Test
    public void acquireLockCpuStrategiesNullLock()
    {
        final AffinityLock affinityLock = null;
        final ThreadAffinityConfig config =
            createCpuStrategiesAffinity();

        try
        {
            ThreadAffinity.acquireLock(affinityLock, config);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex).hasMessage("lock is null");
        }
    } // end of acquireLockCpuStrategiesNullLock()

    @Test
    public void acquireLockCpuStrategiesWrongType()
    {
        try
        {
            final AffinityLock affinityLock =
                ThreadAffinity.acquireLock(
                    createCpuIdAffinity());
            final ThreadAffinityConfig config =
                createAnyCoreAffinity();

            ThreadAffinity.acquireLock(affinityLock, config);
        }
        catch (IllegalStateException statex)
        {
            // Ignore.
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(
                    "invalid affinity type ANY_CORE, must be CPU_STRATEGIES");
        }
    } // end of acquireLockCpuStrategiesWrongType()

    @Test
    public void acquireLockCpuStrategies()
    {
        try
        {
            final AffinityLock affinityLock =
                ThreadAffinity.acquireLock(
                    createCpuIdAffinity());
            final ThreadAffinityConfig config =
                createCpuStrategiesAffinity();

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
        final AffinityLock affinityLock =
            ThreadAffinity.acquireLock(
                createAnyCoreAffinity());
        final ThreadAffinityConfig config =
            createCpuStrategiesAffinity();

        try
        {
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

    private static ThreadAffinityConfig createAnyCoreAffinity()
    {
        final ThreadAffinityConfig retval =
            new ThreadAffinityConfig();

        retval.setAffinityType(AffinityType.ANY_CORE);
        retval.setBindFlag(false);

        return (retval);
    } // end of createAnyCoreAffinity()

    private static ThreadAffinityConfig createAnyCpuAffinity()
    {
        final ThreadAffinityConfig retval =
            new ThreadAffinityConfig();

        retval.setAffinityType(AffinityType.ANY_CPU);
        retval.setBindFlag(false);
        retval.setWholeCoreFlag(false);

        return (retval);
    } // end of createAnyCpuAffinity()

    private static ThreadAffinityConfig createCpuIdAffinity()
    {
        final int cpuId = sAvailableCpus.removeLast();
        final ThreadAffinityConfig retval =
            new ThreadAffinityConfig();

        retval.setAffinityType(AffinityType.CPU_ID);
        retval.setCpuId(cpuId);
        retval.setBindFlag(false);
        retval.setWholeCoreFlag(false);

        return (retval);
    } // end of createCpuIdAffinity()

    private static ThreadAffinityConfig createCpuLastMinusAffinity()
    {
        final ThreadAffinityConfig retval =
            new ThreadAffinityConfig();

        retval.setAffinityType(AffinityType.CPU_LAST_MINUS);
        retval.setLastMinusOffset(4);
        retval.setBindFlag(false);
        retval.setWholeCoreFlag(false);

        return (retval);
    } // end of createCpuLastMinusAffinity()

    private static ThreadAffinityConfig createCpuStrategiesAffinity()
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
        retval.setBindFlag(false);
        retval.setWholeCoreFlag(false);

        return (retval);
    } // end of createCpuStrategiesAffinity()
} // end of class ThreadAffinityTest