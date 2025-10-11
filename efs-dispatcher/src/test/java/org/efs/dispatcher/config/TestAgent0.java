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

package org.efs.dispatcher.config;

import org.efs.dispatcher.IEfsAgent;

/**
 * Test efs agent needed to successfully set dispatcher classes
 * property.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class TestAgent0
    implements IEfsAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    public static final String AGENT_NAME = "agent-0";

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // IEfsAgent Interface Implementation.
    //

    @Override
    public String name()
    {
        return (AGENT_NAME);
    } // end of name()

    //
    // end of IEfsAgent Interface Implementation.
    //-----------------------------------------------------------
} // end of class TestAgent0
