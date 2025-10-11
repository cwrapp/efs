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

import javax.annotation.Nullable;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * Provides the lowest latency delivery of expired timer task its
 * eBus object's event queue. This scheduled executor achieves
 * this low latency by spinning on a tight loop waiting for the
 * next timer to expire.
 * <p>
 * A spinning scheduled executor is best used with thread
 * affinity for a core isolated from the operating system.
 * </p>
 *
 * @see EScheduledExecutorSpinSleep
 * @see EScheduledExecutorSpinYield
 * @see EScheduledExecutorBlocking
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

/* package */ final class EfsScheduledExecutorSpinning
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
     * Creates a new spinning eBus scheduled executor instance
     * based on the given builder settings.
     * @param builder scheduled executor configuration.
     */
    /* package */ EfsScheduledExecutorSpinning(final Builder builder)
    {
        super (builder);
    } // end of EScheduledExecutorSpinning(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Abstract Method Implementations.
    //

    @Override
    protected @Nullable EfsTimerImpl pollTimer()
    {
        EfsTimerImpl retval = null;

        while (mRunFlag && (retval = nextTimer()) == null)
        {
            // Keep spinning until a timer is scheduled.
        }

        return (retval);
    } // end of pollTimer()

    @Override
    protected boolean waitForExpiration(final EfsTimerImpl timer)
    {
        final long expiration = timer.expiration();
        long currNanos;

        sLogger.debug("{}: waiting {} nanos for timer to expire.",
                      getName(),
                      expiration);

        // Keep spinning until expiration time is reached or
        // timers map is updated.
        while ((currNanos = System.nanoTime()) < expiration &&
               !mUpdateFlag.compareAndSet(true, false))
        {}

        return (currNanos >= expiration);
    } // end of waitForExpiration(EfsTimerImpl)

    //
    // end of Abstract Method Implementations.
    //-----------------------------------------------------------
} // end of class EfsScheduledExecutorSpinning
