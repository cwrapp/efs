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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import net.sf.eBus.util.Validator;

/**
 * An interval endpoint based on event time offset. This offset
 * is applied when {@code EfsFeed} processes the associated
 * subscription.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsDurationEndpoint
    extends EfsIntervalEndpoint
    implements Comparable<EfsDurationEndpoint>
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Offset into event time map.
     */
    private final Duration mTimeOffset;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of EfsDurationEndpoint.
     */
    private EfsDurationEndpoint(final Builder builder)
    {
        super (builder);

        mTimeOffset = builder.mTimeOffset;
    } // end of EfsDurationEndpoint(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Comparable Interface Implementation.
    //

    @Override
    public int compareTo(final EfsDurationEndpoint ep)
    {
        int retval =
            IntervalLocation.compare(mLocation, ep.mLocation);

        if (retval == 0)
        {
            retval = mTimeOffset.compareTo(ep.mTimeOffset);
        }

        return (retval);
    } // end of compareTo(EfsDurationEndpoint)

    //
    // end of Comparable Interface Implementation.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns time offset duration as text.
     * @return {@code Duration.toString()}.
     */
    public String toString()
    {
        return (mTimeOffset.toString());
    } // end of toString()

    /**
     * Returns {@code true} if {@code o} is an
     * {@code EfsDurationEndpoint} instance with the same
     * duration, clusivity, and interval location as
     * {@code this EfsDurationEndpoint} instance.
     * @param o comparison object.
     * @return {@code true} if {@code o} equals
     * {@code this EfsDurationEndpoint}.
     */
    @Override
    public boolean equals(final Object o)
    {
        boolean retcode = (this == o);

        if (!retcode && o instanceof EfsDurationEndpoint)
        {
            final EfsDurationEndpoint ep =
                (EfsDurationEndpoint) o;

            retcode = (super.equals(ep) &&
                       mTimeOffset.equals(ep.mTimeOffset));
        }

        return (retcode);
    } // end of equals(Object)

    /**
     * Returns a hash value based on endpoint duration,
     * clusivity, and interval location.
     * @return efs duration endpoint hash code.
     */
    @Override
    public int hashCode()
    {
        return (Objects.hash(super.hashCode(), mTimeOffset));
    } // end of hashCode()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns time offset.
     * @return time offset.
     */
    public Duration timeOffset()
    {
        return (mTimeOffset);
    } // end of timeOffset()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Returns a new {@code EfsDurationEndpoint} builder instance
     * used to create an {@code EfsDurationEndpoint} object. It
     * is recommended that a new builder be used for each new
     * {@code EfsDurationEndpoint} instance and not re-use the
     * same {@code Builder} instance to create multiple
     * intervals.
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
     * Builder class for {@link EfsDurationEndpoint}.
     */
    public static final class Builder
        extends EfsIntervalEndpoint.EndpointBuilder<EfsDurationEndpoint>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Constants.
        //

        //
        // Exception messages.
        //

        /**
         * When attempting to set time offset to {@code null},
         * then {@code NullPointerException} contains message
         * {@value}.
         */
        public static final String TIME_OFFSET_NULL =
            "offset is null";

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Event store time offset.
         */
        private Duration mTimeOffset;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Builder()
        {
            super (EndpointType.TIME_OFFSET);
        } // end of Builder()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Implementations.
        //

        /**
         * Returns a new {@code EfsDurationEndpoint} instance
         * based on this builder's settings.
         * @return new {@code EfsDurationEndpoint} instance.
         */
        @Override
        protected EfsDurationEndpoint buildImpl()
        {
            return (new EfsDurationEndpoint(this));
        } // end of buildImpl()

        //
        // end of Abstract Method Implementations.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets time offset to given values and clusivity.
         * Offset may be &lt;, equal to, or &gt;
         * {@link Duration#ZERO}. Returns {@code this Builder}
         * instance so that builder method calls can be chained.
         * @param offset event store time offset.
         * @param clusivity endpoint clusivity.
         * @return {@code this Builder} instance.
         */
        public Builder timeOffset(final Duration offset,
                                  final Clusivity clusivity)
        {
            Objects.requireNonNull(offset, TIME_OFFSET_NULL);
            Objects.requireNonNull(clusivity, CLUSIVITY_NULL);

            mTimeOffset = offset;
            mClusivity = clusivity;

            final int c = offset.compareTo(Duration.ZERO);

            if (c < 0)
            {
                mLocation = IntervalLocation.PAST;
            }
            else if (c == 0)
            {
                mLocation = IntervalLocation.NOW;
            }
            else
            {
                mLocation = IntervalLocation.FUTURE;
            }

            return (this);
        } // end of timeOffset(Duration, Clusivity)

        /**
         * Sets time offset to zero with the given clusivity.
         * @param clusivity endpoint clusivity.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code clusivity} is {@code null}.
         */
        public Builder now(final Clusivity clusivity)
        {
            Objects.requireNonNull(clusivity, CLUSIVITY_NULL);

            mTimeOffset = Duration.ZERO;
            mClusivity = clusivity;
            mLocation = IntervalLocation.NOW;

            return (this);
        } // end of now(Clusivity)

        /**
         * Sets time offset to {@link ChronoUnit#FOREVER},
         * interval location to {@link IntervalLocation#FUTURE},
         * and clusivity to {@link Clusivity#EXCLUSIVE}. This
         * setting can only be used as an ending endpoint since
         * it is always &gt; any other endpoint.
         * @return {@code this Builder} instance so that builder
         * method calls can be chained.
         */
        public Builder endNever()
        {
            mTimeOffset = ChronoUnit.FOREVER.getDuration();
            mClusivity = Clusivity.EXCLUSIVE;
            mLocation = IntervalLocation.FUTURE;

            return (this);
        } // end of endNever()

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Verifies that time offset is set.
         * @param problems append invalid setting messages to
         * this list.
         * @return {@code problems}.
         */
        @Override
        protected Validator validate(final Validator problems)
        {
            return (super.validate(problems)
                         .requireNotNull(mTimeOffset,
                                         "timeOffset"));
        } // end of validate(Validator)
    } // end of class Builder
} // end of class EfsDurationEndpoint
