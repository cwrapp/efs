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

import java.time.Instant;
import java.util.Objects;
import org.efs.event.type.EfsEventLayout;

/**
 * {@link BaseEvent} sub-class. Used to test
 * {@link EfsEventLayout} parser.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class ChildEvent
    extends BaseEvent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    private final double mPrice;
    private final Instant mTimestamp;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public ChildEvent(final Builder builder)
    {
        super (builder);

        mPrice = builder.mPrice;
        mTimestamp = builder.mTimestamp;
    } // end of ChildEvent()

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    public double getPrice()
    {
        return (mPrice);
    } // end of getPrice()

    public Instant getTimestamp()
    {
        return (mTimestamp);
    } // end of getTimestamp()

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
        extends BaseBuilder<ChildEvent, Builder>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private double mPrice;
        private Instant mTimestamp;

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
        protected Builder self()
        {
            return (this);
        } // end of self()

        @Override
        public ChildEvent build()
        {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        //
        // end of Abstract Method Implementations.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        public Builder setPrice(final double px)
        {
            mPrice = px;

            return (this);
        } // end of setPrice(double)

        public Builder setTimestamp(final Instant ts)
        {
            mTimestamp =
                Objects.requireNonNull(ts, "ts is null");

            return (this);
        } // end of setTimestamp(Instant)

        //
        // end of Set Methods.
        //-------------------------------------------------------
    } // end of class Builder
} // end of class ChildEvent
