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

import jakarta.annotation.Nonnull;
import java.util.Objects;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import org.efs.event.EfsTopicKey;
import org.efs.event.IEfsEvent;
import org.efs.event.IEfsEventBuilder;

/**
 * This event is sent to publishing agents to inform on number of
 * in-place subscribers for a given topic. If there are zero
 * subscribers, then publisher should cease publishing events to
 * the topic. Failure to do so results in an
 * {@code IllegalStateException}.
 * <p>
 * If subscriber count &gt; zero, then publisher is clear to
 * publish events to topic. Previous subscriber count is provided
 * to allow publisher to determine if subscriber count is growing
 * or shrinking.
 * </p>
 *
 * @param <E> event class.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsSubscribeStatus<E extends IEfsEvent>
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * A subscriber count &lt; zero results in an
     * {@code IllegalArgumentException} with message {@value}.
     */
    public static final String INVALID_SUBSCRIBERS_COUNT =
        "subscriber count < 0";

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Subscribe status applies to this topic.
     */
    @Nonnull private final EfsTopicKey<E> mTopicKey;

    /**
     * Previous number of subscribers. Value &ge; zero.
     */
    private final int mPreviousSubscribers;

    /**
     * Current number of subscribers. Value &ge; zero.
     */
    private final int mActiveSubscribers;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new subscribe status event based on builder
     * settings.
     * @param builder contains event settings.
     */
    private EfsSubscribeStatus(final Builder<E> builder)
    {
        mTopicKey = builder.mTopicKey;
        mPreviousSubscribers = builder.mPreviousSubscribers;
        mActiveSubscribers = builder.mActiveSubscribers;
    } // end of EfsSubscribeStatus(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    @Override
    public String toString()
    {
        return (
            String.format(
                "[topic=%s, # subscribers previous=%,d, # subscribers active=%,d]",
                mTopicKey,
                mPreviousSubscribers,
                mActiveSubscribers));
    } // end of toString()

    @SuppressWarnings ("unchecked")
    @Override
    public boolean equals(final Object o)
    {
        boolean retcode = (this == o);

        if (!retcode && o instanceof EfsSubscribeStatus)
        {
            final EfsSubscribeStatus<E> ess =
                (EfsSubscribeStatus<E>) o;

            retcode = (mTopicKey.equals(ess.mTopicKey) &&
                       mPreviousSubscribers ==
                           ess.mPreviousSubscribers &&
                       mActiveSubscribers ==
                           ess.mActiveSubscribers);
        }

        return (retcode);
    } // end of equals(Object)

    @Override
    public int hashCode()
    {
        return (Objects.hash(mTopicKey,
                             mPreviousSubscribers,
                             mActiveSubscribers));
    } // end of hashCode()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns topic key to which this subscribe status event
     * applies.
     * @return subscribe status applies to this topic key.
     */
    @Nonnull public EfsTopicKey<E> topicKey()
    {
        return (mTopicKey);
    } // end of topicKey()

    /**
     * Returns previous subscriber count.
     * @return previous subscriber count.
     */
    public int previousSubscribers()
    {
        return (mPreviousSubscribers);
    } // end of previousSubscribers()

    /**
     * Returns active subscribers count.
     * @return active subscribers count.
     */
    public int activeSubscribers()
    {
        return (mActiveSubscribers);
    } // end of activeSubscribers()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Returns a new {@code EfsSubscribeStatus} builder instance.
     * It is recommended that a new builder be used for each
     * new {@code EfsSubscribeStatus} event rather than re-using
     * a builder to create multiple events.
     * @param <E> subscribe status event applies to this event.
     * @return a new {@code EfsSubscribeStatus} event builder.
     */
    public static <E extends IEfsEvent> Builder<E> builder()
    {
        return (new Builder<>());
    } // end of builder()

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * TODO
     * @param <E> efs event class.
     */
    public static final class Builder<E extends IEfsEvent>
        implements IEfsEventBuilder<EfsSubscribeStatus<E>>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private EfsTopicKey<E> mTopicKey;
        private int mPreviousSubscribers;
        private int mActiveSubscribers;

    //-----------------------------------------------------------
    // Member methods.
    //
        //-------------------------------------------------------
        // Constructors.
        //

        private Builder()
        {
            mPreviousSubscribers = -1;
            mActiveSubscribers = -1;
        } // end of Builder()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // IEfsEventBuilder Interface Implementation.
        //

        /**
         * Returns a new {@link EfsSubscribeStatus} event based
         * on this builder's settings.
         * @return new {@code EfsSubscribeStatus} event.
         * @throws ValidationException
         * if this builder's setting are incorrectly set.
         */
        @Override
        public EfsSubscribeStatus<E> build()
        {
            final Validator problems = new Validator();

            problems.requireNotNull(mTopicKey, "topicKey")
                    .requireTrue((mPreviousSubscribers >= 0),
                                 "previousSubscribers",
                                 Validator.NOT_SET)
                    .requireTrue((mActiveSubscribers >= 0),
                                 "activeSubscribers",
                                 Validator.NOT_SET)
                    .throwException(EfsSubscribeStatus.class);

            return (new EfsSubscribeStatus<>(this));
        } // end of build()

        //
        // end of IEfsEventBuilder Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets topic key to given value.
         * @param topicKey subscribe status event is for this
         * topic key.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code topicKey} is {@code null}.
         */
        public Builder<E> topicKey(@Nonnull final EfsTopicKey<E> topicKey)
        {
            mTopicKey =
                Objects.requireNonNull(
                    topicKey, EfsEventBus.NULL_TOPIC_KEY);

            return (this);
        } // end of topicKey(EfsTopicKey)

        /**
         * Sets previous subscriber count to given value. This
         * value must &ge; zero. There is no maximum value limit.
         * @param n previous subscribers count.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code n} &lt; zero.
         */
        public Builder<E> previousSubscribers(final int n)
        {
            if (n < 0)
            {
                throw (
                    new IllegalArgumentException(
                        INVALID_SUBSCRIBERS_COUNT));
            }

            mPreviousSubscribers = n;

            return (this);
        } // end of previousSubscribers(int)

        /**
         * Sets active subscriber count to given value. This
         * value must &ge; zero. There is no maximum value limit.
         * @param n active subscribers count.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code n} &lt; zero.
         */
        public Builder<E> activeSubscribers(final int n)
        {
            if (n < 0)
            {
                throw (
                    new IllegalArgumentException(
                        INVALID_SUBSCRIBERS_COUNT));
            }

            mActiveSubscribers = n;

            return (this);
        } // end of activeSubscribers(int)

        //
        // end of Set Methods.
        //-------------------------------------------------------
    } // end of class Builder
} // end of class EfsSubscribeStatus
