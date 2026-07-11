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

import jakarta.annotation.Nullable;
import java.time.Instant;
import org.efs.event.IEfsEvent;
import org.efs.io.EfsFileConnection.Retrieval;

/**
 * {@link EfsFile} posts this event to target agent
 * signifying a retrieval reached its termination.
 *
 * @param <E> efs event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class RetrievalCompleteEvent<E extends IEfsEvent>
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member enums.
//

    /**
     * A retrieval request ends either due to it reaching its
     * ending point, user cancellation, {@link EfsFileConnection}
     * closing, or {@link EfsFile} closing.
     */
    public enum CompletionType
    {
        /**
         * Retrieval successfully reached its termination
         * {@link EfsIntervalEndpoint endpoint}.
         */
        RETRIEVAL_COMPLETED,

        /**
         * Retrieval terminated due to user cancellation.
         */
        USER_CANCEL,

        /**
         * Retrieve terminated due to connection closing.
         */
        CONNECTION_CLOSED,

        /**
         * Retrieve terminated due to file closing.
         */
        FILE_CLOSED
    } // end of enum CompletionType

//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Specifies how retrieval ended.
     */
    private final CompletionType mCompletionType;

    /**
     * Specifies when retrieval ended.
     */
    private final Instant mCompletionTime;

    /**
     * Unique subscription identifier.
     */
    private final Retrieval<E> mRetrieval;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new retrieval completion event for specified
     * request and reason why it ended.
     * @param type how retrieval ended.
     * @param endTime when retrieval ended.
     * @param retrieval retrieval request. Is {@code null} for
     * tag retrieval events.
     */
    /* package */ RetrievalCompleteEvent(final CompletionType type,
                                         final Instant endTime,
                                         @Nullable final Retrieval<E> retrieval)
    {
        mCompletionType = type;
        mCompletionTime = endTime;
        mRetrieval = retrieval;
    } // end of RetrievalCompleteEvent(...)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns reason for retrieval completion.
     * @return reason for retrieval completion.
     */
    public CompletionType completionType()
    {
        return (mCompletionType);
    } // end of completionType()

    /**
     * Returns timestamp when efs event retrieval completed.
     * @return event retrieval completion timestamp.
     */
    public Instant completionTime()
    {
        return (mCompletionTime);
    } // end of completionTime()

    /**
     * Returns now completed retrieval request; returns
     * {@code null} if this is a tag retrieval request.
     * @return completed retrieval request.
     */
    @Nullable
    public Retrieval<E> retrieval()
    {
        return (mRetrieval);
    } // end of retrieval()

    //
    // end of Get Methods.
    //-----------------------------------------------------------
} // end of class RetrievalCompleteEvent
