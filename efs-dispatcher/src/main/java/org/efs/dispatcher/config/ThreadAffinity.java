//
// Copyright 2021 Charles W. Rapp
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

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.openhft.affinity.AffinityLock;
import net.openhft.affinity.AffinityStrategies;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import org.efs.dispatcher.config.ThreadAffinityConfig.AffinityType;
import static org.efs.dispatcher.config.ThreadAffinityConfig.AffinityType.ANY_CORE;
import static org.efs.dispatcher.config.ThreadAffinityConfig.AffinityType.ANY_CPU;
import static org.efs.dispatcher.config.ThreadAffinityConfig.AffinityType.CPU_ID;

/**
 * Provides interface to
 * <a href="https://github.com/OpenHFT/Java-Thread-Affinity" target="_blank">OpenHFT Thread Affinity Library</a>
 * based on a given {@link ThreadAffinityConfig}. The idea is
 * that by binding a thread to a core thread performance is
 * improved. But this improvement can only be realized if the
 * core is isolated from operating system use. Otherwise the
 * thread still faces losing its core to the OS, having to wait
 * to gain the core again with its cache wrecked by the OS
 * thread.
 * <p>
 * Please see {@link ThreadAffinityConfig} for a detailed
 * discussion on how to configure thread affinity.
 * </p>
 * <p>
 * This class is designed for use with
 * {@code net.sf.efs.dispatcher.EfsDispatcherThread} so that it
 * may be bound to a core. This would normally be done when the
 * thread is configured as spinning.
 * </p>
 *
 * @see ThreadAffinityConfig
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class ThreadAffinity
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Locks CPU affinity lock process to prevent concurrent
     * acquisition which will cause failures.
     */
    private static final Lock sAcquisitionLock =
        new ReentrantLock();

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Private constructor to prevent instantiation.
     */
    private ThreadAffinity()
    {}

    //
    // end of Constructors.
    //-----------------------------------------------------------

    /**
     * Returns an OpenHFT thread affinity lock.
     * @param config thread affinity configuration.
     * @return thread affinity lock. This lock should be
     * {@link AffinityLock#release() released} when the thread
     * stops.
     * @throws ValidationException
     * if {@code config} does not contain a valid thread affinity
     * configuration.
     * @throws IllegalStateException
     * if {@code config.affinityType} is
     * {@code AffinityType.CPU_STRATEGIES} which requires an
     * existing {@code AffinityLock} to work.
     *
     * @see #acquireLock(AffinityLock, ThreadAffinityConfigure)
     */
    public static AffinityLock acquireLock(final ThreadAffinityConfig config)
    {
        validate(config);

        final AffinityType type = config.getAffinityType();
        final AffinityLock retval;

        if (type == AffinityType.CPU_STRATEGIES)
        {
            throw (
                new IllegalStateException(
                    "affinity lock acquisition using strategies requires an existing lock"));
        }

        sAcquisitionLock.lock();
        try
        {
            retval =
                switch (type)
                {
                    case ANY_CORE -> AffinityLock.acquireCore();
                    case ANY_CPU -> AffinityLock.acquireLock();
                    case CPU_ID ->
                        AffinityLock.acquireLock(
                            config.getCpuId());
                    default ->
                        AffinityLock.acquireLockLastMinus(
                            config.getLastMinusOffset());
                };

            if (config.getBindFlag())
            {
                retval.bind(config.getWholeCoreFlag());
            }
        }
        finally
        {
            sAcquisitionLock.unlock();
        }

        return (retval);
    } // end of acquireLock(ThreadAffinityConfig)

    /**
     * Returns an OpenHFT thread affinity lock based on an
     * existing lock and CPU strategy configuration. The idea
     * is OpenHFT will select a CPU based on the existing lock's
     * CPU and the CPU selection strategies. Please not that the
     * final strategy is {@link AffinityStrategies#ANY} allowing
     * any CPU to be chosen if no available CPU matches the
     * previous strategies.
     * @param lock existing affinity lock.
     * @param config CPU selection strategy.
     * @return thread affinity lock. This lock should be
     * {@link AffinityLock#release() released} when the thread
     * stops.
     * @throws ValidationException
     * if {@code config} does not contain a valid thread affinity
     * configuration.
     * @throws NullPointerException
     * if {@code lock} or {@code config} is {@code null}.
     * @throws IllegalArgumentException
     * if {@code config} affinity type is not
     * {@code AffinityType.CPU_STRATEGIES}.
     *
     * @see #acquireLock(ThreadAffinityConfigure)
     */
    public static AffinityLock acquireLock(final AffinityLock lock,
                                           final ThreadAffinityConfig config)
    {
        validate(config);

        final AffinityType type = config.getAffinityType();
        final AffinityLock retval;

        Objects.requireNonNull(lock, "lock is null");
        if (type != AffinityType.CPU_STRATEGIES)
        {
            throw (
                new IllegalArgumentException(
                    String.format(
                        "invalid affinity type %s, must be %s",
                        type,
                        AffinityType.CPU_STRATEGIES)));
        }

        sAcquisitionLock.lock();
        try
        {
            final List<AffinityStrategies> strategies =
                config.getStrategies();
            final AffinityStrategies[] sArray =
                strategies.toArray(AffinityStrategies[]::new);

            retval = lock.acquireLock(sArray);

            if (config.getBindFlag())
            {
                retval.bind(config.getWholeCoreFlag());
            }
        }
        finally
        {
            sAcquisitionLock.unlock();
        }

        return (retval);
    } // end of acquireLock(AffinityLock,ThreadAffinityConfig)

    /**
     * Validates affinity configuration.
     * @param config affinity configuration.
     * @throws ValidationException
     * if {@code config} contains a invalid thread affinity
     * configuration.
     */
    @VisibleForTesting
    public static void validate(final ThreadAffinityConfig config)
    {
        final AffinityType affinityType =
            config.getAffinityType();
        final Validator problems = new Validator();

        problems.requireNotNull(affinityType,
                                ThreadAffinityConfig.AFFINITY_TYPE_KEY)
                .requireTrue((affinityType != CPU_ID ||
                              config.getCpuId() >= 0),
                             ThreadAffinityConfig.CPU_ID_KEY,
                             Validator.NOT_SET)
                .requireTrue((affinityType !=
                                  AffinityType.CPU_LAST_MINUS ||
                              config.getLastMinusOffset() != 0),
                             ThreadAffinityConfig.CPU_OFFSET_KEY,
                             Validator.NOT_SET)
                .requireTrue((affinityType !=
                                  AffinityType.CPU_STRATEGIES ||
                              config.getStrategies() != null),
                             ThreadAffinityConfig.STRATEGIES_KEY,
                             Validator.NOT_SET)
                .throwException(ThreadAffinityConfig.class);
    } // end of validate(ThreadAffinityConfig)
} // end of class ThreadAffinity
