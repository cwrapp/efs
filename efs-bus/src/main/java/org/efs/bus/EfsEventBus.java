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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.efs.dispatcher.EfsDispatchTarget;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;
import org.efs.event.ConflationEvent;
import org.efs.event.EfsTopicKey;
import org.efs.event.IEfsEvent;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;


/**
 * TODO: write class description.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class EfsEventBus
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * A null topic key results in a {@code NullPointerException}
     * with message {@value}.
     */
    public static final String NULL_TOPIC_KEY =
        "topicKey is null";

    /**
     * A null event class results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_EVENT_CLASS =
        "eventClass is null";

    /**
     * A null callback results in a {@code NullPointerException}
     * with message {@value}.
     */
    public static final String NULL_CALLBACK =
        "callback is null";

    /**
     * A null agent argument results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_AGENT = "agent is null";

    /**
     * A null event router argument results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_ROUTER =
        "event router is null";

    /**
     * A null event argument results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_EVENT = "event is null";

    /**
     * A null topics list argument results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_TOPICS = "topics is null";

    /**
     * An invalid topic results in an
     * {@code IllegalArgumentException} with message {@value}.
     */
    public static final String INVALID_TOPIC =
        "topic is either null, an empty string, or blank";

    /**
     * Attempting to interact with a closed advertisement results
     * in an {@code IllegalStateException} with the message
     * {@value}.
     */
    public static final String ADVERTISEMENT_CLOSED =
        "advertisement is closed";

    /**
     * Attempting to fowardEvent an event while fowardEvent status is
set to {@code false} results in an
     * {@code IllegalStateException} with the message {@value}.
     */
    public static final String PUBLISH_STATUS_DOWN =
        "publish status is down";

    /**
     * Attempting to fowardEvent an event while there are no
subscribers to the topic results in an
{@code IllegalStateException} with the message {@value}.
     */
    public static final String NO_SUBSCRIBERS = "no subscribers";

    /**
     * An invalid bus name results in an
     * {@code IllegalArgumentException} with message {@value}.
     */
    public static final String INVALID_BUS_NAME =
        "busName is either null, an empty string, or blank";

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Maps unique event bus name to its event bus instance.
     */
    private static final ConcurrentHashMap<String, EfsEventBus> sBuses =
        new ConcurrentHashMap<>();

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(EfsEventBus.class);

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Unique event bus name within JVM.
     */
    private final String mBusName;

    /**
     * Maps topic+event type key to its subscription list.
     */
    private final ConcurrentHashMap<EfsTopicKey<? extends IEfsEvent>,
                                    TopicFeed<? extends IEfsEvent>> mTopicMap;

    /**
     * Acquire this lock when <em>modifying</em>
     * {@link #mTopicMap} but not when reading this map.
     */
    private final Lock mTopicLock;

    /**
     * Wildcard advertisement and subscription list. This data
     * member should only be accessed while holding
     * {@link #mTopicLock}.
     */
    private final List<WildcardAccessPoint<?>> mWildcards;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new efs event bus instance with a given name.
     * @param busName unique event bus name.
     */
    private EfsEventBus(final String busName)
    {
        mBusName = busName;

        mTopicMap = new ConcurrentHashMap<>();
        mTopicLock = new ReentrantLock(true);
        mWildcards = new ArrayList<>();
    } // end of EfsEventBus(String, Set<>)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns efs event bus name which is unique within JVM.
     * @return unique event bus name.
     */
    public String busName()
    {
        return (mBusName);
    } // end of busName()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Reports a event topic key to bus independent of an
     * advertisement or subscription. This method is used when
     * {@link #advertiseAll(Class, String, Consumer, Consumer, IEfsAgent)}
     * and
     * {@link #subscribeAll(Class, String, boolean, Consumer, Consumer, Consumer, IEfsAgent)}
     * reference the same topic keys. Since it is not possible to
     * match advertised and subscribed wildcard topics, it is
     * necessary to report concrete topics separately.
     * @param <E> event type.
     * @param topicKey event topic key to be added.
     * @throws NullPointerException
     * if {@code topicKey} is {@code null}.
     */
    public <E extends IEfsEvent> void addTopic(final EfsTopicKey<E> topicKey)
    {
        Objects.requireNonNull(topicKey, NULL_TOPIC_KEY);

        // Add topic key to topic map.
        mTopicLock.lock();
        try
        {
            doAddTopic(topicKey);
        }
        finally
        {
            mTopicLock.unlock();
        }
    } // end of addTopic(EfsTopicKey)

    /**
     * Reports multiple topics for a given event class
     * independent of an advertisement or subscription. This
     * method is used when
     * {@link #advertiseAll(Class, String, Consumer, Consumer, IEfsAgent)}
     * and
     * {@link #subscribeAll(Class, String, boolean, Consumer, Consumer, Consumer, IEfsAgent)}
     * reference the same topic keys. Since it is not possible to
     * match advertised and subscribed wildcard topics, it is
     * necessary to report concrete topics separately.
     * <p>
     * Exceptions are thrown before any topic key processing
     * happens, meaning that no topic keys are added to topic
     * key map when an exception is thrown.
     * </p>
     * @param <E> event type.
     * @param eventClass topic key class.
     * @param topics topic key topics.
     * @throws NullPointerException
     * if either {@code eventClass} or {@code topics} is
     * {@code null}.
     * @throws IllegalArgumentException
     * if {@code topics} contains a null, empty, or blank
     * topic. If thrown, then no topics were processed.
     */
    public <E extends IEfsEvent> void addTopics(final Class<E> eventClass,
                                                final List<String> topics)
    {
        Objects.requireNonNull(eventClass, NULL_EVENT_CLASS);
        Objects.requireNonNull(topics, NULL_TOPICS);

        // Validate list contains only valid topics.
        for (String t : topics)
        {
            if (Strings.isNullOrEmpty(t) || t.isBlank())
            {
                throw (
                    new IllegalArgumentException(INVALID_TOPIC));
            }
        }

        mTopicLock.lock();
        try
        {
            for (String t : topics)
            {
                doAddTopic(EfsTopicKey.getKey(eventClass, t));
            }
        }
        finally
        {
            mTopicLock.unlock();
        }
    } // end of addTopics(Class, List<>)

    /**
     * Advertises an event class, topic published by an agent,
     * returning a closeable instance used to retract this
     * advertisement when closed.
     * If this is a new topic key, then finds all extant wildcard
     * subscriptions which match this topic key and has them
     * subscribe.
     * <p>
     * Returned {@code Advertisement} is then used to set the
     * publish status and publish events.
     * </p>
     * @param <E> event class.
     * @param topicKey advertise event class and topic.
     * @param sscb {@code EfsSubscribeStatus} event callback.
     * @param publisher agent advertising ability to publish
     * to given topic key.
     * @return closeable instance used to retract advertisement.
     * @throws NullPointerException
     * if either {@code topicKey}, {@code sscb}, or
     * {@code publisher} is {@code null}.
     * @throws IllegalStateException
     * {@code publisher} is not registered with
     * {@code EfsDispatcher}.
     */
    @SuppressWarnings({"unchecked"})
    public <E extends IEfsEvent> Advertisement<E> advertise(final EfsTopicKey<E> topicKey,
                                                            final Consumer<EfsSubscribeStatus<E>> sscb,
                                                            final IEfsAgent publisher)
    {
        final Advertisement<E> retval;

        Objects.requireNonNull(topicKey, NULL_TOPIC_KEY);
        Objects.requireNonNull(sscb, NULL_CALLBACK);
        Objects.requireNonNull(publisher, NULL_AGENT);

        // Is publisher a registered agent?
        if (!EfsDispatcher.isRegistered(publisher))
        {
            throw (
                new IllegalStateException(
                    String.format(
                        "%s is not registered with a dispatcher",
                        publisher.name())));
        }

        sLogger.info("{} advertising {}.",
                     publisher.name(),
                     topicKey);

        mTopicLock.lock();
        try
        {
            final TopicFeed<E> topic = doAddTopic(topicKey);

            sLogger.trace("{} advertising {} topic.",
                          publisher.name(),
                          topicKey);

            retval = topic.advertise(sscb, publisher);
        }
        finally
        {
            mTopicLock.unlock();
        }

        return (retval);
    } // end of advertise(EfsTopicKey, IEfsAgent)

    /**
     * TODO
     * @param <E> efs event class.
     * @param eventClass target efs event class.
     * @param regexTopic regular expression matched against
     * existing concrete topics.
     * @param sscb {@code EfsSubscribeStatus} event callback.
     * @param topicUpdate newly discovered topic keys matching
     * wildcard reported via this callback.
     * @param publisher agent advertising ability to publish
     * to given topic key.
     * @return wildcard advertisement.
     */
    @SuppressWarnings ({"java:S2093"})
    public <E extends IEfsEvent> WildcardAdvertisement<E> advertiseAll(final Class<E> eventClass,
                                                                       final String regexTopic,
                                                                       final Consumer<EfsSubscribeStatus<E>> sscb,
                                                                       final Consumer<EfsTopicKey<E>> topicUpdate,
                                                                       final IEfsAgent publisher)
    {
        final Pattern pattern;
        final WildcardAdvertisement<E> retval;

        Objects.requireNonNull(eventClass, NULL_EVENT_CLASS);
        Objects.requireNonNull(sscb, NULL_CALLBACK);
        Objects.requireNonNull(publisher, NULL_AGENT);

        if (Strings.isNullOrEmpty(regexTopic) ||
            regexTopic.isBlank())
        {
            throw (new IllegalArgumentException(INVALID_TOPIC));
        }

        try
        {
            pattern = Pattern.compile(regexTopic);
        }
        catch (Exception jex)
        {
            throw (
                new IllegalArgumentException(
                    String.format(
                        "invalid regular expression \"%s\"",
                        regexTopic),
                    jex));
        }

        sLogger.info("{} {} wildcard advertisement to {}.",
                     publisher.name(),
                     regexTopic,
                     eventClass.getSimpleName());

        mTopicLock.lock();
        try
        {
            retval = new WildcardAdvertisement<>(eventClass,
                                                 regexTopic,
                                                 pattern,
                                                 topicUpdate,
                                                 publisher,
                                                 sscb);

            mWildcards.add(retval);

            // Are there any concrete topics in place?
            if (!mTopicMap.isEmpty())
            {
                // Yes. Find those topics matching this regular
                // expression advertisement.
                findMatchingTopics(retval);
            }
        }
        finally
        {
            mTopicLock.unlock();
        }

        return (retval);
    } // end of advertiseAll(...)

    /**
     * TODO
     * @param <E> efs event class
     * @param topicKey subscribe to this event class and topic.
     * @param pscb forward publisher status events to this
     * callback.
     * @param ecb forward events to this callback.
     * @param subscriber agent subscriber.
     * @return closeable subscription.
     */
    public <E extends IEfsEvent> Subscription<E> subscribe(final EfsTopicKey<E> topicKey,
                                                           final Consumer<EfsPublishStatus<E>> pscb,
                                                           final Consumer<E> ecb,
                                                           final IEfsAgent subscriber)
    {
        return (subscribe(false, topicKey, pscb, ecb, subscriber));
    } // end of subscribe(...)

    /**
     * TODO
     * @param <E> efs event class.
     * @param topicKey subscribe to this event class and topic.
     * @param pscb forward publisher status events to this
     * callback.
     * @param ecb forward events to this callback.
     * @param subscriber agent subscriber.
     * @return closeable subscription.
     */
    public <E extends IEfsEvent> Subscription<E> subscribeInbox(final EfsTopicKey<E> topicKey,
                                                                final Consumer<EfsPublishStatus<E>> pscb,
                                                                final Consumer<E> ecb,
                                                                final IEfsAgent subscriber)
    {
        return (subscribe(true, topicKey, pscb, ecb, subscriber));
    } // end of subscribeInbox(...)

    /**
     * TODO
     * @param <E> event type
     * @param eventClass subscribing to this event class.
     * @param regexTopic regular expression matched against event
     * topics within event class.
     * @param isInBox {@code true} if this is an inbox
     * subscription.
     * @param pscb forward publisher status events to this
     * callback.
     * @param ecb forward events to this callback.
     * @param topicUpdate inform agent of newly discovered topic
     * keys via this callback.
     * @param subscriber agent subscriber.
     * @return closeable subscription.
     */
    @SuppressWarnings({"java:S2093"})
    public <E extends IEfsEvent> WildcardSubscription<E> subscribeAll(final Class<E> eventClass,
                                                                      final String regexTopic,
                                                                      final boolean isInBox,
                                                                      final Consumer<EfsPublishStatus<E>> pscb,
                                                                      final Consumer<E> ecb,
                                                                      final Consumer<EfsTopicKey<E>> topicUpdate,
                                                                      final IEfsAgent subscriber)
    {
        final Pattern pattern;
        final WildcardSubscription<E> retval;

        Objects.requireNonNull(eventClass, NULL_EVENT_CLASS);
        Objects.requireNonNull(pscb, NULL_CALLBACK);
        Objects.requireNonNull(ecb, NULL_CALLBACK);
        Objects.requireNonNull(subscriber, NULL_AGENT);

        if (Strings.isNullOrEmpty(regexTopic) ||
            regexTopic.isBlank())
        {
            throw (new IllegalArgumentException(INVALID_TOPIC));
        }

        try
        {
            pattern = Pattern.compile(regexTopic);
        }
        catch (Exception jex)
        {
            throw (
                new IllegalArgumentException(
                    String.format(
                        "invalid regular expression \"%s\"",
                        regexTopic),
                    jex));
        }

        sLogger.info("{} {} wildcard {} subscription to {}.",
                     subscriber.name(),
                     regexTopic,
                     (isInBox ? "inbox" : "concrete"),
                     eventClass.getSimpleName());

        mTopicLock.lock();
        try
        {
            retval = new WildcardSubscription<>(eventClass,
                                                regexTopic,
                                                pattern,
                                                topicUpdate,
                                                subscriber,
                                                isInBox,
                                                pscb,
                                                ecb);

            mWildcards.add(retval);

            // Are there any concrete topics in place?
            if (!mTopicMap.isEmpty())
            {
                // Yes. Find those topics matching this regular
                // expression subscription.
                findMatchingTopics(retval);
            }
        }
        finally
        {
            mTopicLock.unlock();
        }

        return (retval);
    } // end of subscribeAll(...)

    /**
     * TODO
     * @param <E> event class
     * @param topicKey subscribe to this topic key.
     * @param pscb publish status event callback.
     * @param subscriber agent placing this subscription.
     * @param router routes events to agents.
     * @return subscription used to track topic key publish
     * status.
     * @throws NullPointerException
     * if either {@code topicKey} or {@code router} is
     * {@code null}.
     */
    @SuppressWarnings({"unchecked"})
    public <E extends IEfsEvent> Subscription<E> subscribeRouter(final EfsTopicKey<E> topicKey,
                                                                 final Consumer<EfsPublishStatus<E>> pscb,
                                                                 final IEventRouter<E> router,
                                                                 final IEfsAgent subscriber)
    {
        final Subscription<E> retval;

        Objects.requireNonNull(topicKey, NULL_TOPIC_KEY);
        Objects.requireNonNull(pscb, NULL_CALLBACK);
        Objects.requireNonNull(router, NULL_ROUTER);
        Objects.requireNonNull(subscriber, NULL_AGENT);

        sLogger.info("Router subscription to {}.", topicKey);

        mTopicLock.lock();
        try
        {
            final TopicFeed<E> topicInfo = doAddTopic(topicKey);

            retval = topicInfo.subscribe(pscb,
                                         subscriber,
                                         router);
        }
        finally
        {
            mTopicLock.unlock();
        }

        return (retval);
    } // end of subscribeRouter(EfsTopicKey, IEventRouter)

    /**
     * Returns efs event bus associated with the given name. If
     * there is no such event bus for given name, then a new
     * event bus is constructed for that name.
     * @param busName bus name. Must be unique within JVM.
     * @return event bus for given name.
     * @throws IllegalArgumentException
     * if {@code busName} is either {@code null}, an empty
     * string, or blank.
     */
    public static EfsEventBus findOrCreateBus(final String busName)
    {
        if (Strings.isNullOrEmpty(busName) || busName.isBlank())
        {
            throw (
                new IllegalArgumentException(INVALID_BUS_NAME));
        }

        return (
            sBuses.computeIfAbsent(
                busName, n -> new EfsEventBus(busName)));
    } // end of createBus(String)

    /**
     * Performs the actual work of adding a topic key to the
     * topic map if not already in the map. Caller is expected to
     * have validated the topic key argument and acquired the
     * topic map lock prior to calling this method.
     * @param <E> efs event type.
     * @param topicKey event topic key to be added.
     * @return topic feed for given topic key.
     */
    @SuppressWarnings ("unchecked")
    private <E extends IEfsEvent> TopicFeed<E> doAddTopic(final EfsTopicKey<E> topicKey)
    {
        final TopicFeed<E> retval;

        // Is this topic key already known?
        if (mTopicMap.containsKey(topicKey))
        {
            // Yes, return existing feed.
            retval = (TopicFeed<E>) mTopicMap.get(topicKey);
        }
        else
        {
            // No. Add topic key and then find all matching
            // wildcard advertisements and subscriptions.
            retval = new TopicFeed<>(topicKey);
            mTopicMap.put(topicKey, retval);
            findMatchingWildcardAccess(topicKey);
        }

        return (retval);
    } // end of doAddTopic(EfsTopicKey<>)

    /**
     * Returns a new subscription based on {@code isInBox} flag
     * and adds to topic map. If this is the first subscription
     * to topic key, then it is matched against existing
     * wildcard subscriptions.
     * @param <E> event class.
     * @param isInBox {@code true} if this is an inbox
     * subscription.
     * @param topicKey event class and topic key.
     * @param pscb {@link EfsPublishStatus} callback.
     * @param ecb event callback.
     * @param subscriber subscribing agent.
     * @return closeable subscription instance.
     * @throws NullPointerException
     * if either {@code topicKey}, {@code pscb}, {@code ecb}, or
     * {@code subscriber} is {@code null}.
     * @throws IllegalStateException
     * if {@code subscriber} is not registered with
     * {@code EfsDispatcher}.
     */
    @SuppressWarnings({"java:S2093", "unchecked"})
    private <E extends IEfsEvent> Subscription<E> subscribe(final boolean isInBox,
                                                            final EfsTopicKey<E> topicKey,
                                                            final Consumer<EfsPublishStatus<E>> pscb,
                                                            final Consumer<E> ecb,
                                                            final IEfsAgent subscriber)
    {
        final Subscription<E> retval;

        Objects.requireNonNull(topicKey, NULL_TOPIC_KEY);
        Objects.requireNonNull(pscb, NULL_CALLBACK);
        Objects.requireNonNull(ecb, NULL_CALLBACK);
        Objects.requireNonNull(subscriber, NULL_AGENT);

        // Is subscriber a registered agent?
        if (!EfsDispatcher.isRegistered(subscriber))
        {
            throw (
                new IllegalStateException(
                    String.format(
                        "%s is not registered with a dispatcher",
                        subscriber.name())));
        }

        sLogger.info("{} {} subscription to {}.",
                     subscriber.name(),
                     (isInBox ? "inbox" : "concrete"),
                     topicKey);

        mTopicLock.lock();
        try
        {
            final TopicFeed<E> topicInfo = doAddTopic(topicKey);

            retval = topicInfo.subscribe(isInBox,
                                         pscb,
                                         ecb,
                                         subscriber);
        }
        finally
        {
            mTopicLock.unlock();
        }

        return (retval);
    } // end of subscribe(...)

    /**
     * Finds those topics which match the given wildcard
     * subscription and has the wildcard subscription subscribe
     * to that matching topic.
     * @param <E> event class.
     * @param wca find topics matching this wildcard
     * subscription.
     */
    private <E extends IEfsEvent> void findMatchingTopics(final WildcardAccessPoint<E> wca)
    {
        final Set<EfsTopicKey<?>> topics = mTopicMap.keySet();

        topics.stream()
              .filter(wca::matches)
              .forEachOrdered(wca::addAccess);
    } // end of findMatchingTopics(Pattern)

    /**
     * Given a new concrete topic, find all extant wildcard
     * subscriptions matching that topic, and have that
     * wildcard subscription subscribe to topic.
     * @param topic new concrete topic.
     */
    private <E extends IEfsEvent> void findMatchingWildcardAccess(final EfsTopicKey<E> topicKey)
    {
        mWildcards.stream()
                  .filter(s -> s.matches(topicKey))
                  .forEachOrdered(a -> a.addAccess(topicKey));
    } // end of findMatchingWildcardAccess(EfsTopicKey)

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Tracks publishers and subscribers for a given topic key.
     * Responsible for forwarding published events to subscribers
     * <em>if</em> there are any subscribers. If there are no
     * subscribers, then throws an {@code IllegalStateException}.
     *
     * @param <E> event class.
     */
    private static final class TopicFeed<E extends IEfsEvent>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * This unique topic.
         */
        private final EfsTopicKey<E> mTopicKey;

        /**
         * Maps advertised publishing agent names to object
containing publishing agent active fowardEvent state where
{@code true} means active and {@code false} means
         * inactive.
         */
        private final ConcurrentHashMap<String, Advertisement<E>> mPublishers;

        /**
         * Tracks number of advertised publishing agents for this
         * topic. This value is &ge; zero and &ge;
         * {@link #mActivePublishers}.
         */
        private final AtomicInteger mAdvertisedPublishers;

        /**
         * Tracks number of actively publishing agents for this
         * topic. This value is &ge; zero and &le;
         * {@link #mAdvertisedPublishers}.
         */
        private final AtomicInteger mActivePublishers;

        /**
         * Active subscribers for this topic. Subscribers list
         * is immutable.
         */
        private final AtomicReference<List<Subscription<E>>> mSubscribers;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates a new topic information instance for given
         * event class+topic key.
         * @param topicKey event class+topic key.
         */
        private TopicFeed(final EfsTopicKey<E> topicKey)
        {
            mTopicKey = topicKey;
            mPublishers = new ConcurrentHashMap<>();
            mAdvertisedPublishers = new AtomicInteger();
            mActivePublishers = new AtomicInteger();
            mSubscribers =
                new AtomicReference<>(ImmutableList.of());
        } // end of TopicInfo(EfsTopicKey)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns {@code true} if there are active subscribers
         * to this topic and {@code false} if there are no
         * subscribers.
         * @return {@code true} if there are active subscribers
         * to this topic.
         */
        private boolean hasSubscribers()
        {
            return (!(mSubscribers.get()).isEmpty());
        } // end of hasSubscribers()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Registers given agent as a topic publisher, returning
         * a closeable instance used to retract the
         * advertisement.
         * @param sscb dispatch {@code EfsSubscribeStatus} events
         * to this publisher callback.
         * @param publisher agent advertising ability to fowardEvent
events on this topic.
         * @return closeable instance used to retract
         * advertisement.
         * @throws IllegalStateException
         * if {@code publisher} is already advertised.
         */
        private synchronized Advertisement<E> advertise(final Consumer<EfsSubscribeStatus<E>> sscb,
                                                        final IEfsAgent publisher)
        {
            final String pubName = publisher.name();
            final Advertisement<E> retval;

            if (mPublishers.containsKey(pubName))
            {
                throw (
                    new IllegalStateException(
                        String.format(
                            "agent {} already advertised for topic {}",
                            pubName,
                            mTopicKey)));
            }

            // Add publisher to advertised publishers map.
            retval = new Advertisement<>(this, publisher, sscb);
            mPublishers.put(pubName, retval);
            mAdvertisedPublishers.incrementAndGet();

            // Foward subscribe and fowardEvent status events based
            // on updated status.
            fowardSubscribeStatus(retval);
            forwardPublishStatus();

            return (retval);
        } // end of advertise(Consumer, IEfsAgent)

        /**
         * Removes publisher agent from publishers map and
         * updates advertised and active publisher counts.
         * Dispatches publish status event to all subscribers.
         * @param pubName unadvertised publisher name.
         */
        private synchronized void unadvertise(final IEfsAgent publisher)
        {
            final String pubName = publisher.name();
            final Advertisement<E> ad =
                mPublishers.remove(pubName);

            // Is this a known publishing agent for this topic?
            if (ad != null)
            {
                // Yes. Update advertised and active flags.
                mAdvertisedPublishers.decrementAndGet();

                // If publisher is active at time of
                // unadvertisement, then decrement active
                // publisher count.
                if (ad.isPublished())
                {
                    mActivePublishers.decrementAndGet();
                }

                forwardPublishStatus();
            }
        } // end of unadvertise(String)

        /**
         * Creates and returns either a concrete or an in-box
         * subscription based on {@code isInBox}. Subscription
         * is added to subscribers list resulting in a
         * subscription status update published to extant
         * subscribers.
         * @param isInBox {@code true} if this is an in-box
         * subscription and {@code false} if a concrete
         * subscription.
         * @param pscb publish status event callback.
         * @param ecb event callback.
         * @param subscriber subscribing agent.
         * @return subscription used to interact with topic feed.
         */
        private synchronized Subscription<E> subscribe(final boolean isInBox,
                                                       final Consumer<EfsPublishStatus<E>> pscb,
                                                       final Consumer<E> ecb,
                                                       final IEfsAgent subscriber)
        {
            final Subscription<E> retval =
                (isInBox ?
                 new InboxSubscription<>(this,
                                         subscriber,
                                         pscb,
                                         ecb) :
                 new ConcreteSubscription<>(this,
                                            subscriber,
                                            pscb,
                                            ecb));

            subscribe(retval);

            sLogger.trace("{} subscribed to {}.",
                          subscriber.name(),
                          mTopicKey);

            return (retval);
        } // end of subscribe(...)

        /**
         * Creates and returns a router subscription for given
         * event router instance.
         * @param router event router.
         * @return event router subscription.
         */
        private synchronized Subscription<E> subscribe(final Consumer<EfsPublishStatus<E>> pscb,
                                                       final IEfsAgent subscriber,
                                                       final IEventRouter<E> router)
        {
            final Subscription<E> retval =
                new RouterSubscription<>(this,
                                         pscb,
                                         subscriber,
                                         router);

            subscribe(retval);

            sLogger.trace("Added routing subscriber {} to {}.",
                          subscriber.name(),
                          mTopicKey);

            return (retval);
        } // end of subscribe(IEventRouter)

        /**
         * Adds given subscription to subscriptions list,
         * forwards current publish status to subscriber, and
         * then forwards updated subscribe status to publishers.
         * @param sub new subscription.
         */
        private void subscribe(final Subscription<E> sub)
        {
            final List<Subscription<E>> subs =
                mSubscribers.get();
            final int numSubs = subs.size();
            final ImmutableList.Builder<Subscription<E>> builder =
                ImmutableList.builder();

            builder.addAll(subs);
            builder.add(sub);
            mSubscribers.set(builder.build());

            // Forward current fowardEvent status to new
            // subscriber.
            forwardPublishStatus(sub);

            // Forward current subscribe status to existing
            // publishers.
            forwardSubscribeStatus(numSubs, (numSubs + 1));
        } // end of subscribe(Subscription)

        /**
         * Removes given subscription from subscription list and,
         * if subscription was on that list, informs extant
         * publishers of this update.
         * @param sub remove this closed subscription from
         * subscription list.
         */
        private synchronized void unsubscribe(final Subscription<E> sub)
        {
            final List<Subscription<E>> subs =
                new ArrayList<>(mSubscribers.get());
            final int numSubs = subs.size();

            // Was this subscription in the list?
            if (subs.remove(sub))
            {
                mSubscribers.set(ImmutableList.copyOf(subs));

                // Yes. Dispatch subscriber count update to
                // publishers.
                forwardSubscribeStatus(numSubs, (numSubs - 1));
            }
        } // end of unsubscribe(AbstractSubscription<E>)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Updates active publisher count based on given status
         * flag. If status flag is {@code true}, count is
         * incremented; if {@code false}, count is decremented.
Once count is updated, then subscribers are informed
of this update to fowardEvent status.
         * @param statusFlag {@code true} means publishing is
         * up and {@code false} means down.
         */
        private void updatePublisherStatus(final boolean statusFlag)
        {
            // Up or down?
            if (statusFlag)
            {
                // Up. Increment active count.
                mActivePublishers.incrementAndGet();
            }
            else
            {
                // Down. Decrement active count.
                mActivePublishers.decrementAndGet();
            }

            // Send updated feed status to subscribers.
            forwardPublishStatus();
        } // end of updatePublisherStatus(boolean)

        /**
         * Posts a fowardEvent status event to all active
subscribers using the current
         */
        private void forwardPublishStatus()
        {
            final List<Subscription<E>> subs =
                mSubscribers.get();

            // Are there any subscribers?
            if (!subs.isEmpty())
            {
                // Yes. Post a new fowardEvent status event to each
                // subscriber.
                final EfsPublishStatus.Builder<E> builder =
                    EfsPublishStatus.builder();
                final EfsPublishStatus<E> pse =
                    builder.topicKey(mTopicKey)
                           .advertisedPublishers(
                               mAdvertisedPublishers.get())
                           .activePublishers(
                               mActivePublishers.get())
                           .build();

                for (Subscription<E> sub : subs)
                {
                    sub.forwardPublishStatus(pse);
                }
            }
        } // end of forwardPublishStatus()

        /**
         * Creates a new fowardEvent status event based on current
publisher stats and dispatches this event to given
callback and subscribing agent.
         * @param pscb fowardEvent status event callback.
         * @param subscriber subscribing agent.
         */
        private void forwardPublishStatus(final Subscription<E> sub)
        {
            final EfsPublishStatus.Builder<E> builder =
                EfsPublishStatus.builder();
            final EfsPublishStatus<E> pse =
                builder.topicKey(mTopicKey)
                       .advertisedPublishers(
                           mAdvertisedPublishers.get())
                       .activePublishers(
                           mActivePublishers.get())
                       .build();

            sub.forwardPublishStatus(pse);
        } // end of forwardPublishStatus(Consumer, IEfsAgent)

        private void forwardSubscribeStatus(final int previousSubscribers,
                                            final int activeSubscribers)
        {
            if (!mPublishers.isEmpty())
            {
                final EfsSubscribeStatus.Builder<E> builder =
                    EfsSubscribeStatus.builder();
                final EfsSubscribeStatus<E> sse =
                    builder.topicKey(mTopicKey)
                           .previousSubscribers(previousSubscribers)
                           .activeSubscribers(activeSubscribers)
                           .build();

                for (Advertisement<E> ad : mPublishers.values())
                {
                    ad.forwardSubscribeStatus(sse);
                }
            }
        } // end of forwardSubscribeStatus(int)

        /**
         * Dispatches current subscribe status to given
         * publishing agent.
         * @param ad dispatch subscribe status to given
         * callback and publishing agent.
         */
        private void fowardSubscribeStatus(final Advertisement<E> ad)
        {
            final int subscribers = (mSubscribers.get()).size();
            final EfsSubscribeStatus.Builder<E> builder =
                EfsSubscribeStatus.builder();
            final EfsSubscribeStatus<E> sse =
                builder.topicKey(mTopicKey)
                       .previousSubscribers(subscribers)
                       .activeSubscribers(subscribers)
                       .build();

            ad.forwardSubscribeStatus(sse);
        } // end of forwardSubscribeStatus(Advertisement)

        /**
         * Dispatches given event to all subscribers currently
         * in subscribers list.
         * @param event dispatch this event.
         */
        private void forwardEvent(final E event)
        {
            (mSubscribers.get()).forEach(
                s -> s.fowardEvent(event));
        } // end of forwardEvent(E)
    } // end of class TopicFeed

    /**
     * Base class for topic advertisements and subscriptions.
     * Contains common data members such as topic feed, active
     * flag, publisher status, event count, and latest event
     * timestamp.
     * <p>
     * Note: once an access point is closed it remains closed and
     * cannot be re-opened.
     * </p>
     *
     * @param <E> event class.
     */
    private abstract static class AccessPoint<E extends IEfsEvent>
        implements AutoCloseable
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Accesses this topic feed.
         */
        protected final TopicFeed<E> mTopic;

        /**
         * Publishing or subscribing agent. Set to {@code null}
         * for a {@code RouterSubscription}.
         */
        protected final IEfsAgent mAgent;

        /**
         * Set to {@code true} if access point is active and
         * {@code false} otherwise. Initially set to
         * {@code true}.
         */
        protected final AtomicBoolean mActive;

        /**
         * Set to {@code true} if there is a publisher actively
         * posting events to this topic and {@code false} if not.
         * Initially set to {@code false}.
         */
        protected final AtomicBoolean mPubStatus;

        /**
         * Total number of events posted/received on this
         * advertisement/subscription.
         */
        protected long mEventCount;

        /**
         * Timestamp of latest event posted/received on this
         * topic. Initialized to epoch time.
         */
        protected Instant mLatestEvent;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates a new topic access point for given topic and
         * agent.
         * @param topic topic feed.
         * @param agent publisher or subscriber agent.
         */
        private AccessPoint(final TopicFeed<E> topic,
                            final IEfsAgent agent)
        {
            mTopic = topic;
            mAgent = agent;

            mActive = new AtomicBoolean(true);
            mPubStatus = new AtomicBoolean();
            mEventCount = 0L;
            mLatestEvent = Instant.EPOCH;
        } // end of AbstractFeed<>(TopicFeed, IEfsAgent)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns event class+topic key for this feed.
         * @return event class+topic key.
         */
        public final EfsTopicKey<E> topicKey()
        {
            return (mTopic.mTopicKey);
        } // end of topicKey()

        /**
         * Returns {@code true} if this access point is open
         * and {@code false} if closed. Note: once an access
         * point is closed, it remains closed and cannot be
         * re-opened.
         * @return {@code true} if open and {@code false} if
         * closed.
         */
        public final boolean isOpen()
        {
            return (mActive.get());
        } // end of isOpen()

        /**
         * Returned value interpretation depends on whether this
         * is an advertisement or a subscription:
         * <ul>
         *   <li>
         *     <strong>Advertisement:</strong> {@code true} means
         *     that publisher is able to post events to topic and
         *     {@code false} if unable to post.
         *   </li>
         *   <li>
         *     <strong>Subscription:</strong> {@code true} means
         *     there is at least one publisher actively posting
         *     events to this topic and {@code false} means there
         *     are no publishers.
         *   </li>
         * </ul>
         * Note: a {@code true} value does <em>not</em> mean that
         * events are actively being posted to the topic but only
         * that they <em>may</em> be posted. Conversely,
         * {@code false} means that no events are being posted to
         * the topic.
         * @return {@code true} if events may be posted to topic
         * and {@code false} if events are not being posted to
         * topic.
         */
        public final boolean isPublished()
        {
            return (mPubStatus.get());
        } // end of isPublished()

        /**
         * Returns total number of events posted/received by
         * this advertisement/subscription since opening.
         * @return total number of events on this access point.
         */
        public final long eventCount()
        {
            return (mEventCount);
        } // end of eventCount()

        /**
         * Returns timestamp of latest event posted/received by
         * this advertisement/subscription. If no event has
         * crossed this access point, then returns
         * {@link Instant#EPOCH epoch time}.
         * @return latest event timestamp.
         */
        @Nonnull public final Instant latestEvent()
        {
            return (mLatestEvent);
        } // end of latestEvent()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of class AbstractFeedAccess

    /**
     * An advertisement is an publishing agent's access point to
     * a event class+topic feed. Publisher uses this
     * advertisement to:
     * <ol>
     *   <li>
     *     {@link #publishStatus(boolean) Set the publisher's event publishing status}.
     *     Setting this value to {@code true} means that the
    fowardEvent is able to fowardEvent events (although is may
    chose not to do so) and {@code false} means that is is
    unable to fowardEvent events (and is not allowed to do
    so).
  </li>
     *   <li>
     *     {@link #publish(IEfsEvent)  Publish events} to extant
    subscribers. Doing so requires this advertisement to
    be 1) open, 2) fowardEvent status is {@code true}, and 3)
     *     there are subscribers to this topic.
     *   </li>
     * </ol>
     * Once an advertisement is {@link #close() closed}, it
cannot be re-opened and setting fowardEvent status or
publishing events is disallowed.
{@link #eventCount() Event count} and
     * {@link #latestEvent() latest event timestamp} may still
     * be retrieved but these values will never change once the
     * advertisement is closed. That said,
     * {@link #hasSubscribers()} will reflect the current topic
     * subscription status since that is independent of
     * advertisements.
     *
     * @param <E> event class
     */
    public static final class Advertisement<E extends IEfsEvent>
        extends AccessPoint<E>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Dispatcher {@code EfsSubscribeStatus} events to this
         * callback.
         */
        private final Consumer<EfsSubscribeStatus<E>> mStatusCallback;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates a new advertisement instance containing topic
         * feed, publishing agent, and subscription status event
         * callback.
         * @param topic topic feed.
         * @param publisher publishing agent.
         * @param sscb subscription status event callback.
         */
        private Advertisement(final TopicFeed<E> topic,
                              final IEfsAgent publisher,
                              final Consumer<EfsSubscribeStatus<E>> sscb)
        {
            super (topic, publisher);

            mStatusCallback = sscb;
        } // end of Advertisement(TopicInfo, IEfsAgent)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // AutoCloseable Interface Implementation.
        //

        /**
         * Closes this advertisement. If will no longer be
         * possible to publish events to this advertisement after
         * this point.
         * <p>
         * If advertisement is already closed, then does nothing.
         * </p>
         * @throws Exception
         * if an error occurs closing this advertisement.
         */
        @Override
        public void close()
            throws Exception
        {
            // Is this advertisement currently open?
            if (mActive.compareAndExchange(true, false))
            {
                // Yes. Retract this advertisement from the
                // topic.
                mTopic.unadvertise(mAgent);
            }
        } // end of close()

        //
        // end of AutoCloseable Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns {@code true} if there is at least one
         * subscriber to this topic and {@code false} if there
         * are none. {@link #publish(IEfsEvent)} may be called
         * only when this method returns {@code true}.
         * @return {@code true} if publisher has subscribers and
is clear to fowardEvent events to this advertisement.
         */
        public boolean hasSubscribers()
        {
            return (mTopic.hasSubscribers());
        } // end of hasSubscribers()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets publisher's active publishing status to given
         * value where {@code true} means publisher can post
         * event and {@code false} means publisher cannot do so.
         * If given publishing status is different than current
         * status, then all extant subscribers are informed of
         * this change.
         * @param pubStatus active publishing status.
         * @throws IllegalStateException
         * if this advertisement is closed.
         */
        public void publishStatus(final boolean pubStatus)
        {
            // Is this adverisement still open?
            if (!mActive.get())
            {
                // No.
                throw (
                    new IllegalStateException(
                        ADVERTISEMENT_CLOSED));
            }

            // Is this new status different than the current
            // status?
            if (mPubStatus.compareAndSet(!pubStatus, pubStatus))
            {
                sLogger.info(
                    "{} set {} publish status to {}.",
                    mAgent.name(),
                    mTopic.mTopicKey,
                    pubStatus);

                // Yes. Forward this status change to
                // subscribers.
                mTopic.updatePublisherStatus(pubStatus);
            }
            // No change. Nothing more to do.
        } // end of publishStatus(boolean)

        /**
         * Publishes given event to all extant subscribers.
         * @param event forward this event to subscribers.
         * @throws NullPointerException
         * if {@code event} is {@code null}.
         * @throws IllegalStateException
         * if:
         * <ul>
         *   <li>
         *     if this advertisement is closed or
         *   </li>
         *   <li>
         *     if publishing agent has not set publish status
         *     to {@code true}.
         *   </li>
         * </ul>
         */
        public void publish(final E event)
        {
            Objects.requireNonNull(event, NULL_EVENT);

            // Is this advertisement still open?
            if (!mActive.get())
            {
                // No.
                throw (
                    new IllegalStateException(
                        ADVERTISEMENT_CLOSED));
            }

            // Has this publisher marked this feed as up?
            if (!mPubStatus.get())
            {
                // No. Publisher needs to do that first prior to
                // posting any events.
                throw (
                    new IllegalStateException(
                        PUBLISH_STATUS_DOWN));
            }

            // Everything checks out. Cleared to send event to
            // subscribers.
            mTopic.forwardEvent(event);
            mLatestEvent = Instant.now();
            ++mEventCount;

            sLogger.trace("{} published {} event {} at {}.",
                          mAgent.name(),
                          mTopic.mTopicKey,
                          mEventCount,
                          mLatestEvent);
        } // end of fowardEvent(E)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Dispatches given subscribe status event to publishing
         * agent on configured callback.
         * @param sse subscribe status event.
         */
        private void forwardSubscribeStatus(final EfsSubscribeStatus<E> sse)
        {
            EfsDispatcher.dispatch(mStatusCallback, sse, mAgent);
        }
    } // end of class Advertisement

    /**
     * Abstract base class for concrete, inbox, and router
     * subscriptions. Defines {@code AutoCloseable.close} method.
     * @param <E> efs event class.
     */
    public abstract static class Subscription<E extends IEfsEvent>
        extends AccessPoint<E>
    {
    //-----------------------------------------------------------
    // Member data.
    //

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates subscription for given topic feed and
         * subscribing agent.
         * @param topic subscription is for this topic.
         * @param subscriber agent placing this subscription.
         */
        protected Subscription(final TopicFeed<E> topic,
                               final IEfsAgent subscriber)
        {
            super (topic, subscriber);
        } // end of AbstractSubscription(TopicInfo)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Declarations.
        //

        /**
         * Dispatches fowardEvent status event to subscriber.
         * @param pse fowardEvent status event.
         */
        protected abstract void forwardPublishStatus(final EfsPublishStatus<E> pse);

        /**
         * Performs work of dispatching given event to
         * subscribing agent and callback.
         * @param event dispatch this event to subscribing agent
         * and callback.
         */
        protected abstract void fowardEvent(final E event);

        //
        // end of Abstract Method Declarations.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // AutoCloseable Interface Implementation.
        //

        /**
         * Closes this subscription. Once completed, events will
         * no longer be dispatched to subscriber. It is possible
         * that events dispatched to subscriber prior to closing
         * this subscription will be delivered after this method
         * returned.
         * <p>
         * If subscription is already closed, then does nothing.
         * </p>
         * @throws Exception
         * if an error occurs closing this subscription.
         */
        @Override
        public final void close()
            throws Exception
        {
            // Is this subscription open?
            if (mActive.compareAndSet(true, false))
            {
                // Yes. Remove this subscription from the topic.
                mTopic.unsubscribe(this);
            }
        } // end of close()

        //
        // end of AutoCloseable Interface Implementation.
        //-------------------------------------------------------
    } // end of class AbstractSubscription

    /**
     * Subscription containing a fixed callback and agent pair
     * to which all events are dispatched.
     *
     * @param <E> event class.
     */
    private static class ConcreteSubscription<E extends IEfsEvent>
        extends Subscription<E>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Post {@code EfsPublishStatus} events to subscribers
         * via this callback.
         */
        private final Consumer<EfsPublishStatus<E>> mPublishStatusCallback;

        /**
         * Dispatch event to this callback.
         */
        protected final Consumer<E> mCallback;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates a concrete subscription to specified topic
         * key with given callback and agent.
         * @param topic subscription topic.
         * @param subscriber dispatch events to this agent.
         * @param pscb fowardEvent status event callback.
         * @param ecb dispatch events to this callback.
         */
        private ConcreteSubscription(final TopicFeed<E> topic,
                                     final IEfsAgent subscriber,
                                     final Consumer<EfsPublishStatus<E>> pscb,
                                     final Consumer<E> ecb)
        {
            super (topic, subscriber);

            mPublishStatusCallback = pscb;
            mCallback = ecb;
        } // end of ConcreteSubscription(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Implementations.
        //

        /**
         * Dispatches fowardEvent status event to subscriber.
         * @param pse fowardEvent status event.
         */
        @Override
        protected final void forwardPublishStatus(final EfsPublishStatus<E> pse)
        {
            EfsDispatcher.dispatch(
                mPublishStatusCallback, pse, mAgent);
        } // end of forwardPublishStatus(final EfsPublishStatus<E> pse)

        /**
         * Dispatches event to subscriber on configured event
         * callback.
         * @param event dispatch this event to subscriber.
         */
        @Override
        protected void fowardEvent(final E event)
        {
            try
            {
                EfsDispatcher.dispatch(mCallback, event, mAgent);
            }
            catch (Exception jex)
            {
                    sLogger.warn(
                        "Failed to forward {} event to agent {}.",
                        mTopic.mTopicKey,
                        mAgent.name(),
                        jex);
            }
        } // end of fowardEvent(E)

        //
        // end of Abstract Method Implementations.
        //-------------------------------------------------------
    } // end of class ConcreteSubscription

    /**
     * Subscription where only latest (conflated) event is
     * delivered to subscribing agent because agent is only
     * interested in latest event.
     *
     * @param <E> event class.
     */
    private static final class InboxSubscription<E extends IEfsEvent>
        extends ConcreteSubscription<E>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Forward events to subscriber using this conflation
         * event.
         */
        private final ConflationEvent<E> mInbox;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates an inbox subscription to specified topic key
         * with given event callback and subscription agent.
         * @param topic subscription topic.
         * @param subscriber dispatch events to this agent.
         * @param pscb fowardEvent status event callback.
         * @param ecb dispatch events to this callback.
         */
        private InboxSubscription(final TopicFeed<E> topic,
                                  final IEfsAgent subscriber,
                                  final Consumer<EfsPublishStatus<E>> pscb,
                                  final Consumer<E> ecb)
        {
            super (topic, subscriber, pscb, ecb);

            mInbox = new ConflationEvent<>();
        } // end of InboxSubscription(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Implementations.
        //

        /**
         * Places event inside inbox (conflation event). If inbox
         * is <em>not</em> currently posted to subscribing
         * agent's event queue, then inbox is dispatched to
         * agent.
         * @param event place event inside inbox.
         */
        @Override
        protected void fowardEvent(final E event)
        {
            // Is this inbox event already on the agent's
            // queue?
            if (mInbox.offer(event))
            {
                // No. Dispatch inbox event to agent.
                try
                {
                    EfsDispatcher.dispatch(
                        mCallback, event, mAgent);
                }
                catch (Exception jex)
                {
                    sLogger.warn(
                        "Failed to forward {} event to agent {}.",
                        mTopic.mTopicKey,
                        mAgent.name(),
                        jex);
                }
            }
        } // end of pubish(E)

        //
        // end of Abstract Method Implementations.
        //-------------------------------------------------------
    } // end of class InboxSubscription

    /**
     * Subscription where event is dynamically routed to various
     * agents based on event contents.
     *
     * @param <E> event class.
     */
    private static final class RouterSubscription<E extends IEfsEvent>
        extends Subscription<E>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Post {@code EfsPublishStatus} events to subscribers
         * via this callback.
         */
        private final Consumer<EfsPublishStatus<E>> mPublishStatusCallback;

        /**
         * Dynamically routes events to various agents and
         * callbacks based on event's values.
         */
        private final IEventRouter<E> mRouter;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates a router subscription for given event topic,
         * subscriber, and subscription agent.
         * @param topic subscription topic key.
         * @param subscriber agent placing this subscription.
         * @param router event router.
         */
        private RouterSubscription(final TopicFeed<E> topic,
                                   final Consumer<EfsPublishStatus<E>> pscb,
                                   final IEfsAgent subscriber,
                                   final IEventRouter<E> router)
        {
            super (topic, subscriber);

            mPublishStatusCallback = pscb;
            mRouter = router;
        } // end of RouterSubscription(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Implementations.
        //

        /**
         * Has event router dispatch given fowardEvent status event
to all its subordinate targets.
         * @param pse dispatch this fowardEvent status event to all
router targets.
         */
        @Override
        protected void forwardPublishStatus(final EfsPublishStatus<E> pse)
        {
            EfsDispatcher.dispatch(
                mPublishStatusCallback, pse, mAgent);
        } // end of forwardPublishStatus(EfsPublishStatus)

        /**
         * Dispatches event to agent and callback specified by
         * router. If router returns a {@code null} target, then
         * event is quietly not dispatched.
         * @param event dispatch this event to agent and
         * callback specified by event router.
         */
        @Override
        protected void fowardEvent(E event)
        {
            final EfsDispatchTarget<E> target =
                mRouter.routeTo(event);

            if (target != null)
            {
                try
                {
                    EfsDispatcher.dispatch(target, event);
                }
                catch (Exception jex)
                {
                    sLogger.warn(
                        "Failed to dispatch {} event to agent {}.",
                        mTopic.mTopicKey,
                        (target.agent()).name(),
                        jex);
                }
            }
            else
            {
                sLogger.trace(
                    "{} event not routed to any agent, {}.",
                    mTopic.mTopicKey,
                    event);
            }
        } // end of fowardEvent(E)

        //
        // end of Abstract Method Implementations.
        //-------------------------------------------------------
    } // end of class RouterSubscription

    /**
     * Base class for wildcard advertisements and subscriptions.
     * Contains event class, wildcard topic and pattern, agent,
     * and flag denoting whether access point is open or closed.
     *
     * @param <E> event type.
     */
    private abstract class WildcardAccessPoint<E extends IEfsEvent>
        implements AutoCloseable
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Accessing this event class.
         */
        protected final Class<E> mEventClass;

        /**
         * Regular expression topic matched against concrete
         * topics.
         */
        protected final String mWildcardTopic;

        /**
         * Compiled {@link #mWildcardTopic}.
         */
        protected final Pattern mPattern;

        /**
         * Report newly discovered topic keys to agent via this
         * callback.
         */
        protected final Consumer<EfsTopicKey<E>> mTopicUpdate;

        /**
         * Dispatch event to this agent.
         */
        protected final IEfsAgent mAgent;

        /**
         * Initialized to {@code true} meaning that subscription
         * is open and events may be dispatched. Set to
         * {@code false} when subscription is closed.
         */
        private final AtomicBoolean mOpenFlag;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        protected WildcardAccessPoint(final Class<E> eventClass,
                                      final String wildcardTopic,
                                      final Pattern pattern,
                                      final Consumer<EfsTopicKey<E>> topicUpdate,
                                      final IEfsAgent agent)
        {
            mEventClass = eventClass;
            mWildcardTopic = wildcardTopic;
            mPattern = pattern;
            mTopicUpdate = topicUpdate;
            mAgent = agent;

            mOpenFlag = new AtomicBoolean(true);
        } // end of WildcardAccessPoint(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // AutoCloseable Interface Implementation.
        //

        /**
         * Closes wildcard access point if currently open. If
         * closed, then does nothing.
         * @throws Exception
         * if an error occurs.
         */
        @Override
        public void close()
            throws Exception
        {
            // Is this access point open?
            if (mOpenFlag.compareAndSet(true, false))
            {
                // Yes. Close the access point now.
                doClose();
            }
        } // end of close()

        //
        // end of AutoCloseable Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Declarations.
        //

        /**
         * Adds an advertisement or subscription access point
         * for given topic key.
         * @param topicKey newly encountered topic key matching
         * this wildcard access point.
         */
        protected abstract void addAccess(final EfsTopicKey<?> topicKey);

        /**
         * Subclass overrides this method to perform actual work
         * to close this access paint.
         */
        protected abstract void doClose();

        //
        // end of Abstract Method Declarations.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns {@code true} if wildcard access point is open
         * and {@code false} if closed.
         * @return {@code true} if wildcard access point is open.
         */
        public final boolean isOpen()
        {
            return (mOpenFlag.get());
        } // end of isOpen()

        /**
         * Returns event class.
         * @return event class.
         */
        public final Class<E> eventClass()
        {
            return (mEventClass);
        } // end of eventClass()

        /**
         * Returns access point wildcard topic.
         * @return wildcard topic.
         */
        public final String wildcardTopic()
        {
            return (mWildcardTopic);
        } // end of wildcardTopic()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        /**
         * Returns {@code true} if event class and wildcard topic
         * pattern matches given topic key.
         * @param topic check if wildcard topic pattern matches
         * this topic.
         * @return {@code true} if event class and wildcard topic
         * matches given topic key.
         */
        private boolean matches(final EfsTopicKey<?> topicKey)
        {
            return (
                mEventClass.equals(topicKey.eventClass()) &&
                (mPattern.matcher(topicKey.topic())).matches());
        } // end of matches(String)
    } // end of class WildcardAccessPoint<E extends IEfsEvent>

    private final class WildcardAdvertisement<E extends IEfsEvent>
        extends WildcardAccessPoint<E>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Dispatcher {@code EfsSubscribeStatus} events to this
         * callback.
         */
        private final Consumer<EfsSubscribeStatus<E>> mStatusCallback;

        /**
         * Advertisements created when wildcard topic matches
         * newly encountered concrete topics. When this wildcard
         * advertisement is closed, then all these advertisements
         * are closed.
         */
        private final Map<EfsTopicKey<E>, Advertisement<E>> mAdvertisements;

    //-----------------------------------------------------------
    // Member methods.
    //


        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates a new wildcard advertisement for given event
         * class and wildcard topic. Creates new concrete
         * advertisement based on remaining parameters.
         * @param eventClass advertise this event class.
         * @param wildcardTopic regular expression matched
         * against concrete topics.
         * @param pattern compiled {@code wildcardTopic}.
         * @param topicUpdate report newly discovered topic keys
         * to agent via this callback.
         * @param agent publisher agent.
         * @param sscb subscriber status lambda callback.
         */
        private WildcardAdvertisement(final Class<E> eventClass,
                                      final String wildcardTopic,
                                      final Pattern pattern,
                                      final Consumer<EfsTopicKey<E>> topicUpdate,
                                      final IEfsAgent agent,
                                      final Consumer<EfsSubscribeStatus<E>> sscb)
        {
            super (eventClass,
                   wildcardTopic,
                   pattern,
                   topicUpdate,
                   agent);

            mStatusCallback = sscb;

            mAdvertisements = new ConcurrentHashMap<>();
        } // end of WildcardAdvertisement(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Implementation.
        //

        /**
         * If this wildcard advertisement is open, then creates
         * a new advertisement for given concrete topic matching
         * wildcard topic. Newly created advertisement's publish
         * status is set to current value.
         * @param topicKey concrete topic key matching wildcard.
         */
        @Override
        @SuppressWarnings ("unchecked")
        protected void addAccess(final EfsTopicKey<?> topicKey)
        {
            // Is this wildcard advertisement still open?
            if (isOpen())
            {
                // Yes, create new advertisement for topic.
                final EfsTopicKey<E> key =
                    (EfsTopicKey<E>) topicKey;
                final Advertisement<E> ad =
                    advertise(key, mStatusCallback, mAgent);

                mAdvertisements.put(key, ad);

                // Inform agent about the new concrete
                // advertisement.
                mTopicUpdate.accept(key);
            }
        } // end of addAdvertisement(EfsTopicKey)

        /**
         * Closes all advertisements created when this wildcard
         * advertisement matched a concrete topic.
         *
         * @see WildcardAccessPoint#close()
         */
        @Override
        protected void doClose()
        {
            final List<AutoCloseable> ads =
                ImmutableList.copyOf(mAdvertisements.values());

            mAdvertisements.clear();

            for (AutoCloseable ac : ads)
            {
                try
                {
                    ac.close();
                }
                catch (Exception jex)
                {
                    // Ignore.
                }
            }
        } // end of doClose()

        //
        // end of Abstract Method Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns concrete advertisement associated with given
         * topic key. Returns {@code null} if there is no such
         * associated advertisement.
         * @param topicKey return advertisement associated with
         * this key.
         * @return concrete advertisement with given key or
         * {@code null}.
         * @throws NullPointerException
         * if {@code topicKey} is {@code null}.
         */
        @Nullable public Advertisement<E> advertisement(final EfsTopicKey<E> topicKey)
        {
            Objects.requireNonNull(topicKey, NULL_TOPIC_KEY);

            return (mAdvertisements.get(topicKey));
        } // end of advertisement(EfsTopicKey)

        /**
         * Returns an immutable list containing the concrete
         * topic keys to which this wildcard advertisement is
         * advertised. Note that these topic keys may change
         * over time as event bus encounters new concrete topics
         * which match the wildcard advertisement.
         * @return immutable list of concrete topic keys.
         */
        public List<EfsTopicKey<E>> advertisementKeys()
        {
            final ImmutableList.Builder<EfsTopicKey<E>> builder =
                ImmutableList.builder();

            (mAdvertisements.keySet()).forEach(
                k -> builder.add(k));

            return (builder.build());
        } // end of advertisementKeys()

        /**
         * Returns an immutable list containing the concrete
         * topics to which this wildcard advertisement is
         * advertised. Note that these topics may change over
         * time as event bus encounters new concrete topics
         * which match the wildcard advertisement.
         * @return immutable list of concrete topics.
         */
        public List<String> advertisementTopics()
        {
            final ImmutableList.Builder<String> builder =
                ImmutableList.builder();

            (mAdvertisements.keySet()).forEach(
                k -> builder.add(k.topic()));

            return (builder.build());
        } // end of advertisementTopics()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Applies given publish status to all extant concrete
         * advertisements.
         * @param pubStatus new event publish status.
         * @throws IllegalStateException
         * if this wildcard advertisement is closed.
         */
        public void publishStatusAll(final boolean pubStatus)
        {
            // Is this wildcard advertisement still open?
            if (!isOpen())
            {
                // No.
                throw (
                    new IllegalStateException(
                        ADVERTISEMENT_CLOSED));
            }

            sLogger.info(
                "{} set {} publish status to {}.",
                mAgent.name(),
                mWildcardTopic,
                pubStatus);

            // Yes. Apply this status change to all concrete
            // advertisements.
            (mAdvertisements.values()).forEach(
                a -> a.publishStatus(pubStatus));
        } // end of publishStatusAll(boolean)

        //
        // end of Set Methods.
        //-------------------------------------------------------
    } // end of class WildcardAdvertisement

    /**
     * Tracks concrete subscriptions created when new topics
     * for given event class are introduced to event bus. These
     * subscriptions are automatically closed when wildcard
     * subscription is closed.
     *
     * @param <E>
     */
    private final class WildcardSubscription<E extends IEfsEvent>
        extends WildcardAccessPoint<E>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Set to {@code true} if this is an inbox subscription.
         */
        private final boolean mInboxFlag;

        /**
         * Post {@code EfsPublishStatus} events to subscribers
         * via this callback.
         */
        private final Consumer<EfsPublishStatus<E>> mPublishStatusCallback;

        /**
         * Dispatch event to this callback.
         */
        private final Consumer<E> mCallback;

        /**
         * Subscriptions created when wildcard topic matches
         * newly encountered concrete topics. When this wildcard
         * subscription is closed, then all these subscriptions
         * are closed.
         */
        private final List<Subscription<E>> mSubscriptions;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates a new wildcard subscription for given event
         * class and wildcard topic. Creates new concrete
         * subscriptions based on the remaining parameters.
         * @param eventClass subscribe to this event class.
         * @param wildcardTopic regular expression matched
         * against concrete topics.
         * @param pattern compiled {@code wildcardTopic}.
         * @param topicUpdate report newly discovered topic keys
         * to agent via this callback.
         * @param agent subscriber agent.
         * @param isInbox {@code true} if this is an inbox
         * subscription.
         * @param pscb publisher status lambda callback.
         * @param callback event lambda callback.
         */
        @SuppressWarnings({"java:S107"})
        private WildcardSubscription(final Class<E> eventClass,
                                     final String wildcardTopic,
                                     final Pattern pattern,
                                     final Consumer<EfsTopicKey<E>> topicUpdate,
                                     final IEfsAgent agent,
                                     final boolean isInbox,
                                     final Consumer<EfsPublishStatus<E>> pscb,
                                     final Consumer<E> callback)
        {
            super (eventClass,
                   wildcardTopic,
                   pattern,
                   topicUpdate,
                   agent);

            mInboxFlag = isInbox;
            mPublishStatusCallback = pscb;
            mCallback = callback;

            mSubscriptions = new ArrayList<>();
        } // end of WildcardSubscription(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Implementation.
        //

        /**
         * If this wildcard subscription is open, then creates
         * a new subscription for given concrete topic matching
         * wildcard topic.
         * @param topicKey concrete topic key matching wildcard.
         */
        @SuppressWarnings ("unchecked")
        @Override
        protected void addAccess(final EfsTopicKey<?> topicKey)
        {
            // Is this wildcard subscription still open?
            if (isOpen())
            {
                final EfsTopicKey<E> key =
                    (EfsTopicKey<E>) topicKey;

                // Yes, create new subscription for topic.
                mSubscriptions.add(
                    subscribe(mInboxFlag,
                              key,
                              mPublishStatusCallback,
                              mCallback,
                              mAgent));

                // Inform agent about the new concrete
                // subscription.
                mTopicUpdate.accept(key);
            }
        } // end of addAccess(EfsTopicKey)

        /**
         * Closes all subscriptions created when this wildcard
         * subscription matched a concrete topic.
         *
         * @see WildcardAccessPoint#close()
         */
        @Override
        protected void doClose()
        {
            final List<AutoCloseable> subs =
                ImmutableList.copyOf(mSubscriptions);

            mSubscriptions.clear();

            for (AutoCloseable ac : subs)
            {
                try
                {
                    ac.close();
                }
                catch (Exception jex)
                {
                    // Ignore.
                }
            }
        } // end of doClose()

        //
        // end of Abstract Method Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns {@code true} if this wildcard subscription is
         * an inbox subscription.
         * @return {@code true} if subscription is inbox.
         */
        public boolean isInbox()
        {
            return (mInboxFlag);
        } // end of isInbox()

        /**
         * Returns an immutable list containing the concrete
         * topic keys to which this wildcard subscription is
         * subscribed. Note that these topic keys may change
         * over time as event bus encounters new concrete topics
         * which match the wildcard subscription.
         * @return immutable list of concrete topic keys.
         */
        public List<EfsTopicKey<E>> subscriptionKeys()
        {
            final ImmutableList.Builder<EfsTopicKey<E>> builder =
                ImmutableList.builder();

            mSubscriptions.forEach(
                s -> builder.add(s.topicKey()));

            return (builder.build());
        } // end of subscriptionKeys()

        /**
         * Returns an immutable list containing the concrete
         * topics to which this wildcard subscription is
         * subscribed. Note that these topics may change over
         * time as event bus encounters new concrete topics
         * which match the wildcard subscription.
         * @return immutable list of concrete topics.
         */
        public List<String> subscriptionTopics()
        {
            final ImmutableList.Builder<String> builder =
                ImmutableList.builder();

            mSubscriptions.forEach(
                s -> builder.add((s.topicKey()).topic()));

            return (builder.build());
        } // end of subscriptionTopics()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of class WildcardSubscription
} // end of class EfsEventBus
