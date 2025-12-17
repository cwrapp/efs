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

package org.efs.event.type;

import org.efs.event.IEfsEvent;

/**
 * Exception associated with failure to generate a layout for
 * an {@link IEfsEvent}.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class InvalidEventException
    extends Exception
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Serialization version identifier.
     */
    private static final long serialVersionUID = 0x00010000L;

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Exception is associated with this efs event class.
     */
    private final Class<? extends IEfsEvent> mEventClass;

//---------------------------------------------------------------
// Member methods.
//

    /**
     * Creates a new instance of {@code InvalidEventException}
     * containing invalid efs event class and  detail message.
     * @param eventClass invalid efs event class.
     * @param msg message explaining why {@code eventClass} is
     * invalid.
     */
    public InvalidEventException(final Class<? extends IEfsEvent> eventClass,
                                 final String msg)
    {
        this (eventClass, msg, null);
    } // end of InvalidEventException()

    /**
     * Constructs an instance of {@code InvalidEventException}
     * with the specified efs event class, detail message, and
     * cause.
     * @param eventClass invalid efs event class.
     * @param msg the detail message.
     * @param cause this exception's underlying cause (which is
     * saved for later retrieval by the
     * {@code Throwable.getCause()} method). A {@code null}
     * value is permitted, and indicates that the cause is
     * nonexistent or unknown.
     */
    public InvalidEventException(final Class<? extends IEfsEvent> eventClass,
                                 final String msg,
                                 final Throwable cause)
    {
        super (msg, cause);

        mEventClass = eventClass;
    } // end of InvalidEventException(String, Throwable)
} // end of InvalidEventException
