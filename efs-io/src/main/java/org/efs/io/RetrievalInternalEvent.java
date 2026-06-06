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
import org.efs.event.IEfsEvent;
import org.efs.io.EfsFile.Retrieval;

/**
 * Internal event used to pass a retrieval request to
 * {@code EfsFile.onRetrieve}.
 *
 * @param <E> efs event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
/* package */ final class RetrievalInternalEvent<E extends IEfsEvent>
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Event retrieval request.
     */
    private final Retrieval<E> mRequest;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of RetrievalInternalEvent.
     */
    /* package */ RetrievalInternalEvent(final Retrieval<E> request)
    {
        mRequest = request;
    } // end of RetrievalInternalEvent(Retrieval<>)

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
        return (mRequest.toString());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns event retrieval request.
     * @return event retrieval request.
     */
    /* package */ Retrieval<E> request()
    {
        return (mRequest);
    } // end of request()

    //
    // end of Get Methods.
    //-----------------------------------------------------------
} // end of class RetrievalInternalEvent
