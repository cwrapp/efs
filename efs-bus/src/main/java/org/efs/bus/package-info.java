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

/**
 * <p>
 * {@link org.efs.bus.EfsEventBus EfsEventBus} provides a
 * loosely-coupled interface between
 * {@link org.efs.dispatcher.IEfsAgent efs agents}. This bus
 * matches publishing agents to subscribing agents using a
 * {@link org.efs.event.EfsTopicKey type+topic key} By combining
 * an efs event type with a topic, it allows the same topic to be
 * used with multiple event types. {@code EfsEventBus} divides
 * agents into publishers and subscribers. Please note that the
 * same agent may be both a publisher and a subscriber at the
 * same time, even to the same topic key (but why you would want
 * to do that outside of unit testing I cannot imagine).
 * </p>
  <p style="background-color:#ffcccc;padding:5px;border: 2px solid darkred;">
    <strong>Note:</strong> all agents <em>must</em> be registered
    with a dispatcher prior to interacting with an event bus.
    Failure to do so results in the interaction throwing an
    {@code IllegalStateException}.
  </p>
 * <h2>Publishing Events</h2>
 * <p>
 * An agent announces its ability to publish events on a given
 * topic key by calling
 * {@link org.efs.bus.EfsEventBus#advertise(org.efs.bus.EfsTopicKey, java.util.function.Consumer, org.efs.dispatcher.IEfsAgent) EfsEventBus.advertise}
 * which returns an
 * {@link org.efs.bus.EfsEventBus.Advertisement EfsEventBus.Advertisement}
 * instance. Before publishing any events, the agent must first
 * announce its ability to publish such events by calling
 * {@link org.efs.bus.EfsEventBus.Advertisement#publishStatus(boolean) Advertisement.publishStatus}
 * with a {@code true} argument. After setting the publish status
 * to {@code true}, the agent is clear to
 * {@link org.efs.bus.EfsEventBus.Advertisement#publish(org.efs.event.IEfsEvent) publish events}
 * for the given event type topic.
 * </p>
 * <p>
 * The publishing agent is informed when subscribers join or
 * leave the topic key's feed via its
 * {@code Consumer<EfsSubscribeStatus<E>>} callback. This allows
 * the publishing agent to take necessary steps when a new
 * subscribing agent joins or leaves especially when there is the
 * first subscriber to join or the last subscriber to leave.
 * </p>
 * <p>
 * When a publishing agent is finished publishing on the topic
 * key, it calls
 * {@link org.efs.bus.EfsEventBus.Advertisement#close() Advertisement.close}.
 * Once done, the advertisement may not be used again. If the
 * agent needs to start publishing again, it must call
 * {@code EfsEventBus.advertise} again and acquire a new
 * {@code Advertisement} instance.
 * </p>
 * <h2>Subscribing to Events</h2>
 * <p>
 * An agent subscribes to published events by calling
 * {@link org.efs.bus.EfsEventBus#subscribe(org.efs.bus.EfsTopicKey, java.util.function.Consumer, java.util.function.Consumer, org.efs.dispatcher.IEfsAgent) EfsEventBus.subscribe}
 * passing in the topic key, publish status callback, event
 * callback, and subscribing agent instance, receiving a
 * {@link org.efs.bus.EfsEventBus.Subscription} instance in
 * return. This {@code Subscription} instance is used to close
 * the subscription when the agent no longer wishes to receive
 * events for the given topic.
 * </p>
 * <p>
 * The subscribing agent is informed of when a new publishing
 * agent advertises itself and when it updates its publish
 * status. This allows an subscriber to know if there are any
 * active publishers for a given topic key.
 * {@link org.efs.bus.EfsPublishStatus EfsPublishStatus} contains
 * a topic key, the number of <em>advertised</em> publishers and
 * the number of <em>active</em> publisher (that is, publishers
 * which have set their publish status to {@code true}). If the
 * active publisher count is zero, that means the subscriber will
 * not be receiving any events for the topic key.
 * </p>
 * <h3>Inbox Subscriptions</h3>
 * <p>
 * Suppose an agent is subscribing to a very active topic key and
 * it is possible that events may arrive faster than the agent
 * is able to consume those events. If this happens the agent
 * only wishes to receive the latest event. For example, while
 * the subscribing agent is processing one event another two
 * events arrive. When the current processing is completed, the
 * agent wants to skip the first event and process the second.
 * </p>
 * <p>
 * In this case, the agent uses
 * {@link org.efs.bus.EfsEventBus#subscribeInbox(org.efs.bus.EfsTopicKey, java.util.function.Consumer, java.util.function.Consumer, org.efs.dispatcher.IEfsAgent) EfsEventBus.subscribeInbox}.
 * An inbox subscription only forwards the latest event to the
 * subscribing agent. This subscription is most useful when
 * accessing financial market data.
 * </p>
 * <h3>Subscription Router</h3>
 * <p>
 * {@link org.efs.bus.EfsEventBus#subscribeRouter(org.efs.event.EfsTopicKey, java.util.function.Consumer, org.efs.bus.IEventRouter, org.efs.dispatcher.IEfsAgent) EfsEventBus.subscribeRouter}
 * is used to spread events between multiple agents. An agent may
 * only process a single event at a time. If time-sensitive
 * events arrive at a single agent faster than the agent can
 * process them, then those events delayed on the agent event
 * queue.
 * </p>
 * <p>
 * A solution to this problem is to create multiple agents for
 * processing those events and create an
 * {@link org.efs.bus.IEventRouter event router} which deals out
 * events among those agents. This distribution may be something
 * simple like round-robin or a more sophisticated weighted
 * distribution based on agent response time or current event
 * queue size.
 * </p>
 * <h2>Wildcard Advertising &amp; Subscribing</h2>
 * <p>
 * This section deals with large topic spaces. Consider an
 * event {@code TopOfBook} published on multiple topics with the
 * format
 * <code>/exchange/acme/equity/<strong><em>symbol</em></strong></code>
 * where <strong><em>symbol</em></strong> is an exchange ticker
 * symbol. This topic format allows an agent to receive market
 * data ticks for a given symbol. Dropping the ticker symbol from
 * the topic would mean the agent would have to receive ticks for
 * each of the exchange's (say) 3,000 symbols. Not a good idea.
 * </p>
 * <p>
 * But if a publisher or subscriber wishes to interact with
 * multiple ticker feeds (e.g. all symbols starting with A - I),
 * the following shows how this can be done using a single
 * {@code EfsEventBus} call rather than hundreds of individual
 * calls.
 * </p>
 * <p>
 * For publishers:<pre><code>
bus.advertiseAll(TopOfBook.class, "[A-I].*", this::onSubscribeStatus, this::onTopicUpdate, this)
</code></pre>
 * <p>
 * which creates a
 * {@link org.efs.bus.EfsEventBus.WildcardAdvertisement wildcard advertisement}
 * for all {@code TopOfBook} topic keys whose topic matches the
 * {@link java.util.regex.Pattern regular expression} "[A-I].*".
 * This wildcard advertisement tracks the concrete topic keys
 * matching the regular expression. The publisher works with
 * these individual advertisements to set publish status and
 * publish events.
 * {@link org.efs.bus.EfsEventBus.WildcardAdvertisement#advertisement(org.efs.bus.EfsTopicKey) WildcardAdvertisement.advertisement}
 * to access a concrete topic.
 * </p>
 * <p>
 * For subscribers:<pre><code>
bus.subscribeAll(TopOfBook.class, "[A-I].*", true, this::onPublishStatus, this::onMarketData, this::onTopicUpdate, this)
</code></pre>
 * <p>
 * which creates a
 * {@link org.efs.bus.EfsEventBus.WildcardSubscription} instance.
 * This wildcard subscription tracks all the concrete topic keys
 * matching the regular expression.
 * </p>
 * <p>
 * Closing a wildcard advertisement and subscription closes all
 * the underlying concrete advertisements and subscriptions.
 * </p>
 * <p>
 * In the above scenario
 * </p>
 * <h2>Sample Event Bus Code</h2>
 * <p>
 * TODO
 * </p>
 */

package org.efs.bus;
