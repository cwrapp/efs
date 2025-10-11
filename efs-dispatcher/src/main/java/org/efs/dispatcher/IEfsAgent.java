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

package org.efs.dispatcher;

/**
 * This interface solves the problem of start up and shutting
 * down from a non-efs thread. When an application object is
 * opening feeds, it is possible that efs will call the object
 * back before the object's start up method completion.
 * Synchronization must then be used to prevent start up
 * interruption. But synchronization should not be necessary
 * due to efs guaranteeing that it will access clients in a
 * single-threaded fashion.
 * <p>
 * The solution is to have an dispatcher thread trigger the
 * object start up and shutdown methods. While the object is
 * opening feeds, any and all callbacks triggered by the feed
 * opening will not be posted until after the start up method
 * returns.
 * </p>
 * <p>
 * A similar issue exists with shutting down application objects
 * from a non-efs thread: after a feed is closed, the feed may
 * still deliver messages to the application object. Shutting
 * down feeds from an dispatcher thread removes this issue since
 * all the object's pending events queue will be cleared.
 * </p>
 * <p>
 * An application is required to override these methods when
 * registering with an {@code EfsFeed}. Also {@link #name()}
 * <em>><strong>must</strong></em> return an {@code IEfsAgent}
 * unique name within the JVM so that
 * {@code EfsDispatcher.register} is successful.
 * </p>
 * <h2>Example IEfsAgent Implementation</h2>
 * An agent's main role is to send and receive
 * {@link org.efs.event.IEfsEvent events}. This is done via
 * {@link java.util.function.Consumer lambdas} referencing agent
 * methods. The following example shows how a stock trading algo
 * could be implemented as an {@code IEfsAgent}:
 * <pre><code>import org.efs.dispatcher.IEfsAgent;

public final class MakeMoneyAlgo implements IEfsAgent {

    <em>// Provides agent's unique name.</em>
    &#64;Override public String name() {
        return ((this.getClass()).getSimpleName());
    }

    <em>// The following methods are responsible for processing the following IEfsEvent-derived event:
    // + NewOrderEvent: places a new algo order.
    // + CancelOrderEvent: cancels an in-place algo order.</em>
    // + ConfigUpdateEvent: updates algo's configuration.
    private void onNewOrder(final NewOrderEvent order) {
       ...
    }

    private void onCancelOrder(final CancelOrderEvent cancel) {
        ...
    }

    private void onConfigUpdate(final ConfigUpdateEvent update) {
        ...
    }
}</code></pre>
 * <p>
 * It happens that an application can have an agent responsible
 * for receiving and coordinating a sizable number of different
 * events, say 20. Putting 20 event methods into a single agent
 * class can be confusing and error prone. One solution is to
 * partition these events into different categories (say 4) and
 * create a "handler" class for each category. These classes
 * contain the member data and member methods needed to process
 * each event category. The overall agent class contains a single
 * instance of each handler class. The {@code Consumer} lambda
 * references the handler method but the event is dispatched
 * to the agent instance.
 * </p>
 * <p>
 * The example below shows an agent class named
 * {@code ApplicationMonitor} responsible for tracking an
 * application's on-going performance. This includes such things
 * as event queue sizes, event processing latencies, memory
 * usage, and garbage collection frequency and duration. This
 * work is divided in sub-monitors:
 * </p>
 * <pre><code>import org.efs.dispatcher.IEfsAgent;

public final class ApplicationMonitor implements IEfsAgent {

    private final String mName;
    private final EventMonitor mEventMonitor;
    private final MemoryMonitor mMemoryMonitor;
    private final GCMonitor mGCMonitor;

    &#64;Override public String name() {
        return (mAgentName);
    }

    <em>// Dispatch events to the sub-monitor methods but as this agent.</em>
    public void dispatch(final EventUpdateEvent event) {
        EfsDispatcher.dispatch(mEventMonitor::onEventUpdate, event, this);
    }
}</code></pre>
 * <p>
 * See {@link org.efs.dispatcher package info} for an overall
 * example of how to use dispatcher in an application.
 * </p>
 *
 * @see org.efs.dispatcher
 * @see org.efs.dispatcher.IEfsDispatcher
 * @see org.efs.dispatcher.EfsDispatcher
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public interface IEfsAgent
{
//---------------------------------------------------------------
// Member data.
//

//---------------------------------------------------------------
// Member methods.
//

    /**
     * Returns <em><strong>unique</strong></em> efs agent name.
     * This returned name may <em><strong>not</strong></em> be
     * {@code null} or an empty string.
     * @return efs agent name.
     */
    String name();
} // end of interface IEfsAgent

