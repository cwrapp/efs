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

import org.efs.dispatcher.IEfsAgent;

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
    // Locals.
    //

    /**
     * Unique agent name.
     */
    protected final String mAgentName;

    /**
     * Market data event file.
     */
    protected final EfsFile<TradeEvent> mTradeFile;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    protected AbstractTestAgent(final String agentName,
                                final EfsFile<TradeEvent> tradeFile)
    {
        mAgentName = agentName;
        mTradeFile = tradeFile;
    } // end of AbstractTestAgent(STring, EfsFile)

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
} // end of class AbstractTestAgent
