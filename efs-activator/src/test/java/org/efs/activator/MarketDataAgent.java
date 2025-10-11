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

package org.efs.activator;

/**
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class MarketDataAgent
    implements IEfsActivateAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    public static final String AGENT_NAME = "market-data-agent";

    //-----------------------------------------------------------
    // Statics.
    //

    //-----------------------------------------------------------
    // Locals.
    //

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of MarketDataAgent.
     */
    public MarketDataAgent()
    {
    } // end of MarketDataAgent()

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // IEfsActivateAgent Interface Implementation.
    //

    @Override
    public String name()
    {
        return (AGENT_NAME);
    } // end of name()

    @Override
    public void startup()
    {
        // TODO
    } // end of startup()

    @Override
    public void activate()
    {
        // TODO
    } // end of activate()

    @Override
    public void deactivate()
    {
        // TODO
    } // end of deactivate()

    @Override
    public void stop()
    {
        // TODO
    } // end of stop()

    //
    // end of IEfsActivateAgent Interface Implementation.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    //
    // end of Set Methods.
    //-----------------------------------------------------------
} // end of class MarketDataAgent
