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
import java.util.Set;
import java.util.stream.Collectors;
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
     * Immutable set containing zero or more user-defined tags
     * for this event.
     */
    private final Set<Integer> mTags;

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
     * @param tags user-defined event tags.
     * @param event published event.
     */
    /* package */ AddInternalEvent(final Instant pubTime,
                                   final Set<Integer> tags,
                                   final E event)
    {
        mPublishTimestamp = pubTime;
        mTags = tags;
        mEvent = event;
    } // end of AddInternalEvent(Instant, Set<>, E)

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
        return (
            String.format(
                "[timestamp=%s, tags=%s, event=%s]",
                mPublishTimestamp,
                mTags.stream()
                     .map(String::valueOf)
                     .collect(Collectors.joining(", ")),
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
     * Returns immutable set of user-defined event tags.
     * @return user-defined event tags set.
     */
    /* package */ Set<Integer> tags()
    {
        return (mTags);
    } // end of tags()

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
