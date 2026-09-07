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

package org.efs.io;

import jakarta.annotation.Nullable;
import java.util.Iterator;
import java.util.concurrent.locks.LockSupport;
import org.efs.io.EfsFile.AccessMode;

/**
 * Posts a fixed number of rows to given trade file. This posting
 * is done on the test thread.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class TestLimitPublisher
    extends AbstractTestAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Wait 500 microseconds between posting trades.
     */
    private static final long WAIT_TIME_NANOS = 500_000L;

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Store caught exception here.
     */
    private Throwable mException;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public TestLimitPublisher(final String agentName,
                              final EfsFile<TradeEvent> tradeFile)
    {
        super (agentName, AccessMode.WRITE_ONLY, tradeFile);
    } // end of TestLimitPublisher(String, EfsFile<>)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns exception caught while posting events to event
     * file. May return {@code null}.
     * @return exception thrown when adding event to event file.
     */
    @Nullable
    public Throwable caughtException()
    {
        return (mException);
    } // end of caughtException()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Synchronously posts trade events provided by row iterator.
     * @param rIt event row iterator.
     */
    public void postTrades(final Iterator<EfsRow<TradeEvent>> rIt)
    {
        while (rIt.hasNext() && mException == null)
        {
            try
            {
                mTradeConnection.add((rIt.next()).getEvent());

                // Wait before posting next exception.
                LockSupport.parkNanos(WAIT_TIME_NANOS);
            }
            catch (Throwable tex)
            {
                mException = tex;
            }
        }
    } // end of postTrades(Iterator<>)
} // end of class TestLimitPublisher
