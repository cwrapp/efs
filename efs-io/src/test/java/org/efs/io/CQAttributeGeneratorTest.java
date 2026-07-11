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

package org.efs.io;

import com.google.common.collect.ImmutableList;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.MultiValueAttribute;
import com.googlecode.cqengine.attribute.MultiValueNullableAttribute;
import com.googlecode.cqengine.attribute.SimpleAttribute;
import com.googlecode.cqengine.attribute.SimpleNullableAttribute;
import com.googlecode.cqengine.query.option.QueryOptions;
import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.decimal4j.immutable.Decimal2f;
import org.efs.event.IEfsEvent;
import static org.efs.io.EfsFileTest.GMT;
import static org.efs.io.EfsFileTest.TEST_TIME;
import org.efs.io.TradeEvent.PriceTrend;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CQAttributeGenerator}.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */
@DisplayName("CQAttributeGenerator")
public final class CQAttributeGeneratorTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final long EVENT_ID = 2_002L;
    private static final String EVENT_NAME = "snafu";
    private static final String EVENT_ADDRESS = "here and there";
    private static final String EVENT_TEXT = "bare bodkin";
    private static final List<String> EVENT_TAGS =
        ImmutableList.of("a", "b", "c");
    private static final QueryOptions NO_OPTS =
        new QueryOptions();

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Event layout for test events.
     */
    private static EfsEventLayout<TestEvent> sLayout;

    /**
     * Used to generate same timestamp for all tests.
     */
    private static Clock sTestClock;

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void setUpAll()
    {
        sLayout = EfsEventLayout.getLayout(TestEvent.class);
        sTestClock = Clock.fixed(Instant.parse(TEST_TIME), GMT);
    } // end of setUpAll()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    @DisplayName("create attributes, null layout")
    public void createAttributeMapNullLayoutTest()
    {
        final EfsEventLayout<TestEvent> layout = null;

        assertThatThrownBy(
            () -> CQAttributeGenerator.createAttributeMap(layout))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(CQAttributeGenerator.NULL_LAYOUT);
    } // end of createAttributeMapNullLayoutTest()

    @Test
    @DisplayName("create attributes, check mapping")
    public void createAttributeMapTest()
        throws Exception
    {
        final Map<String, Attribute<EfsRow<TestEvent>, ?>> attributes =
            CQAttributeGenerator.createAttributeMap(sLayout);
        final Instant timestamp = sTestClock.instant();
        final TestEvent event = new TestEvent(EVENT_ID,
                                              EVENT_NAME,
                                              EVENT_ADDRESS,
                                              EVENT_TEXT,
                                              EVENT_TAGS,
                                              timestamp,
                                              false);
        final EfsRow<TestEvent> row =
            new EfsRow<>(
                timestamp, 101L, EfsFile.NO_TAGS, event);

        assertThat(attributes).isNotNull();
        assertThat(attributes).hasSize(8);

        validateAttribute(attributes,
                          "id",
                          SimpleAttribute.class,
                          row,
                          false,
                          EVENT_ID);
        validateAttribute(attributes,
                          "name",
                          SimpleNullableAttribute.class,
                          row,
                          false,
                          EVENT_NAME);
        validateAttribute(attributes,
                          "tags",
                          MultiValueAttribute.class,
                          row,
                          false,
                          EVENT_TAGS);
        validateAttribute(attributes,
                          "values",
                          MultiValueNullableAttribute.class,
                          row,
                          true,
                          null);
        validateAttribute(attributes,
                          "timestamp",
                          SimpleAttribute.class,
                          row,
                          false,
                          timestamp);

        assertThat(attributes).doesNotContainKey("unannotated");
    } // end of createAttributeMapTest()

    @Test
    @DisplayName("create TradeEvent attributes")
    public void createTradeAttributeMapTest()
        throws Exception
    {
        final String symbol = "XYZ";
        final Decimal2f price =
            Decimal2f.valueOfUnscaled(4321, 2);
        final int size = 1_500;
        final PriceTrend trend = PriceTrend.UP;
        final int volume = 54_300;
        final EfsEventLayout<TradeEvent> tradeLayout =
            EfsEventLayout.getLayout(TradeEvent.class);
        final Map<String, Attribute<EfsRow<TradeEvent>, ?>> attributes =
            CQAttributeGenerator.createAttributeMap(tradeLayout);
        final TradeEvent event =
            (TradeEvent.builder()).symbol(symbol)
                                  .price(price)
                                  .size(size)
                                  .priceTrend(trend)
                                  .volume(volume)
                                  .build();
        final EfsRow<TradeEvent> row =
            new EfsRow<>(sTestClock.instant(),
                         202L,
                         EfsFile.NO_TAGS,
                         event);

        assertThat(attributes).isNotNull();
        assertThat(attributes).hasSize(5);

        validateAttribute(attributes,
                          "symbol",
                          SimpleAttribute.class,
                          row,
                          false,
                          symbol);

        validateAttribute(attributes,
                          "price",
                          SimpleAttribute.class,
                          row,
                          false,
                          price);

        validateAttribute(attributes,
                          "size",
                          SimpleAttribute.class,
                          row,
                          false,
                          size);

        validateAttribute(attributes,
                          "priceTrend",
                          SimpleAttribute.class,
                          row,
                          false,
                          trend);

        validateAttribute(attributes,
                          "volume",
                          SimpleAttribute.class,
                          row,
                          false,
                          volume);
    } // end of createTradeAttributeMapTest()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private <E extends IEfsEvent> void validateAttribute(final Map<String, Attribute<EfsRow<E>, ?>> attributes,
                                                         final String attributeName,
                                                         final Class<?> attributeType,
                                                         final EfsRow<E> row,
                                                         final boolean isNull,
                                                         final Object expectedValue)
    {
        final Attribute<EfsRow<E>, ?> attribute =
                attributes.get(attributeName);
        final Iterable<?> values;
        final Iterator<?> vIt;
        Object value;

        assertThat(attribute).isNotNull();
        assertThat(attribute).isInstanceOf(attributeType);

        values = attribute.getValues(row, NO_OPTS);

        if (isNull)
        {
            assertThat(values).isNull();
        }
        else
        {
            assertThat(values).isNotNull();

            vIt = values.iterator();

            // Is expected value a collection?
            if (expectedValue instanceof Iterable iterable)
            {
                // Yes. Make sure all the expected values are
                // received.
                final Iterator eIt = iterable.iterator();

                while (eIt.hasNext())
                {
                    assertThat(vIt.hasNext()).isTrue();

                    value = vIt.next();
                    assertThat(value).isEqualTo(eIt.next());
                }
            }
            // No, single value.
            else
            {
                assertThat(vIt.hasNext()).isTrue();

                value = vIt.next();
                assertThat(value).isEqualTo(expectedValue);
            }

            // There should be no more values.
            assertThat(vIt.hasNext()).isFalse();
        }
    } // end of validateAttribute(Map, String, EfsRow, Object)
} // end of class CQAttributeGeneratorTest
