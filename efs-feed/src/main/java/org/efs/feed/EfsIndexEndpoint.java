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

import java.util.Objects;
import javax.annotation.concurrent.Immutable;
import net.sf.eBus.util.Validator;

/**
 * An interval endpoint based on event index offset. This offset
 * is applied when {@link EfsFeed} processes the associated
 * subscription.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
public final class EfsIndexEndpoint
    extends EfsIntervalEndpoint
    implements Comparable<EfsIndexEndpoint>
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Offset into event store. May be &lt;, equal to, or &gt;
     * zero.
     */
    private final int mIndexOffset;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of EfsIndexEndpoint.
     */
    private EfsIndexEndpoint(final Builder builder)
    {
        super (builder);

        mIndexOffset = builder.mIndexOffset;
    } // end of EfsIndexEndpoint(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Comparable Interface Implementation.
    //

    /**
     * Returns integer value &lt;, equal to, or &gt; zero if
     * {@code this EfsIndexEndpoint} is &lt;, equal to, or &gt;
     * given time endpoint argument. Comparison is based on
     * {@link #location() location} first,
     * {@link #indexOffset() ()} second, and {@link #clusivity()}
     * third.
     * @param ep comparison index endpoint.
     * @return integer value &lt;, equal to, or &gt; zero.
     */
    @Override
    public int compareTo(final EfsIndexEndpoint ep)
    {
        int retval =
            IntervalLocation.compare(mLocation, ep.mLocation);

        if (retval == 0)
        {
            retval = (mIndexOffset - ep.mIndexOffset);
        }

        return (retval);
    } // end of compareTo(EfsIndexEndpoint)

    //
    // end of Comparable Interface Implementation.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns index offset only as text.
     * @return index offset as text.
     */
    @Override
    public String toString()
    {
        return (String.format("%,d", mIndexOffset));
    } // end of toString()

    /**
     * Returns {@code true} if {@code o} is an
     * {@code EfsIndexEndpoint} instance with the same index
     * offset, clusivity, and interval location as
     * {@code this EfsIndexEndpoint} instance.
     * @param o comparison object.
     * @return {@code true} if {@code o} equals
     * {@code this EfsIndexEndpoint}.
     */
    @Override
    public boolean equals(final Object o)
    {
        boolean retcode = (this == o);

        if (!retcode && o instanceof EfsIndexEndpoint)
        {
            final EfsIndexEndpoint ep = (EfsIndexEndpoint) o;

            retcode = (super.equals(ep) &&
                       mIndexOffset == ep.mIndexOffset);
        }

        return (retcode);
    } // end of equals(Object)

    /**
     * Returns a hash value based on endpoint offset, clusivity,
     * and interval location.
     * @return efs index offset endpoint hash code.
     */
    @Override
    public int hashCode()
    {
        return (Objects.hash(super.hashCode(), mIndexOffset));
    } // end of hashCode()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns event store index offset.
     * @return index offset.
     */
    public int indexOffset()
    {
        return (mIndexOffset);
    } // end of indexOffset()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Returns a new {@code EfsIndexEndpoint} builder instance
     * used to create an {@code EfsIndexEndpoint} object. It is
     * recommended that a new builder be used for each new
     * {@code EfsIndexEndpoint} instance and not re-use the same
     * {@code Builder} instance to create multiple intervals.
     * @return interval builder instance.
     */
    public static Builder builder()
    {
        return (new Builder());
    } // end of builder()

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Builder class for {@link EfsIndexEndpoint}.
     */
    public static final class Builder
        extends EfsIntervalEndpoint.EndpointBuilder<EfsIndexEndpoint>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Event store index offset.
         */
        private int mIndexOffset;

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

        private Builder()
        {
            super (EndpointType.INDEX_OFFSET);

            mSetFlag = false;
        } // end of Builder()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Implementations.
        //

        /**
         * Returns a new {@code EfsIndexEndpoint} instance based
         * on this builder's settings.
         * @return new {@code EfsIndexEndpoint} instance.
         */
        @Override
        protected EfsIndexEndpoint buildImpl()
        {
            return (new EfsIndexEndpoint(this));
        } // end of buildImpl()

        //
        // end of Abstract Method Implementations.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets index offset to given value and clusivity. Offset
         * may be &lt;, equal to, or &gt; zero. Returns
         * {@code this Builder} instance so that builder method
         * calls can be chained.
         * @param offset event store index offset.
         * @param clusivity index offset clusivity.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code clusivity} is {@code null}.
         */
        public Builder indexOffset(final int offset,
                                   final Clusivity clusivity)
        {
            Objects.requireNonNull(clusivity, CLUSIVITY_NULL);

            mIndexOffset = offset;
            mClusivity = clusivity;

            if (offset < 0)
            {
                mLocation = IntervalLocation.PAST;
            }
            else if (offset == 0)
            {
                mLocation = IntervalLocation.NOW;
            }
            else
            {
                mLocation = IntervalLocation.FUTURE;
            }

            mSetFlag = true;

            return (this);
        } // end of indexOffset(int, Clusivity)

        /**
         * Sets index offset to zero with the given clusivity.
         * @param clusivity endpoint clusivity.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code clusivity} is {@code null}.
         */
        public Builder now(final Clusivity clusivity)
        {
            Objects.requireNonNull(clusivity, CLUSIVITY_NULL);

            mIndexOffset = 0;
            mClusivity = clusivity;
            mLocation = IntervalLocation.NOW;

            mSetFlag = true;

            return (this);
        } // end of now(Clusivity)

        /**
         * Sets index offset to {@link Integer#MAX_VALUE},
         * interval location to {@link IntervalLocation#FUTURE},
         * and clusivity to {@link Clusivity#EXCLUSIVE}. This
         * setting can only be used as an ending endpoint since
         * it is always &gt; any other endpoint.
         * @return {@code this Builder} instance so that builder
         * method calls can be chained.
         */
        public Builder endNever()
        {
            mIndexOffset = Integer.MAX_VALUE;
            mClusivity = Clusivity.EXCLUSIVE;
            mLocation =  IntervalLocation.FUTURE;

            mSetFlag = true;

            return (this);
        } // end of endNever()

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Verifies that index offset is set.
         * @param problems append invalid setting messages to
         * this list.
         * @return {@code problems}.
         */
        @Override
        protected Validator validate(final Validator problems)
        {
            return (super.validate(problems)
                         .requireTrue(mSetFlag,
                                      "indexOffset",
                                      Validator.NOT_SET));
        } // end of validate(Validator)
    } // end of class Builder
} // end of class EfsIndexEndpoint
