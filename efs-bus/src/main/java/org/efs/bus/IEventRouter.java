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
 * Implement this interface to dynamically route an event to a
 * given agent and callback pair. If event is <em>not</em> to be
 * dispatched, then return a {@code null EfsDispatchTarget}.
 *
 * @param <E> event class
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

