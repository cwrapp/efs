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

import com.google.common.base.Strings;
import com.typesafe.config.ConfigException;
import com.typesafe.config.Optional;
import java.time.Duration;
import javax.annotation.Nullable;
import org.efs.dispatcher.config.ThreadAffinityConfig;
import org.efs.dispatcher.config.ThreadType;

/**
 * {@link org.efs.timer.EfsScheduledExecutor EfsScheduledExecutor}
 * configuration definition. Contains all properties required to
 * build an {@code EfsScheduledExecutor}. This class is
 * defined as a
 * <a href="https://github.com/lightbend/config" target="_blank">typesafe</a>
 * configuration bean.
 * <h2>Example EfsScheduledExecutorConfig File Entry</h2>
 * For further explanation on what these properties mean and
 * valid settings, see property set method (e.g.
 * {@link #setSpinLimit(long)}.
 * <pre><code>executorName = "AlgoTimer"
threadType = SPINPARK
priority = 10
spinLimit = 250000000
parkTime = 500 nanos
</code></pre>
 *
 * @see EfsScheduledExecutorsConfig
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsScheduledExecutorConfig
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
     * Key {@value} contains a unique scheduled executor name.
     * Must be unique within JVM.
     */
    public static final String EXECUTOR_NAME_KEY =
        "executorName";

    /**
     * Key {@value} contains scheduled executor
     * {@link ThreadType thread type}.
     */
    public static final String THREAD_TYPE_KEY = "threadType";

    /**
     * Key {@value} contains scheduled executor thread priority.
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

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Executor name. Must be unique within the JVM.
     */
    private String mExecutorName;

    /**
     * Defines underlying scheduled executor thread type.
     */
    private ThreadType mThreadType;

    /**
     * Defines priority used for each underlying thread.
     */
    private int mPriority;

    /**
     * Defines spin limit used by
     * {@link ThreadType#SPINPARK spin+park} and
     * {@link ThreadType#SPINYIELD spin+yield} thread type.
     */
    @Nullable
    @Optional
    private long mSpinLimit;

    /**
     * Defines park time used by
     * {@link ThreadType#SPINPARK spin+park} thread type.
     */
    @Nullable
    @Optional
    private Duration mParkTime;

    /**
     * Define optional scheduled executor thread affinity.
     */
    @Nullable
    @Optional
    private ThreadAffinityConfig mAffinity;

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
    public EfsScheduledExecutorConfig()
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

        output.append("[name=").append(mExecutorName)
              .append(", thread type=").append(mThreadType)
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

        output.append(']');

        return (output.toString());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns unique executor name.
     * @return executor name.
     */
    public String getExecutorName()
    {
        return (mExecutorName);
    } // end of getExecutorName()

    /**
     * Returns thread type for underlying executor thread.
     * @return underlying executor thread type.
     */
    public ThreadType getThreadType()
    {
        return (mThreadType);
    } // end of getThreadType()

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

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets unique scheduled executor name.
     * @param name scheduled executor name.
     * @throws ConfigException
     * if {@code name} is either {@code null} or an empty string.
     */
    public void setExecutorName(final String name)
    {
        if (Strings.isNullOrEmpty(name))
        {
            throw (
                new ConfigException.BadValue(
                    EXECUTOR_NAME_KEY,
                    "name is null or an empty string"));
        }

        mExecutorName = name;
    } // end of setExecutorName(String)

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
     * Sets priority for all underlying executor threads.
     * @param priority underlying executor thread priority.
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
     * executor thread.
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
     * Sets park time for spin+park underlying executor
     * thread.
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
     * Sets core affinity applied to underlying thread. A
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

    //
    // end of Set Methods.
    //-----------------------------------------------------------
} // end of class EfsScheduledExecutorConfig
