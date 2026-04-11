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

import org.efs.event.EfsTopicKey;
import jakarta.annotation.Nonnull;
import java.util.Objects;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import org.efs.event.IEfsEvent;
import org.efs.event.IEfsEventBuilder;

/**
 * Reports a given topic's publish status. This includes number
 * of publishers and if any of those publishers are currently
 * publishing on the given topic. Value returned by
 * {@link #activePublishers()} defines the topic publish status:
 * <ul>
 *   <li>
 *     Zero: there are no publishers actively post events on this
 *     topic and subscribers will not be receiving events.
 *   </li>
 *   <li>
 *     &gt; Zero: number of publishers actively posting events on
 *     this topic and subscribers will receive any events posted
 *     in the future.
 *   </li>
 *   <li>
 *     == {@link #advertisedPublishers()}: all publishers are
 *     actively posting events on this topic.
 *   </li>
 * </ul>
 * {@link #activePublishers()} is the critical value and defines
 * whether subscribers may expect to receive events on topic.
 * {@link #advertisedPublishers()} is provided an an ancillary
 * value which may be used to trouble shoot why no events are
 * being published to topic. That is, there is one publisher
 * expected for this topic and that publisher is not advertised.
 * Now the issue is: why is the publisher not even advertised?
 *
 * @param <E> published event class.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsPublishStatus<E extends IEfsEvent>
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * An advertised publisher count &lt; zero results in an
     * {@code IllegalArgumentException} with message {@value}.
     */
    public static final String INVALID_ADVERTISED_COUNT =
        "advertised publisher count < 0";

    /**
     * An active publisher count &lt; zero results in an
     * {@code IllegalArgumentException} with message {@value}.
     */
    public static final String INVALID_ACTIVE_COUNT =
        "active publisher count < 0";

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Publish status applies to this topic.
     */
    @Nonnull private final EfsTopicKey<E> mTopicKey;

    /**
     * Number publishers currently advertised for topic.
     */
    private final int mAdvertisedPublishers;

    /**
     * Number of publishers actively publishing on topic. Note
     * that it is possible for a publisher to be advertised but
     * not yet
     */
    private final int mActivePublishers;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of EfsPublishStatus.
     */
    private EfsPublishStatus(final Builder<E> builder)
    {
        mTopicKey = builder.mTopicKey;
        mAdvertisedPublishers = builder.mAdvertisedPublishers;
        mActivePublishers = builder.mActivePublishers;
    } // end of EfsPublishStatus(Builder)

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
                "[topic=%s, # publishers advertised=%,d, # publishers active=%,d]",
                mTopicKey,
                mAdvertisedPublishers,
                mActivePublishers));
    } // end of toString()

    @SuppressWarnings ("unchecked")
    @Override
    public boolean equals(final Object o)
    {
        boolean retcode = (this == o);

        if (!retcode && o instanceof EfsPublishStatus)
        {
            final EfsPublishStatus<E> ps =
                (EfsPublishStatus<E>) o;

            retcode = (mTopicKey.equals(ps.mTopicKey) &&
                       mAdvertisedPublishers ==
                           ps.mAdvertisedPublishers &&
                       mActivePublishers ==
                           ps.mActivePublishers);
        }

        return (retcode);
    } // end of equals(Object)

    @Override
    public int hashCode()
    {
        return (Objects.hash(mTopicKey,
                             mAdvertisedPublishers,
                             mActivePublishers));
    } // end of hashCode()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns topic key to which this publish status event
     * applies.
     * @return publish status applies to this topic key.
     */
    @Nonnull public EfsTopicKey<E> topicKey()
    {
        return (mTopicKey);
    } // end of topicKey()

    /**
     * Returns number of publishers currently advertised on
     * {@link #topicKey() topic key}. This value will be &ge;
     * zero and &ge; {@link #activePublishers()}. If value is
     * zero, then subscribers will <em>not</em> receive events
     * for given topic.
     * @return advertised publisher count.
     *
     * @see #activePublishers()
     */
    public int advertisedPublishers()
    {
        return (mAdvertisedPublishers);
    } // end of advertisedPublishers()

    /**
     * Returns number of publishers actively publishing events on
     * {@link #topicKey() topic key}. This value will be &ge;
     * zero and &le; {@link #advertisedPublishers()}. If value
     * is zero, then subscribers will <em>not</em> receive events
     * for given topic.
     * @return active publisher count.
     *
     * @see #advertisedPublishers()
     */
    public int activePublishers()
    {
        return (mActivePublishers);
    } // end of activePublishers()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Returns a new {@code EfsPublishStatus} builder instance.
     * It is recommended that a new builder be used for each
     * new {@code EfsPublishStatus} event rather than re-using
     * a builder to create multiple events.
     * @param <E> publish status event applies to this event.
     * @return a new {@code EfsPublishStatus} event builder.
     */
    public static <E extends IEfsEvent> Builder<E> builder()
    {
        return (new Builder<>());
    } // end of builder()

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Builder class used to create {@link EfsPublishStatus}
     * instances. A new {@code Builder} instance is acquired by
     * calling {@link EfsPublishStatus#builder()}.
     *
     * @param <E> event class
     */
    public static final class Builder<E extends IEfsEvent>
        implements IEfsEventBuilder<EfsPublishStatus<E>>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private EfsTopicKey<E> mTopicKey;
        private int mAdvertisedPublishers;
        private int mActivePublishers;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Builder()
        {
            mAdvertisedPublishers = -1;
            mActivePublishers = -1;
        } // end of Builder()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // IEfsEventBuilder Interface Implementation.
        //

        /**
         * Returns a new {@link EfsPublishStatus} event based on
         * this builder's settings.
         * @return new {@code EfsPublishStatus} event.
         * @throws ValidationException
         * if this builder's settings are incorrectly set.
         */
        @Override
        public EfsPublishStatus<E> build()
        {
            final Validator problems = new Validator();

            problems.requireNotNull(mTopicKey, "topicKey")
                    .requireTrue((mAdvertisedPublishers >= 0),
                                 "advertisedPublishers",
                                 Validator.NOT_SET)
                    .requireTrue((mActivePublishers >= 0),
                                 "activePublishers",
                                 Validator.NOT_SET)
                    // By checking first for negative counts
                    // means this check is ignored when counts
                    // are not set. Invalid counts are reported
                    // by the above checks.
                    .requireTrue((mAdvertisedPublishers < 0 ||
                                  mActivePublishers < 0 ||
                                  mActivePublishers <= mAdvertisedPublishers),
                                 "activePublishers",
                                 "activePublishers > advertisedPublishers")
                    .throwException(EfsPublishStatus.class);

            return (new EfsPublishStatus<>(this));
        } // end of build()

        //
        // end of IEfsEventBuilder Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets topic key to given value.
         * @param topicKey publish status event is for this topic
         * key.
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
         * Sets advertised publisher count to given value. This
         * value must be &ge; zero. There is no maximum value
         * limit.
         * @param n advertised publisher count.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code n} &lt; zero.
         */
        public Builder<E> advertisedPublishers(final int n)
        {
            if (n < 0)
            {
                throw (
                    new IllegalArgumentException(
                        INVALID_ADVERTISED_COUNT));
            }

            mAdvertisedPublishers = n;

            return (this);
        } // end of advertisedPublishers(int)

        /**
         * Sets active publisher count to given value. This
         * value must be &ge; zero and &le; advertised publisher
         * count.
         * @param n advertised publisher count.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code n} &lt; zero.
         */
        public Builder<E> activePublishers(final int n)
        {
            if (n < 0)
            {
                throw (
                    new IllegalArgumentException(
                        INVALID_ACTIVE_COUNT));
            }

            mActivePublishers = n;

            return (this);
        } // end of activePublishers(int)

        //
        // end of Set Methods.
        //-------------------------------------------------------
    } // end of class Builder
} // end of class EfsPublishStatus
