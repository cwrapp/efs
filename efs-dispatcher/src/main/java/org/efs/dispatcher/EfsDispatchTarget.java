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
 * This immutable class contains {@code Consumer} and
 * {@code IEfsAgent} used by
 * {@link EfsDispatcher#dispatch(EfsDispatchTarget, IEfsEvent)} \
 * to post an event.
 * <p>
 * This class does <em>not</em> override {@code equals} or
 * {@code hashCode} methods.
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
} // end of class EfsDispatchTarget
