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

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import org.efs.event.BaseEvent;
import org.efs.event.ChildEvent;
import org.efs.event.IEfsEvent;
import org.efs.event.IEfsEventBuilder;
import org.efs.event.type.EfsEventLayout.EfsEventField;
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
    } // end of setUp()

    @AfterEach
    public void tearDown()
    {
    } // end of tearDown()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void getLayoutNullClass()
        throws InvalidEventException
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
    public void getLayoutAbstractClass()
    {
        final Class<BaseEvent> ec = BaseEvent.class;

        try
        {
            EfsEventLayout.getLayout(ec);
        }
        catch (InvalidEventException eventex)
        {
            assertThat(eventex)
                .hasMessage("event class is abstract");
        }
    } // end of getLayoutAbstractClass()

    @Test
    public void getLayoutEventClassNoBuilder()
    {
        final Class<NoBuilderEvent> ec = NoBuilderEvent.class;

        try
        {
            EfsEventLayout.getLayout(ec);
        }
        catch (InvalidEventException eventex)
        {
            assertThat(eventex)
                .hasMessage(
                    "event class has no public static method named builder");
        }
    } // end of getLayoutEventClassNoBuilder()

    @Test
    public void getLayoutInvalidBuilder()
    {
        final Class<InvalidBuilderEvent> ec =
            InvalidBuilderEvent.class;

        try
        {
            EfsEventLayout.getLayout(ec);
        }
        catch (InvalidEventException eventex)
        {
            assertThat(eventex)
                .hasMessage(
                    "event class has no public static method named builder");
        }
    } // end of getLayoutInvalidBuilder()

    @Test
    public void getLayoutSuccess()
        throws InvalidEventException
    {
        final Class<ChildEvent> ec = ChildEvent.class;
        final EfsEventLayout<ChildEvent> layout =
            EfsEventLayout.getLayout(ec);
        EfsEventField field;

        assertThat(layout).isNotNull();
        assertThat(layout.eventClass()).isEqualTo(ec);
        assertThat(layout.builderMethod()).isNotNull();
        assertThat(layout.buildMethod()).isNotNull();

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
            layout.field(null);
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(INVALID_FIELD_NAME);
        }

        try
        {
            layout.field("");
        }
        catch (IllegalArgumentException argex)
        {
            assertThat(argex).hasMessage(INVALID_FIELD_NAME);
        }

        assertThat(layout.field("snafu")).isNull();

        field = layout.field("name");
        assertThat(field).isNotNull();
        assertThat(field.dataType()).isEqualTo(String.class);

        field = layout.field("timestamp");
        assertThat(field).isNotNull();
        assertThat(field.dataType()).isEqualTo(Instant.class);

        final List<String> expectedFieldNames =
            ImmutableList.of("name", "id", "price", "timestamp");

        assertThat(layout.fields())
            .containsAll(expectedFieldNames);

        assertThat(EfsEventLayout.getLayout(ec))
            .isSameAs(layout);

        assertThat(layout.toString()).isNotEmpty();
    } // end of getLayoutSuccess()

    @Test
    @SuppressWarnings ("unchecked")
    public void reflectionsTest()
        throws Throwable
    {
        final Class<SampleEvent> ec = SampleEvent.class;
        final EfsEventLayout<SampleEvent> layout =
            EfsEventLayout.getLayout(ec);
        final SampleEvent event = createEvent();

        // Build a new event based on an existing event using
        // getter methods, builder setter and build methods.
        final IEfsEvent copy = copyEvent(event, layout);

        assertThat(copy).isEqualTo(event);
    } // end of reflectionsTest()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private IEfsEvent copyEvent(final IEfsEvent event,
                                final EfsEventLayout<SampleEvent> layout)
        throws Throwable
    {
        final IEfsEventBuilder builder =
            (IEfsEventBuilder) (layout.builderMethod()).invoke();
        EfsEventField field;
        Object o;

        for (String fn : layout.fields())
        {
            field = layout.field(fn);

            assertThat(field).isNotNull();

            o = (field.getter()).invoke(event);
            (field.setter()).invoke(builder, o);
        }

        return (
            (IEfsEvent) (layout.buildMethod()).invoke(builder));
    } // end of copyEvent(IEfsEvent)

    private static SampleEvent createEvent()
    {
        final BaseBean bean =
            (SubBean.builder()).setDescription("abcd").build();
        final SampleEvent.Builder builder =
            SampleEvent.builder();

        return (builder.setName("TestEvent")
                       .setFlag0(false)
                       .setFlag1(true)
                       .setOneByteInt((byte) 123)
                       .setTwoByteInt((short) 54_321)
                       .setFourByteInt(1_234_567)
                       .setEightByteInt(10_000_000_000L)
                       .setFloatValue(123.456f)
                       .setDoubleValue(654.321d)
                       .setDirection(SampleEvent.Direction.RIGHT)
                       .setNumbers(ImmutableList.of(1, 2, 3, 4))
                       .setText(ImmutableList.of("To be",
                                                 "or not to be"))
                       .setRecord(bean)
                       .build());
    } // end of createEvent()
} // end of class EfsEventLayoutTest