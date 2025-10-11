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

import javax.annotation.Nullable;

/**
 * Lists thread types available to efs users when configuring
 * efs dispatchers. A dispatcher thread is either:
 * <ul>
 *   <li>
 *     blocks while waiting for the desire event to occur,
 *   </li>
 *   <li>
 *     spins, repeatedly check for the event to occur, or
 *   </li>
 *   <li>
 *     spins a specified number of times on the event check and,
 *     if the event does not occur when the spin limit is
 *     reached, then
 *     {@link java.util.concurrent.locks.LockSupport#parkNanos(long) parks}
 *     for the nanosecond time.
 *   </li>
 *   <li>
 *     spins a specified number of times on the event check and,
 *     if the event does not occur when the spin limit is
 *     reached, then
 *     {@link java.util.concurrent.locks.LockSupport#park() parks}
 *     for an indefinite time.
 *   </li>
 * </ul>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public enum ThreadType
{
    /**
     * The thread blocks while waiting for the desired event
     * to occur. The thread continues if either the event
     * happened or an interrupt was caught. It is likely that a
     * thread using this type will be moved off core when
     * blocked.
     */
    BLOCKING ("blocking"),

    /**
     * The thread repeatedly checks if the event has arrived
     * using a non-blocking call without pausing in between
     * checks. This thread type effectively monopolizes a core and
     * keeps the thread on core.
     */
    SPINNING ("spinning"),

    /**
     * This thread repeatedly checks for the event using a
     * non-blocking call but only for a fixed number of times
     * (the spin limit). When the spin limit is reached, the
     * thread
     * {@link java.util.concurrent.locks.LockSupport#parkNanos(long) parks}
     * for a specified nanosecond time limit. Upon waking up,
     * the threads resets the spin count and starts the repeated,
     * non-blocking checks again. This continues until an event
     * is detected.
     * <p>
     * This type is a compromise between blocking and spinning
     * by aggressively spinning until the event occurs but not
     * entirely monopolizing a core. By parking, the thread may
     * be moved off core but not as likely as in a blocking
     * thread.
     * </p>
     */
    SPINPARK ("spin+park"),

    /**
     * This thread repeatedly check for the event using a
     * non-blocking call but only for a fixed number of times
     * (the spin limit). When the spin limit is reached, the
     * thread
     * {@link java.util.concurrent.locks.LockSupport#park() yields}
     * the core to another thread. When the thread re-gains the
     * core, the the spin count is reset and the repeated,
     * non-blocking checks start again. This continues until an
     * event is detected.
     * <p>
     * This type is a compromise between blocking and spinning
     * by aggressively spinning until an event occurs but not
     * entirely monopolizing a core. By yielding, the thread
     * may be moved off core but not as likely as in a blocking
     * thread.
     * </p>
     */
    SPINYIELD ("spin+yield");

//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * The name used in {@code toString}.
     */
    private final String mTextName;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new thread type instance with given
     * human-readable name.
     * @param name human-readable name for this thread type.
     */
    private ThreadType(final String name)
    {
        mTextName = name;
    } // end of ThreadType(String)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    @Override
    public String toString()
    {
        return (mTextName);
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns the thread type text name.
     * @return text name.
     */
    public String textName()
    {
        return (mTextName);
    } // end of textName()

    /**
     * Returns the thread type with a text name equaling
     * {@code s}, ignoring case. Will return {@code null} if
     * {@code s} does not match any thread type text name.
     * @param s compare the thread type text name to this given
     * value.
     * @return thread type for the given text name.
     */
    @Nullable public static ThreadType find(final String s)
    {
        final ThreadType[] values = ThreadType.values();
        final int size = values.length;
        int i;
        ThreadType retval = null;

        for (i = 0; i < size && retval == null; ++i)
        {
            if ((values[i].mTextName).equalsIgnoreCase(s))
            {
                retval = values[i];
            }
        }

        return (retval);
    } // end of find(String)

    //
    // end of Get Methods.
    //-----------------------------------------------------------
} // end of enum ThreadType
