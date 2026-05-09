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

package org.efs.dispatcher;

import jakarta.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.concurrent.Immutable;
import org.efs.event.IEfsEvent;

/**
 * This immutable class combines and {@code IEfsAgent} and
 * {@code Consumer} into a event delivery target by
 * {@link EfsDispatcher#dispatch(EfsDispatchTarget, IEfsEvent)}.
 * <p>
 * Note: this class does <em>not</em> override {@code equals} or
 * {@code hashCode} methods.
 * </p>
 * <p>
 * {@code EfsDispatchTarget} has two uses:
 * </p>
 * <h2>1. Event Reply</h2>
 * <p>
 * The simplest and easiest use to understand is for implementing
 * a request, reply event pair. The first agent dispatches the
 * following event to a second agent expecting a reply:
 * </p>
 * <pre><code>public final class RequestEvent implements IEfsEvent {
    private final IEfsDispatchTarget&lt;ReplyEvent&gt; mReplyTo;
    <em>// Event data members here.</em>

    public RequestEvent(final IEfsDispatchTarget&lt;ReplyEvent&gt; replyTo) {
        mReplyTo = replyTo;
    }

    public void postReply(final ReplyEvent reply) {
        mReplyTo.dispatch(reply);
    }
}</code></pre>
 * <p>
 * When the second agent receives the {@code RequestEvent}
 * replying to the event is as simple as creating a
 * {@code ReplyEvent} and posting it to the first agent via
 * {@code request.postReply(reply)}:
 * </p>
 * <pre><code>public void onRequest(final RequestEvent request) {
    final ReplyEvent reply = <em>generate reply for request</em>

    request.postReply(reply);
}</code></pre>
 * <h2>2. Event Bus Routing</h2>
 * <p>
 * {@code org.efs.bus.EfsEventBus} routes events from event
 * publisher to event subscriber. Event bus supports a
 * specific subscriber type, {@code IEfsEventRouter}. This router
 * does not process an event itself but provides an
 * {@code EfsDispatchTarget} specifying to where the event is
 * dispatched. In short, the event router distributes events
 * amongst multiple agents with the goal of forwarding the event
 * to the agent most appropriate for that event.
 * </p>
 *
 * @param <E> targeted efs event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
public final class EfsDispatchTarget<E extends IEfsEvent>
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Event is dispatched to this agent callback.
     */
    @Nonnull private final Consumer<E> mCallback;

    /**
     * Event is dispatched to this agent.
     */
    @Nonnull private final IEfsAgent mAgent;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new dispatch target for given callback and
     * efs agent.
     * @param callback dispatch event to this callback.
     * @param agent dispatch event to this agent.
     * @throws NullPointerException
     * if either {@code callback} or {@code agent} is
     * {@code null}.
     */
    public EfsDispatchTarget(@Nonnull final Consumer<E> callback,
                             @Nonnull final IEfsAgent agent)
    {
        mCallback =
            Objects.requireNonNull(
                callback, EfsDispatcher.NULL_CALLBACK);
        mAgent =
            Objects.requireNonNull(
                agent, EfsDispatcher.NULL_AGENT);
    } // end of EfsDispatchTarget(Consumer, IEfsAgent)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns callback target.
     * @return callback target.
     */
    @Nonnull public Consumer<E> callback()
    {
        return (mCallback);
    } // end of callback()

    /**
     * Returns targeted agent.
     * @return targeted agent.
     */
    @Nonnull public IEfsAgent agent()
    {
        return (mAgent);
    } // end of agent()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Dispatches given event to the configured callback and
     * agent.
     * @param event dispatch this event to agent.
     * @throws NullPointerException
     * if {@code event} is {@code null}.
     */
    public void dispatch(final E event)
    {
        EfsDispatcher.dispatch(mCallback, event, mAgent);
    } // end of dispatch(E)
} // end of class EfsDispatchTarget
