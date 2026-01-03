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

package org.efs.feed;

import com.google.common.base.Strings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;
import org.efs.dispatcher.config.ThreadType;
import org.efs.event.IEfsEvent;

/**
 * Responsible for storing and forwarding efs events.
 *
 * @param <E> efs event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsFeed<E extends IEfsEvent>
    implements IEfsAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Default efs feed dispatcher is named {@value}. This
     * dispatcher is configured as follows:
     * <ul>
     *   <li>
     *     thread count:
     *     {@link #DEFAULT_DISPATCHER_THREAD_COUNT}.
     *   </li>
     *   <li>
     *     thread type: {@link #DEFAULT_DISPATCHER_THREAD_TYPE}.
     *   </li>
     *   <li>
     *     thread priority: {@link #DEFAULT_DISPATCHER_PRIORITY}.
     *   </li>
     *   <li>
     *     event queue capacity:
     *     {@link #DEFAULT_DISPATCHER_EVENT_QUEUE_CAPACITY}.
     *   </li>
     *   <li>
     *     maximum events per callout:
     *     {@link #DEFAULT_DISPATCHER_MAX_EVENTS}.
     *   </li>
     *   <li>
     *     run queue capacity:
     *     {@link #DEFAULT_DISPATCHER_RUN_QUEUE_LIMIT}.
     *   </li>
     * </ul>
     */
    public static final String DEFAULT_DISPATCHER_NAME =
        "__DefaultFeedDispatcher__";

    /**
     * Default efs feed dispatcher thread count is {@value}.
     */
    public static final int DEFAULT_DISPATCHER_THREAD_COUNT = 4;

    /**
     * Default efs feed dispatcher thread type is
     * {@link ThreadType#BLOCKING}.
     */
    public static final ThreadType DEFAULT_DISPATCHER_THREAD_TYPE =
        ThreadType.BLOCKING;

    /**
     * Default efs feed dispatcher priority is {@value}.
     */
    public static final int DEFAULT_DISPATCHER_PRIORITY = 5;

    /**
     * Default efs feed dispatcher event queue capacity is
     * {@value}.
     */
    public static final int DEFAULT_DISPATCHER_EVENT_QUEUE_CAPACITY = 512;

    /**
     * Default efs feed dispatcher may process up to {@value}
     * events at a time.
     */
    public static final int DEFAULT_DISPATCHER_MAX_EVENTS = 32;

    /**
     * Default efs feed dispatcher run queue capacity is
     * {@value}.
     */
    public static final int DEFAULT_DISPATCHER_RUN_QUEUE_LIMIT =
        65_536;

    /**
     * Initial persistent store size is {@value} events.
     */
    public static final int DEFAULT_FEED_STORE_SIZE = 65_536;

    //
    // Exception messages.
    //

    /**
     * Invalid event message is {@value}.
     */
    public static final String INVALID_EVENT = "event is null";

    /**
     * Invalid subscriber message is {@value}.
     */
    public static final String INVALID_SUBSCRIBER =
        "subscriber is null";

    /**
     * Invalid event callback message is {@value}.
     */
    public static final String INVALID_CALLBACK =
        "eventCallback is null";

    /**
     * Invalid interval message is {@value}.
     */
    public static final String INVALID_INTERVAL =
        "interval is null";

    /**
     * Invalid event feed key message is {@value}.
     */
    public static final String INVALID_FEED_KEY = "key is null";

    /**
     * Invalid event class message is {@value}.
     */
    public static final String INVALID_EVENT_CLASS =
        "eventClass is null";

    /**
     * Invalid dispatcher name message is {@value}.
     */
    public static final String INVALID_DISPATCHER_NAME =
        "dispatcherName is either null or an empty string";

    /**
     * Invalid event class-to-dispatcher name map is {@value}.
     */
    public static final String INVALID_DISPATCHERS =
        "dispatchers is null";

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Maps an {@link IEfsEvent} class name to dispatcher used
     * for {@code EfsFeed} agent. Map key is
     * {@code IEfsEvent Class.getName()} (and not {@code Class}
     * itself). Map value are dispatcher names known to
     * {@link EfsDispatcher}.
     */
    private static final Map<String, String> sFeedDispatchers =
        new HashMap<>();

    /**
     * Maps efs event class and subject key to its feed instance.
     */
    private static final Map<EfsFeedKey<? extends IEfsEvent>, EfsFeed<? extends IEfsEvent>> sFeeds =
        new HashMap<>();

    // Class static initialization.
    static
    {
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(DEFAULT_DISPATCHER_NAME);

        // Create a default blocking dispatcher for efs feeds.
        builder.dispatcherType(EfsDispatcher.DispatcherType.EFS)
               .numThreads(DEFAULT_DISPATCHER_THREAD_COUNT)
               .threadType(DEFAULT_DISPATCHER_THREAD_TYPE)
               .priority(DEFAULT_DISPATCHER_PRIORITY)
               .eventQueueCapacity(DEFAULT_DISPATCHER_PRIORITY)
               .maxEvents(DEFAULT_DISPATCHER_MAX_EVENTS)
               .runQueueCapacity(DEFAULT_DISPATCHER_PRIORITY)
               .build();
    } // end of class static initialization.

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Unique feed event class and subject key.
     */
    private final EfsFeedKey<E> mKey;

    /**
     * Stores events in published order. Insertion index is then
     * placed into the {@link #mEventIndex} <em>if</em> timestamp
     * is not already in the map.
     */
    private final List<E> mEventStore;

    /**
     * Maps event timestamp to beginning and ending indices
     * associated with that timestamp.
     */
    private final SortedMap<Instant, EventIndexEntry> mEventIndex;

    /**
     * Currently active subscriptions. Once a subscription
     * reaches its ending endpoint or is closed, then it is
     * removed from this list.
     */
    private final List<EfsSubscription<E>> mSubscriptions;

    /**
     * Tracks current event index. Starts at zero.
     */
    private int mFeedIndex;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of EfsFeed.
     */
    private EfsFeed(final EfsFeedKey<E> key)
    {
        mKey = key;
        mEventStore = new ArrayList<>(DEFAULT_FEED_STORE_SIZE);
        mEventIndex = new TreeMap<>();
        mSubscriptions = new ArrayList<>();
    } // end of EfsFeed(EfsFeedKey)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // IEfsAgent Interface Implementation.
    //

    /**
     * Returns {@link EfsFeedKey#toString()} as dispatcher name.
     * @return efs feed key as text.
     */
    @Override
    public String name()
    {
        return (mKey.toString());
    } // end of name()

    //
    // end of IEfsAgent Interface Implementation.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns key containing event class and subject.
     * @return feed key.
     */
    public EfsFeedKey<E> key()
    {
        return (mKey);
    } // end of key()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Maps efs event class to efs dispatcher used for feeds
     * for that event class. This method requires the named
     * dispatcher already exists before calling this method.
     * <p>
     * Note: if {@code eventClass} is already in the feed
     * dispatcher map, then its existing dispatcher mapping will
     * be overridden - if this method call successfully returns.
     * </p>
     * @param <E> efs event type.
     * @param eventClass efs event class.
     * @param dispatcherName efs dispatcher name.
     * @throws NullPointerException
     * if {@code eventClass} is {@code null}.
     * @throws IllegalArgumentException
     * if {@code dispatcherName} is either {@code null}, an
     * empty string, or is an unknown dispatcher.
     */
    public static <E extends IEfsEvent> void setFeedDispatcher(final Class<E> eventClass,
                                                               final String dispatcherName)
    {
        Objects.requireNonNull(eventClass, INVALID_EVENT_CLASS);

        if (Strings.isNullOrEmpty(dispatcherName))
        {
            throw (
                new IllegalArgumentException(
                    INVALID_DISPATCHER_NAME));
        }

        if (!EfsDispatcher.isDispatcher(dispatcherName))
        {
            throw (
                new IllegalArgumentException(
                    String.format(
                        "unknown dispatcher named \"%s\"",
                        dispatcherName)));
        }

        sFeedDispatchers.put(
            eventClass.getName(), dispatcherName);
    } // end of setFeedDispatcher(Class, String)

    /**
     * Places given event class-to-dispatcher name mappings into
     * internal mappings. This method first validates given
     * mappings for correctness before placing any of these given
     * mappings into internal set. In short, either all of given
     * mappings are placed into internal set or none.
     * <p>
     * Note: if {@code dispatchers} contains event class keys
     * already in the internal mappings, then these new mappings
     * replace existing mappings.
     * </p>
     * @param dispatchers contains efs event class-to-dispatcher
     * name mappings.
     * @throws NullPointerException
     * if either {@code dispatchers} is {@code null} or contains
     * {@code null Class} keys.
     * @throws IllegalArgumentException
     * if {@code dispatchers} contains dispatcher name values
     * which are either {@code null}, an empty string, or is
     * unknown to {@code EfsDispatcher}.
     */
    public static void setFeedDispatchers(final Map<Class<? extends IEfsEvent>, String> dispatchers)
    {
        String dispatcherName;

        Objects.requireNonNull(dispatchers, INVALID_DISPATCHERS);

        // Validate given map *before* adding any values into
        // feed dispatchers map.
        for (Map.Entry<Class<? extends IEfsEvent>, String> e :
                 dispatchers.entrySet())
        {
            dispatcherName = e.getValue();

            if (e.getKey() == null)
            {
                throw (
                    new IllegalArgumentException(
                        "dispatchers contains null eventClass key"));
            }

            if (Strings.isNullOrEmpty(dispatcherName))
            {
                throw (
                    new IllegalArgumentException(
                        "dispatchers contains null or empty string dispatcher name value"));
            }

            if (!EfsDispatcher.isDispatcher(dispatcherName))
            {
                throw (
                    new IllegalArgumentException(
                        String.format(
                            "dispatchers contains an unknown dispatcher \"%s\"",
                            dispatcherName)));
            }
        }

        // If we reached here, then dispatchers contains valid
        // entries. Put the entire dispatchers map into the
        // feed dispatchers map.
        for (Map.Entry<Class<? extends IEfsEvent>, String> e :
                 dispatchers.entrySet())
        {
            sFeedDispatchers.put((e.getKey()).getName(),
                                 e.getValue());
        }
    } // end of setFeedDispatchers(Map<>)

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    /**
     * Posts given event to feed and forwards event to active
     * subscribers. Feed uses {@link Instant#now()} as event
     * timestamp since it does not assume that {@code event}
     * has its own timestamp.
     * @param event publish event and forward to event to active
     * subscribers.
     * @throws NullPointerException
     * if {@code event} is {@code null}.
     */
    @SuppressWarnings ("unchecked")
    public void publish(final E event)
    {
        Objects.requireNonNull(event, INVALID_EVENT);

        EfsDispatcher.dispatch(this::doPublish,
                               new PublishEvent<>(event),
                               this);
    } // end of publish(E)

    /**
     * Posts given subscriber and subscription interval to
     * dispatcher thread and returns closable subscription
     * instance.
     * @param subscriber forward events to this subscriber.
     * @param eventCallback forward events to subscriber via this
     * lambda callback.
     * @param interval subscription interval.
     * @return closable event subscription.
     * @throws NullPointerException
     * if {@code subscriber}, {@code eventCallback}, or
     * {@code interval} is {@code null}.
     */
    @SuppressWarnings ("unchecked")
    public EfsSubscription<E> subscribe(final IEfsAgent subscriber,
                                        final Consumer<E> eventCallback,
                                        final EfsInterval interval)
    {
        final EfsSubscription<E> retval =
            new EfsSubscription(
                Objects.requireNonNull(
                    subscriber, INVALID_SUBSCRIBER),
                Objects.requireNonNull(
                    eventCallback, INVALID_CALLBACK),
                Objects.requireNonNull(
                    interval, INVALID_INTERVAL));

        EfsDispatcher.dispatch(this::doSubscribe,
                               new SubscribeEvent<>(retval),
                               this);

        return (retval);
    } // end of subscribe(IEfsAgent, Consumer, EfsInterval)

    /**
     * Returns efs feed associated with the given feed key. If
     * there is no such feed, then creates feed and registers
     * that feed with dispatcher associated with
     * {@link EfsFeedKey#eventClass()}. If there is no such
     * association, then uses default efs feed dispatcher.
     * @param <E> efs event type.
     * @param key efs feed key.
     * @return efs feed for given feed key.
     * @throws NullPointerException
     * if {@code key} is {@code null}.
     */
    @SuppressWarnings ("unchecked")
    public static <E extends IEfsEvent> EfsFeed<E> openFeed(final EfsFeedKey<E> key)
    {
        final EfsFeed<E> retval;

        Objects.requireNonNull(key, INVALID_FEED_KEY);

        synchronized (sFeeds)
        {
            // Does this feed already exist?
            if (sFeeds.containsKey(key))
            {
                // Yes, retrieve that feed from map.
                retval = (EfsFeed<E>) sFeeds.get(key);
            }
            // No, create the feed and put into map.
            else
            {
                retval = createFeed(key);
                sFeeds.put(key, retval);
            }
        }

        return (retval);
    } // end of openFeed(EfsFeedKey)

    private void doPublish(final PublishEvent<E> pubEvent)
    {
        final Instant pubTimestamp = pubEvent.mTimestamp;
        final E event = pubEvent.event();
        final int index = mFeedIndex++;
        final EventIndexEntry entry;

        // 1. Forward event to all live subscriptions, removing
        //    expire subscriptions along the way.
        forwardEvent(index, pubTimestamp, event);

        // 2. Put event into event array list.
        mEventStore.add(event);

        // 3. Map timestamp to event index.
        entry =
            mEventIndex.computeIfAbsent(
                pubTimestamp,
                t -> new EventIndexEntry(pubTimestamp, index));

        entry.endIndex(index);
    } // end of doPublush(PublishEvent)

    private void doSubscribe(final SubscribeEvent<E> subEvent)
    {
        final Instant now = Instant.now();
        final EfsSubscription<E> subscription =
            subEvent.subscription();

        // 1. Set subscription beginning and ending endpoints
        //    based on interval.
        subscription.setEndpoints(mFeedIndex, now);

        // 2. If beginning endpoint is in the past or now
        //    inclusive, then forward past events to subscriber
        //    using event callback.
        if (subscription.beginsPast(mFeedIndex, now))
        {
            forwardPastEvents(subscription);
        }

        // 3. If ending endpoint is in the future, then post
        //    subscription to list.
        if (subscription.endsFuture(mFeedIndex, now))
        {
            mSubscriptions.add(subscription);
        }
    } // end of doSubscribe(SubscribeEvent)

    /**
     * Forwards stored past events to specified subscriber.
     * @param subscription forward past events to this
     * subscription.
     */
    private void forwardPastEvents(final EfsSubscription<E> subscription)
    {
        final IEfsAgent subscriber = subscription.subscriber();
        final Consumer<E> callback = subscription.eventCallback();
        final int beginIndex =
            subscription.beginningIndex(mFeedIndex, mEventIndex);
        final int endIndex =
            subscription.endingIndex(mFeedIndex, mEventIndex);
        int index;

        for (index = beginIndex; index <= endIndex; ++index)
        {
            EfsDispatcher.dispatch(callback,
                                   mEventStore.get(index),
                                   subscriber);
        }
    } // end of forwardPastEvents(EfsSubscription)

    /**
     * Forwards event to all active subscribers. Those
     * subscriptions which are no longer active due to interval
     * being past are removed from subscriptions list.
     * @param index event index.
     * @param timestamp event timestamp.
     * @param event forwarded efs event.
     */
    @SuppressWarnings ("unchecked")
    private void forwardEvent(final int index,
                              final Instant timestamp,
                              final E event)
    {
        final Iterator<EfsSubscription<E>> sIt =
            mSubscriptions.iterator();
        EfsSubscription subscription;
        int location;

        while (sIt.hasNext())
        {
            subscription = sIt.next();
            location = subscription.compare(index, timestamp);

            // Is this event within the subscribed endpoints?
            if (location == 0)
            {
                // Yes, dispatch event to the subscriber.
                EfsDispatcher.dispatch(
                    subscription.eventCallback(),
                    event,
                    subscription.subscriber());
            }
            // Is this event past the subscribed endpoint?
            else if (location > 0)
            {
                // Yes, and that means the subscription is now
                // defunct. Close subscription and remove from
                // list.
                subscription.close();
                sIt.remove();
            }

        }
    } // end of forwardEvent(Instant, int, E)

    /**
     * Creates a new efs feed for given feed key. Registers efs
     * feed with dispatcher based on
     * {@link EfsFeedKey#eventClass()} name. If there is no
     * specific dispatcher associated with event class, then uses
     * {@link #DEFAULT_DISPATCHER_NAME default feed dispatcher}.
     * @param key event feed key.
     * @return newly created efs feed instance.
     */
    private static <E extends IEfsEvent> EfsFeed<E> createFeed(final EfsFeedKey<E> key)
    {
        final String dispatcherName =
            sFeedDispatchers.computeIfAbsent(
                key.eventClassName(),
                n -> DEFAULT_DISPATCHER_NAME);
        final EfsFeed<E> retval = new EfsFeed<>(key);

        EfsDispatcher.register(retval, dispatcherName);

        return (retval);
    } // end of createFeed(EfsFeedKey)

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Used to forward an event to feed dispatcher thread.
     *
     * @param <T> efs event type.
     */
    private static final class PublishEvent<T extends IEfsEvent>
        implements IEfsEvent
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * {@link EfsFeed#publish(IEfsEvent)} timestamp.
         */
        private final Instant mTimestamp;

        /**
         * Published event.
         */
        private final T mEvent;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private PublishEvent(final T event)
        {
            mTimestamp = Instant.now();
            mEvent = event;
        } // end of PublishEvent(E)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        public Instant timestamp()
        {
            return (mTimestamp);
        } // end of timestamp()

        public T event()
        {
            return (mEvent);
        } // end of event()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of class PublishEvent

    /**
     * Used to forward a subscription interval to feed dispatcher
     * thread.
     *
     * @param <T> efs event type.
     */
    private static final class SubscribeEvent<T extends IEfsEvent>
        implements IEfsEvent
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Event subscription itself.
         */
        private final EfsSubscription<T> mSubscription;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private SubscribeEvent(final EfsSubscription<T> subscription)
        {
            mSubscription = subscription;
        } // end of SubscribeEvent(EfsSubscription)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns event's encapsulated subscription.
         * @return subscription.
         */
        public EfsSubscription<T> subscription()
        {
            return (mSubscription);
        } // end of subscription()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of class SubscribeEvent

    /**
     * Tracks event indices associated with a given timestamp.
     */
    /* package */ static final class EventIndexEntry
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Event entry timestamp.
         */
        private final Instant mTimestamp;

        /**
         * Index of first event in this timestamp entry.
         */
        private final int mBeginIndex;

        /**
         * Index of last event in this timestamp entry.
         */
        private int mEndIndex;

    //-----------------------------------------------------------
    // Member methods.
    //
        //-------------------------------------------------------
        // Constructors.
        //

        /* package */ EventIndexEntry(final Instant timestamp,
                                      final int beginIndex)
        {
            mTimestamp = timestamp;
            mBeginIndex = beginIndex;
        } // end of EventIndex(Instant, int)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        @Override
        public String toString()
        {
            return (String.format("%s: [%,d, %,d]",
                                  mTimestamp,
                                  mBeginIndex,
                                  mEndIndex));
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        public Instant timestamp()
        {
            return (mTimestamp);
        } // end of timestamp()

        public int beginIndex()
        {
            return (mBeginIndex);
        } // end of beginIndex()

        public int endIndex()
        {
            return (mEndIndex);
        } // end of endIndex()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        public void endIndex(final int index)
        {
            mEndIndex = index;
        } // end of endIndex(int)

        //
        // end of Set Methods.
        //-------------------------------------------------------
    } // end of class EventIndex
} // end of class EfsFeed
