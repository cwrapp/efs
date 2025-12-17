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

import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import org.efs.event.IEfsEvent;

/**
 * Base bean class used to demonstrate how multiple bean subclass
 * values may be stored in a field referencing only the bean
 * base class.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public abstract class BaseBean
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member data.
//

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of AbstractBean.
     */
    protected BaseBean()
    {
    } // end of BaseBean()

    //
    // end of Constructors.
    //-----------------------------------------------------------

//---------------------------------------------------------------
// Inner classes.
//

    public static abstract class BaseBuilder<T extends BaseBean,
                                             B extends BaseBuilder>
    {
    //-----------------------------------------------------------
    // Member data.
    //

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        protected BaseBuilder()
        {}

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Declarations.
        //

        public abstract B self();
        public abstract T buildImpl();

        //
        // end of Abstract Method Declarations.
        //-------------------------------------------------------

        public final T build()
        {
            final Validator problems = new Validator();

            validate(problems)
                .throwException(ValidationException.class);

            return (buildImpl());
        } // end of build()

        protected Validator validate(final Validator problems)
        {
            return (problems);
        } // end of validate(Validator)
    } // end of class BaseBuilder
} // end of class BaseBean
