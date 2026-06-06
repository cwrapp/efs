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

import com.google.errorprone.annotations.Immutable;
import java.time.Instant;
import org.efs.event.IEfsEvent;

/**
 * Internal event used to dispatch an event to
 * {@code EfsFile.onAdd(Instant, E)}.
 *
 * @param <E> efs event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
/* package */ final class AddInternalEvent<E extends IEfsEvent>
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Event publish timestamp.
     */
    private final Instant mPublishTimestamp;

    /**
     * Published event.
     */
    private final E mEvent;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates an interval event add event.
     * @param pubTime event publish timestamp.
     * @param event published event.
     */
    /* package */ AddInternalEvent(final Instant pubTime,
                                   final E event)
    {
        mPublishTimestamp = pubTime;
        mEvent = event;
    } // end of AddInternalEvent(Instant, E)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * @InheritDoc
     */
    @Override
    public String toString()
    {
        return (String.format("[timestamp=%s, event=%s]",
                              mPublishTimestamp,
                              mEvent));
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns event publish timestamp.
     * @return event publish timestamp.
     */
    /* package */ Instant publishTimestamp()
    {
        return (mPublishTimestamp);
    } // end of publishTimestamp()

    /**
     * Returns published event.
     * @return published event.
     */
    /* package */ E event()
    {
        return (mEvent);
    } // end of event()

    //
    // end of Get Methods.
    //-----------------------------------------------------------
} // end of class AddInternalEvent
