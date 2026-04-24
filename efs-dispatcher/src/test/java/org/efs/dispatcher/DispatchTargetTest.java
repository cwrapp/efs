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

import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.event.IEfsEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Dispatch target tests.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@ExtendWith(MockitoExtension.class)
@DisplayName("EfsDispatchTarget Unit Tests")
public class DispatchTargetTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    //-----------------------------------------------------------
    // Statics.
    //

    //-----------------------------------------------------------
    // Locals.
    //

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    @DisplayName("Target constructor, null callback")
    public void ctorNullCallback()
    {
        final Consumer<TestEvent> callback = null;
        final IEfsAgent agent = mock(IEfsAgent.class);

        Assertions.assertThatThrownBy(
            () -> new EfsDispatchTarget<>(callback, agent))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsDispatcher.NULL_CALLBACK);
    } // end of ctorNullCallback()

    @Test
    @DisplayName("Target constructor, null agent")
    public void ctorNullAgent()
    {
        final Consumer<TestEvent> callback = e -> {};
        final IEfsAgent agent = null;

        Assertions.assertThatThrownBy(
            () -> new EfsDispatchTarget<>(callback, agent))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsDispatcher.NULL_AGENT);
    } // end of ctorNullAgent()

    @Test
    @DisplayName("Target constructor success")
    public void ctorSuccess()
    {
        final Consumer<TestEvent> callback = e -> {};
        final IEfsAgent agent = mock(IEfsAgent.class);
        final EfsDispatchTarget<TestEvent> target =
            new EfsDispatchTarget<>(callback, agent);

        assertThat(target).isNotNull();
        assertThat(target.callback()).isSameAs(callback);
        assertThat(target.agent()).isSameAs(agent);
    } // end of ctorSuccess()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

//---------------------------------------------------------------
// Inner classes.
//

    private interface TestEvent
        extends IEfsEvent
    {}
} // end of class DispatchTargetTest