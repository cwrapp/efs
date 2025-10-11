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

/**
 * A
 * <a href="https://github.com/lightbend/config" target="_blank">typesafe</a>
 * configuration bean class containing necessary
 * information needed to create an affinity between a thread and
 * a core using
 * <a href="https://github.com/OpenHFT/Java-Thread-Affinity" target="_blank">OpenHFT Thread Affinity Library</a>.
 * User is assumed to understand thread affinity and the
 * necessary operating system configuration needed to support it.
 * Please see the above link for more information on how to
 * use thread affinity and its correct use.
 * <p>
 * {@code ThreadAffinityConfig} consist of the following
 * typesafe properties:
 * </p>
 * <ul>
 *   <li>
 *     {@code affinityType}: Required. Defines how core is
 *     acquired for the thread. There are five acquisition types
 *     as defined by {@link AffinityType}:
 *     <ol>
 *       <li>
 *         {@link AffinityType#ANY_CORE ANY_CORE}: Use
 *         {@code AffinityLock.acquireCore()} to assign any free
 *         core to thread.
 *       </li>
 *       <li>
 *         {@link AffinityType#ANY_CPU ANY_CPU}: Use
 *         {@code AffinityLock.acquireLock()} to assign any free
 *         CPU to thread.
 *       </li>
 *       <li>
 *         {@link AffinityType#CPU_LAST_MINUS CPU_LAST_MINUS}:
 *         Use {@code AffinityLock.acquireLock(int cpuId} to
 *         assign a CPU with specified identifier to thread.
 *         Requires property {@link #CPU_OFFSET_KEY} be set.
 *       </li>
 *       <li>
 *         {@link AffinityType#CPU_ID CPU_ID}: Use
 *         {@code AffinityLock.acquireLock(int cpuId} to
 *         assign a CPU with specified identifier to thread.
 *         Requires property {@link #CPU_ID_KEY} be set.
 *       </li>
 *       <li>
 *         {@link AffinityType#CPU_STRATEGIES CPU_STRATEGIES}:
 *         Use {@code AffinityLock.acquireLock(AffinityStrategies...)}
 *         to assign a CPU for thread affinity. Selects a CPU for
 *         thread affinity based on the given selection
 *         strategies. Requires property {@link #STRATEGIES_KEY}
 *         be set.
 *         <p>
 *         Please note that this type may not be used by itself
 *         or as an initial CPU acquisition type. Rather there
 *         must be previous CPU allocation to this (for example a
 *         previous dispatcher configuration using thread
 *         affinity) which the strategy then uses to allocate the
 *         next CPU. Attempts to use this acquisition type either
 *         by itself or as the first strategy will result in an
 *         error and no CPU allocated for the thread.
 *         </p>
 *       </li>
 *     </ol>
 *   </li>
 *   <li>
 *     {@code bind}: Optional, default value is {@code false}.
 *     If {@code true}, then bind current thread to allocated
 *     {@code AffinityLock}.
 *   </li>
 *   <li>
 *     {@code wholeCore}: Optional, default value is
 *     {@code false}. If {@code true}, then bind current thread
 *     to allocated {@code AffinityLock} reserving the whole
 *     core. This property is used only when {@code bind}
 *     property is {@code true}.
 *   </li>
 *   <li>
 *     {@code cpuId}: Required when {@code affinityType} is set
 *     to {@code CPU_ID}. Specifies the allocated CPU by its
 *     identifier.
 *   </li>
 *   <li>
 *     {@code cpuStrategies}: Required when {@code affinityType}
 *     is set to {@code CPU_STRATEGIES}. Values are restricted to
 *     enum {@code net.openhft.affinity.AffinityStrategies}.
 *     <p>
 *     <strong>Note:</strong> strategy ordering is important.
 *     {@code AffinityStrategies.ANY} must appear as the last
 *     listed strategy. This allows any CPU to be selected in
 *     case none of the other strategies found an acceptable CPU.
 *     </p>
 *   </li>
 * </ul>
 * <p>
 * Users should be familiar with the OpenHFT Java Thread Affinity
 * library and how it works before using efs thread affinity
 * configuration. This includes configuring the operating system
 * to isolate acquired CPUs from the operating system. This
 * prevents the OS from preempting the thread from its assigned
 * CPU which means the thread does not entirely own the CPU. That
 * said, isolating too many CPUs from the OS can lead to a kernel
 * panic. So using thread affinity is definitely an advanced
 * software technique, requiring good understanding of how an OS
 * functions.
 * <p>
 * The following example shows a
 * <a href="https://github.com/lightbend/config/blob/master/HOCON.md" target="_blanK">typesafe HOCON</a>
 * file defining a
 * how to use thread affinity for
 * efs dispatcher threads and especially the CPU_STRATEGIES
 * acquisition type.
 * </p>
 * <pre><code>dispatchers  : [
    {
        dispatcherName = "mdDispatcher"
        threadType = SPINNING
        numThreads = 1
        priority = 9
        eventQueueCapacity = 65536
        maxEvents = 65536
        runQueueCapacity = 1 // only one market data agent.
        affinity {       // optional, selector thread core affinity
            affinityType = CPU_ID // required, core selection type.
            cpuId = 7             // required for CPU_ID affinity type
            bindFlag = true       // optional, defaults to false
            wholeCoreFlag = true  // optional, defaults to false
        }
    },
    {
        dispatcherName = "orderDispatcher"
        threadType = SPINNING
        numThreads = 1
        priority = 9
        eventQueueCapacity = 8196
        maxEvents = 128
        runQueueCapacity = 16 // equals number of order processing agents.
        affinity {       // optional, selector thread core affinity
            affinityType = CPU_STRATEGIES // required, core selection type.
            cpuStrategies : [             // required for CPU_STRATEGIES affinity type
              SAME_CORE, SAME_SOCKET, ANY // Note: ANY *must* be last strategy.
            ]
            bindFlag = true       // optional, defaults to false
            wholeCoreFlag = true  // optional, defaults to false
        }
    },
    {
        dispatcherName = "defaultDispatcher"
        threadType = BLOCKING
        numThreads = 8
        priority = 4
        eventQueueCapacity = 512
        maxEvents = 64
        runQueueCapacity = 128
    }
]</code></pre>
 * <p>
 * efs uses this configuration to pin
 * {@link org.efs.dispatcher.EfsDispatcherThread EfsDispatcherThread}
 * instances to a core or cores.
 * </p>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class ThreadAffinityConfig
{
//---------------------------------------------------------------
// Member enums.
//

    /**
     * These affinity types map to a specific
     * {@code AffinityLock static} method used to acquire an
     * affinity lock.
     */
    public enum AffinityType
    {
        /**
         * Use {@code AffinityLock.acquireCore()} to assign any
         * free core to thread.
         */
        ANY_CORE,

        /**
         * Use {@code AffinityLock.acquireLock()} to assign any
         * free CPU to thread.
         */
        ANY_CPU,

        /**
         * Use {@code AffinityLock.acquireLockLastMinus(int n}
         * to allocate a CPU from the end of the core set based
         * on the given positive number. Requires property
         * {@link #CPU_OFFSET_KEY CPU index} is set.
         */
        CPU_LAST_MINUS,

        /**
         * Use {@code AffinityLock.acquireLock(int cpuId} to
         * assign a CPU with specified identifier to thread.
         * Requires CPU identifier is set.
         */
        CPU_ID,

        /**
         * Use {@code AffinityLock.acquireLock(AffinityStrategies...)}
         * to assign a CPU for thread affinity. Selects a CPU for
         * thread affinity based on the given selection
         * strategies. Requires selection
         * {@link AffinityStrategies strategy types} are set.
         * Strategy order is significant and
         * {@link AffinityStrategies#ANY}
         * <strong><em>must</em></strong> be the final strategy
         * type.
         */
        CPU_STRATEGIES
    } // end of enum AffinityType

