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
import java.util.Objects;
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
    // Constants.
    //

    // Exception messages.

    /**
     * A {@code null timestamp} argument results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_TIMESTAMP =
        "timestamp is null";

    /**
     * A {@code rowIndex} &lt; zero results in an
     * {@code IllegalArgumentException} with message {@value}.
     */
    public static final String INVALID_ROW_INDEX =
        "rowIndex < zero";

    /**
     * A {@code null tags} argument results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_TAGS = "tags is null";

    /**
     * A {@code null event} argument results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_EVENT = "event is null";

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
    private EfsRow(final Instant timestamp,
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

    /**
     * Returns a new efs event row generated from the given
     * arguments. The event tags are an empty set.
     * @param <E> efs event type.
     * @param timestamp event timestamp.
     * @param rowIndex event row index.
     * @param event efs event contained in row.
     * @return efs event row.
     * @throws NullPointerException
     * if either {@code timestamp} or {@code event} is
     * {@code null}.
     * @throws IllegalArgumentException
     * if {@code rowIndex} &lt; zero.
     */
    public static <E extends IEfsEvent> EfsRow<E> createRow(@Nonnull final Instant timestamp,
                                                            final long rowIndex,
                                                            @Nonnull final E event)
    {
        return (
            createRow(
                timestamp, rowIndex, EfsFile.NO_TAGS, event));
    } // end of createRow(Instant, long, E)

    /**
     * Returns a new efs event row generated from the given
     * arguments.
     * @param <E> efs event type.
     * @param timestamp event timestamp.
     * @param rowIndex event row index.
     * @param tags optional event tags. May be empty but not
     * {@code null}.
     * @param event efs event contained in row.
     * @return efs event row.
     * @throws NullPointerException
     * if either {@code timestamp}, {@code tags}, or
     * {@code event} is {@code null}.
     * @throws IllegalArgumentException
     * if {@code rowIndex} &lt; zero.
     */
    public static <E extends IEfsEvent> EfsRow<E> createRow(@Nonnull final Instant timestamp,
                                                            final long rowIndex,
                                                            @Nonnull final Set<Integer> tags,
                                                            @Nonnull final E event)
    {
        Objects.requireNonNull(timestamp, NULL_TIMESTAMP);
        Objects.requireNonNull(tags, NULL_TAGS);
        Objects.requireNonNull(event, NULL_EVENT);

        if (rowIndex < 0L)
        {
            throw (
                new IllegalArgumentException(INVALID_ROW_INDEX));
        }

        return (new EfsRow<>(timestamp, rowIndex, tags, event));
    } // end of createRow(Instant, long, Set<>, E)

    /**
     * Returns a dummy row for an empty efs event file. This
     * row has a zero index, no tags, and
     * <em>{@code null} event</em>. This is because:
     * <ol>
     *   <li>
     *     this row is never forwarded to an agent and
     *   </li>
     *   <li>
     *     there is no reliable way to create {@code E} event
     *     class instance.
     *   </li>
     * </ol>
     * <p>
     * Since only {@code EfsFile} sees this row and does not
     * use this row's event, a {@code null} event is acceptable.
     * </p>
     * @param <E> efs event type.
     * @param timestamp current timestamp.
     * @return an efs event row with a {@code null} event.
     */
    /* package */ static <E extends IEfsEvent> EfsRow<E> createRow(@Nonnull final Instant timestamp)
    {
        return (
            new EfsRow<>(timestamp, 0L, EfsFile.NO_TAGS, null));
    } // end of createRow(Instant)
} // end of class EfsRow
