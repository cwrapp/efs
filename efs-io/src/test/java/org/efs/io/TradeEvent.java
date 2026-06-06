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

import com.google.common.base.Strings;
import com.google.errorprone.annotations.Immutable;
import jakarta.annotation.Nonnull;
import java.util.Objects;
import net.sf.eBus.util.Validator;
import org.decimal4j.immutable.Decimal2f;
import org.efs.event.IEfsEvent;
import org.efs.event.IEfsEventBuilder;

/**
 * Event reporting a financial trade and latest trading volume.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
public final class TradeEvent
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member enum.
//

    public enum PriceTrend
    {
        UP,
        DOWN,
        ZERO_PLUS,
        ZERO_MINUS
    } // end of enum PriceTrend

//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    private final String mSymbol;
    private final Decimal2f mPrice;
    private final int mSize;
    private final PriceTrend mPriceTrend;
    private final int mVolume;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    private TradeEvent(final Builder builder)
    {
        mSymbol = builder.mSymbol;
        mPrice = builder.mPrice;
        mSize = builder.mSize;
        mPriceTrend = builder.mPriceTrend;
        mVolume = builder.mVolume;
    } // end of TradeEvent(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    @Override
    public String toString()
    {
        return (String.format("%,d %s @ %s (%s) volume=%,d",
                              mSize,
                              mSymbol,
                              mPrice,
                              mPriceTrend,
                              mVolume));
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    @Nonnull
    @CQAttribute(attribute = CQAttributeType.SIMPLE,
                 index = CQIndexType.RADIX_TREE_INDEX)
    public String getSymbol()
    {
        return (mSymbol);
    } // end of getSymbol()

    @Nonnull
    @CQAttribute(attribute = CQAttributeType.SIMPLE,
                 index = CQIndexType.NAVIGABLE_INDEX)
    public Decimal2f getPrice()
    {
        return (mPrice);
    } // end of getPrice()

    @CQAttribute(attribute = CQAttributeType.SIMPLE,
                 index = CQIndexType.NAVIGABLE_INDEX)
    public int getSize()
    {
        return (mSize);
    } // end of getSize()

    @Nonnull
    @CQAttribute(attribute = CQAttributeType.SIMPLE,
                 index = CQIndexType.HASH_INDEX)
    public PriceTrend getPriceTrend()
    {
        return (mPriceTrend);
    } // end of getPriceTrend()

    // No CQEngine attribute for this getter.
    public int getVolume()
    {
        return (mVolume);
    } // end of getVolume()

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
        implements IEfsEventBuilder<TradeEvent>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private String mSymbol;
        private Decimal2f mPrice;
        private int mSize;
        private PriceTrend mPriceTrend;
        private int mVolume;

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
        // IEfsEventBuilder Interface Implementation.
        //

        @Override
        public TradeEvent build()
        {
            validate();

            return (new TradeEvent(this));
        } // end of build()

        //
        // end of IEfsEventBuilder Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        public Builder symbol(final String symbol)
        {
            if (Strings.isNullOrEmpty(symbol) ||
                symbol.isBlank())
            {
                throw (
                    new IllegalArgumentException(
                        "symbol is either null, empty, or blank"));
            }

            mSymbol = symbol;

            return (this);
        } // end of symbol(String)


        public Builder price(final Decimal2f price)
        {
            Objects.requireNonNull(price, "price is null");

            if (price.compareTo(Decimal2f.ZERO) <= 0)
            {
                throw (
                    new IllegalArgumentException(
                        "price <= zero"));
            }

            mPrice = price;

            return (this);
        } // end of price(Decimal2f)

        public Builder size(final int size)
        {
            if (size <= 0)
            {
                throw (
                    new IllegalArgumentException(
                        "size <= zero"));
            }

            mSize = size;

            return (this);
        } // end of size(final int size)

        public Builder priceTrend(final PriceTrend trend)
        {
            mPriceTrend =
                Objects.requireNonNull(trend, "trend is null");

            return (this);
        } // end of priceTrend(PriceTrend)

        public Builder volume(final int volume)
        {
            if (volume <= 0)
            {
                throw (
                    new IllegalArgumentException(
                        "volume <= zero"));
            }

            mVolume = volume;

            return (this);
        } // end of volume(final int volume)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        private void validate()
        {
            final Validator problems = new Validator();

            problems.requireNotNull(mSymbol, "symbol")
                    .requireNotNull("price", "price")
                    .requireTrue((mSize > 0),
                                 "size",
                                 Validator.NOT_SET)
                    .requireNotNull(mPriceTrend, "priceTrend")
                    .requireTrue((mVolume > 0),
                                 "volume",
                                 Validator.NOT_SET)
                    .throwException(TradeEvent.class);
        } // end of validate()
    } // end of class Builder
} // end of class TradeEvent
