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

import jakarta.annotation.Nonnull;
import java.time.Instant;
import java.util.function.Consumer;
import javax.annotation.concurrent.Immutable;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.IEfsAgent;
import org.efs.event.IEfsEvent;
import org.efs.io.RetrievalCompleteEvent.CompletionType;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * Internal event used to pass a retrieval-by-tag request to
 * {@code EfsFile.onRetrieve}.
 *
 * @param <E> efs event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
/* package */ final class TagRetrieveInternalEvent<E extends IEfsEvent>
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Logging subsystem interface. Uses {@code EfsFile} because
     * this event works on behalf of that class.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(EfsFile.class);

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Retrieve event rows with this user-defined event tag.
     */
    private final int mTag;

    /**
     * Dispatch retrieved rows to this agent.
     */
    @Nonnull
    private final IEfsAgent mAgent;

    /**
     * Post events to this agent callback.
 */
    @Nonnull
    private final Consumer<EfsRow<E>> mEventCB;

    /**
 * Post {@link RetrievalCompleteEvent} to this callback
 * method.
 */
    @Nonnull
    private final Consumer<RetrievalCompleteEvent<E>> mCompletionCB;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new tag retrieval request.
     * @param tag retrieve efs event rows with this tag.
     * @param agent efs agent requesting retrieval.
     * @param eventCB forward retrieved events to this callback.
     * @param completionCB report retrieval completion on this
     * callback.
     */
    /* package */ TagRetrieveInternalEvent(final int tag,
                                           final IEfsAgent agent,
                                           final Consumer<EfsRow<E>> eventCB,
                                           final Consumer<RetrievalCompleteEvent<E>> completionCB)
    {
        mTag = tag;
        mAgent = agent;
        mEventCB = eventCB;
        mCompletionCB = completionCB;
    } // end of TagRetrieveInternalEvent(...)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns user-defined event tag.
     * @return user-defined event tag.
     */
    /* package */ int tag()
    {
        return (mTag);
    } // end of tag()

    /**
     * Returns agent retrieving rows.
     * @return retrieving agent.
     */
    public IEfsAgent agent()
    {
        return (mAgent);
    } // end of agent()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Posts matched row to agent using event callback. Logs a
     * warning if attempt to dispatch the row fails.
     * @param row dispatch this event row to agent.
     */
    /* package */ void postRow(final EfsRow<E> row)
    {
        try
        {
            EfsDispatcher.dispatch(mEventCB, row, mAgent);
        }
        catch (Exception jex)
        {
            sLogger.warn(
                "Failed to dispatch event row to agent {}",
                mAgent.name());
        }
    } // end of postRow(EfsRow)

    /**
     * Posts retrieval completion to agent using completion
     * callback. Logs a warning if attempt to dispatch event
     * fails.
     * @param timestamp retrieval completion timestamp.
     * @param completionType retrieval completion type.
     */
    /* package */ void postCompletion(final Instant timestamp,
                                      final CompletionType completionType)
    {
        try
        {
            EfsDispatcher.dispatch(
                mCompletionCB,
                new RetrievalCompleteEvent<>(
                    completionType, timestamp, null),
                mAgent);
        }
        catch (Exception jex)
        {
            sLogger.warn(
                "Failed to dispatch retrieval completion to agent {}",
                mAgent.name());
        }
    } // end of postCompletion(Instant, CompletionType)
} // end of class TagRetrieveInternalEvent
