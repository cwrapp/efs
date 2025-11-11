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

/**
 * Base event class used to test {@link EfsEventLayout}.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public abstract class BaseEvent
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    protected final String mName;
    protected final int mId;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    protected BaseEvent(final String name,
                        final int id)
    {
        mName = name;
        mId = id;
    } // end of BaseEvent(String, int)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    @Override
    public boolean equals(final Object o)
    {
        boolean retcode = (this == o);

        if (!retcode && o instanceof BaseEvent)
        {
            retcode = (mId == ((BaseEvent) o).mId);
        }

        return (retcode);
    } // end of equals(Object)

    @Override
    public int hashCode()
    {
        return (mId);
    } // end of hashCode()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    public final String getName()
    {
        return (mName);
    } // end of getName()

    public final int getId()
    {
        return (mId);
    } // end of getId()

    //
    // end of Get Methods.
    //-----------------------------------------------------------
} // end of class BaseEvent
