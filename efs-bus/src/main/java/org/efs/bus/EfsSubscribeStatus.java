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
import javax.annotation.concurrent.ThreadSafe;
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

@ThreadSafe
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
     * Builder for constructing {@link EfsSubscribeStatus} events.
     *
     * <p>
     * <strong>Purpose:</strong><br>
     * {@code Builder} is used to construct
     * {@code EfsSubscribeStatus} events sent to publishing
     * agents to inform them of the current subscriber count for
     * a given type+topic key. Publishers receive these status
     * updates when subscribers join or leave a topic, allowing
     * them to manage publishing resources efficiently.
     * </p>
     *
     * <p>
     * <strong>Immutability and Thread Safety:</strong>
     * </p>
     * <p>
     * While the builder itself is not thread-safe, the resulting
     * {@link EfsSubscribeStatus} events are immutable and fully
     * thread-safe. It is recommended that a new builder be used
     * for each new event rather than re-using a builder across
     * multiple events.
     * </p>
     *
     * <p>
     * <strong>Fluent API:</strong>
     * </p>
     * <p>
     * All setter methods return {@code this} to enable method chaining:
     * </p>
     * <pre><code>
     * EfsSubscribeStatus&lt;MyEvent&gt; status = EfsSubscribeStatus.builder()
     *     .topicKey(myTopicKey)
     *     .previousSubscribers(5)
     *     .activeSubscribers(7)
     *     .build();</code></pre>
     *
     * <p>
     * <strong>Required Fields:</strong>
     * </p>
     * <p>
     * The following fields must be set before calling {@link #build()}:
     * </p>
     * <ul>
     *   <li>
     *     <strong>topicKey:</strong> Status applies to this
     *     type+topic key.
     *   </li>
     *   <li>
     *     <strong>previousSubscribers:</strong> Previous
     *     subscriber count (&ge; 0)
     *   </li>
     *   <li>
     *     <strong>activeSubscribers:</strong> Current active
     *     subscriber count (&ge; 0)
     *   </li>
     * </ul>
     * <p>
     * If any required field is not set, {@link #build()} throws
     * a {@link net.sf.eBus.util.ValidationException}.
     * </p>
     *
     * <h2>Basic Usage Example</h2>
     * <pre><code>
     * // Create a builder
     * EfsSubscribeStatus.Builder&lt;OrderEvent&gt; builder = EfsSubscribeStatus.builder();
     *
     * // Set the topic
     * builder.topicKey(EfsTopicKey.getKey(OrderEvent.class, "orders"));
     *
     * // Set subscriber counts when a new subscriber joins
     * builder.previousSubscribers(5);   // Was 5 subscribers
     * builder.activeSubscribers(6);     // Now 6 subscribers
     *
     * // Build the status event
     * EfsSubscribeStatus&lt;OrderEvent&gt; status = builder.build();</code></pre>
     *
     * <h2>Use Case 1: Tracking Growing Subscriber Interest</h2>
     * <p>
     * A publisher can use the status updates to decide whether to allocate more resources
     * for publishing:
     * </p>
     * <pre><code>
     * private void handleSubscriberStatusChange(EfsSubscribeStatus&lt;SensorEvent&gt; status) {
     *     int previous = status.previousSubscribers();
     *     int active = status.activeSubscribers();
     *
     *     // Subscribers added
     *     if (active &gt; previous) {
     *         System.out.println("Subscribers increased from " + previous + " to " + active);
     *         allocatePublishingResources();
     *
     *     // Subscribers removed
     *     } else if (active &lt; previous) {
     *         System.out.println("Subscribers decreased from " + previous + " to " + active);
     *
     *         if (active == 0) {
     *             System.out.println("No more subscribers, stopping publisher");
     *             stopPublisher();
     *         }
     *     }
     * }</code></pre>
     *
     * <h2>Use Case 2: Event Bus Publishing Flow</h2>
     * <p>
     * When a publisher advertises a type+topic key, this builder
     * is used internally by the event bus to construct status
     * events sent back to the publisher:
     * </p>
     * <pre><code>
     * // Inside EfsEventBus.TopicFeed (internal usage)
     * private void forwardSubscribeStatus(final int previousCount, final int activeCount) {
     *     if (!mPublishers.isEmpty()) {
     *         final EfsSubscribeStatus.Builder&lt;E&gt; builder =
     *             EfsSubscribeStatus.builder();
     *         final EfsSubscribeStatus&lt;E&gt; status =
     *             builder.topicKey(mTopicKey)
     *                    .previousSubscribers(previousCount)
     *                    .activeSubscribers(activeCount)
     *                    .build();
     *
     *         // Dispatch to all publishers
     *         for (Advertisement&lt;E&gt; ad : mPublishers.values()) {
     *             ad.forwardSubscribeStatus(status);
     *         }
     *     }
     * }</code></pre>
     *
     * <h2>Use Case 3: Method Chaining Pattern</h2>
     * <p>
     * Take full advantage of the fluent API for concise and
     * readable code:
     * </p>
     * <pre><code>
     * // Clean, readable one-liner
     * EfsSubscribeStatus&lt;PaymentEvent&gt; status = EfsSubscribeStatus.builder()
     *     .topicKey(EfsTopicKey.getKey(PaymentEvent.class, "payments/processing"))
     *     .previousSubscribers(10)
     *     .activeSubscribers(12)
     *     .build();
     *
     * // Different approach: step-by-step
     * EfsSubscribeStatus.Builder&lt;PaymentEvent&gt; builder = EfsSubscribeStatus.builder();
     * builder.topicKey(paymentTopic);
     * if (newSubscriberJoined()) {
     *     builder.previousSubscribers(currentCount - 1);
     *     builder.activeSubscribers(currentCount);
     * }
     * EfsSubscribeStatus&lt;PaymentEvent&gt; status = builder.build();</code></pre>
     *
     * <p>
     * <strong>Validation Behavior:</strong>
     * </p>
     * <p>
     * When {@link #build()} is called, the builder validates:
     * </p>
     * <ul>
     *   <li>
     *     topicKey is not null → throws
     *     {@code NullPointerException} via
     *     {@link #topicKey(EfsTopicKey)}
     *   </li>
     *   <li>
     *     previousSubscribers ≥ 0 → throws
     *     {@code IllegalArgumentException} via
     *     {@link #previousSubscribers(int)}
     *   </li>
     *   <li>
     *     activeSubscribers ≥ 0 → throws
     *     {@code IllegalArgumentException} via
     *     {@link #activeSubscribers(int)}
     *   </li>
     *   <li>
     *     All required fields are set → throws
     *    {@code ValidationException} if not
     *   </li>
     * </ul>
     *
     * <p>
     * <strong>Error Examples:</strong>
     * </p>
     * <pre><code>
     * // Error: topicKey not set
     * try {
     *     EfsSubscribeStatus&lt;MyEvent&gt; status = EfsSubscribeStatus.builder()
     *         .previousSubscribers(5)
     *         .activeSubscribers(6)
     *         .build();
     * } catch (ValidationException e) {
     *     // topicKey is required
     * }
     *
     * // Error: negative subscriber count
     * try {
     *     EfsSubscribeStatus&lt;MyEvent&gt; status = EfsSubscribeStatus.builder()
     *         .topicKey(myTopic)
     *         .previousSubscribers(-1)  // Invalid!
     *         .activeSubscribers(5)
     *         .build();
     * } catch (IllegalArgumentException e) {
     *     System.out.println(e.getMessage()); // "subscriber count &lt; 0"
     * }
     *
     * // Error: null topic key
     * try {
     *     EfsSubscribeStatus&lt;MyEvent&gt; status = EfsSubscribeStatus.builder()
     *         .topicKey(null)  // Invalid!
     *         .previousSubscribers(5)
     *         .activeSubscribers(6)
     *         .build();
     * } catch (NullPointerException e) {
     *     // topicKey is null
     * }</code></pre>
     *
     * @param <E> efs event class.
     *
     * @see EfsSubscribeStatus
     * @see EfsSubscribeStatus#builder()
     * @see org.efs.event.IEfsEventBuilder
     * @see net.sf.eBus.util.ValidationException
     *
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
