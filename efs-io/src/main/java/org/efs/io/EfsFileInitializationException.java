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

package org.efs.io;

/**
 * Exception thrown when {@link EfsFile.Builder} encounters
 * an error when initializing a {@link EfsFile} instance or
 * adding new row to event file.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsFileInitializationException
    extends RuntimeException
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

//---------------------------------------------------------------
// Member methods.
//

    /**
     * Creates a new {@code EfsFileInitializationException}
     * instance without detail message.
     */
    public EfsFileInitializationException()
    {}

    /**
     * Constructs a new {@code EfsFileInitializationException}
     * instance with the specified detail message.
     * @param msg detail message.
     */
    public EfsFileInitializationException(final String msg)
    {
        super (msg);
    } // end of EfsFileInitializationException(String)

    /**
     * Constructs a new {@code EfsFileInitializationException}
     * instance with the specified detail message and cause.
     * @param msg the detail message.
     * @param cause this exception's underlying cause (which is
     * saved for later retrieval by the
     * {@code Throwable.getCause()} method). A {@code null}
     * value is permitted, and indicates that the cause is
     * nonexistent or unknown.
     */
    public EfsFileInitializationException(final String msg,
                                          final Throwable cause)
    {
        super (msg, cause);
    } // end of EfsFileInitializationException(String, Throwable)
} // end of EfsFileInitializationException
