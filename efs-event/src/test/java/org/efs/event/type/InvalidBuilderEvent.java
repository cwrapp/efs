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

import com.google.common.base.Strings;
import org.efs.event.IEfsEvent;

/**
 * efs event class has a private getter.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class InvalidBuilderEvent
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    private final String mName;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public InvalidBuilderEvent(final Builder builder)
    {
        mName = builder.mName;
    } // end of InvalidBuilderEvent(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /* package */ String getName()
    {
        return (mName);
    } // end of getName()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    public static Builder builder()
    {
        return (new Builder());
    } // end of builder()

//---------------------------------------------------------------
// Inner classes.
//

    public static final class Builder
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private String mName;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Builder()
        {
            // TODO
        } // end of Builder()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // IEfsEventBuilder Interface Implementation.
        //

        //
        // end of IEfsEventBuilder Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        public Builder setName(final String name)
        {
            if (Strings.isNullOrEmpty(name))
            {
                throw (
                    new IllegalArgumentException(
                        "name is either null or an empty string"));
            }

            mName = name;

            return (this);
        } // end of setName(String)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        public InvalidBuilderEvent build()
        {
            return (new InvalidBuilderEvent(this));
        } // end of build()
    } // end of class Builder
} // end of class InvalidBuilderEvent
