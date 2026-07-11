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

import jakarta.annotation.Nonnull;
import java.time.Instant;
import java.util.Set;
import javax.annotation.concurrent.Immutable;
import org.efs.event.IEfsEvent;

/**
 * Contains event publish timestamp and row index for a given
 * event.
 *
 * @param <E> efs event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
public final class EfsRow<E extends IEfsEvent>
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Timestamp when event was published to event file.
     */
    private final Instant mPublishTimestamp;

    /**
     * Event's unique row index within event file.
     */
    private final long mRowIndex;

    /**
     * Immutable set containing event row tags. These tags are
     * user-defined. This set may be empty but may not contain
     * {@code null} values.
     */
    private final Set<Integer> mTags;

    /**
     * Published event itself.
     */
    private final E mEvent;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new efs event row for the given publish
     * timestamp, row index, and event.
     * @param timestamp publish timestamp.
     * @param rowIndex row index.
     * @param tags user-defined tags associated with this row.
     * @param event actual efs event.
     */
    /* package */ EfsRow(final Instant timestamp,
                         final long rowIndex,
                         final Set<Integer> tags,
                         final E event)
    {
        mPublishTimestamp = timestamp;
        mRowIndex = rowIndex;
        mTags = tags;
        mEvent = event;
    } // end of EfsRow(Instant, int, Set<>, E)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns text containing row index, publish timestamp, and
     * event.
     * @return efs row as text.
     */
    @Override
    public String toString()
    {
        return (String.format("[%,d] %s: %s",
                              mRowIndex,
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
     * Returns timestamp when event was published to
     * {@link EfsFile}.
     * @return event publish timestamp.
     */
    @Nonnull
    public final Instant getPublishTimestamp()
    {
        return (mPublishTimestamp);
    } // end of getPublishTimestamp()

    /**
     * Returns event index within {@link EfsFile}.
     * @return event row index.
     */
    public final long getRowIndex()
    {
        return (mRowIndex);
    } // end of getRowIndex()

    /**
     * Returns user-define tags immutable set. Set may be empty
     * but does not contain {@code null} values.
     * @return immutable set of user defined tags.
     */
    @Nonnull
    public final Set<Integer> getTags()
    {
        return (mTags);
    } // end of getTags()

    /**
     * Returns event stored in {@link EfsFile}.
     * @return stored event.
     */
    @Nonnull
    public final E getEvent()
    {
        return (mEvent);
    } // end of getEvent()

    //
    // end of Get Methods.
    //-----------------------------------------------------------
} // end of class EfsRow
