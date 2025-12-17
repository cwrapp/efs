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

package org.efs.event;

import com.google.common.base.MoreObjects;
import java.time.Instant;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;

/**
 * This class provides meta-information concerning the
 * encapsulated event. This meta-information is:
 * <ul>
 *   <li>
 *
 *   </li>
 * </ul>
 *
 * <p>
 * Note: this class is immutable.
 * </p>
 *
 * @param <E> encapsulated event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
public final class EfsEventWrapper<E extends IEfsEvent>
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

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Timestamp when event was posted to efs.
     */
    @Nonnull private final Instant mPublishTimestamp;

    /**
     * Uniquely identifies event within the current wall clock
     * second. This value is reset to zero when wall clock ticks
     * over to a new second.
     */
    private final int mEventId;

    /**
     * Event posted to efs by this publisher.
     */
    private final long mPublisherId;

    /**
     * Event forwarded to this many subscribers. Will be &ge;
     * zero. Will be zero if {@link #mDeadLetterCount} is
     * &gt; zero.
     */
    private final int mSubscriberCount;

    /**
     * Encapsulted event.
     */
    @Nonnull private final E mEvent;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of EfsEventWrapper.
     */
    private EfsEventWrapper(final Builder<E> builder)
    {
        mPublishTimestamp = builder.mPublishTimestamp;
        mEventId = builder.mEventId;
        mPublisherId = builder.mPubilsherId;
        mSubscriberCount = builder.mSubscriberCount;
        mEvent = builder.mEvent;
    } // end of EfsEventWrapper(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    @Override
    public String toString()
    {
        final MoreObjects.ToStringHelper helper =
            MoreObjects.toStringHelper(this);

        return (helper.add("publishTimestamp", mPublishTimestamp)
                      .add("eventId", mEventId)
                      .add("publisherId", mPublisherId)
                      .add("subscriberCount", mSubscriberCount)
                      .add("event", mEvent)
                      .toString());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns timestamp when event was published to efs.
     * @return efs publish timestamp.
     *
     * @see #eventId()
     */
    @Nonnull public Instant publishTimestamp()
    {
        return (mPublishTimestamp);
    } // end of publishTimestamp()

    /**
     * Returns event identifier. This identifier is unique
     * within the wall clock second of
     * {@link #publishTimestamp()}. Therefore, this identifier
     * can be repeated over time.
     * @return event identifier.
     *
     * @see #publishTimestamp()
     */
    public int eventId()
    {
        return (mEventId);
    } // end of eventId()

    /**
     * Returns event publisher identifier.
     * @return event publisher identifier.
     */
    public long publisherId()
    {
        return (mPublisherId);
    } // end of publisherId()

    /**
     * Returns number of subscribers to which event was forwarded
     * when event was first published. This does not include
     * number of subscribers who retrieved the event after
     * publish. Returned value will be &ge; zero.
     * @return event forwarded to this many subscribers.
     */
    public int subscriberCount()
    {
        return (mSubscriberCount);
    } // end of subscriberCount()

    /**
     * Returns encapsulated event.
     * @return encapsulated event.
     */
    @Nonnull public E event()
    {
        return (mEvent);
    } // end of event()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Returns a new efs event wrapper builder.
     * @param <E> encapsulated efs event type.
     * @return efs event wrapper builder.
     */
    public static <E extends IEfsEvent> Builder<E> builder()
    {
        return (new Builder<>());
    } // end of builder()

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Builder for constructing an {@link EfsEventWrapper}
     * instance.
     *
     * @param <E> encapsulated efs event type.
     */
    public static final class Builder<E extends IEfsEvent>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private Instant mPublishTimestamp;
        private int mEventId;
        private long mPubilsherId;
        private int mSubscriberCount;
        private E mEvent;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Builder()
        {
            mEventId = -1;
            mPubilsherId = 0L;
            mSubscriberCount = -1;
        } // end of Builder()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets event publish timestamp.
         * @param timestamp event published to efs at this time.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code timestamp} is {@code null}.
         */
        public Builder<E> publishTimestamp(@Nonnull final Instant timestamp)
        {
            mPublishTimestamp =
                Objects.requireNonNull(
                    timestamp, "timestamp is null");

            return (this);
        } // end of publishTimestamp(Instant)

        /**
         * Sets event identifier which uniquely identifies event
         * within a wall clock second.
         * @param id event identifier.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code id} &lt; zero.
         */
        public Builder<E> eventId(final int id)
        {
            if (id < 0)
            {
                throw (
                    new IllegalArgumentException("id < zero"));
            }

            mEventId = id;

            return (this);
        } // end of eventId(int)

        /**
         * Sets event publisher identifier.
         * @param id publisher identifier.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code id} is zero.
         */
        public Builder<E> publisherId(final long id)
        {
            if (id == 0)
            {
                throw (
                    new IllegalArgumentException("id is zero"));
            }

            mPubilsherId = id;

            return (this);
        } // end of publisherId(long)

        /**
         * Sets number of subscribers which received event
         * when published.
         * @param count subscriber count. Must be &ge; zero.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code count} &lt; zero.
         */
        public Builder<E> subscriberCount(final int count)
        {
            if (count < 0)
            {
                throw (
                    new IllegalArgumentException(
                        "count < zero"));
            }

            mSubscriberCount = count;

            return (this);
        } // end of subscriberCount(int)

        /**
         * Sets encapsulated event.
         * @param event event encapsulated in wrapper.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code event} is {@code null}.
         */
        public Builder<E> event(@Nonnull final E event)
        {
            mEvent =
                Objects.requireNonNull(event, "event is null");

            return (this);
        } // end of event(final E event)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Creates a new {@code EfsEventWrapper} instance based
         * on this builder's settings.
         * @return new build efs event wrapper instance.
         * @throws ValidationException
         * if {@code this Builder} instance contains one or more
         * invalid settings.
         */
        public EfsEventWrapper<E> build()
        {
            validate();

            return (new EfsEventWrapper<>(this));
        } // end of build()

        /**
         * Validates {@code EfsEventWrapper} builder settings.
         * This validation is "fail slow" meaning that a single
         * validation call determines all configuration
         * errors.
         * @throws ValidationException
         * if {@code this Builder} instance contains one or more
         * invalid settings.
         */
        private void validate()
        {
            final Validator problems = new Validator();

            problems.requireTrue(mEventId >= 0,
                                 "eventId",
                                 Validator.NOT_SET)
                    .requireNotNull(mPublishTimestamp,
                                    "publishTimestamp")
                    .requireTrue(mPubilsherId != 0,
                                 "publisherId",
                                 Validator.NOT_SET)
                    .requireTrue(mSubscriberCount >= 0,
                                 "subscriberCount",
                                 Validator.NOT_SET)
                    .requireNotNull(mEvent, "event")
                    .throwException(EfsEventWrapper.class);
        } // end of validate()
    } // end of class Builder
} // end of class EfsEventWrapper
