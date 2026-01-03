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

package org.efs.feed;

import java.time.Instant;
import org.efs.event.IEfsEvent;

/**
 * Sample efs event used for unit testing.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class SampleEvent
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    private final String mText;
    private final int mNumber;
    private final Instant mTimestamp;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    public SampleEvent(final String text,
                       final int number,
                       final Instant timestamp)
    {
        mText = text;
        mNumber = number;
        mTimestamp = timestamp;
    } // end of SampleEvent(String, int, Instant)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    @Override
    public String toString()
    {
        return (
            String.format(
                "[text=\"%s\", number=%,d, timestamp=%s]",
                mText,
                mNumber,
                mTimestamp));
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    public String getText()
    {
        return (mText);
    } // end of getText()

    public int getNumber()
    {
        return (mNumber);
    } // end of getNumber()

    public Instant getTimestamp()
    {
        return (mTimestamp);
    } // end of getTimestamp()

    //
    // end of Get Methods.
    //-----------------------------------------------------------
} // end of class SampleEvent
