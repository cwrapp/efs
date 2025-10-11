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

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * Uses a lock and condition to wait on timer expiration and to
 * detect when a new timer is added or removed which impacts when
 * the next timer expiration occurs. This timer has the greatest
 * latency with respect to detecting an expiration and posting
 * the associated task to the {@code IEfsAgent}'s event queue.
 * The reason is that acquiring a lock and waiting on a condition
 * will likely result in the executor thread being removed from
 * its core. That means when the expiration occurs, the thread
 * must wait to be placed back on a core before it can deliver
 * the timer task to the eBus object queue.
 *
 * @see EfsScheduledExecutorSpinning
 * @see EfsScheduledExecutorSpinPark
 * @see EfsScheduledExecutorSpinYield
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

/* package */ final class EfsScheduledExecutorBlocking
    extends EfsScheduledExecutor
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

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Acquire this lock prior when either updating timers set,
     * retrieving next scheduled timer, or waiting for timer to
     * expire.
     */
    private final Lock mUpdateLock;

    /**
     * Signal scheduled executor thread that a new,
     * next-to-expire timer was added to timers set.
     */
    private final Condition mUpdateCondition;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new blocking scheduled executor based on builder
     * settings.
     * @param builder contains executor configuration.
     */
    @SuppressWarnings ({"java:S1172"})
    protected EfsScheduledExecutorBlocking(final Builder builder)
    {
        super (builder);

        mUpdateLock = new ReentrantLock();
        mUpdateCondition = mUpdateLock.newCondition();
    } // end of EfsScheduledExecutorBlocking(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Abstract Method Implementations.
    //

    @Override
    protected void addTimer(final EfsTimerImpl timer)
    {
        final EfsTimerImpl nextTimer = nextTimer();

        mUpdateLock.lock();
        try
        {
            mTimers.add(timer);

            // Is this the new next-to-expire timer?
            // It is if 1) there was no timer scheduled or
            // 2) the new timer expires prior to the current
            // next-to-expire timer.
            if (nextTimer == null ||
                timer.expiration() < nextTimer.expiration())
            {
                // Yes. Inform waitForExpiration of this fact.
                mUpdateCondition.signal();
            }
        }
        finally
        {
            mUpdateLock.unlock();
        }
    } // end of addTimer(EfsTimerImpl)

    @Override
    protected void removeTimer(final EfsTimerImpl timer)
    {
        final EfsTimerImpl nextTimer = nextTimer();

        mUpdateLock.lock();
        try
        {
            mTimers.remove(timer);

            // Was the next timer just removed?
            if (timer == nextTimer)
            {
                // Yes. Inform waitForExpiration of this fact.
                mUpdateCondition.signal();
            }
        }
        finally
        {
            mUpdateLock.unlock();
        }
    } // end of removeTimer(EfsTimerImpl)

    @Override
    protected @Nullable EfsTimerImpl pollTimer()
    {
        EfsTimerImpl retval = null;

        mUpdateLock.lock();
        try
        {
            // Wait for a new scheduled timer to appear or for
            // scheduled executor to be shut down.
            while (mRunFlag && (retval = nextTimer()) == null)
            {
                try
                {
                    mUpdateCondition.await();
                }
                catch (InterruptedException interrupt)
                {}
            }
        }
        finally
        {
            mUpdateLock.unlock();
        }

        return (retval);
    } // end of pollTimer()

    // This lock/condition is inside the EScheduledExector.run()
    // while loop.
    @SuppressWarnings ({"java:S2274"})
    @Override
    protected boolean waitForExpiration(final EfsTimerImpl timer)
    {
        final long delay = (timer.delay()).toNanos();
        boolean retcode = false;

        sLogger.debug("{}: waiting {} nanos for timer to expire.",
                      getName(),
                      delay);

        mUpdateLock.lock();
        try
        {
            // Timer expired if awaitNanos returns value <= zero.
            retcode = (mUpdateCondition.awaitNanos(delay) <= 0L);
        }
        catch (InterruptedException interrupt)
        {
            // Ignore and return false.
        }
        finally
        {
            mUpdateLock.unlock();
        }

        return (retcode);
    } // end of waitForExpiration(EfsTimerImpl)

    //
    // end of Abstract Method Implementations.
    //-----------------------------------------------------------
} // end of class EfsScheduledExecutorBlocking
