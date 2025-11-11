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

import org.efs.dispatcher.EfsDispatcher;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * This before all extension initializes efs dispatchers once
 * for all package tests.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class DispatcherInitialization
    implements BeforeAllCallback
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String DISPATCHER_FILE_NAME =
        "./src/test/resources/test-dispatchers.conf";

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Set to {@code true} when efs dispatcher initialization
     * is performed.
     */
    private static boolean sIsStarted = false;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // BeforeAllCallback Interface Implementation.
    //

    @Override
    public void beforeAll(final ExtensionContext context)
        throws Exception
    {
        if (!sIsStarted)
        {
            sIsStarted = true;

            EfsDispatcher.loadDispatchersConfigFile(
                DISPATCHER_FILE_NAME);

        }
    } // end of beforeAll(ExtensionContext)

    //
    // end of BeforeAllCallback Interface Implementation.
    //-----------------------------------------------------------
} // end of class DispatcherInitialization
