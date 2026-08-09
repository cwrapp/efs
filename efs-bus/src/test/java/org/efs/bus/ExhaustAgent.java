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

package org.efs.bus;

import org.efs.dispatcher.IEfsAgent;

/**
 * Counts up exhausted events.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class ExhaustAgent
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
     * Tracks number of exhaust calls.
     */
    private int mExhaustCount;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of ExhaustAgent.
     */
    public ExhaustAgent(final String name)
    {
        mAgentName = name;
    } // end of ExhaustAgent(String)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // IEfsAgent Interface Implementation.
    //

    @Override
    public String name()
    {
        return (mAgentName);
    } // end of name()

    //
    // end of IEfsAgent Interface Implementation.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    public int exhaustCount()
    {
        return (mExhaustCount);
    } // end of exhaustCount()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    public void resetCount()
    {
        mExhaustCount = 0;
    } // end of resetCount()

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Exhaust Method.
    //

    public void onExhaust(final EfsEnvelope<PerformanceEvent> event)
    {
        ++mExhaustCount;
    } // end of onExhaust(EfsEnvelope<>)

    //
    // end of Exhaust Method.
    //-----------------------------------------------------------
} // end of class ExhaustAgent
