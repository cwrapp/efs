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

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises efs event layout parsing.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class EfsEventLayoutTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String INVALID_FIELD_NAME =
        "name is either null or an empty string";

    //-----------------------------------------------------------
    // Statics.
    //

    //-----------------------------------------------------------
    // Locals.
    //

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void setUpClass()
    {
    }

    @AfterAll
    public static void tearDownClass()
    {
    }

    @BeforeEach
    public void setUp()
    {
    }

    @AfterEach
    public void tearDown()
    {
    }

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void getLayoutNullClass()
    {
        final Class<ChildEvent> ec = null;

        try
        {
            EfsEventLayout.getLayout(ec);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex).hasMessage("eventClass is null");
        }
    } // end of getLayoutNullClass()

    @Test
    public void getLayoutSuccess()
    {
        final Class<ChildEvent> ec = ChildEvent.class;
        final EfsEventLayout<ChildEvent> layout =
            EfsEventLayout.getLayout(ec);

        assertThat(layout).isNotNull();
        assertThat(layout.eventClass()).isEqualTo(ec);

        try
        {
            layout.containsField(null);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(INVALID_FIELD_NAME);
        }

        try
        {
            layout.containsField("");
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(INVALID_FIELD_NAME);
        }

        assertThat(layout.containsField("foobar")).isFalse();
        assertThat(layout.containsField("id")).isTrue();
        assertThat(layout.containsField("timestamp")).isTrue();

        try
        {
            layout.fieldType(null);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(INVALID_FIELD_NAME);
        }

        try
        {
            layout.fieldType("");
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(INVALID_FIELD_NAME);
        }

        assertThat(layout.fieldType("snafu")).isNull();
        assertThat(layout.fieldType("name"))
            .isEqualTo(String.class);
        assertThat(layout.fieldType("timestamp"))
            .isEqualTo(Instant.class);

        final List<String> expectedFieldNames =
            ImmutableList.of("name", "id", "price", "timestamp");

        assertThat(layout.fields())
            .containsAll(expectedFieldNames);

        assertThat(EfsEventLayout.getLayout(ec))
            .isSameAs(layout);

        assertThat(layout.toString()).isNotEmpty();
    } // end of getLayoutSuccess()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------
} // end of class EfsEventLayoutTest