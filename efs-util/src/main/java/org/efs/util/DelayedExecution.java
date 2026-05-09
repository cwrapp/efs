//
// Copyright 2026 Charles W. Rapp
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

package org.efs.util;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

/**
 * This class provides static methods which perform a
 * {@code Runnable} task after a specified delay. This delay and
 * execution are performed on the caller's thread. This means
 * the caller is blocked until the execution is completed.
 * <p>
 * Note: timing is at millisecond granularity. Any delay &lt; one
 * millisecond will be treated as zero milliseconds.
 * </p>
 * <p>
 * This utility class is targeted towards unit testing. While
 * it appears to be a function provided by
 * <a href="https://github.com/awaitility/awaitility">awaitility</a>,
 * it is not since awaitility is
 * focused on waiting for a condition to be satisfied and not on
 * task execution.
 * </p>
 * <p>
 * This functionality is <em>not</em> meant to replace
 * {@code org.efs.timer} which provides scheduled execution for
 * the {@code IEfsAgent}s but only for simple scheduled task
 * execution for unit tests.
 * </p>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class DelayedExecution
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * When delay is {@code null}, a {@code NullPointerException}
     * is thrown with message {@value}.
     */
    public static final String NULL_DELAY = "delay is null";

    /**
     * When task is {@code null}, a {@code NullPointerException}
     * is thrown with message {@value}.
     */
    public static final String NULL_TASK = "task is null";

    /**
     * When delay is &lt; zero, an
     * {@code IllegalArgumentException} is thrown with message
     * {@value}.
     */
    public static final String NEGATIVE_DELAY = "delay < zero";

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Private constructor to prevent instantiation.
     */
    private DelayedExecution()
    {}

    //
    // end of Constructors.
    //-----------------------------------------------------------

    /**
     * Executes given task after waiting specified
     * <em>millisecond</em> delay. If delay &lt; one millisecond,
     * then task is executed immediately. Current thread is
     * blocked until delay and task execution completes.
     * <p>
     * Note: any exception thrown by task is allowed to flow back
     * to caller.
     * </p>
     * @param delay wait this duration before executing task.
     * @param task execute this delay after delay completes.
     * @throws NullPointerException
     * if either {@code delay} or {@code task} is {@code null}.
     * @throws IllegalArgumentException
     * if {@code delay} &lt; zero.
     */
    public static void delayedExecution(final Duration delay,
                                        final Runnable task)
    {
        Objects.requireNonNull(task, NULL_TASK);

        waitUntil(delay);
        task.run();
    } // end of delayedExecution(Duration, Runnable)

    /**
     * Waits on the current thread until specified delay is
     * reached. Current thread is blocked for this delay.
     * @param delay wait this many <em>milliseconds</em>.
     */
    public static void waitUntil(final Duration delay)
    {
        Objects.requireNonNull(delay, NULL_DELAY);

        if (delay.compareTo(Duration.ZERO) < 0)
        {
            throw (new IllegalArgumentException(NEGATIVE_DELAY));
        }

        long now = System.currentTimeMillis();
        final long deadline = (now + delay.toMillis());

        while (now < deadline)
        {
            LockSupport.parkUntil(deadline);
            now = System.currentTimeMillis();
        }
    } // end of waitUntil(Duration)
} // end of class DelayedExecution
