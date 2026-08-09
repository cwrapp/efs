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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.efs.dispatcher.IEfsAgent;


/**
 * Test agent used to collect exhausted event rows.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class TestExhaust
    implements IEfsAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Unique agent name.
     */
    private final String mAgentName;

    /**
     * Store exhausted row in this list.
     */
    private final List<EfsRow<TradeEvent>> mExhaustedRows;

    /**
     * Market data event file.
     */
    private EfsFile<TradeEvent> mTradeFile;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public TestExhaust(final String agentName)
    {
        mAgentName = agentName;

        mExhaustedRows = new ArrayList<>();
    } // end of TestExhaust()

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

    public int tradesExhausted()
    {
        return (mExhaustedRows.size());
    } // end of tradesExhausted()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    public void tradeFile(final EfsFile<TradeEvent> file)
    {
        mTradeFile = file;
    } // end of tradeFile(EfsFile<>)

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Exhaust Methods.
    //

    public void onExhaust(final EfsRow<TradeEvent> row)
    {
        mExhaustedRows.add(row);
    } // end of onExhaust(EfsRow<>)

    //
    // end of Exhaust Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    public Iterator<EfsRow<TradeEvent>> onInitialize()
    {
        return (mExhaustedRows.iterator());
    } // end of onInitialize()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------
} // end of class TestExhaust
