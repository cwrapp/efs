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

import java.time.Clock;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.efs.dispatcher.IEfsAgent;
import org.efs.io.EfsFile.AccessMode;

/**
 * Base class for publisher and retriever agents.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public abstract class AbstractTestAgent
    implements IEfsAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final long MIN_TIME_DELTA = 100_000L;
    private static final long MAX_TIME_DELTA = 5_000_000L;

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Used to generate random prices, sizes, and trade delays.
     */
    protected static final Random sRandomizer =
        ThreadLocalRandom.current();

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Unique agent name.
     */
    protected final String mAgentName;

    /**
     * Access trade file using this mode.
     */
    protected final AccessMode mAccessMode;

    /**
     * Market data event file.
     */
    protected final EfsFile<TradeEvent> mTradeFile;

    /**
     * Agent connect to trade file.
     */
    protected EfsFileConnection<TradeEvent> mTradeConnection;

    /**
     * Test clock always updated to latest publish timestamp.
     */
    protected Clock mTestClock;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    protected AbstractTestAgent(final String agentName,
                                final AccessMode accessMode,
                                final EfsFile<TradeEvent> tradeFile)
    {
        mAgentName = agentName;
        mAccessMode = accessMode;
        mTradeFile = tradeFile;
    } // end of AbstractTestAgent(STring, AccessMode, EfsFile)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // IEfsAgent Interface Implementation.
    //

    @Override
    public final String name()
    {
        return (mAgentName);
    } // end of name()

    //
    // end of IEfsAgent Interface Implementation.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    public final AccessMode accessMode()
    {
        return (mAccessMode);
    } // end of accessMode()

    public final EfsFileConnection<TradeEvent> connection()
    {
        return (mTradeConnection);
    } // end of connection()

    public final long rowCount()
    {
        return (mTradeConnection.rowCount());
    } // end of rowCount()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Opens connect to trade file.
     */
    public void open()
    {
        mTradeConnection = mTradeFile.connect(mAccessMode, this);
    } // end of open()

    /**
     * Closes connect to trade file.
     */
    public void close()
    {
        mTradeConnection.close();
    } // end of close()

    protected Duration generateTimeDelta()
    {
        final long nanodelta =
            sRandomizer.nextLong(
                MIN_TIME_DELTA, MAX_TIME_DELTA);

        return (Duration.ofNanos(nanodelta));
    } // end of generateTimeDelta()
} // end of class AbstractTestAgent
