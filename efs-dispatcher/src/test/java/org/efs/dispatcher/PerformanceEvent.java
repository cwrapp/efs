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

package org.efs.dispatcher;

import javax.annotation.Nullable;
import org.efs.event.IEfsEvent;

/**
 * Used to measure event dispatch and processing delta within a
 * JVM.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class PerformanceEvent
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Performance event index.
     */
    public final int index;

    /**
     * When event was posted to dispatcher.
     */
    public final long nanotime;

    /**
     * Post reply to this object.
     */
    @Nullable public final ReplyTo<ReplyEvent> replyTo;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public PerformanceEvent(final int index,
                            final long nanotime,
                            @Nullable final ReplyTo<ReplyEvent> replyTo)
    {
        this.index = index;
        this.nanotime = nanotime;
        this.replyTo = replyTo;
    } // end of PerformanceEvent(int, long, ReplyTo<>)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    @Override
    public String toString()
    {
        final StringBuilder retval = new StringBuilder();

        return (retval.append("[index=").append(index)
                      .append(", nanotime=").append(nanotime)
                      .append(']')
                      .toString());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------
} // end of class PerformanceEvent
