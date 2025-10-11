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

package org.efs.event;

import java.util.Objects;
import java.util.function.Consumer;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;

/**
 * This class is used when an agent posting an event to a second
 * agent expecting a reply to its event. By placing a
 * {@code ReplyTo} instance in the initial event, the second
 * agent can dispatch the expected reply class directly to the
 * initiating agent.
 * <p>
 * This class is immutable.
 * </p>
 * <h2>ReplyTo Example</h2>
 * <pre><code>public class FirstAgent {
    public void makeRequest() {
        
    }
</code></pre>
 *
 * @param <E> defines reply event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class ReplyTo<E extends IEfsEvent>
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Dispatch reply event to this callback.
     */
    private final Consumer<E> mCallback;

    /**
     * Dispatch reply to this agent.
     */
    private final IEfsAgent mAgent;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new reply-to instance with the given callback
     * and agent.
     * @param callback callback lambda.
     * @param agent dispatch reply to this agent.
     */
    public ReplyTo(final Consumer<E> callback,
                   final IEfsAgent agent)
    {
        mCallback =
            Objects.requireNonNull(callback, "callback is null");
        mAgent = Objects.requireNonNull(agent, "agent is null");
    } // end of ReplyTo()

    //
    // end of Constructors.
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
} // end of class ReplyTo
