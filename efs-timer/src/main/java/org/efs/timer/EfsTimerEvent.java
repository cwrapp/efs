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

import jakarta.annotation.Nullable;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;
import org.efs.event.IEfsEvent;

/**
 * Event reporting a timer expiration. Contains:
 * <ul>
 *   <li>
 *     user-specified timer name which may be {@code null} or an
 *     empty string.
 *   </li>
 *   <li>
 *     {@link System#nanoTime()} timestamp when timer was
 *     dispatched to agent. Provided so agent can determine delta
 *     between timer dispatchTimestamp and timer event delivery.
 *   </li>
 * </ul>
 *
 *
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
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
     * User-specified datum forwarded in timer event. May be
     * {@code null}. This object is <em>not</em> included in the
     * timer event hash code.
     */
    @Nullable private final Object mDatum;

    /**
     * {@code java.lang.System.nanoTime} when timer event was
     * dispatched to agent. Provided so agent can determine
     * delta between timer expiration and timer event delivery.
     */
    private final long mDispatchTimestamp;

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
     * @param daturm user-specified datum forwarded in this timer
     * event.
     * @param dispatchTimestamp Java nanosecond timestamp when
     * this timer event was dispatched to agent.
     */
    /* package */ EfsTimerEvent(@Nullable final String timerName,
                                @Nullable final Object datum,
                                final long dispatchTimestamp)
    {
        mTimerName = timerName;
        mDatum = datum;
        mDispatchTimestamp = dispatchTimestamp;
    } // end of EfsTimerEvent(String, Object, long)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns {@code true} if {@code o} is a non-{@code null}
     * timer event with the same name and dispatcher timestamp as
     * this timer event.
     * @param o comparison object.
     * @return {@code true} if {@code o} equals this timer event.
     */
    @Override
    public boolean equals(final Object o)
    {
        boolean retcode = (this == o);

        if (!retcode && o instanceof EfsTimerEvent)
        {
            final EfsTimerEvent e = (EfsTimerEvent) o;

            retcode =
                (Objects.equals(mTimerName, e.mTimerName) &&
                 mDispatchTimestamp == e.mDispatchTimestamp);
        }

        return (retcode);
    } // end of equals(Object)

    /**
     * Returns hash code based on timer name and dispatch
     * timestamp.
     * @return hash code based on timer name and dispatch
     * timestamp.
     */
    @Override
    public int hashCode()
    {
        return (Objects.hash(mTimerName, mDispatchTimestamp));
    } // end of hashCode()

    /**
     * Returns textual representation of timer event.
     * @return textual representation of timer event.
     */
    @Override
    public String toString()
    {
        return (
            String.format(
                "[timer name=%s, dispatch timestamp=%,d]",
                (mTimerName == null ?
                 "(not set)" :
                 mTimerName),
                mDispatchTimestamp));
    } // end to toString()

    //
    // end of Object Method Overrides.
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
     * Returns optional user-provided datum forwarded in this
     * timer. May be {@code null}
     * @return optional, user-provided datum.
     */
    @Nullable public Object datum()
    {
        return (mDatum);
    } // end of datum()

    /**
     * Returns when timer event dispatched to agent in Java
     * nanoseconds (see {@link System#nanoTime()}). This value
     * allows agent to determine delay between timer's expiration
     * and delivery to agent. The idea being that an agent may
     * decide to take alternative action if timer delivery was
     * delayed beyond an acceptable time limit.
     * @return timer event dispatch time in nanoseconds.
     */
    public long dispatchTimestamp()
    {
        return (mDispatchTimestamp);
    } // end of dispatchTimestamp()

    //
    // end of Get Methods.
    //-----------------------------------------------------------
} // end of class EfsTimerEvent
