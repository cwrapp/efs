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

    public ChildEvent(final String name,
                      final int id,
                      final double price,
                      final Instant timestamp)
    {
        super (name, id);

        mPrice = price;
        mTimestamp = timestamp;
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
} // end of class ChildEvent
