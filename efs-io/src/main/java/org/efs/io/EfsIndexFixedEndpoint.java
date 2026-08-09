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

import java.time.Instant;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;
import net.sf.eBus.util.Validator;
import static org.efs.io.EfsIntervalEndpoint.EndpointBuilder.CLUSIVITY_NULL;


/**
 * An interval endpoint based on fixed event index values. The
 * interval row index value must be &ge; zero.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
public final class EfsIndexFixedEndpoint
    extends EfsIntervalEndpoint
    implements Comparable<EfsIndexFixedEndpoint>
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    //
    // Exception messages.
    //

    /**
     * When attempting to set an endpoint index to a value &lt;
     * zero, then an {@code IllegalArgumentException} contains
     * message {@value}.
     */
    public static final String INVALID_INDEX = "index < zero";

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Fixed row index value. Must be &ge; zero.
     */
    private final long mFixedIndex;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new fixed efs event index endpoint based on
     * builder settings.
     * @param builder contains validated fixed index value.
     */
    private EfsIndexFixedEndpoint(final Builder builder)
    {
        super (builder);

        mFixedIndex = builder.mFixedIndex;
    } // end of EfsIndexFixedEndpoint(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Abstract Method Implementations.
    //

    @Override
    public boolean isFuture(final long nextIndex,
                            final Instant now)
    {
        return (mFixedIndex >= nextIndex);
    } // end of isFuture(long, Instant)

    //
    // end of Abstract Method Implementations.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Comparable Interface Implementation.
    //

    @Override
    public int compareTo(final EfsIndexFixedEndpoint ep)
    {
        int retval =
            IntervalLocation.compare(mLocation, ep.mLocation);

        if (retval == 0)
        {
            retval = Long.compare(mFixedIndex, ep.mFixedIndex);
        }

        return (retval);
    } // end of compareTo(EfsIndexFixedEndpoint)

    //
    // end of Comparable Interface Implementation.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns fixed row index only as text.
     * @return fixed row index as text.
     */
    @Override
    public String toString()
    {
        return (String.format("%,d", mFixedIndex));
    } // end of toString()

    /**
     * Returns {@code true} if {@code o} is an
     * {@code EfsIndexFixedEndpoint} instance with the same fixed
     * index, clusivity, and interval location as
     * {@code this EfsIndexFixedEndpoint} instance.
     * @param o comparison object.
     * @return {@code true} if {@code o} equals
     * {@code this EfsIndexFixedEndpoint}.
     */
    @Override
    public boolean equals(final Object o)
    {
        boolean retcode = (this == o);

        if (!retcode && o instanceof EfsIndexFixedEndpoint)
        {
            final EfsIndexFixedEndpoint ep =
                (EfsIndexFixedEndpoint) o;

            retcode = (super.equals(ep) &&
                       mFixedIndex == ep.mFixedIndex);
        }

        return (retcode);
    } // end of equals(Object)

    /**
     * Returns a hash value based on endpoint fixed index,
     * clusivity, and interval location.
     * @return efs fixed index endpoint hash code.
     */
    @Override
    public int hashCode()
    {
        return (Objects.hash(super.hashCode(), mFixedIndex));
    } // end of hashCode()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns fixed event row index.
     * @return fixed event row index.
     */
    public long fixedIndex()
    {
        return (mFixedIndex);
    } // end of fixedIndex()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Returns a new {@code EfsIndexFixedEndpoint} builder
     * instance used to create an {@code EfsIndexFixedEndpoint}
     * object. It is recommended that a new builder be used for
     * each new {@code EfsIndexFixedEndpoint} instance and not
     * re-use the same {@code Builder} instance to create
     * multiple intervals.
     * @param rowCount number of rows in target {@link EfsFile}.
     * This value is needed to determine whether interval is in
     * the past or future.
     * @return interval builder instance.
     */
    public static Builder builder(final long rowCount)
    {
        return (new Builder(rowCount));
    } // end of builder(long)

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Builder class for {@link EfsIndexFixedEndpoint}.
     */
    public static final class Builder
        extends EfsIntervalEndpoint.EndpointBuilder<EfsIndexFixedEndpoint>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Number of event rows currently in target
         * {@code EfsFile}.
         */
        private final long mRowCount;

        /**
         * Fixed event row index.
         */
        private long mFixedIndex;

        /**
         * Set to {@code true} when index offset to set.
         */
        private boolean mSetFlag;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Builder(final long rowCount)
        {
            super (EndpointType.FIXED_INDEX);

            mRowCount = (rowCount - 1L);
            mSetFlag = false;
        } // end of Builder(long)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Implementation.
        //

        @Override
        protected EfsIndexFixedEndpoint buildImpl()
        {
            return (new EfsIndexFixedEndpoint(this));
        } // end of buildImpl()

        //
        // end of Abstract Method Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets fixed event index to given value and clusivity.
         * Index must be &ge; zero. Returns {@code this Builder}
         * instance so that builder method calls ca be chained.
         * @param index fixed event row index.
         * @param clusivity event clusivity.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code clusivity} is {@code null}.
         * @throws IllegalArgumentException
         * if {@code index} &lt; zero.
         */
        public Builder fixedIndex(final long index,
                                  final Clusivity clusivity)
        {
            Objects.requireNonNull(clusivity, CLUSIVITY_NULL);

            if (index < 0L)
            {
                throw (
                    new IllegalArgumentException(INVALID_INDEX));
            }

            mFixedIndex = index;
            mClusivity = clusivity;

            if (index < mRowCount)
            {
                mLocation = IntervalLocation.PAST;
            }
            else if (index == mRowCount)
            {
                mLocation = IntervalLocation.NOW;
            }
            else
            {
                mLocation = IntervalLocation.FUTURE;
            }

            mSetFlag = true;

            return (this);
        } // end of fixedIndex(long, Clusivity)

        /**
         * Sets fixed event index to event row count with given
         * clusivity.
         * @param clusivity endpoint clusivity.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code clusivity} is {@code null}.
         */
        public Builder now(final Clusivity clusivity)
        {
            Objects.requireNonNull(clusivity, CLUSIVITY_NULL);

            mFixedIndex = mRowCount;
            mClusivity = clusivity;
            mLocation = IntervalLocation.NOW;

            mSetFlag = true;

            return (this);
        } // end of now(Clusivity)

        /**
         * Sets fixed event index to {@link Integer#MAX_VALUE},
         * interval location to {@link IntervalLocation#FUTURE},
         * and clusivity to {@link Clusivity#EXCLUSIVE}. This
         * setting can only be used as an ending endpoint since
         * it is always &gt; any other endpoint.
         * @return {@code this Builder} instance so that builder
         * method calls can be chained.
         */
        public Builder endNever()
        {
            mFixedIndex = Long.MAX_VALUE;
            mClusivity = Clusivity.EXCLUSIVE;
            mLocation =  IntervalLocation.FUTURE;

            mSetFlag = true;

            return (this);
        } // end of endNever()

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Verifies that fixed event index is set.
         * @param problems append invalid setting messages to
         * this list.
         * @return {@code problems}.
         */
        @Override
        protected Validator validate(final Validator problems)
        {
            return (super.validate(problems)
                         .requireTrue(mSetFlag,
                                      "fixedIndex",
                                      Validator.NOT_SET));
        } // end of validate(Validator)
    } // end of class Builder
} // end of class EfsIndexFixedEndpoint
