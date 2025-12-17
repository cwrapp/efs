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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import org.efs.event.EfsCollectionInfo;
import org.efs.event.EfsStringInfo;
import org.efs.event.IEfsEvent;
import org.efs.event.IEfsEventBuilder;


/**
 * This event contains all supported efs data types providing
 * an example of an efs event.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class SampleEvent
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member enum.
//

    /**
     * Sample enum value.
     */
    public enum Direction
    {
        UP,
        DOWN,
        LEFT,
        RIGHT
    } // end of Direction

//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Maximum name length is {@value} characters.
     */
    public static final int MAX_NAME_LENGTH = 20;

    /**
     * Maximum number of integer values is {@value}.
     */
    public static final int MAX_NUMBERS = 100;

    /**
     * Maximum individual text length is {@value} characters.
     */
    public static final int MAX_TEXT_LENGTH = 40;

    /**
     * Up to {@value} text entries may be provided.
     */
    public static final int MAX_TEXT = 10;

    //-----------------------------------------------------------
    // Locals.
    //

    private final String mName;
    private final boolean mFlag0;
    private final boolean mFlag1;
    private final byte mOneByteInt;
    private final short mTwoByteInt;
    private final int mFourByteInt;
    private final long mEightByteInt;
    private final float mFloatValue;
    private final double mDoubleValue;

    private final Direction mDirection;

    private final List<Integer> mNumbers;
    private final List<String> mText;

    private final BaseBean mRecord;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of SampleEvent.
     */
    private SampleEvent(final Builder builder)
    {
        mName = builder.mName;
        mFlag0 = builder.mFlag0;
        mFlag1 = builder.mFlag1;
        mOneByteInt = builder.mOneByteInt;
        mTwoByteInt = builder.mTwoByteInt;
        mFourByteInt = builder.mFourByteInt;
        mEightByteInt = builder.mEightByteInt;
        mFloatValue = builder.mFloatValue;
        mDoubleValue = builder.mDoubleValue;

        mDirection = builder.mDirection;

        mNumbers = builder.mNumbers;
        mText = builder.mText;

        mRecord = builder.mRecord;
    } // end of SampleEvent(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    @Override
    public boolean equals(final Object o)
    {
        boolean retcode = (this == o);

        if (!retcode && o instanceof SampleEvent)
        {
            final SampleEvent e = (SampleEvent) o;

            retcode = (mName.equals(e.mName) &&
                       mFlag0 == e.mFlag0 &&
                       mFlag1 == e.mFlag1 &&
                       mOneByteInt == e.mOneByteInt &&
                       mTwoByteInt == e.mTwoByteInt &&
                       mFourByteInt == e.mFourByteInt &&
                       mEightByteInt == e.mEightByteInt &&
                       mFloatValue == e.mFloatValue &&
                       mDoubleValue == e.mDoubleValue &&
                       mDirection == e.mDirection &&
                       mNumbers.equals(e.mNumbers) &&
                       mText.equals(e.mText) &&
                       mRecord.equals(e.mRecord));
        }

        return (retcode);
    } // end of equals(Object)

    @Override
    public int hashCode()
    {
        return (Objects.hash(mName,
                             mFlag0,
                             mFlag1,
                             mOneByteInt,
                             mTwoByteInt,
                             mFourByteInt,
                             mEightByteInt,
                             mFloatValue,
                             mDoubleValue,
                             mDirection,
                             mNumbers,
                             mText,
                             mRecord));
    } // end of hashCode()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns event name. Please note that
     * {@code @EfsStringInfo (maximumSize = MAX_NAME_LENGTH)} is
     * set to a constant rather than a raw integer value. This
     * allows the name length to be correctly validated in
     * {@link Builder#setName(String)}. Changing the constant
     * value automatically updates both the annotation and the
     * setter validation.
     * @return event name.
     */
    @EfsStringInfo (maximumSize = MAX_NAME_LENGTH)
    public String getName()
    {
        return (mName);
    } // end of getName()

    public boolean isFlag0()
    {
        return (mFlag0);
    } // end of isFlag0()

    public boolean isFlag1()
    {
        return (mFlag1);
    } // end of isFlag1()

    public byte getOneByteInt()
    {
        return (mOneByteInt);
    } // end of getOneByteInt()

    public short getTwoByteInt()
    {
        return (mTwoByteInt);
    } // end of getTwoByteInt()

    public int getFourByteInt()
    {
        return (mFourByteInt);
    } // end of getFourByteInt()

    public long getEightByteInt()
    {
        return (mEightByteInt);
    } // end of getEightByteInt()

    public float getFloatValue()
    {
        return (mFloatValue);
    } // end of getFloatValue()

    public double getDoubleValue()
    {
        return (mDoubleValue);
    } // end of getDoubleValue()

    public Direction getDirection()
    {
        return (mDirection);
    } // end of getDirection()

    @EfsCollectionInfo (maximumSize = MAX_NUMBERS)
    public List<Integer> getNumbers()
    {
        return (mNumbers);
    } // end of getNumbers()

    @EfsStringInfo (maximumSize = MAX_TEXT_LENGTH)
    @EfsCollectionInfo (maximumSize = MAX_TEXT)
    public List<String> getText()
    {
        return (mText);
    } // end of getText()

    public BaseBean getRecord()
    {
        return (mRecord);
    } // end of getRecord()

    // The following methods are not valid getters and should not
    // be seen by EfsEventLayout.

    public int get()
    {
        return (0);
    } // end of get()

    public int get(final int index)
    {
        return (mNumbers.get(index));
    } // end of get(int)

    public boolean is()
    {
        return (true);
    } // end of is()

    public boolean isPositive(final int index)
    {
        return (index > 0);
    } // end of isPositive(int)

    public int isWrong()
    {
        return (1);
    } // end of isWrong()

    // Non-public get methods are not seen by EfsEventLayout.
    /* package */ String getFirstText()
    {
        return (mText.getFirst());
    } // end of getFirstText()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Returns a new {@code Builder} instance. This
     * {@code public static} method is required for all classes
     * implementing {@link IEfsEvent}.
     * <p>
     * If is <em>recommended</em> (but not required) that a new
     * {@code Builder} instance be used to create each new
     * {@code SampleEvent} instance.
     * </p>
     * @return new {@code Builder} instance.
     */
    public static Builder builder()
    {
        return (new Builder());
    } // end of builder()

//---------------------------------------------------------------
// Inner classes.
//

    public static final class Builder
        implements IEfsEventBuilder<SampleEvent>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private String mName;
        private boolean mFlag0;
        private boolean mFlag1;
        private byte mOneByteInt;
        private short mTwoByteInt;
        private int mFourByteInt;
        private long mEightByteInt;
        private float mFloatValue;
        private double mDoubleValue;

        private Direction mDirection;

        private List<Integer> mNumbers;
        private List<String> mText;

        private BaseBean mRecord;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Builder()
        {
            // TODO
        } // end of Builder()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // IEfsEventBuilder Interface Implementation.
        //

        /**
         * Returns a new {@code SampleEvent} instance based on
         * this builder's settings. Validates
         * @return new {@code SampleEvent} instance.
         * @throws ValidationException
         * if {@code this Builder}'s settings are invalid.
         */
        @Override
        public SampleEvent build()
        {
            final Validator validator = new Validator();

            validator.requireNotNull(mName, "name")
                     .requireNotNull(mDirection, "enumValue")
                     .requireNotNull(mNumbers, "numbers")
                     .requireNotNull(mText, "text")
                     .throwException(SampleEvent.class);

            return (new SampleEvent(this));
        } // end of build()

        //
        // end of IEfsEventBuilder Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets event name to given value.
         * @param name event name.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code name} is {@code null} or an empty string or
         * {@code name}'s length &gt; {@link #MAX_NAME_LENGTH}.
         */
        public Builder setName(final String name)
        {
            if (Strings.isNullOrEmpty(name))
            {
                throw (
                    new IllegalArgumentException(
                        "name is either null or an empty string"));
            }

            if (name.length() > MAX_NAME_LENGTH)
            {
                throw (
                    new IllegalArgumentException(
                        "name length > " + MAX_NAME_LENGTH));
            }

            mName = name;

            return (this);
        } // end of setName(String)

        /**
         * Sets first flag to given value.
         * @param flag first flag value.
         * @return {@code this Builder} instance.
         */
        public Builder setFlag0(final boolean flag)
        {
            mFlag0 = flag;

            return (this);
        } // end of setFlag0(boolean)

        /**
         * Sets second flag to given value.
         * @param flag second flag value.
         * @return {@code this Builder} instance.
         */
        public Builder setFlag1(final boolean flag)
        {
            mFlag1 = flag;

            return (this);
        } // end of setFlag1(final boolean flag)

        /**
         * Sets {@code byte} to given value.
         * @param value one byte integer value.
         * @return {@code this Builder} instance.
         */
        public Builder setOneByteInt(final byte value)
        {
            mOneByteInt = value;

            return (this);
        } // end of setOneByteInt(byte)

        /**
         * Sets {@code short} to given value.
         * @param value two byte integer value.
         * @return {@code this Builder} instance.
         */
        public Builder setTwoByteInt(final short value)
        {
            mTwoByteInt = value;

            return (this);
        } // end of setTwoByteInt(short)

        /**
         * Sets {@code int} to given value.
         * @param value four byte integer value.
         * @return {@code this Builder} instance.
         */
        public Builder setFourByteInt(final int value)
        {
            mFourByteInt = value;

            return (this);
        } // end of setFourByteInt(int)

        /**
         * Sets {@code long} to given value.
         * @param value eight byte integer value.
         * @return {@code this Builder} instance.
         */
        public Builder setEightByteInt(final long value)
        {
            mEightByteInt = value;

            return (this);
        } // end of setOneByteInt(long)

        /**
         * Sets {@code float} to given value.
         * @param value float value.
         * @return {@code this Builder} instance.
         */
        public Builder setFloatValue(final float value)
        {
            mFloatValue = value;

            return (this);
        } // end of setFloatValue(float)

        /**
         * Sets {@code double} to given value.
         * @param value double value.
         * @return {@code this Builder} instance.
         */
        public Builder setDoubleValue(final double value)
        {
            mDoubleValue = value;

            return (this);
        } // end of setDoubleValue(double)

        /**
         * Sets direction value.
         * @param direction event direction.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code direction} is {@code null}.
         */
        public Builder setDirection(final Direction direction)
        {
            mDirection =
                Objects.requireNonNull(
                    direction, "direction is null");

            return (this);
        } // end of setDirection(Direction)

        /**
         * Sets number list to an immutable copy of given value.
         * @param numbers {@code int} array.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code numbers} is {@code null}.
         * @throws IllegalArgumentException
         * if {@code numbers} size > {@link #MAX_NUMBERS}.
         */
        public Builder setNumbers(final List<Integer> numbers)
        {
            Objects.requireNonNull(numbers, "numbers is null");

            if (numbers.size() > MAX_NUMBERS)
            {
                throw (
                    new IllegalArgumentException(
                        "numbers size > " + MAX_NUMBERS));
            }

            // Make a copy of array since it argument is mutable.
            mNumbers = ImmutableList.copyOf(numbers);

            return (this);
        } // end of setNumbers(List<>)

        /**
         * Sets text list to an immutable copy of given value.
         * @param text text list.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code text} is {@code null}.
         * @throws IllegalArgumentException
         * if {@code text} size &gt; {@link #MAX_TEXT} or
         * contains string value with length &gt;
         * {@link #MAX_TEXT_LENGTH}
         */
        public Builder setText(final List<String> text)
        {
            Objects.requireNonNull(text, "text is null");

            if (text.size() > MAX_TEXT)
            {
                throw (
                    new IllegalArgumentException(
                        "text size > " + MAX_TEXT));
            }

            final ImmutableList.Builder<String> builder =
                ImmutableList.builder();
            int ti = 0;

            // Create new immutable list checking each text item
            // along the way.
            for (String t : text)
            {
                if (t.length() > MAX_TEXT_LENGTH)
                {
                    throw (
                        new IllegalArgumentException(
                            String.format(
                                "text item %d length > %d",
                                ti,
                                MAX_TEXT_LENGTH)));
                }

                builder.add(t);
                ++ti;
            }

            mText = builder.build();

            return (this);
        } // end of setText(List<>)

        /**
         * Sets record value.
         * @param record record stored in sample event.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code record} is {@code null}.
         */
        public Builder setRecord(final BaseBean record)
        {
            mRecord =
                Objects.requireNonNull(record, "record is null");

            return (this);
        } // end of setRecord(BaseBean)

        //
        // Invalid set methods.
        //

        /**
         * This method has a correct signature but does not
         * match an event field. Therefore it should not be
         * used.
         * @param address address text.
         * @return {@code this Builder} instance.
         */
        public Builder setAddress(final String address)
        {
            return (this);
        } // end of setAddress(String)

        /**
         * This method has an incorrect signature.
         * @param direction event direction.
         * @param description event description.
         * @return {@code this Builder} instance.
         */
        public Builder setDirection(final Direction direction,
                                    final String description)
        {
            return (this);
        } // end of setDirection(Direction, String)

        /**
         * This method has an incorrect signature.
         * @return {@code this Builder} instance.
         */
        public Builder setDirection()
        {
            return (this);
        } // end of setDirection()

        //
        // end of Set Methods.
        //-------------------------------------------------------
    } // end of class Builder
} // end of class SampleEvent
