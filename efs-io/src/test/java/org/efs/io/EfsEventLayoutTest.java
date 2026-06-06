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

import java.util.Map;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import org.decimal4j.immutable.Decimal2f;
import org.efs.io.EfsEventLayout.GetterMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class EfsEventLayoutTest
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
    @DisplayName("null event class")
    public void efsEventLayoutNullEventClass()
    {
        final Class<TradeEvent> eventClass = null;

        assertThatThrownBy(
            () -> EfsEventLayout.getLayout(eventClass))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(EfsEventLayout.NULL_EVENT_CLASS);
    } // end of efsEventLayoutNullEventClass()

    @Test
    @DisplayName("abstract event class")
    public void efsEventLayoutAbstractEventClass()
    {
        final Class<AbstractTestEvent> eventClass =
            AbstractTestEvent.class;
        final String message =
            String.format(EfsEventLayout.ABSTRACT_EVENT_CLASS,
                          eventClass.getName());

        assertThatThrownBy(
            () -> EfsEventLayout.getLayout(eventClass))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(message);
    } // end of efsEventLayoutAbstractEventClass()

    @Test
    @DisplayName("event parse")
    public void efsEventLayoutParseTest()
    {
        final Class<TradeEvent> tc = TradeEvent.class;
        final String[] fieldNames =
        {
            "symbol",
            "price",
            "size",
            "priceTrend"
        };
        final String[] methodNames =
        {
            "getSymbol",
            "getPrice",
            "getSize",
            "getPriceTrend"
        };
        final Class[] fieldTypes =
        {
            String.class,
            Decimal2f.class,
            int.class,
            TradeEvent.PriceTrend.class
        };
        final CQIndexType[] indexTypes =
        {
            CQIndexType.RADIX_TREE_INDEX,
            CQIndexType.NAVIGABLE_INDEX,
            CQIndexType.NAVIGABLE_INDEX,
            CQIndexType.HASH_INDEX
        };
        final int numFields = fieldNames.length;
        final EfsEventLayout<TradeEvent> layout =
            EfsEventLayout.getLayout(tc);

        assertThat(layout).isNotNull();
        assertThat(layout.eventClass()).isEqualTo(tc);

        final Map<String, GetterMethod> getters =
            layout.getters();
        GetterMethod getter;
        CQAttribute annotation;

        assertThat(getters).isNotNull();
        assertThat(getters.size()).isEqualTo(numFields);

        for (int i = 0; i < numFields; ++i)
        {
            getter = getters.get(fieldNames[i]);

            assertThat(getter).isNotNull();

            annotation = getter.annotation();

            assertThat(getter.methodName())
                .isEqualTo(methodNames[i]);
            assertThat(getter.fieldName())
                .isEqualTo(fieldNames[i]);
            assertThat(getter.dataType())
                .isEqualTo(fieldTypes[i]);
            assertThat(annotation).isNotNull();
            assertThat(annotation.attribute())
                .isEqualTo(CQAttributeType.SIMPLE);
            assertThat(annotation.index())
                .isEqualTo(indexTypes[i]);
        }

        assertThat(getters.containsKey("volume")).isFalse();

        assertThat(EfsEventLayout.getLayout(tc))
            .isSameAs(layout);
    } // end of efsEventLayoutParseTest()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------
} // end of class EfsEventLayoutTest