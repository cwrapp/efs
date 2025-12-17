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
import java.util.List;
import net.openhft.affinity.AffinityStrategies;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.dispatcher.config.ThreadAffinityConfig.AffinityType;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@code ThreadAffinityConfig} typesafe bean.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class ThreadAffinityConfigTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Test Methods.
    //

    @Test
    public void nullAffinityType()
    {
        final AffinityType type = null;
        final ThreadAffinityConfig config =
            new ThreadAffinityConfig();

        try
        {
            config.setAffinityType(type);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("type is null");
        }
    } // end of nullAffinityType()

    @Test
    public void negativeCpuId()
    {
        final int cpuId = -1;
        final ThreadAffinityConfig config =
            new ThreadAffinityConfig();

        try
        {
            config.setCpuId(cpuId);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("id < zero");
        }
    } // end of negativeCpuId()

    @Test
    public void zeroOffset()
    {
        final int offset = 0;
        final ThreadAffinityConfig config =
            new ThreadAffinityConfig();

        try
        {
            config.setLastMinusOffset(offset);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("offset <= zero");
        }
    } // end of zeroOffset()

    @Test
    public void nullStrategies()
    {
        final List<AffinityStrategies> strategies = null;
        final ThreadAffinityConfig config =
            new ThreadAffinityConfig();

        try
        {
            config.setStrategies(strategies);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith(
                    "strategies is either null or an empty list");
        }
    } // end of nullStrategies()

    @Test
    public void emptyStrategies()
    {
        final List<AffinityStrategies> strategies =
            ImmutableList.of();
        final ThreadAffinityConfig config =
            new ThreadAffinityConfig();

        try
        {
            config.setStrategies(strategies);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith(
                    "strategies is either null or an empty list");
        }
    } // end of emptyStrategies()

    @Test
    public void invalidStrategies()
    {
        final List<AffinityStrategies> strategies =
            ImmutableList.of(
                AffinityStrategies.SAME_CORE,
                AffinityStrategies.SAME_SOCKET);
        final ThreadAffinityConfig config =
            new ThreadAffinityConfig();

        try
        {
            config.setStrategies(strategies);
        }
        catch (ConfigException confex)
        {
            assertThat(confex.getMessage())
                .endsWith("final strategy is not ANY");
        }
    } // end of invalidStrategies()

    @Test
    public void invalidAffinityTypeNotSet()
    {
        final ThreadAffinityConfig config =
            new ThreadAffinityConfig();

        try
        {
            ThreadAffinity.validate(config);
        }
        catch (ValidationException validex)
        {
            assertThat(validex.getMessage())
                .contains(
                    ThreadAffinityConfig.AFFINITY_TYPE_KEY +
                    ": " +
                    Validator.NOT_SET);
        }
    } // end of invalidAffinityTypeNotSet()

    @Test
    public void invalidCpuIdAffinity()
    {
        final AffinityType affinityType = AffinityType.CPU_ID;
        final ThreadAffinityConfig config =
            new ThreadAffinityConfig();

        config.setAffinityType(affinityType);

        try
        {
            ThreadAffinity.validate(config);
        }
        catch (ValidationException validex)
        {
            assertThat(validex.getMessage())
                .contains(
                    ThreadAffinityConfig.CPU_ID_KEY +
                    ": " +
                    Validator.NOT_SET);
        }
    } // end of invalidCpuIdAffinity()

    @Test
    public void invalidCpuLastMinusAffinity()
    {
        final AffinityType affinityType =
            AffinityType.CPU_LAST_MINUS;
        final ThreadAffinityConfig config =
            new ThreadAffinityConfig();

        config.setAffinityType(affinityType);

        try
        {
            ThreadAffinity.validate(config);
        }
        catch (ValidationException validex)
        {
            assertThat(validex.getMessage())
                .contains(
                    ThreadAffinityConfig.CPU_OFFSET_KEY +
                    ": " +
                    Validator.NOT_SET);
        }
    } // end of invalidCpuLastMinusAffinity()

    @Test
    public void invalidCpuStrategiesAffinity()
    {
        final AffinityType affinityType =
            AffinityType.CPU_STRATEGIES;
        final ThreadAffinityConfig config =
            new ThreadAffinityConfig();

        config.setAffinityType(affinityType);

        try
        {
            ThreadAffinity.validate(config);
        }
        catch (ValidationException validex)
        {
            assertThat(validex.getMessage())
                .contains(
                    ThreadAffinityConfig.STRATEGIES_KEY +
                    ": " +
                    Validator.NOT_SET);
        }
    } // end of invalidCpuStrategiesAffinity()

    @Test
    public void anyCoreAffinity()
    {
        final AffinityType affinityType = AffinityType.ANY_CORE;
        final boolean bindFlag = false;
        final String text =
            String.format(
                "[type=%s, bind=%b]",
                affinityType,
                bindFlag);
        final ThreadAffinityConfig config =
            new ThreadAffinityConfig();

        config.setAffinityType(affinityType);

        assertThat(config.getAffinityType())
            .isEqualTo(affinityType);
        assertThat(config.toString()).isEqualTo(text);
    } // end of anyCoreAffinity()

    @Test
    public void anyCpuAffinity()
    {
        final AffinityType affinityType = AffinityType.ANY_CPU;
        final boolean bindFlag = true;
        final boolean wholeCoreFlag = false;
        final String text =
            String.format(
                "[type=%s, bind=%b, core=%b]",
                affinityType,
                bindFlag,
                wholeCoreFlag);
        final ThreadAffinityConfig config =
            new ThreadAffinityConfig();

        config.setAffinityType(affinityType);
        config.setBindFlag(bindFlag);
        config.setWholeCoreFlag(wholeCoreFlag);

        assertThat(config.getAffinityType())
            .isEqualTo(affinityType);
        assertThat(config.getBindFlag()).isTrue();
        assertThat(config.getWholeCoreFlag()).isFalse();
        assertThat(config.toString()).isEqualTo(text);
    } // end of anyCpuAffinity()

    @Test
    public void cpuIdAffinity()
    {
        final AffinityType affinityType = AffinityType.CPU_ID;
        final int cpuId = 7;
        final boolean bindFlag = true;
        final boolean wholeCoreFlag = true;
        final String text =
            String.format(
                "[type=%s, bind=%b, core=%b, cpu ID=%d]",
                affinityType,
                bindFlag,
                wholeCoreFlag,
                cpuId);
        final ThreadAffinityConfig config =
            new ThreadAffinityConfig();

        config.setAffinityType(affinityType);
        config.setCpuId(cpuId);
        config.setBindFlag(bindFlag);
        config.setWholeCoreFlag(wholeCoreFlag);

        assertThat(config.getAffinityType())
            .isEqualTo(affinityType);
        assertThat(config.getCpuId()).isEqualTo(cpuId);
        assertThat(config.getBindFlag()).isTrue();
        assertThat(config.getWholeCoreFlag()).isTrue();
        assertThat(config.toString()).isEqualTo(text);
    } // end of cpuIdAffinity()

    @Test
    public void cpuLastMinusAffinity()
    {
        final AffinityType affinityType =
            AffinityType.CPU_LAST_MINUS;
        final int offset = 4;
        final boolean bindFlag = true;
        final boolean coreFlag = false;
        final String text =
            String.format(
                "[type=%s, bind=%b, core=%b, offset=%d]",
                affinityType,
                bindFlag,
                coreFlag,
                offset);
        final ThreadAffinityConfig config =
            new ThreadAffinityConfig();

        config.setAffinityType(affinityType);
        config.setLastMinusOffset(offset);
        config.setBindFlag(bindFlag);

        assertThat(config.getAffinityType())
            .isEqualTo(affinityType);
        assertThat(config.getLastMinusOffset()).isEqualTo(offset);
        assertThat(config.getBindFlag()).isTrue();
        assertThat(config.toString()).isEqualTo(text);
    } // end of cpuLastMinusAffinity()

    @Test
    public void cpuStrategiesAffinity()
    {
        final AffinityType affinityType =
            AffinityType.CPU_STRATEGIES;
        final boolean bindFlag = false;
        final List<AffinityStrategies> strategies =
            ImmutableList.of(
                AffinityStrategies.SAME_CORE,
                AffinityStrategies.SAME_SOCKET,
                AffinityStrategies.ANY);
        final String text =
            String.format(
                "[type=%s, bind=%b, strategies={%s,%s,%s}]",
                affinityType,
                bindFlag,
                strategies.get(0),
                strategies.get(1),
                strategies.get(2));
        final ThreadAffinityConfig config =
            new ThreadAffinityConfig();

        config.setAffinityType(affinityType);
        config.setStrategies(strategies);

        assertThat(config.getAffinityType())
            .isEqualTo(affinityType);
        assertThat(config.getStrategies())
            .containsExactlyElementsOf(strategies);
        assertThat(config.toString()).isEqualTo(text);
    } // end of cpuStrategiesAffinity()

    //
    // end of JUnit Test Methods.
    //-----------------------------------------------------------
} // end of class ThreadAffinityConfigTest
