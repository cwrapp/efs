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

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 *
 *
 * @author charlesr
 */

public final class ConflationEventTest
{
//---------------------------------------------------------------
// Member data.
//

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Test Methods.
    //

    @Test
    public void testConflationEvent()
    {
        final TestEvent peIn =
            new TestEvent(0, System.nanoTime());
        final ConflationEvent<TestEvent> ce =
            new ConflationEvent<>();
        TestEvent peOut;

        assertThat(ce.isEmpty()).isTrue();
        assertThat(ce.contains(TestEvent.class))
            .isFalse();

        assertThat(ce.offer(peIn)).isTrue();

        assertThat(ce.isEmpty()).isFalse();
        assertThat(ce.contains(TestEvent.class))
            .isTrue();

        peOut = ce.peek();

        assertThat(peOut).isNotNull();
        assertThat(peOut).isSameAs(peIn);
        assertThat(ce.offer(peIn)).isFalse();
        assertThat(ce.currentMissedEventCount()).isOne();

        peOut = ce.poll();

        assertThat(peOut).isNotNull();
        assertThat(peOut).isSameAs(peIn);
        assertThat(ce.isEmpty()).isTrue();
        assertThat(ce.contains(TestEvent.class))
            .isFalse();
        assertThat(ce.missedEventCount()).isOne();
        assertThat(ce.currentMissedEventCount()).isZero();
    } // end of testConflationEvent()

    //
    // end of JUnit Test Methods.
    //-----------------------------------------------------------
} // end of class ConflationEventTest
