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

import com.google.common.base.Strings;
import com.typesafe.config.ConfigException;
import com.typesafe.config.Optional;
import java.time.Duration;
import javax.annotation.Nullable;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.EfsDispatcher.DispatcherType;
import org.efs.dispatcher.IEfsAgent;

/**
 * {@link org.efs.dispatcher.EfsDispatcher EfsDispatcher}
 * configuration definition. Contains all the properties
 * required to build an {@code EfsDispatcher}. This class is
 * defined as a
 * <a href="https://github.com/lightbend/config" target="_blank">typesafe</a>
 * configuration bean.
 * <p>
 * Please note that this class may only be used to configure
 * {@link DispatcherType#EFS efs} dispatchers and not
 * special dispatchers.
 * </p>
 * <h2>Example EfsDispatcherConfig File Enty</h2>
 * For further explanation on what these properties mean and
 * valid settings, see the property set method (e.g.
 * {@link #setSpinLimit(long)}).
 * <pre><code>dispatcherName = AlgoDispatcher
threadType = SPINPARK
numThreads = 3
priority = 8 <em>// Thread.MIN_PRIORITY &le; priority &lt; Thread.MAX_PRIORITY</em>
spinLimit = 1000000
parkTime = 500 nanos
eventQueueCapacity = 256
maxEvents = 64
runQueueCapacity = 8
</code></pre>
 *
 * @see EfsDispatchersConfig
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsDispatcherConfig
{
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
     * Key {@value} contains a unique dispatcher name.
     */
    public static final String DISPATCHER_NAME_KEY =
        "dispatcherName";

    /**
     * Key {@value} contains dispatcher
     * {@link ThreadType thread type}.
     */
    public static final String THREAD_TYPE_KEY = "threadType";

    /**
     * Key {@value} contains number of underlying dispatcher
     * threads.
     */
    public static final String NUM_THREADS_KEY = "numThreads";

    /**
     * Key {@value} contains underlying dispatcher thread
     * priority.
     */
    public static final String PRIORITY_KEY = "priority";

    /**
     * Key {@value} contains spin limit for spin+park and
     * spin+yield thread types.
     */
    public static final String SPIN_LIMIT_KEY = "spinLimit";

    /**
     * Key {@value} contains park time for spin+park thread type.
     */
    public static final String PARK_TIME_KEY = "parkTime";

    /**
     * Key {@value} contains {@link IEfsAgent efs agent} event
     * queue capacity.
     */
    public static final String EVENT_QUEUE_CAPACITY_KEY =
        "eventQueueCapacity";

    /**
     * Key {@value} contains dispatcher run queue capacity.
     */
    public static final String RUN_QUEUE_CAPACITY_KEY =
        "runQueueCapacity";

    /**
     * Key {@value} contains maximum number of events an efs agent
     * may process at any one time.
     */
    public static final String MAX_EVENTS_KEY = "maxEvents";

    /**
     * Key {@value} contains {@link IEfsAgent efs agent} class
     * names supported by this dispatcher.
     */
    public static final String CLASSES_KEY = "classes";

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Dispatcher name. Must be unique within the JVM.
     */
    private String mDispatcherName;

    /**
     * Defines underlying thread type.
     */
    private ThreadType mThreadType;

    /**
     * Defines number of underlying threads in this dispatcher.
     */
    private int mNumThreads;

    /**
     * Defines priority used for each underlying thread.
     */
    private int mPriority;

    /**
     * Defines spin limit used by spin+park and spin+yield
     * thread type.
     */
    @Nullable
    @Optional
    private long mSpinLimit;

    /**
     * Defines park time used by spin+park thread type.
     */
    @Nullable
    @Optional
    private Duration mParkTime;

    /**
     * Define optional dispatcher thread affinity.
     */
    @Nullable
    @Optional
    private ThreadAffinityConfig mAffinity;

    /**
     * Agent event queue capacity. Must be &ge;
     * {@link EfsDispatcher#MIN_EVENT_QUEUE_SIZE}
     */
    private int mEventQueueCapacity;

    /**
     * Agent run queue capacity. Must be &gt; zero.
     */
    private int mRunQueueCapacity;

    /**
     * Maximum number of events an agent may process at any one
     * time.
     */
    private int mMaxEvents;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Default constructor required for typesafe config bean.
     */
    @SuppressWarnings ({"java:S1186"})
    public EfsDispatcherConfig()
    {}

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    @Override
    public String toString()
    {
        final StringBuilder output = new StringBuilder();

        output.append("[name=").append(mDispatcherName)
              .append(", thread type=").append(mThreadType)
              .append(", # threads=").append(mNumThreads)
              .append(", priority=").append(mPriority);

        if (mThreadType == ThreadType.SPINPARK ||
            mThreadType == ThreadType.SPINYIELD)
        {
            output.append(", spin limit=").append(mSpinLimit);
        }

        if (mThreadType == ThreadType.SPINPARK)
        {
            output.append(", park time=").append(mParkTime);
        }

        if (mAffinity != null)
        {
            output.append(", affinity=").append(mAffinity);
        }

        return (output.append(", event queue capacity=")
                      .append(mEventQueueCapacity)
                      .append(", run queue capacity=")
                      .append(mRunQueueCapacity)
                      .append(", max events=")
                      .append(mMaxEvents)
                      .append(']')
                      .toString());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns unique dispatcher name.
     * @return dispatcher name.
     */
    public String getDispatcherName()
    {
        return (mDispatcherName);
    } // end of getDispatcherName()

    /**
     * Returns thread type for underlying thread(s).
     * @return underlying thread type.
     */
    public ThreadType getThreadType()
    {
        return (mThreadType);
    } // end of getThreadType()

    /**
     * Returns number of underlying threads.
     * @return underlying thread count.
     */
    public int getNumThreads()
    {
        return (mNumThreads);
    } // end of getNumThreads()

    /**
     * Returns priority of underlying threads.
     * @return underlying thread priority.
     */
    public int getPriority()
    {
        return (mPriority);
    } // end of getPriority()

    /**
     * Returns priority of underlying threads. Applies only to
     * spin+park and spin+yield thread type.
     * @return underlying thread priority.
     */
    @Nullable
    @Optional
    public long getSpinLimit()
    {
        return (mSpinLimit);
    } // end of getSpinLimit()

    /**
     * Returns part time used by spin+park underlying thread(s).
     * @return underlying thread park time.
     */
    @Nullable
    @Optional
    public Duration getParkTime()
    {
        return (mParkTime);
    } // end of getParkTime()

    /**
     * Returns core affinity for underlying threads. This is an
     * optional property and may return {@code null}.
     * @return underlying thread core affinity.
     */
    @Nullable
    @Optional
    public ThreadAffinityConfig getAffinity()
    {
        return (mAffinity);
    } // end of getAffinity()

    /**
     * Returns agent event queue capacity. Will be &ge;
     * {@link EfsDispatcher#MIN_EVENT_QUEUE_SIZE}.
     * @return agent event queue capacity.
     */
    public int getEventQueueCapacity()
    {
        return (mEventQueueCapacity);
    } // end of getEventQueueCapacity()

    /**
     * Returns dispatcher agent run queue capacity (&gt; zero).
     * @return agent run queue capacity.
     */
    public int getRunQueueCapacity()
    {
        return (mRunQueueCapacity);
    } // end of getRunQueueCapacity()

    /**
     * Returns maximum number of events an {@link IEfsAgent} may
     * process at a time.
     * @return efs agent may process up to this many events at a
     * time.
     */
    public int getMaxEvents()
    {
        return (mMaxEvents);
    } // end of getMaxEvents()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets unique dispatcher name.
     * @param name dispatcher name.
     * @throws ConfigException
     * if {@code name} is either {@code null} or an empty string.
     */
    public void setDispatcherName(final String name)
    {
        if (Strings.isNullOrEmpty(name))
        {
            throw (
                new ConfigException.BadValue(
                    DISPATCHER_NAME_KEY,
                    "name is null or an empty string"));
        }

        mDispatcherName = name;
    } // end of setDispatcherName(String)

    /**
     * Sets dispatcher underlying thread type.
     * @param type underlying thread type.
     * @throws ConfigException
     * if {@code type} is {@code null}.
     */
    public void setThreadType(final ThreadType type)
    {
        if (type == null)
        {
            throw (
                new ConfigException.BadValue(
                    THREAD_TYPE_KEY, "type is null"));
        }

        mThreadType = type;
    } // end of setThreadType(ThreadType)

    /**
     * Sets number of underlying dispatcher threads.
     * @param numThreads underlying dispatcher thread count.
     */
    public void setNumThreads(final int numThreads)
    {
        if (numThreads <= 0)
        {
            throw (
                new ConfigException.BadValue(
                    NUM_THREADS_KEY,
                    "numThreads <= zero"));
        }

        mNumThreads = numThreads;
    } // end of setNumThreads(int)

    /**
     * Sets priority for all underlying dispatcher threads.
     * @param priority underlying dispatcher thread priority.
     * @throws ConfigException
     * if {@code priority} is either &lt;
     * {@link Thread#MIN_PRIORITY} or &gt;
     * {@link Thread#MAX_PRIORITY}.
     */
    public void setPriority(int priority)
    {
        if (priority < Thread.MIN_PRIORITY ||
            priority > Thread.MAX_PRIORITY)
        {
            throw (
                new ConfigException.BadValue(
                    PRIORITY_KEY,
                    "priority out of bounds"));
        }

        mPriority = priority;
    } // end of setPriority(int)

    /**
     * Sets spin limit for spin+park and spin+yield underlying
     * threads.
     * @param spinLimit spin+park, spin+yield spin limit.
     * @throws ConfigException
     * if {@code spinLimit} &le; zero.
     */
    public void setSpinLimit(final long spinLimit)
    {
        if (spinLimit <= 0L)
        {
            throw (
                new ConfigException.BadValue(
                    SPIN_LIMIT_KEY,
                    "spinLimit <= zero"));
        }

        mSpinLimit = spinLimit;
    } // end of setSpinLimit(long)

    /**
     * Sets park time for spin+park underlying dispatcher
     * threads.
     * @param parkTime spin+park park time.
     * @throws ConfigException
     * if {@code parkTime} is either {@code null} or &le; zero.
     */
    public void setParkTime(final Duration parkTime)
    {
        if (parkTime == null)
        {
            throw (
                new ConfigException.BadValue(
                    PARK_TIME_KEY,
                    "parkTime is null"));
        }

        if (parkTime.compareTo(Duration.ZERO) <= 0)
        {
            throw (
                new ConfigException.BadValue(
                    PARK_TIME_KEY,
                    "parkTime <= zero"));
        }

        mParkTime = parkTime;
    } // end of setParkTime(Duration)

    /**
     * Sets core affinity applied to underlying threads. A
     * {@code null} value is ignored.
     * @param affinity underlying thread core affinity.
     */
    public void setAffinity(@Nullable ThreadAffinityConfig affinity)
    {
        // Ignore null affinity values.
        if (affinity != null)
        {
            mAffinity = affinity;
        }
    } // end of setAffinity(ThreadAffinityConfig)

    /**
     * Sets efs agent event queue capacity. This capacity is
     * applied to each efs agent
     * <em>associated with this dispatcher</em>.
     * <p>
     * {@code capacity} should be a 2 power value. If not, event
     * queue capacity is set to the next 2 power value &gt; given
     * capacity.
     * </p>
     * @param capacity agent event queue capacity.
     * @throws ConfigException
     * if {@code capacity} &lt;
     * {@link EfsDispatcher#MIN_EVENT_QUEUE_SIZE}.
     */
    public void setEventQueueCapacity(final int capacity)
    {
        if (capacity < EfsDispatcher.MIN_EVENT_QUEUE_SIZE)
        {
            throw (
                new ConfigException.BadValue(
                    EVENT_QUEUE_CAPACITY_KEY,
                    "capacity < " +
                    EfsDispatcher.MIN_EVENT_QUEUE_SIZE));
        }

        mEventQueueCapacity = capacity;
    } // end of setEventQueueCapacity(int)

    /**
     * Set efs dispatcher agent queue capacity.
     * <p>
     * The capacity should be a 2 power value. If not, event
     * queue capacity is set to the next 2 power value &gt;
     * given capacity.
     * </p>
     * @param capacity agent queue capacity.
     * @throws ConfigException
     * if {@code capacity} &le; zero.
     */
    public void setRunQueueCapacity(int capacity)
    {
        if (capacity <= 0)
        {
            throw (
                new ConfigException.BadValue(
                    RUN_QUEUE_CAPACITY_KEY,
                    "capacity <= zero"));
        }

        mRunQueueCapacity = capacity;
    } // end of setRunQueueCapacity(int)

    /**
     * Sets maximum number of events an efs agent
     * <em>associated with this dispatcher</em> may process when
     * dispatched. This is done to prevent an agent from unfairly
     * dominating a dispatcher. Note: there is no limit to this
     * value and may be set to {@link Integer#MAX_VALUE}.
     * @param maxEvents agent may process up to this many events
     * at a time.
     * @throws ConfigException
     * if {@code maxEvents} &le; zero.
     */
    public void setMaxEvents(final int maxEvents)
    {
        if (maxEvents <= 0)
        {
            throw (
                new ConfigException.BadValue(
                    MAX_EVENTS_KEY,
                    "maxEvents <= zero"));
        }

        mMaxEvents = maxEvents;
    } // end of setMaxEvents(int)

    //
    // end of Set Methods.
    //-----------------------------------------------------------
} // end of class EfsDispatcherConfig
