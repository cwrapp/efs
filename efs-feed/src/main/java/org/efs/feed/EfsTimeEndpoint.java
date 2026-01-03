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

import java.time.Instant;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;
import net.sf.eBus.util.Validator;

/**
 * An interval endpoint based on time. This time is fixed when
 * endpoint is created.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
public final class EfsTimeEndpoint
    extends EfsIntervalEndpoint
    implements Comparable<EfsTimeEndpoint>
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Timestamp defining this endpoint.
     */
    private final Instant mTime;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of TimeEndpoint.
     */
    private EfsTimeEndpoint(final Builder builder)
    {
        super (builder);

        mTime = builder.mTime;
    } // end of EfsTimeEndpoint(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Comparable Interface Implementation.
    //

    /**
     * Returns integer value &lt;, equal to, or &gt; zero if
     * {@code this EfsTimeEndpoint} is &lt;, equal to, or &gt;
     * given time endpoint argument. Comparison is based on
     * {@link #location() location} first,
     * {@link #time()} second, and
     * {@link #clusivity()} third.
     * @param ep comparison time endpoint.
     * @return integer value &lt;, equal to, or &gt; zero.
     */
    @Override
    public int compareTo(final EfsTimeEndpoint ep)
    {
        int retval =
            IntervalLocation.compare(mLocation, ep.mLocation);

        if (retval == 0)
        {
            retval = mTime.compareTo(ep.mTime);
        }

        return (retval);
    } // end of compareTo(EfsTimeEndpoint)

    //
    // end of Comparable Interface Implementation.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns time as text.
     * @return {@code Instant.toString()}.
     */
    @Override
    public String toString()
    {
        return (mTime.toString());
    } // end of toString()

    /**
     * Returns {@code true} if {@code o} is an
     * {@code EfsTimeEndpoint} instance with the same time,
     * clusivity, and interval location as
     * {@code this EfsTimeEndpoint} instance.
     * @param o comparison object.
     * @return {@code true} if {@code o} equals
     * {@code this EfsTimeEndpoint}.
     */
    @Override
    public boolean equals(final Object o)
    {
        boolean retcode = (this == o);

        if (!retcode && o instanceof EfsTimeEndpoint)
        {
            final EfsTimeEndpoint ep = (EfsTimeEndpoint) o;

            retcode = (super.equals(ep) &&
                       mTime.equals(ep.mTime));
        }

        return (retcode);
    } // end of equals(Object)

    /**
     * Returns a hash value based on endpoint time, clusivity,
     * and interval location.
     * @return efs time endpoint hash code.
     */
    @Override
    public int hashCode()
    {
        return (Objects.hash(super.hashCode(), mTime));
    } // end of hashCode()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns endpoint time.
     * @return endpoint time.
     */
    public Instant time()
    {
        return (mTime);
    } // end of time()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Returns a new {@code EfsTimeEndpoint} builder instance
     * used to create an {@code EfsTimeEndpoint} object. It is
     * recommended that a new builder be used for each new
     * {@code EfsTimeEndpoint} instance and not re-use the same
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
     * Builder class for {@link EfsTimeEndpoint}.
     */
    public static final class Builder
        extends EfsIntervalEndpoint.EndpointBuilder<EfsTimeEndpoint>
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
         * When attempting to set endpoint time to {@code null},
         * then {@code NullPointerException} contains message
         * {@value}.
         */
        public static final String TIME_NULL = "time is null";

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Use when setting begin or end time to now.
         */
        private final Instant mNow;

        /**
         * Configured interval time.
         */
        private Instant mTime;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Builder()
        {
            super (EndpointType.FIXED_TIME);

            mNow = Instant.now();
        } // end of Builder()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Implementations.
        //

        /**
         * Returns a new {@code EfsTimeEndpoint} instance based
         * on this builder's settings.
         * @return new {@code EfsTimeEndpoint} instance.
         */
        @Override
        protected EfsTimeEndpoint buildImpl()
        {
            return (new EfsTimeEndpoint(this));
        } // end of buildImpl()

        //
        // end of Abstract Method Implementations.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets interval time and clusivity. Time interval
         * location is based on whether {@code time} is &lt;
         * or &ge; to current time. Returns {@code this Builder}
         * instance so that builder method calls can be chained.
         * @param time interval time.
         * @param clusivity interval time clusivity.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if either {@code time} or {@code clusivity} is
         * {@code null}. Note: if this is the case, then neither
         * time nor clusivity is set.
         *
         * @see #now(Clusivity)
         */
        public Builder time(final Instant time,
                            final Clusivity clusivity)
        {
            Objects.requireNonNull(time, TIME_NULL);
            Objects.requireNonNull(clusivity, CLUSIVITY_NULL);

            mTime = time;
            mClusivity = clusivity;

            // Set the time location based on whether time is <
            // or >= now.
            mLocation = (time.compareTo(mNow) < 0 ?
                         IntervalLocation.PAST :
                         IntervalLocation.FUTURE);

            return (this);
        } // end of time(Instant, Clusivity)

        /**
         * Sets endpoint time to current time, interval
         * location to {@link IntervalLocation#NOW}, and
         * clusivity to given value. When clusivity is combined
         * with interval location, this defines whether interval
         * begins/ends at the current time or just after/before.
         * Returns {@code this Builder} instance so builder
         * method calls can be chained.
         * @param clusivity endpoint clusivity.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code clusivity} is {@code null}. If this is the
         * case, then time and location are not set.
         */
        public Builder now(final Clusivity clusivity)
        {
            Objects.requireNonNull(clusivity, CLUSIVITY_NULL);

            mTime = mNow;
            mClusivity = clusivity;
            mLocation = IntervalLocation.NOW;

            return (this);
        } // end of now(Clusivity)

        /**
         * Sets endpoint to {@link Instant#MAX}, interval
         * location to {@link IntervalLocation#FUTURE}, and
         * clusivity to {@link Clusivity#EXCLUSIVE}. This
         * setting can only be used as an ending endpoint since
         * it is always &gt; any other endpoint.
         * Returns {@code this Builder} instance so that builder
         * method calls can be chained.
         * @return {@code this Builder} instance.
         */
        public Builder endNever()
        {
            mTime = Instant.MAX;
            mClusivity = Clusivity.EXCLUSIVE;
            mLocation = IntervalLocation.FUTURE;

            return (this);
        } // end of endNever()

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Verifies that time is set.
         * @param problems append invalid setting messages to
         * this list.
         * @return {@code problems}.
         */
        @Override
        protected Validator validate(final Validator problems)
        {
            return (super.validate(problems)
                         .requireNotNull(mTime, TIME_NULL));
        } // end of validate(Validator)
    } // end of class Builder
} // end of class EfsTimeEndpoint
