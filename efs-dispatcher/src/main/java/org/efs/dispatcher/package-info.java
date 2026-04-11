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

/**
 * {@link org.efs.dispatcher.EfsDispatcher Dispatcher} forwards
 * {@link org.efs.event.IEfsEvent events} to
 * {@link org.efs.dispatcher.IEfsAgent agents} in an effectively
 * single-threaded manner. This introduction explains what an
 * event and agent are, how dispatcher forwards events to agents,
 * and how agents are effectively single-threaded.
 * <h2>Design Goals</h2>
 * <ul>
 *   <li>
 *     To provide a framework which allows the developer to
 *     have a multi-threaded application but in a single-threaded
 *     manner. No need for synchronization, locks, conditions,
 *     and dealing with dead locks or race conditions.
 *   </li>
 *   <li>
 *     To make the framework easy to understand and easy to use.
 *     There are no restrictions placed on the developer.
 *     {@link org.efs.dispatcher.IEfsAgent IEfsAgent} and
 *     {@link org.efs.event.IEfsEvent IEfsEvent} are
 *     marker interfaces. It is entirely up to the developer to
 *     decide how an agent and event are implemented.
 *   </li>
 *   <li>
 *     Shift developer focus from threads to agents which do the
 *     value-added work. An agent is defined by the events it
 *     receives and/or the events it posts.
 *   </li>
 *   <li>
 *     To strictly limit thread creation. Because a computer has
 *     a fixed number of cores, the more threads running on a
 *     computer, the greater the contention between threads for
 *     core access. Threads are created at application start as
 *     part of {@code EfsDispatcher}s and are not accessible by
 *     the application.
 *   </li>
 *   <li>
 *     To provide sufficient documenting how to use the
 *     dispatcher framework through explanation and coding
 *     examples.
 * </ul>
 * <h2>Definitions</h2>
 * <ul>
 *   <li>
 *     <strong>Event:</strong> Any class implementing the
 *     {@link org.efs.event.IEfsEvent IEfsEvent} marker
 *     interface. An event's purpose is to transfer information,
 *     requests, and replies between agents.
 *     <br/>
 *      An event class should be immutable but this
 *     is not enforced.
 *   </li>
 *   <li>
 *     <strong>Agent:</strong> Any class implementing the
 *     {@link org.efs.dispatcher.IEfsAgent IEfsAgent} interface.
 *     This interface has a single method {@code name()} which
 *     returns a non-{@code null}, non-empty, unique agent name.
 *     Uniqueness is limited to the JVM.
 *   </li>
 *   <li>
 *     <strong>Dispatcher:</strong> Class used to forward events
 *     to agents.
 *   </li>
 * </ul>
 * <h2>Dispatcher Coding example</h2>
 * The coding example follows on
 * {@link org.efs.dispatcher.IEfsAgent IEfsAgent}'s coding
 * example. The application is a financial application
 * responsible for buying and selling equity stocks using
 * proprietary algorithms (algos for short). The algo class
 * {@code MakeMoneyAlgo} implements {@code IEfsAgent} interface
 * and processes {@code IEfsEvent}-derived events
 * {@code NewOrderEvent}, {@code CancelOrderEvent}, and
 * {@code ConfigUpdateEvent}.
 * <pre><code>import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;

public final class AlgoApplication {
    private static final String DISPATCHERS_CONFIG_FILE = "./conf/dispatchers.conf";
    private static final String ALGO_DISPATCHER_NAME = "AlgoDispatcher";

    private final CountDownLatch mDoneSignal;

    public static void main(final String[] args) {
        <em>// Dispatchers are defined as typesafe configurations stored in file named above.
        // See EfsDispatcher javadocs for more on how to create dispatchers.
        // <strong>NOTE</strong>: dispatchers *must* be created prior to creating agents.
        // Note: this configuration contains a dispatcher named "AlgoDispatcher".</em>
        EfsDispatcher.loadDispatchersConfig(DISPATCHERS_CONFIG_FILE);

        <em>// Create make money algo instance and assign it to a dispatcher.</em>
        final IEfsAgent algo = new MakeMoneyAlgo();

        EfsDispatcher.register(algo, ALGO_DISPATCHER_NAME);

        <em>// Class OrderBusInterface takes new and cancel order events off a network bus and posts
        // them to the make money algo. This is done as follow:
        //
        // EfsDispatcher(MakeMoneyAlgo::onNewOrder, newOrderEvent, mAlgo);
        //
        // where newOrderEvent was taken off the wire and mAlgo is algo instance based to
        // OrderBusInterface constructor.</em>
        final OrderBusInterface orderBus = new OrderBusInterface(algo);

        <em>// Wait here for application to be shut down.</em>
        try {
            mDoneSignal().await();
        }
        catch (InterruptedException interrupt) {}

        <em>// Deregister agent from its dispatcher before shutting down the application.</em>
        EfsDispatcher.deregister(algo);

       <em>//<strong>NOTE:</strong> dispatchers cannot be shut down. Once started, they continue running for
       // application's lifetime.</em>
    }
}</code></pre>
 * <h2>Under the covers</h2>
 * The following diagrams show how {@code EfsDispatcher} and
 * application agents interact. These diagrams go over an agent's
 * {@link org.efs.dispatcher.EfsAgent.RunState run states}:
 * <ul>
 *   <li>
 *     {@code IDLE}: agent's event queue is empty.
 *   </li>
 *   <li>
 *     {@code READY}: agent's event queue is not empty and agent
 *     is posted to its dispatcher's run queue. Again, this agent
 *     appears only once on that run queue.
 *   </li>
 *   <li>
 *     {@code RUNNING}: agent is acquired by a dispatcher thread
 *     and is processing its event queue.
 *   </li>
 * </ul>
 * <h3>Agent Registration</h3>
 * <img src="doc-files/agent-register.png" alt="agent register" />
 * <h3>Dispatching an Event to an Agent</h3>
 * <p>
 * Upon registering, an agent starts life in the
 * {@link org.efs.dispatcher.EfsAgent.RunState#IDLE idle} state.
 * </p>
 * <img src="doc-files/event-dispatch.png" alt="event dispatch" />
 * <p>
 * Now that the agent has an event in its event queue, it is
 * moved to the
 * {@link org.efs.dispatcher.EfsAgent.RunState#READY ready}
 * state.  Because a ready {@code EfsAgent} appears on its
 * dispatcher's run queue <em>only one time</em> that means that
 * only one dispatcher thread is able to acquire that agent. This
 * fact is what make agents effectively single threaded.
 * </p>
 * <h3>Agent Processing Events</h3>
 * <img src="doc-files/agent-processing.png" alt="agent event processing" />
 * <p>
 * When an agent is acquired by a dispatcher thread, the agent
 * moves to the
 * {@link org.efs.dispatcher.EfsAgent.RunState#RUNNING running}
 * state and begins processing its event queue.
 * </p>
 * <h2>Dispatching Events</h2>
 * So far this documentation describes how
 * {@code EfsDispatcher.dispath} works but not how an application
 * decides to which {@code IEfsAgent}(s) an {@code IEfsEvent}
 * should be dispatched. Two possible solutions are:
 * <ul>
 *   <li>
 *     Hard-code event delivery. After creating an event, call
 *     {@code EfsDispatcher.dispatch(targetAgent::onEvent, event, targetAgent}.
 *     This is a tightly coupled solution requiring code change
 *     to send the event to a different agent or different
 *     callback. On the other hand, tight coupling is the fastest
 *     way to get an event from its source to target.
 *   </li>
 *   <li>
 *     Map an event class <em>name</em> (not the class itself) to
 *     a list or set or {@code IEfsAgent}, {@code Consumer}
 *     pairs. Forwarding an event means looking up the event in
 *     the map and dispatching the event to each of the agent,
 *     callback pairs. This is a loosely coupled technique where
 *     source and target are independent of each other. The
 *     target agent decides what events it receives and the
 *     callback method for that event. This loose coupling
 *     increases the event transmission time with the map lookup.
 *     It also requires providing thread safety for the event
 *     distribution map. The simplest solution there is to use a
 *     lock to protect the map but that raises the risk of a
 *     thread attempting to acquire the lock losing its core,
 *     greatly increasing event delivery latency. A low latency
 *     solution to this mapping is not provided here.
 *   </li>
 * </ul>
 * <h2><a id="FrameworkCompare">Comparison with Existing Concurrency Frameworks</a></h2>
 * <h3>LMAX Disruptor</h3>
 * <a href="https://lmax-exchange.github.io/disruptor/user-guide/index.html" target="_blank">LMAX Disruptor</a>
 * has excellent thread and memory discipline. The following
 * diagram gives a good overview on how Disruptor operates:
 * <p>
 * <img src="doc-files/disruptor-architecture.png" alt="Disruptor Architecture" />
 * </p>
 * <p>
 * I think of Disruptor as a conveyor belt (the Disruptor ring
 * buffer) with threads assigned to different stages along the
 * conveyor belt, providing value-added work on the conveyor belt
 * items as they pass by. This conveyor belt contains a fixed
 * number of slots. So Disruptor limits threads to those working
 * on the conveyor belt and limits memory to conveyor belt slots.
 * Disruptor is thread-centric
 * </p>
 * <p>
 * The main differences between Dispatcher and Disruptor are:
 * </p>
 * <ul>
 *   <li>
 *     Disruptor ring buffer is homogenous. Dispatcher allows
 *     for heterogenous events.
 *   </li>
 *   <li>
 *     Disruptor ring buffer contains <em>mutable</em> elements.
 *     This means that ring buffer elements are created once at
 *     application start-up and re-used throughout application
 *     lifetime. This minimizes object instantiation which, in
 *     turn, lowers need for garbage collection. Dispatcher
 *     encourages creation of new events. You could implement an
 *     event pool to support re-use but that is up to you.
 *   </li>
 *   <li>
 *     Disruptor supports element processing in one direction
 *     only. An element is copied into the next available slot
 *     and processed by threads in stage ordering. Dispatcher
 *     allows events to arrive at the agent from multiple
 *     sources.
 *   </li>
 * </ul>
 * <p>
 * I have found Disruptor to be useful for "sink" applications
 * only where data is coming in one end, placed in to the ring
 * buffer, and processed to completion by stage threads. It does
 * not work for applications are receiving events from multiple
 * sources and event processing is focused on coordinating
 * responses to these events. That said, if you are developing a
 * "sink" application, then LMAX Disruptor should be considered.
 * </p>
 * <h3>Staged Event-Driven Architecture (SEDA)</h3>
 * <a href="https://en.wikipedia.org/wiki/Staged_event-driven_architecture" target="_blank">SEDA</a>
 * is similar to Dispatcher but with a more sophisticated
 * control structure with respect to event queues and event
 * processing.
 * <p>
 * <img src="doc-files/seda.png" alt="SEDA" />
 * </p>
 * <p>
 * The controller provides is the heart of a SEDA stage and
 * performs:
 * </p>
 * <ul>
 *   <li>
 *     admission control: whether to admit an event to the event
 *     queue, and event queue ordering.
 *   </li>
 *   <li>
 *     run-time control: update stage scheduling parameters and
 *     load management.
 *   </li>
 * </ul>
 * <p>
 * SEDA's focus is on resource management, adjusting parameters
 * so that flow event flow meets design limits. Dispatcher
 * provides no control mechanisms. That is left to the user if
 * they have the need.
 * </p>
 * <h3>Actor Model</h3>
 * <p>
 * {@code EfsDispatcher} is closest to the Actor model in
 * computational theory and
 * <a href="https://akka.io" target="_blank">Akka framework</a>.
 * Both frameworks share the same fundamental goal:
 * <strong>
 * enabling concurrent, multi-threaded applications while
 * providing effective semantics
 * </strong> to the developer &mdash; eliminating the need for
 * locks/synchronization and risk of deadlocks and race
 * conditions.
 * </p>
 * <h4>Similarities</h4>
 * <table class="role-table">
 *   <caption>EfsDispatcher/Actor Similarities</caption>
 *   <tr>
 *     <th><strong>Concept</strong></th>
 *     <th><strong>Actor Model</strong></th>
 *     <th><strong>efs dispatcher</strong></th>
 *   </tr>
 *   <tr>
 *     <td>Processing Entity</td>
 *     <td>Actor</td>
 *     <td>{@link org.efs.dispatcher.IEfsAgent}</td>
 *   </tr>
 *   <tr>
 *     <td>Communication Unit</td>
 *     <td>Message</td>
 *     <td>{@link org.efs.event.IEfsEvent}</td>
 *   </tr>
 *   <tr>
 *     <td>Per-Entity Inbox</td>
 *     <td>Actor mailbox</td>
 *     <td>Agent event queue</td>
 *   </tr>
 *   <tr>
 *     <td>Guarantee</td>
 *     <td>One message at a time per actor.</td>
 *     <td>One event at a time per agent.</td>
 *   </tr>
 *   <tr>
 *     <td>Identity</td>
 *     <td>Actor address/ActorRef</td>
 *     <td>
 *       Agent name (
 *       {@link org.efs.dispatcher.IEfsAgent#name} must be
 *       unique within JVM).
 *     </td>
 *   </tr>
 * </table>
 * <h4>Key Differences</h4>
 * <ol>
 *   <li>
 *     <strong>Threading Model</strong>
 *     <ul>
 *       <li>
 *         <strong>Actor Model:</strong> Actors are scheduled
 *         onto a shared thread pool (typically a fork-join
 *         pool). The is largely opaque to the developer.
 *       </li>
 *       <li>
 *         <strong>EfsDispatcher:</strong> Agents are explicitly
 *         assigned to named <strong>dispatchers</strong>, each
 *         with a configurable number of dispatcher threads. The
 *         developer controls the thread count, thread type
 *         (blocking, spinning, spin+park, and spin+yield),
 *         thread priority, CPU affinity, and spin limits. This
 *         gives
 *         <strong>fine-grained, latency-oriented control</strong>
 *         over threading which the classic Actor model does not
 *         expose.
 *       </li>
 *     </ul>
 *   </li>
 *   <li>
 *     <strong>Event/Message Routing</strong>
 *     <ul>
 *       <li>
 *         <strong>Actor Model:</strong> Actors send messages to
 *         other actors using an <strong>actor reference</strong>
 *         (ActorRef). The sender does not need to know the
 *         recipient's internals &mdash; only its address. This
 *         supports <strong>location transparency</strong>
 *         (recipient may be either local or remote).
 *       </li>
 *       <li>
 *         <strong>EfsDispatcher:</strong> Event dispatch is done
 *         using a <strong>static method call</strong> with an
 *         explicit {@code Consumer} lambda and target agent:
 *         <br>
 *         <pre><code>EfsDispatcher.dispatch(targetAgent::onEvent, event, targetAgent)</code></pre>
 *         <p>
 *         This is
 *         <strong>direct, tightly-coupled dispatch</strong>
 *         &mdash; the caller must hold a reference to the target
 *         agent and know (and have access to) the callback
 *         method. There is no built-in location transparency or
 *         actor-reference abstraction. The
 *         {link org.efs.dispatcher.ReplyTo class provides a
 *         reply-address pattern but must be manually set.
 *         </p>
 *         <p>
 *         Indirect, loosely-coupled dispatch is provded in
 *         {@code org.efs.gus} package.
 *         </p>
 *       </li>
 *     </ul>
 *   </li>
 *   <li>
 *     <strong>Actor Lifecycle &amp; Supervision</strong>
 *     <ul>
 *       <li>
 *         <strong>Actor Model:</strong> Rich lifecycle
 *         management &mdash; actors can create child actors,
 *         supervise them, and apply fault-tolerance strategies
 *         (restart, stop, escalate). Hierarchical supervision
 *         trees are the cornerstone of the Akka framework.
 *       </li>
 *       <li>
 *         <strong>EfsDispatcher:</strong> Agents are
 *         <strong>registered</strong> and
 *         <strong>de-registered</strong> with dispatchers. Child
 *         agents are not formally supported but developers can
 *         implement such a relationship. Lifecycle supervision
 *         is supported in the {@code org.efs.activator} package.
 *       </li>
 *     </ul>
 *   </li>
 *   <li>
 *     <strong>State Management:</strong>
 *     <ul>
 *       <li>
 *         <strong>Actor Model:</strong> Actors dynamically
 *         change their message-handling behavior (e.g. Akka's
 *         {@code become/unbecome}. This allows state
 *         machine-like transitions within an actor.
 *       </li>
 *       <li>
 *         <strong>EfsDispatcher:</strong> No built-in
 *         behavior-switching mechanism. The {@code Consumer}
 *         lambda is bound at dispatch time by the caller, not
 *         by the recipient agent itself. State machines would be
 *         implemented manually by the agent (e.g. using
 *         <a href="https://sourceforge.net/projects/smc" target="_blank">SMC</a>
 *         state machine compiler).
 *       </li>
 *     </ul>
 *   </li>
 *   <li>
 *     <strong>Backpressure &amp; Queue Management</strong>
 *     <ul>
 *       <li>
 *         <strong>Actor model:</strong> Mailboxes are typically
 *         unbounded by default (Akka), though bounded mailboxes
 *         exist. Backpressure is handled externally (e.g. Akka
 *         streams).
 *       </li>
 *       <li>
 *         <strong>EfsDispatcher:</strong> Agent event queues are
 *         <strong>bounded</strong> (using
 *         <a href="https://github.com/JCTools/JCTools" target="_blank">JCTools</a>
 *         lock-free queues). An attempt to offer an event to a
 *         full event queue results in a thrown
 *         {@code IllegalStateException}. This provides
 *         <strong>explicit backpressure at the agent level.</strong>
 *         Additionally agents have a configurable
 *         {@code maxEvents} per dispatch cycle &mdash; when an
 *         agent hits this limit, it is re-enqueued to the run
 *         queue. This prevents starving out other agents on the
 *         run queue.
 *       </li>
 *     </ul>
 *   </li>
 *   <li>
 *     <strong>Low Latency Focus</strong>
 *     <ul>
 *       <li>
 *         <strong>Actor Model:</strong> General-purpose
 *         concurrency. Performance tuning is available but not
 *         the primary design focus.
 *       </li>
 *       <li>
 *         <strong>EfsDispatcher:</strong> Explicitly designed
 *         for <strong>low-latency, high-performance</strong> use
 *         cases (financial/trading systems). Evidence includes
 *         <ul>
 *           <li>
 *             Lock-free JCTools queue for both event and run
 *             queues.
 *           </li>
 *           <li>
 *             Configurable spin/park/yield strategies on
 *             dispatcher threads.
 *           </li>
 *           <li>
 *             CPU affinity support via OpenHFT.
 *           </li>
 *           <li>
 *             Nanosecond-level run-time statistics tracking per
 *             agent.
 *           </li>
 *           <li>
 *             Discouragement of thread creation beyond
 *             dispatchers.
 *           </li>
 *         </ul>
 *       </li>
 *     </ul>
 *   </li>
 *   <li>
 *     <strong>Event Typing</strong>
 *     <ul>
 *       <li>
 *         <strong>Actor Model:</strong> Messages are typically
 *         untyped (Classic Akka) or use typed protocols (Akka
 *         Typed). Actors receive {@code Any/Object} and
 *         pattern-match.
 *       </li>
 *       <li>
 *         <strong>EfsDispatcher:</strong> Events implement
 *         {@code IEfsEvent} marker interface, and dispatch is
 *         <strong>type-safe</strong> via {@code Consumer<E>}
 *         lambdas &mdash; the callback signature determines
 *         event type at compile time.
 *       </li>
 *     </ul>
 *   </li>
 *   <li>
 *     <strong>Remote / Distributed Support</strong>
 *     <ul>
 *       <li>
 *         <strong>Actor Model:</strong> Frameworks like Akka
 *         natively support remoting and clustering. Actors can
 *         be addressed across network boundaries.
 *       </li>
 *       <li>
 *         <strong>EfsDispatcher:</strong> Strictly
 *         <strong>in-process, single JVM</strong>. Agent name
 *         uniqueness is scoped to the JVM; there is no network
 *         layer.
 *       </li>
 *     </ul>
 *   </li>
 *   <li>
 *     <strong>Scheduling &amp; Timers:</strong>
 *     <ul>
 *       <li>
 *         <strong>Actor Model:</strong> Akka has built-in
 *         scheduler (context.system.scheduler). Actor receives
 *         timer messages.
 *       </li>
 *       <li>
 *         <strong>EfsDispatcher:</strong> Separate
 *         {@code EfsScheduledExecutor} class which dispatches
 *         {@code EfsTimerEvent} to agents via dispatcher.
 *         This efs scheduler provided in artifact
 *         {@code efs-timer}.
 *       </li>
 *     </ul>
 *   </li>
 * </ol>
 * <h4>Code Example Comparison</h4>
 * <strong>Actor Model (Akka)</strong>
 * <pre><code>class OrderActor extends AbstractActor {
    &commat;Override
    public Receive createReceive() {
        return receiveBuilder().match(NewOrder.class, this::onNewOrder)
                               .match(CancelOrder.class, this::onCancelOrder)
                               .build();
    }

    private void onNewOrder(NewOrder) {
        ...
    }

    private void onCancelOrder(CancelOrder) {
        ...
    }
}

// Usage:
ActorRef orderActor = system.actorOf(Props.create(OrderActor.class));
orderActor.tell(new NewOrder(...), ActorRef.noSender());</code></pre>
 * <p>
 * <strong>EfsDispatcher</strong>
 * </p>
 * <pre><code>class OrderAgent implements IEfsAgent {
    &commat;Override public String name() { return "OrderAgent"; }

    private void onNewOrder(NewOrder) {
        ...
    }

    private void onCancelOrder(CancelOrder) {
        ...
    }
}

// Usage:
OrderAgent agent = new OrderAgent();
EfsDispatcher.register(agent, "OrderDispatcher");
EfsDispatcher.dispatch(agent::onNewOrder, new NewOrderEvent(...), agent);</code></pre>
 * <h4>Summary</h4>
 * <table class="protocol">
 *   <caption>efs dispatcher, Actor Model comparison summary</caption>
 *   <tr>
 *     <th>Aspect</th>
 *     <th>Actor Model</th>
 *     <th>EfsDispatcher</th>
 *   </tr>
 *   <tr>
 *     <td style="font-weight:bold;">Abstract Level</td>
 *     <td>
 *       High (location-transparent, hierarchical)
 *     </td>
 *     <td>
 *       Lower (explicit dispatchers, direct dispatch)
 *     </td>
 *   </tr>
 *   <tr>
 *     <td style="font-weight:bold;">Threading Control</td>
 *     <td>
 *       Opaque pool
 *     </td>
 *     <td>
 *      Fine-grained (thread-type, affinity, priority)
 *     </td>
 *   </tr>
 *   <tr>
 *     <td style="font-weight:bold;">Supervision</td>
 *     <td>
 *       Built-in supervision trees
 *     </td>
 *     <td>
 *      None
 *     </td>
 *   </tr>
 *   <tr>
 *     <td style="font-weight:bold;">Backpressure</td>
 *     <td>
 *       External (streams) or unbounded.
 *     </td>
 *     <td>
 *       Built-in bounded queues + max-events-per-run.
 *     </td>
 *   </tr>
 *   <tr>
 *     <td style="font-weight:bold;">Latency Tuning</td>
 *     <td>
 *       Limited
 *     </td>
 *     <td>
 *       Extensive (spin strategies, JCTools, CPU affinity)
 *     </td>
 *   </tr>
 *   <tr>
 *     <td style="font-weight:bold;">Distribution</td>
 *     <td>
 *       Native remoting/clustering
 *     </td>
 *     <td>
 *       Single-JVM only
 *     </td>
 *   </tr>
 *   <tr>
 *     <td style="font-weight:bold;">Type Safety</td>
 *     <td>
 *       Weak (Classic Akka), Strong (Typed Akka)
 *     </td>
 *     <td>
 *       Strong via {@code Consumer<E>} generics.
 *     </td>
 *   </tr>
 *   <tr>
 *     <td style="font-weight:bold;">Behavior Switching</td>
 *     <td>
 *       Akka {@code becomes/unbecomes}
 *     </td>
 *     <td>
 *       Manual
 *     </td>
 *   </tr>
 * </table>
 * <p>
 * <strong>In essence</strong>, {@code EfsDispatcher} is a
 * <strong>lightweight, latency-optimized, single JVM implementation</strong>
 * of the core Actor idea &mdash; message-driven,
 * single-threaded-per-entity concurrency &mdash; but trades the
 * Actor models high-level abstraction (supervision, location
 * transparency, behavior switching) for
 * <strong>
 *   explicit control over threading, queue management, and
 *   low-latency scheduling.
 * </strong>
 * </p>
 */

package org.efs.dispatcher;
