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

package org.efs.timer;

import javax.annotation.Nullable;
import org.efs.event.IEfsEvent;

/**
 * Event reporting a timer expiration. Contains user-specified
 * timer name which may be {@code null} or an empty string.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsTimerEvent
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * User-specified timer name. May be {@code null} or an
     * empty string.
     */
    @Nullable private final String mTimerName;

    /**
     * {@code java.lang.System.nanoTime} when timer was scheduled
     * to expire. Provided so agent can determine delta between
     * timer expiration and timer delivery.
     */
    private final long mExpiration;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new timer event with the given timer name.
     * @param timerName expired timer's name. May be {@code null}
     * or an empty string.
     * @param expiration timer's scheduled expiration time in
     * Java nanoseconds.
     */
    public EfsTimerEvent(@Nullable final String timerName,
                         final long expiration)
    {
        mTimerName = timerName;
        mExpiration = expiration;
    } // end of EfsTimerEvent(String, long)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns expired timer's name as configured by agent. May
     * be {@code null}.
     * @return expired timer's name.
     */
    @Nullable public String timerName()
    {
        return (mTimerName);
    } // end of timerName()

    /**
     * Returns timer's scheduled expiration time in Java
     * nanoseconds (see {@link System#nanoTime()}). This value
     * allows agent to determine delay between timer's expiration
     * and delivery to agent. The idea being that an agent may
     * decide to take alternative action is timer delivery was
     * delayed beyond an acceptable time limit.
     * @return timer's scheduled expiration time in nanoseconds.
     */
    public long expiration()
    {
        return (mExpiration);
    } // end of expiration()

    //
    // end of Get Methods.
    //-----------------------------------------------------------
} // end of class EfsTimerEvent
