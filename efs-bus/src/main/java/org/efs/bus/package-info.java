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
 *
 * <h3>Example 1: Basic Concrete Topic Publishing and Subscription</h3>
 * <p>
 * This example shows a publisher and subscriber exchanging order
 * events on a concrete topic.
 * </p>
 * <pre><code>// Publisher side - advertise ability to publish OrderEvent on "orders" topic
 * EfsEventBus eventBus = EfsEventBus.findOrCreateBus("orderBus");
 * EfsTopicKey&lt;OrderEvent&gt; orderTopic =
 *     EfsTopicKey.getKey(OrderEvent.class, "orders");
 *
 * Advertisement&lt;OrderEvent&gt; orderAdvertisement = eventBus.advertise(
 *     orderTopic,
 *     status -&gt; {
 *         // Called when subscriber count changes
 *         if (status.activeSubscribers() &gt; 0) {
 *             System.out.println("Now have " + status.activeSubscribers() +
 *                               " active subscribers");
 *         } else if (status.activeSubscribers() == 0) {
 *             System.out.println("No more subscribers, can pause publishing");
 *         }
 *     },
 *     publisherAgent
 * );
 *
 * // Enable publishing
 * orderAdvertisement.publishStatus(true);
 *
 * // Publish orders as they arrive
 * while (hasMoreOrders()) {
 *     OrderEvent order = getNextOrder();
 *
 *     // Check if there are subscribers before publishing
 *     if (orderAdvertisement.hasSubscribers()) {
 *         orderAdvertisement.publish(order);
 *         System.out.println("Published order: " + order.getOrderId());
 *     }
 * }
 *
 * // When done publishing, close the advertisement
 * orderAdvertisement.close();
 *
 *
 * // Subscriber side - subscribe to OrderEvent on "orders" topic
 * Subscription&lt;OrderEvent&gt; orderSubscription = eventBus.subscribe(
 *     orderTopic,
 *     publishStatus -&gt; {
 *         // Called when publisher availability changes
 *         if (publishStatus.activePublishers() &gt; 0) {
 *             System.out.println("Publisher is active, ready to receive orders");
 *         } else {
 *             System.out.println("No active publishers");
 *         }
 *     },
 *     order -&gt; {
 *         // Receive and process each order event
 *         System.out.println("Received order: " + order.getOrderId() +
 *                           " for $" + order.getAmount());
 *         processOrder(order);
 *     },
 *     subscriberAgent
 * );
 *
 * // ... subscribe remains active and receives events ...
 *
 * // Close subscription when done
 * orderSubscription.close();</code></pre>
 *
 * <h3>Example 2: Inbox (Conflated) Subscription for Market Data</h3>
 * <p>
 * This example shows using inbox subscriptions for market data where only
 * the latest price is relevant, intermediate price updates can be skipped.
 * </p>
 * <pre><code>// Publisher publishes market ticks
 * EfsTopicKey&lt;PriceTickEvent&gt; priceTopic =
 *     EfsTopicKey.getKey(PriceTickEvent.class, "ACME/NYSE");
 *
 * Advertisement&lt;PriceTickEvent&gt; priceAdvertisement =
 *     eventBus.advertise(priceTopic,
 *                        status,
 *                        e -&gt; {
 *                            // handle subscriber changes
 *                        },
 *                        publisherAgent);
 * priceAdvertisement.publishStatus(true);
 *
 * // Fast publisher - publishes many ticks per second
 * for (double price : priceStream) {
 *     priceAdvertisement.publish(new PriceTickEvent(price));
 * }
 *
 * // Subscriber uses inbox subscription - only cares about latest price
 * Subscription&lt;PriceTickEvent&gt; priceSubscription =
 *     eventBus.subscribeInbox(priceTopic,
 *                             e -&gt; {
 *                                 // handle publisher changes
 *                             },
 *                             latestTick -&gt; {
 *                                 // Only receives the latest tick, skipping intermediate ones
 *                                 System.out.println("Latest ACME price: $" + latestTick.getPrice());
 *                                 updatePricingDisplay(latestTick);
 *                             },
 *                             subscriberAgent
 * );
 *
 * priceSubscription.close();</code></pre>
 *
 * <h3>Example 3: Event Router for Distributing High-Volume Events</h3>
 * <p>
 * This example shows using a router to distribute events from a
 * single topic to multiple worker agents based on content
 * (e.g., order priority).
 * </p>
 * <pre><code>// Create router that routes orders to different handlers based on priority
 * IEventRouter&lt;OrderEvent&gt; orderRouter = event -&gt; {
 *     if ("URGENT".equals(event.getPriority())) {
 *         // Route urgent orders to priority handler
 *         return new EfsDispatchTarget&lt;&gt;(priorityWorkerAgent,
 *                                           priorityOrderHandler);
 *     } else if ("HIGH".equals(event.getPriority())) {
 *         // Route high priority to expedited handler
 *         return new EfsDispatchTarget&lt;&gt;(expeditedWorkerAgent,
 *                                           expeditedOrderHandler);
 *     } else {
 *         // Route standard orders to normal handler
 *         return new EfsDispatchTarget&lt;&gt;(standardWorkerAgent,
 *                                           standardOrderHandler);
 *     }
 * };
 *
 * // Subscribe with router
 * Subscription&lt;OrderEvent&gt; routerSubscription =
 *     eventBus.subscribeRouter(orderTopic,
 *                              publishStatus -&gt; {
 *                                  // handle publisher changes
 *                              },
 *                              orderRouter,
 *                              coordinatorAgent);
 *
 * // Events are now distributed to appropriate workers based on priority
 * routerSubscription.close();</code></pre>
 *
 * <h3>Example 4: Wildcard Advertising and Subscription for Market Data</h3>
 * <p>
 * This example shows publishing and subscribing to multiple ticker symbols
 * using wildcard patterns. A publisher handles all stocks starting with A-M,
 * while a subscriber wants all tech stocks (symbols containing "TECH").
 * </p>
 * <pre><code>// Publisher advertises ability to publish for multiple symbols using wildcard
 * WildcardAdvertisement&lt;PriceTickEvent&gt; tickerAdvertisement =
 *     eventBus.advertiseAll(PriceTickEvent.class,
 *                           "[A-M].*",  // Regex: all symbols starting with A-M
 *         status -&gt; {
 *             System.out.println("Subscriber count for " +
 *                               status.topicKey() + ": " +
 *                               status.activeSubscribers());
 *         },
 *         newTopic -&gt; {
 *             // Called when a new concrete topic matches the wildcard
 *             System.out.println("New topic discovered: " + newTopic.topic());
 *         },
 *         publisherAgent
 *     );
 *
 * // Enable publishing for all matching topics
 * tickerAdvertisement.publishStatusAll(true);
 *
 * // Publish data for discovered topics
 * for (String symbol : symbolsAtoM) {
 *     EfsTopicKey&lt;PriceTickEvent&gt; symbolKey =
 *         EfsTopicKey.getKey(PriceTickEvent.class, symbol);
 *     Advertisement&lt;PriceTickEvent&gt; ad =
 *         tickerAdvertisement.advertisement(symbolKey);
 *
 *     if (ad != null) {
 *         ad.publish(new PriceTickEvent(symbol, currentPrice(symbol)));
 *     }
 * }
 *
 *
 * // Subscriber subscribes to all TECH symbols using wildcard
 * WildcardSubscription&lt;PriceTickEvent&gt; techSubscription =
 *     eventBus.subscribeAll(
 *         PriceTickEvent.class,
 *         ".*TECH.*",  // Regex: all topics containing "TECH"
 *         false,       // Not an inbox subscription
 *         publishStatus -&gt; {
 *             System.out.println("Publisher count: " +
 *                               publishStatus.activePublishers());
 *         },
 *         priceEvent -&gt; {
 *             // Receive all TECH stock price updates
 *             System.out.println(priceEvent.getSymbol() + " -> $" +
 *                               priceEvent.getPrice());
 *         },
 *         newTopic -&gt; {
 *             // Called when a new TECH topic is discovered
 *             System.out.println("Subscribing to new tech symbol: " +
 *                               newTopic.topic());
 *         },
 *         subscriberAgent
 *     );
 *
 * // Get list of all subscribed topics
 * List&lt;String&gt; techTopics = techSubscription.subscriptionTopics();
 * System.out.println("Currently subscribed to: " + techTopics);
 *
 * techSubscription.close();</code></pre>
 *
 * <h3>Example 5: Complete Order Processing Pipeline</h3>
 * <p>
 * This comprehensive example shows a complete order processing
 * system with publishers, multiple subscription types, and
 * proper resource cleanup.
 * </p>
 * <pre><code>class OrderProcessingService {
 *     private final EfsEventBus eventBus;
 *     private final IEfsAgent publisherAgent;
 *     private final IEfsAgent subscriberAgent;
 *     private Advertisement&lt;OrderEvent&gt; orderAdvertisement;
 *     private Subscription&lt;OrderEvent&gt; orderProcessorSubscription;
 *     private Subscription&lt;OrderConfirmationEvent&gt; confirmationSubscription;
 *
 *     public OrderProcessingService(EfsEventBus bus, IEfsAgent pubAgent,
 *                                   IEfsAgent subAgent) {
 *         this.eventBus = bus;
 *         this.publisherAgent = pubAgent;
 *         this.subscriberAgent = subAgent;
 *     }
 *
 *     public void startup() {
 *         // Advertise ability to publish orders
 *         EfsTopicKey&lt;OrderEvent&gt; orderTopic =
 *             EfsTopicKey.getKey(OrderEvent.class, "orders");
 *
 *         orderAdvertisement = eventBus.advertise(
 *             orderTopic,
 *             this::handleSubscriberStatusChange,
 *             publisherAgent
 *         );
 *         orderAdvertisement.publishStatus(true);
 *
 *         // Subscribe to process orders
 *         orderProcessorSubscription = eventBus.subscribe(
 *             orderTopic,
 *             this::handlePublisherStatus,
 *             this::processOrder,
 *             subscriberAgent
 *         );
 *
 *         // Subscribe to order confirmations
 *         EfsTopicKey&lt;OrderConfirmationEvent&gt; confirmTopic =
 *             EfsTopicKey.getKey(OrderConfirmationEvent.class,
 *                               "order-confirmations");
 *         confirmationSubscription = eventBus.subscribe(
 *             confirmTopic,
 *             status -&gt; {
 *                 // handle
 *             },
 *             this::handleConfirmation,
 *             subscriberAgent
 *         );
 *     }
 *
 *     public void publishOrder(OrderEvent order) {
 *         if (orderAdvertisement.hasSubscribers()) {
 *             orderAdvertisement.publish(order);
 *         } else {
 *             System.out.println("No subscribers available for orders");
 *         }
 *     }
 *
 *     private void processOrder(OrderEvent order) {
 *         System.out.println("Processing order: " + order.getOrderId());
 *         // Process the order...
 *     }
 *
 *     private void handleConfirmation(OrderConfirmationEvent confirmation) {
 *         System.out.println("Order confirmed: " + confirmation.getOrderId());
 *     }
 *
 *     private void handleSubscriberStatusChange(
 *         EfsSubscribeStatus&lt;OrderEvent&gt; status) {
 *         System.out.println("Order subscribers: " +
 *                           status.activeSubscribers());
 *     }
 *
 *     private void handlePublisherStatus(
 *         EfsPublishStatus&lt;OrderEvent&gt; status) {
 *         System.out.println("Active publishers: " +
 *                           status.activePublishers());
 *     }
 *
 *     public void shutdown() throws Exception {
 *         // Close all subscriptions and advertisements
 *         if (orderProcessorSubscription != null) {
 *             orderProcessorSubscription.close();
 *         }
 *         if (confirmationSubscription != null) {
 *             confirmationSubscription.close();
 *         }
 *         if (orderAdvertisement != null) {
 *             orderAdvertisement.close();
 *         }
 *         System.out.println("Order processing service shut down");
 *     }
 * }</code></pre>
 */

package org.efs.bus;
