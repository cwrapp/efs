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
import net.sf.eBus.util.Validator;


/**
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class SubBean
    extends BaseBean
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    private final String mDescription;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    private SubBean(final Builder builder)
    {
        mDescription = builder.mDescription;
    } // end of SubBean(Builder)

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

        if (!retcode && o instanceof SubBean)
        {
            retcode =
                mDescription.equals(((SubBean) o).mDescription);
        }

        return (retcode);
    } // end of equals(Object)

    @Override
    public int hashCode()
    {
        return (mDescription.hashCode());
    } // end of hashCode()

    @Override
    public String toString()
    {
        return ("SubBean0 instance: " + mDescription);
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    public static Builder builder()
    {
        return (new Builder());
    } // end of builder()

//---------------------------------------------------------------
// Inner classes.
//

    public static final class Builder
        extends BaseBuilder<SubBean, Builder>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private String mDescription;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Builder()
        {}

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Implementations.
        //

        @Override
        public Builder self()
        {
            return (this);
        } // end of self()

        @Override
        public SubBean buildImpl()
        {
            return (new SubBean(this));
        } // end of buildImpl()

        //
        // end of Abstract Method Implementations.
        //-------------------------------------------------------//

        //-------------------------------------------------------
        // Set Methods.
        //

        public Builder setDescription(final String description)
        {
            if (Strings.isNullOrEmpty(description))
            {
                throw (
                    new IllegalArgumentException(
                        "description is either null or an empty string"));
            }

            mDescription = description;

            return (this);
        } // end of setDescription(String)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        @Override
        protected Validator validate(final Validator problems)
        {
            return (super.validate(problems)
                         .requireNotNull(mDescription,
                                         "description"));
        } // end of validate(Validator)
    } // end of class Builder
} // end of class SubBean
