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

import jakarta.annotation.Nullable;
import org.efs.dispatcher.EfsDispatchTarget;
import org.efs.event.IEfsEvent;

/**
 * A dynamic event router determines target agent and callback
 * for event delivery based on event content.
 *
 * <p>
 * <strong>Purpose:</strong>
 * </p>
 * <p>
 * {@code IEventRouter} enables sophisticated event routing
 * strategies where events from a single topic are conditionally
 * routed to different agents and callbacks based on the event
 * values. This provides an alternative to having multiple
 * subscriptions when you want centralized routing logic.
 * </p>
 *
 * <p>
 * <strong>When to Use:</strong>
 * </p>
 * <ul>
 *   <li>
 *     You need to route events from a single topic to multiple
 *     different agents.
 *   </li>
 *   <li>
 *     Routing decisions depend on event content (not just
 *     topic).
 *   </li>
 *   <li>
 *     Some events should be filtered/dropped entirely.
 *   </li>
 *   <li>
 *     You want centralized routing logic instead of multiple
 *     subscriptions.
 *   </li>
 *   <li>
 *     Routing logic needs to be dynamic or configurable.
 *   </li>
 * </ul>
 *
 * <p>
 * <strong>How It Works:</strong>
 * </p>
 * <p>
 * When an event is published to a topic with a router
 * subscription:
 * </p>
 * <ol>
 *   <li>
 *     The event is received by the router subscription.
 *   </li>
 *   <li>
 *     {@link #routeTo(IEfsEvent)} method is called with event.
 *   </li>
 *   <li>
 *     Router returns an {@link EfsDispatchTarget} containing
 *     target agent and callback.
 *   </li>
 *   <li>
 *     Event is dispatched to the specified agent and callback.
 *   </li>
 *   <li>
 *     If {@code null} is returned, the event is silently
 *     discarded.
 *   </li>
 * </ol>
 *
 * <p>
 * <strong>Functional Interface:</strong>
 * </p>
 * <p>
 * This is a functional interface with a single method, so it can
 * be implemented using lambda expressions or method references
 * for concise routing logic.
 * </p>
 *
 * <h2>Example 1: Priority-Based Routing</h2>
 * <p>
 * Route orders to different handlers based on priority level:
 * </p>
 * <pre><code>
 * IEventRouter&lt;OrderEvent&gt; priorityRouter = event -&gt; {
 *     if ("URGENT".equals(event.priority())) {
 *         return new EfsDispatchTarget&lt;&gt;(urgentOrderAgent, urgentHandler);
 *     } else if ("HIGH".equals(event.priority())) {
 *         return new EfsDispatchTarget&lt;&gt;(priorityOrderAgent, priorityHandler);
 *     } else {
 *         return new EfsDispatchTarget&lt;&gt;(normalOrderAgent, normalHandler);
 *     }
 * };
 *
 * Subscription&lt;OrderEvent&gt; subscription = eventBus.subscribeRouter(
 *     orderTopicKey,
 *     publishStatus -&gt; handlePublishStatus(publishStatus),
 *     priorityRouter,
 *     masterAgent
 * );</code></pre>
 *
 * <h2>Example 2: Content-Based Filtering and Routing</h2>
 * <p>
 * Route events to different agents based on content, and filter
 * some out entirely:
 * </p>
 * <pre><code>
 * IEventRouter&lt;PaymentEvent&gt; paymentRouter = event -&gt; {
 *     // Filter out test transactions
 *     if (event.isTestTransaction()) {
 *         return null;  // Silently discard
 *     }
 *
 *     // Route based on amount
 *     if (event.amount() &gt; 10000) {
 *         return new EfsDispatchTarget&lt;&gt;(
 *             complianceAgent, complianceHandler);
 *     } else if (event.amount() &gt; 1000) {
 *         return new EfsDispatchTarget&lt;&gt;(
 *             auditAgent, auditHandler);
 *     } else {
 *         return new EfsDispatchTarget&lt;&gt;(
 *             paymentAgent, standardPaymentHandler);
 *     }
 * };</code></pre>
 *
 * <h2>Example 3: Geographic or Customer-Based Routing</h2>
 * <p>
 * Route events to region-specific handlers:
 * </p>
 * <pre><code>
 * IEventRouter&lt;SalesEvent&gt; geoRouter = event -&gt; {
 *     String region = event.customerRegion();
 *
 *     return switch (region) {
 *         case "NORTH_AMERICA" -&gt;
 *             new EfsDispatchTarget&lt;&gt;(naAgent, naHandler);
 *         case "EUROPE" -&gt;
 *             new EfsDispatchTarget&lt;&gt;(euAgent, euHandler);
 *         case "ASIA_PACIFIC" -&gt;
 *             new EfsDispatchTarget&lt;&gt;(apAgent, apHandler);
 *         default -&gt; null;  // Unknown region, discard
 *     };
 * };</code></pre>
 *
 * <h2>Example 4: Stateful Routing Logic</h2>
 * <p>
 * Router with internal state and configurable behavior:
 * </p>
 * <pre><code>
 * class QuotaAwareRouter implements IEventRouter&lt;RequestEvent&gt; {
 *     private final Map&lt;String, Integer&gt; quotas = new ConcurrentHashMap&lt;&gt;();
 *
 *     public QuotaAwareRouter() {
 *         quotas.put("free-tier", 100);
 *         quotas.put("premium", 1000);
 *     }
 *
 *     &#64;Override
 *     public EfsDispatchTarget&lt;RequestEvent&gt; routeTo(RequestEvent event) {
 *         String tier = event.customerTier();
 *         int quota = quotas.getOrDefault(tier, 0);
 *
 *         if (event.requestCount() &gt; quota) {
 *             // Route to rate limit handler
 *             return new EfsDispatchTarget&lt;&gt;(
 *                 rateLimitAgent, rateLimitHandler);
 *         }
 *
 *         // Route to normal handler
 *         return new EfsDispatchTarget&lt;&gt;(
 *             processingAgent, processingHandler);
 *     }
 * }
 *
 * IEventRouter&lt;RequestEvent&gt; router = new QuotaAwareRouter();</code></pre>
 *
 * <h2>Example 5: Delegating Router</h2>
 * <p>
 * Route all events to a single configurable agent:
 * </p>
 * <pre><code>
 * IEventRouter&lt;NotificationEvent&gt; delegatingRouter =
 *     event -&gt; new EfsDispatchTarget&lt;&gt;(notificationAgent,
 *                                         notificationHandler);</code></pre>
 *
 * <p>
 * <strong>Important Considerations:</strong>
 * </p>
 * <ul>
 *   <li>
 *     <strong>Null Return Behavior:</strong> Returning
 *     {@code null} from {@link #routeTo(IEfsEvent)} indicates
 *     event should not be dispatched. This is logged as a
 *     trace-level message by the router subscription.
 *   </li>
 *   <li>
 *     <strong>Exception Handling:</strong> If an exception
 *     occurs during routing or dispatch, it is caught and logged
 *     as a warning by the router subscription. The subscription
 *     remains active for future events.
 *   </li>
 *   <li>
 *     <strong>Thread Safety:</strong> If your router maintains
 *     state, ensure it is thread-safe as multiple threads may
 *     call {@link #routeTo(IEfsEvent)} concurrently.
 *   </li>
 *   <li>
 *     <strong>Performance:</strong> Router logic should be
 *     efficient as it executes on the publisher's thread when
 *     events are published.
 *   </li>
 *   <li>
 *     <strong>Side Effects:</strong> Avoid side effects in
 *     routing logic. Use routers for routing decisions only, not
 *     for data transformations or business logic.
 *   </li>
 * </ul>
 *
 * <p>
 * <strong>Comparison with Other Subscription Types:</strong>
 * </p>
 * <ul>
 *   <li>
 *     <strong>Concrete Subscription:</strong> Single agent/callback pair receives all events.
 *     Use this for simple cases where all events go to one place.
 *   </li>
 *   <li>
 *     <strong>Router Subscription:</strong> Router decides target based on event content.
 *     Use this for conditional routing from a single topic to multiple destinations.
 *   </li>
 *   <li>
 *     <strong>Multiple Subscriptions:</strong> Multiple agent/callback pairs each receive
 *     all events. Use this if each subscriber needs all events (more overhead).
 *   </li>
 * </ul>
 *
 * @param <E> efs event class
 *
 * @see EfsDispatchTarget
 * @see EfsEventBus#subscribeRouter(EfsTopicKey, Consumer, IEventRouter, IEfsAgent)
 * @see EfsEventBus.Subscription
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@FunctionalInterface
public interface IEventRouter<E extends IEfsEvent>
{
//---------------------------------------------------------------
// Member methods.
//

    /**
     * Returns targeted {@code IEfsAgent} and {@code Consumer<E>}
     * callback to which given event should be dispatched.
     * Returns {@code null} if this event is not to be
     * dispatched.
     * @param event decide target agent and callback based on
     * this event's values.
     * @return targeted agent and callback.
     */
    @Nullable EfsDispatchTarget<E> routeTo(E event);
} // end of interface IEventRouter

