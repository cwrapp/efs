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

import com.google.errorprone.annotations.Immutable;
import java.time.Instant;
import org.efs.event.IEfsEvent;
import org.efs.io.EfsFile.Retrieval;

/**
 * Internal retrieval cancellation event.
 *
 * @param <E> efs event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
/* package */ final class CancelInternalEvent<E extends IEfsEvent>
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Request cancellation timestamp.
     */
    private final Instant mCancelTimestamp;

    /**
     * Canceled event retrieval request.
     */
    private final Retrieval<E> mRequest;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new retrieval cancellation event
     * @param timestamp cancellation timestamp.
     * @param request canceled event retrieval request.
     */
    /* package */ CancelInternalEvent(final Instant timestamp,
                                      final Retrieval<E> request)
    {
        mCancelTimestamp = timestamp;
        mRequest = request;
    } // end of CancelInternalEvent(Instant, Retrieval)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * @InheritDoc
     */
    @Override
    public String toString()
    {
        return (String.format("[timestamp=%s, request=%s]",
                              mCancelTimestamp,
                              mRequest));
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns event retrieval cancellation timestamp.
     * @return cancellation timestamp.
     */
    /* package */ Instant cancelTimestamp()
    {
        return (mCancelTimestamp);
    } // end of cancelTimestamp()

    /**
     * Returns canceled retrieval request.
     * @return canceled retrieval request.
     */
    /* package */ Retrieval<E> request()
    {
        return (mRequest);
    } // end of request()

    //
    // end of Get Methods.
    //-----------------------------------------------------------
} // end of class CancelInternalEvent
