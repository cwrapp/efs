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
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;


/**
 * A child pong agent is encapsulated within a {@link PongRouter}
 * and receives performance events when said events are routed
 * to this child.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class ChildPongAgent
    implements IEfsAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    //-----------------------------------------------------------
    // Statics.
    //

    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(ChildPongAgent.class);

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Unique agent name within this JVM.
     */
    private final String mAgentName;

    /**
     * Parent router used to publish pong responses and track
     * dispatch latencies.
     */
    private final PongRouter mRouter;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public ChildPongAgent(final String agentName,
                          final PongRouter router)
    {
        mAgentName = agentName;
        mRouter = router;
    } // end of ChildPongAgent(...)

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

    //-----------------------------------------------------------
    // Event Methods.
    //

    /* package */ void onEvent(final PerformanceEvent event)
    {
        final long delta = (System.nanoTime() - event.nanotime);

        if (mRouter.updateLatency(delta, mAgentName))
        {
            final int eventIndex = event.index;

            mRouter.echo(event, mAgentName);
        }
    } // end of onEvent(PerformanceEvent)

    //
    // end of Event Methods.
    //-----------------------------------------------------------
} // end of class ChildPongAgent
