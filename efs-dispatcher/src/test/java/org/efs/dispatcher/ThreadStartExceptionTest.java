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

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 * Exercises thread start exception class.
 *
 * @author charlesr
 */

public final class ThreadStartExceptionTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String THREAD_NAME = "test-thread";
    private static final String EXCEPTION_MESSAGE = "BOOM!";

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Test Methods.
    //

    @Test
    public void ctorThreadName()
    {
        final ThreadStartException tsex =
            new ThreadStartException(THREAD_NAME);

        assertThat(tsex.threadName()).isEqualTo(THREAD_NAME);
    } // end of ctorThreadName()

    @Test
    public void ctorThreadNameMessage()
    {
        final ThreadStartException tsex =
            new ThreadStartException(
                THREAD_NAME, EXCEPTION_MESSAGE);

        assertThat(tsex.threadName()).isEqualTo(THREAD_NAME);
        assertThat(tsex).hasMessage(EXCEPTION_MESSAGE);
    } // end of ctorThreadNameMessage()

    @Test
    public void ctorThreadNameMessageCause()
    {
        final RuntimeException cause =
            new RuntimeException("I made a boo-boo");
        final ThreadStartException tsex =
            new ThreadStartException(
                THREAD_NAME, EXCEPTION_MESSAGE, cause);

        assertThat(tsex.threadName()).isEqualTo(THREAD_NAME);
        assertThat(tsex).hasMessage(EXCEPTION_MESSAGE);
        assertThat(tsex).hasCause(cause);
    } // end of ctorThreadNameMessageCause()

    //
    // end of JUnit Test Methods.
    //-----------------------------------------------------------
} // end of class ThreadStartExceptionTest
