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

import java.util.concurrent.locks.LockSupport;
import javax.annotation.Nullable;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * Alternates between spinning while waiting for next timer to
 * expire and {@link LockSupport#park() yielding} the core.
 * This technique provides good timer delivery latency while not
 * requiring this executor thread to have core affinity.
 *
 * @see EScheduledExecutorSpinning
 * @see EScheduledExecutorSpinSleep
 * @see EScheduledExecutorBlocking
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

/* package */ final class EfsScheduledExecutorSpinYield
    extends EfsScheduledExecutorAbstractSpin
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger();

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new spin+park scheduled executor based on the
     * given builder settings.
     * @param builder contains scheduled executor configuration.
     */
    /* package */ EfsScheduledExecutorSpinYield(final Builder builder)
    {
        super (builder);
    } // end of EScheduledExecutorSpinYield(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Abstract Method Implementations.
    //

    @Override
    protected @Nullable EfsTimerImpl pollTimer()
    {
        long counter = mSpinLimit;
        EfsTimerImpl retval = null;

        // Is this thread still running?
        // Was an scheduled timer acquired?
        while (mRunFlag && (retval = nextTimer()) == null)
        {
            // Yes, this thread is still running.
            // No, there is no scheduled timer.
            // Spin limit reached?
            if (counter == 0)
            {
                // Yes. Take a nap before continuing.
                LockSupport.park();
                counter = mSpinLimit;
            }

            --counter;
        }

        return (retval);
    } // end of pollTimer()

    @Override
    protected boolean waitForExpiration(final EfsTimerImpl timer)
    {
        final long expiration = timer.expiration();
        long currNanos;
        long counter = mSpinLimit;

        sLogger.debug("{}: waiting {} nanos for timer to expire.",
                      getName(),
                      expiration);

        // Keep spinning until expiration time is reached or
        // timers map is updated.
        while ((currNanos = System.nanoTime()) < expiration &&
               !mUpdateFlag.compareAndSet(true, false))
        {
            // Time to take a nap?
            if (counter == 0)
            {
                // Yes. Nighty, night.
                LockSupport.park();
                counter = mSpinLimit;
            }

            --counter;
        }

        return (currNanos >= expiration);
    } // end of waitForExpiration(EfsTimerImpl)

    //
    // end of Abstract Method Implementations.
    //-----------------------------------------------------------
} // end of class EfsScheduledExecutorSpinYield
