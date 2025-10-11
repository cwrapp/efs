//
// Copyright 2024 Charles W. Rapp
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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for {@link EScheduledExecutorSpinning},
 * {@link EScheduledExecutorSpinSleep}, and
 * {@link EScheduledExecutorSpinYield} scheduled executor types.
 * Contains spin limit setting and update flag common to all
 * three types. Also defines {@link #addTimer(ETimerImpl)},
 * {@link #removeTimer(ETimerImpl)}, and
 * {@link #signalShutdown()} abstract methods.
 *
 * @see EfsScheduledExecutorSpinPark
 * @see EfsScheduledExecutorSpinYield
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

/* package */ abstract class EfsScheduledExecutorAbstractSpin
    extends EfsScheduledExecutor
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Spin this many times waiting for a timer to expire before
     * parking the thread. Used only by spin+park and spin+sleep
     * thread types.
     */
    protected final long mSpinLimit;

    /**
     * Set to {@code true} when {@link #mTimers timers set) is
     * updated (either a new timer is added or an existing timer
     * removed).
     */
    protected final AtomicBoolean mUpdateFlag;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a spinning scheduled executor based on the given
     * builder settings.
     * @param builder contains scheduled executor configuration.
     */
    protected EfsScheduledExecutorAbstractSpin(final Builder builder)
    {
        super (builder);

        mUpdateFlag = new AtomicBoolean();
        mSpinLimit = builder.mSpinLimit;
    } // end of EfsScheduledExecutorAbstractSpin(...)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Abstract Method Implementations.
    //

    @Override
    protected final void addTimer(final EfsTimerImpl timer)
    {
        // Since the timers set is not locked, there is no way to
        // know if this new timer is the next to expire or
        // another timer being added simultaneously. This being
        // the case, add the timer and set the update flag.
        mTimers.add(timer);
        mUpdateFlag.set(true);

        // Interrupt this thread in case it is parked.
        this.interrupt();
    } // end of addTimer(EfsTimerImpl)

    @Override
    protected final void removeTimer(final EfsTimerImpl timer)
    {
        mTimers.remove(timer);
        mUpdateFlag.set(true);

        // Interrupt this thread in case it is parked.
        this.interrupt();
    } // end of removeTimer(EfsTimerImpl)

    //
    // end of Abstract Method Implementations.
    //-----------------------------------------------------------
} // end of class EfsScheduledExecutorAbstractSpin
