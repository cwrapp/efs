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

package org.efs.dispatcher.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class ThreadTypeTest
{
//---------------------------------------------------------------
// Member data.
//

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void findThreadTypeNullName()
    {
        final String name = null;

        try
        {
            ThreadType.find(name);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(ThreadType.INVALID_NAME);
        }
    } // end of findThreadTypeNullName()

    @Test
    public void findThreadTypeEmptyName()
    {
        final String name = "";

        try
        {
            ThreadType.find(name);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(ThreadType.INVALID_NAME);
        }
    } // end of findThreadTypeEmptyName()

    @Test
    public void findThreadTypeBlankName()
    {
        final String name = "   ";

        try
        {
            ThreadType.find(name);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex)
                .hasMessage(ThreadType.INVALID_NAME);
        }
    } // end of findThreadTypeBlankName()

    @Test
    public void findThreadTypeUnknownName()
    {
        final String name = "fubar";
        final ThreadType threadType = ThreadType.find(name);

        assertThat(threadType).isNull();
    } // end of findThreadTypeUnknownName()

    @Test
    public void findThreadTypeKnownName()
    {
        final String name = "SPin+paRK";
        final ThreadType threadType = ThreadType.find(name);

        assertThat(threadType).isNotNull();
        assertThat(threadType).isSameAs(ThreadType.SPINPARK);
    } // end of findThreadTypeKnownName()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------
} // end of class ThreadTypeTest