//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    //
    // Typesafe Property keys.
    //

    /**
     * Key {@value} contain unique affinity name.
     */
    public static final String AFFINITY_NAME_KEY =
        "affinityName";

    /**
     * Key {@value} contains an {@link AffinityType} value.
     */
    public static final String AFFINITY_TYPE_KEY =
        "affinityType";

    /**
     * Key {@value} contains the bind-thread-to-affinity lock
     * flag.
     */
    public static final String BIND_KEY = "bind";

    /**
     * Key {@value} contains reserve-whole-core flag. This
     * property is used only if {@link #BIND_KEY} is set to
     * {@code true}.
     */
    public static final String WHOLE_CORE_KEY = "wholeCore";

    /**
     * Key {@value} contains CPU offset used when
     * {@link #AFFINITY_TYPE_KEY} is set to
     * {@link AffinityType#CPU_LAST_MINUS}. Otherwise this
     * property is ignored for any other affinity type.
     */
    public static final String CPU_OFFSET_KEY = "cpuOffset";

    /**
     * Key {@value} contains CPU identifier used when
     * {@link #AFFINITY_TYPE_KEY} is set to
     * {@link AffinityType#CPU_ID}. Otherwise this property is
     * ignored for any other affinity type. Property value must
     * be &ge; zero.
     */
    public static final String CPU_ID_KEY = "cpuId";

    /**
     * Key {@value} contains CPU selection strategies array
     * used when {@link #AFFINITY_TYPE_KEY} is set to
     * {@link AffinityType#CPU_STRATEGIES}. Otherwise this
     * property is ignored for any other affinity type.
     * Property value must be a non-empty array where the final
     * element is {@link AffinityStrategies#ANY}. Note that
     * strategy ordering is significant.
     */
    public static final String STRATEGIES_KEY = "cpuStrategies";

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Defines how a CPU is chosen for thread affinity. Default
     * setting is {@link AffinityType#ANY_CPU}.
     */
    private AffinityType mAffinityType;

    /**
     * Set to {@code true} when thread is bound to its
     * {@code AffinityLock}. Default setting is {@code false} -
     * no binding is done.
     *
     * @see #mWholeCore
     */
    private boolean mBindFlag;

    /**
     * Set to {@code true} when affinity reserves the whole
     * core. This flag is used only when {@link #mBindFlag} is
     * set to {@code true}.
     *
     * @see #mBindFlag
     */
    private boolean mWholeCore;

    /**
     * When affinity type is {@link AffinityType#CPU_ID} this is the
     * CPU identifier used for thread affinity. Default setting
     * is negative meaning no CPU selected.
     */
    private int mCpuId;

    /**
     * When affinity type is {@link AffinityType#CPU_LAST_MINUS}
     * this is the offset used to select a CPU from the end of
     * the CPU set. Default setting is zero meaning no offset is
     * selected.
     */
    private int mOffset;

    /**
     * When affinity type is {@link AffinityType#CPU_STRATEGIES}
     * these are the pre-defined strategies used to select
     * CPU which with the thread will have affinity. Default
     * setting is {@code null} if no strategies are defined.
     */
    private List<AffinityStrategies> mStrategies;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Default constructor sets fields to invalid values.
     */
    public ThreadAffinityConfig()
    {
        mCpuId = -1;
    } // end of ThreadAffinityConfig()

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    @Override
    public String toString()
    {
        final StringBuilder retval = new StringBuilder();

        retval.append("[type=").append(mAffinityType)
              .append(", bind=").append(mBindFlag);

        if (mBindFlag)
        {
            retval.append(", core=").append(mWholeCore);
        }

        switch (mAffinityType)
        {
            case CPU_ID ->
                retval.append(", cpu ID=").append(mCpuId);

            case CPU_LAST_MINUS ->
                retval.append(", offset=").append(mOffset);

            case CPU_STRATEGIES ->
            {
                String sep = "";

                retval.append(", strategies={");
                for (AffinityStrategies s : mStrategies)
                {
                    retval.append(sep).append(s);
                    sep = ",";
                }
                retval.append('}');
            }

            default -> {}
        }
        // No other data to append.

        retval.append(']');

        return (retval.toString());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns affinity type used for creating thread affinity to
     * selected CPU_ID.
     * @return CPU_ID selection type.
     */
    public AffinityType getAffinityType()
    {
        return (mAffinityType);
    } // end of getAffinityType()

    /**
     * Returns the bind-thread-to-affinity lock setting. If
     * {@code true} then thread is bound to the lock.
     * @return {@code true} if thread is bound to the affinity
     * lock.
     *
     * @see #getWholeCoreFlag()
     */
    public boolean getBindFlag()
    {
        return (mBindFlag);
    } // end of getBindFlag()

    /**
     * Returns whole core reservation bind settings. If
     * {@code true} then thread reserves entire core and does not
     * allow hyper-threading. This value is used only if
     * {@link #getBindFlag()} returns {@code true}; otherwise
     * ignored.
     * @return whole core reservation flag.
     *
     * @see #getBindFlag()
     */
    public boolean getWholeCoreFlag()
    {
        return (mWholeCore);
    } // end of getWholeCoreFlag()

    /**
     * Returns CPU_ID identifier used for {@link AffinityType#CPU_ID}
     * affinity type. Set to a negative number for any other
     * affinity type.
     * @return CPU_ID identifier or &lt; zero if no identifier
     * specified.
     */
    public int getCpuId()
    {
        return (mCpuId);
    } // end of getCpuId()

    /**
     * Returns CPU offset used for
     * {@link AffinityType#CPU_LAST_MINUS} affinity type. Set to
     * zero for any other affinity type.
     * @return CPU offset or zero if no offset specified.
     */
    public int getLastMinusOffset()
    {
        return (mOffset);
    } // end of getLastMinusOffset()

    /**
     * Returns immutable list of CPU_ID selection strategies used
     * for {@link AffinityType#CPU_STRATEGIES} affinity type. Set
     * to {@code null} for any other affinity type.
     * @return CPU_ID selection strategies.
     */
    public List<AffinityStrategies> getStrategies()
    {
        return (mStrategies);
    } // end of getStrategies()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets affinity type to the given value.
     * @param type desired thread affinity type.
     * @throws ConfigException
     * if {@code type} is {@code null}.
     */
    public void setAffinityType(final AffinityType type)
    {
        if (type == null)
        {
            throw (
                new ConfigException.BadValue(
                    AFFINITY_TYPE_KEY, "type is null"));
        }

        mAffinityType = type;
    } // end of setAffinityType(AffinityType)

    /**
     * Sets flag for binding thread to affinity lock.
     * @param flag if {@code true} then bind thread to
     * affinity lock.
     */
    public void setBindFlag(final boolean flag)
    {
        mBindFlag = flag;
    } // end of setBindFlag(boolean)

    /**
     * Sets flag reserving the entire core and not allowing
     * hyper-threading on that core. This flag is used only when
     * {@link #setBindFlag(boolean)} is set to {@code true};
     * otherwise it is ignored.
     * @param flag if {@code true} then binding reserves
     * whole core.
     */
    public void setWholeCoreFlag(final boolean flag)
    {
        mWholeCore = flag;
    } // end of setWholeCoreFlag(boolean)

    /**
     * Sets CPU_ID identifier used by
     * {@link AffinityType#CPU_ID} affinity type. This value
     * <em>must</em> be set when using {@code CPU_ID} affinity
     * type and is ignored for all others.
     * <p>
     * <strong>Note:</strong> {@code id} is <em>not</em>
     * checked to see if it is a valid CPU_ID identifier
     * beyond making sure it is &ge; zero.
     * </p>
     * @param id CPU_ID identifier.
     * @throws ConfigException
     * if {@code id} is &lt; zero.
     */
    public void setCpuId(final int id)
    {
        if (id < 0)
        {
            throw (
                new ConfigException.BadValue(
                    CPU_ID_KEY, "id < zero"));
        }

        mCpuId = id;
    } // end of setCpuId(final int id)

    /**
     * Sets positive integer offset used by
     * {@link AffinityType#CPU_LAST_MINUS} affinity type.
     * This value <em>must</em> be set when using
     * {@code CPU_LAST_MINUS} affinity type and is ignored
     * for all others.
     * <p>
     * <strong>Note:</strong> {@code n} is <em>not</em>
     * checked to see if it is a valid CPU offset beyond
     * making sure it is &gt; zero.
     * @param offset offset from end used to select CPU.
     * @throws ConfigException
     * if {@code n} is &le; zero.
     */
    public void setLastMinusOffset(final int offset)
    {
        if (offset <= 0)
        {
            throw (
                new ConfigException.BadValue(
                    CPU_OFFSET_KEY, "offset <= zero"));
        }

        mOffset = offset;
    } // end of setLastMinusOffset(int)

    /**
     * Sets CPU selection strategies used by
     * {@link AffinityType#CPU_STRATEGIES} affinity type.
     * This list <em>must</em> be set when using
     * {@code CPU_STRATEGIES} affinity type and is ignored
     * for all others.
     * <p>
     * <strong>Note:</strong> strategy ordering is important.
     * The {@code ANY} strategy <em>must</em> appear as the
     * last listed strategy. This allows any CPU to be
     * selected in case none of the other strategies found
     * an acceptable CPU.
     * </p>
     * @param strategies CPU selection strategies list.
     * @throws ConfigException
     * if {@code strategies} is either {@code null}. and empty
     * list, or the final element is not
     * {@link AffinityStrategies#ANY}.
     */
    public void setStrategies(final List<AffinityStrategies> strategies)
    {
        if (strategies == null || strategies.isEmpty())
        {
            throw (
                new ConfigException.BadValue(
                    STRATEGIES_KEY,
                    "strategies is either null or an empty list"));
        }

        final AffinityStrategies last = strategies.getLast();

        if (last != AffinityStrategies.ANY)
        {
            throw (
                new ConfigException.BadValue(
                    STRATEGIES_KEY,
                    "final strategy is not ANY"));
        }

        mStrategies = ImmutableList.copyOf(strategies);
    } // end of setStrategies(List<>)

    //
    // end of Set Methods.
    //-----------------------------------------------------------
} // end of class ThreadAffinityConfig
