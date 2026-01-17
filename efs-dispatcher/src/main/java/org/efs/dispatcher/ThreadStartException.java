//
// Copyright 2025, 2026 Charles W. Rapp
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

import jakarta.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Exception thrown when a
 * {@link EfsDispatcherThread dispatcher thread} fails to
 * start. Contains failed dispatcher and thread names and
 * exception thrown by {@code Thread.start()} causing this
 * exception.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
public class ThreadStartException
    extends RuntimeException
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Serialization version identifier.
     */
    private static final long serialVersionUID = 0x00010000L;

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Thread belows to this dispatcher.
     */
    private final String mDispatcherName;

    /**
     * Name of thread which failed to start.
     */
    private final String mThreadName;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates new {@code ThreadStartException} instance without
     * detail message.
     * @param dispatcherName failed dispatcher's name.
     * @param threadName failed thread's name.
     */
    public ThreadStartException(final String dispatcherName,
                                final String threadName)
    {
        mDispatcherName = dispatcherName;
        mThreadName = threadName;
    } // end of ThreadStartException(String, String)

    /**
     * Creates new {@code ThreadStartException} instance with a
     * detail message.
     * @param dispatcherName failed dispatcher's name.
     * @param threadName failed thread's name.
     * @param msg detail message.
     */
    public ThreadStartException(final String dispatcherName,
                                final String threadName,
                                final String msg)
    {
        super(msg);

        mDispatcherName = dispatcherName;
        mThreadName = threadName;
    } // end of ThreadStartException(String)

    /**
     * Create new {@code ThreadStartException} instance with
     * detail message and cause.
     * @param dispatcherName failed dispatcher's name.
     * @param threadName failed thread's name.
     * @param msg detail message.
     * @param cause this exception's underlying cause (which is
     * saved for later retrieval by the
     * {@code Throwable.getCause()} method). A {@code null}
     * value is permitted, and indicates that the cause is
     * nonexistent or unknown.
     */
    public ThreadStartException(final String dispatcherName,
                                final String threadName,
                                final String msg,
                                @Nullable final Throwable cause)
    {
        super(msg, cause);

        mDispatcherName = dispatcherName;
        mThreadName = threadName;
    } // end of ThreadStartException(String, Throwable)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns failed dispatcher's name.
     * @return dispatcher name.
     */
    public String dispatcherName()
    {
        return (mDispatcherName);
    } // end of dispatcherName()

    /**
     * Returns failed thread's name.
     * @return thread name.
     */
    public String threadName()
    {
        return (mThreadName);
    } // end of threadName()

    //
    // end of Get Methods.
    //-----------------------------------------------------------
} // end of ThreadStartException
