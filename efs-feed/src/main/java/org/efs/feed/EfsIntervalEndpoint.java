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
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;

/**
 * An {@link EfsInterval} consists of two endpoints: beginning
 * and ending. These end points are defined as either index or
 * time. An index endpoint specifies a number of events. For
 * example, a beginning endpoint may be for the up to 100 events
 * in the past and and ending endpoint may be fore up to 100
 * events on into the future.
 * <p>
 * A time endpoint specifies events starting or stopping at
 * particular time. However, it is possible to create a time
 * interval based on a {@link java.time.Duration} and current
 * time. For example, a beginning endpoint may be created for
 * 5 minutes prior to the current time and an ending endpoint for
 * 5 hours after the current time.
 * </p>
 * <p>
 * Endpoints have an associated {@link Clusivity}. The interval
 * may include or exclude the specified value. An index beginning
 * endpoint with a initial value of 100 may be inclusive
 * (interval starts at 1000 events in the past) or exclusive
 * (interval starts at 99 events in the past).
 * </p>
 * <p>
 * Endpoints also have an interval location based on whether
 * it is located in the past, now, future, or is on-going (that
 * is, interval never terminates).
 * </p>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
public abstract class EfsIntervalEndpoint
{
//---------------------------------------------------------------
// Member enums.
//

    /**
     * Specifies whether an end point is inclusive or exclusive.
     */
    public enum Clusivity
    {
        /**
         * The end point time is excluded from the interval.
         */
        EXCLUSIVE ('(', ')'),

        /**
         * The end point time is included in the interval.
         */
        INCLUSIVE ('[', ']');

    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Clusivity symbol for begin time.
         */
        private final char mBeginSymbol;

        /**
         * Clusivity symbol for end time.
         */
        private final char mEndSymbol;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates Clusivity enum instance.
         * @param beginSymbol begin time clusivity symbol.
         * @param endSymbol end time clusivity symbol.
         */
        private Clusivity(final char beginSymbol,
                          final char endSymbol)
        {
            mBeginSymbol = beginSymbol;
            mEndSymbol = endSymbol;
        } // end of Clusivity(char, char)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns clusivity begin symbol.
         * @return begin symbol.
         */
        public char beginSymbol()
        {
            return (mBeginSymbol);
        } // end of beginSymbol()

        /**
         * Returns clusivity end symbol.
         * @return end symbol.
         */
        public char endSymbol()
        {
            return (mEndSymbol);
        } // end of endSymbol()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of enum Clusivity

    /**
     * Denotes if endpoint denotes past, current time, future, or
     * future on-going.
     */
    public enum IntervalLocation
    {
        /**
         * Interval endpoint is in the past.
         */
        PAST (true, false, "past"),

        /**
         * Interval endpoint is the current time. Current
         * time is neither in the past nor the future.
         */
        NOW (false, false, "now"),

        /**
         * Interval endpoint is in the future. Interval beginning
         * may <em>not</em> be in the future.
         */
        FUTURE (false, true, "future");

    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Set to {@code true} if this historic subscription
         * references past notifications.
         */
        private final boolean mIsPast;

        /**
         * Set to {@code true} if this historic subscription
         * references notifications in the future.
         */
        private final boolean mIsFuture;

        /**
         * Time location print version.
         */
        private final String mText;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private IntervalLocation(final boolean pastFlag,
                             final boolean futureFlag,
                             final String text)
        {
            mIsPast = pastFlag;
            mIsFuture = futureFlag;
            mText = text;
        } // end of IntervalLocation(boolean, boolean, String)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        @Override
        public String toString()
        {
            return (mText);
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns {@code true} if time is located in past.
         * Note that {@link #NOW} is neither in the past or the
         * future.
         * @return {@code true} if time is located in past.
         */
        public boolean isPast()
        {
            return (mIsPast);
        } // end of isPast()

        /**
         * Returns {@code true} if time is located in future.
         * Note that {@link #NOW} is neither in the past or the
         * future.
         * @return {@code true} if time is located in future.
         */
        public boolean isFuture()
        {
            return (mIsFuture);
        } // end of isFuture()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        /**
         * Returns an integer value &lt;, equal to, or &gt; zero
         * if {@code l0} is &lt;, equal to, or &gt; {@code l1}.
         * @param l0 first interval location.
         * @param l1 second interval location.
         * @return integer value &lt; equal to, or &gt; zero.
         */
        public static int compare(final IntervalLocation l0,
                                  final IntervalLocation l1)
        {
            return (l0.ordinal() - l1.ordinal());
        } // end of compare(IntervalLocation, IntervalLocation)
    } // end of enum IntervalLocation

    /**
     * An endpoint is based either on fixed time, time offset, or
     * index offset.
     */
    public enum EndpointType
    {
        /**
         * A fixed time endpoint specifies a specific wall clock
         * date/time.
         */
        FIXED_TIME,

        /**
         * A time offset endpoint is relative to the feed's
         * current time.
         */
        TIME_OFFSET,

        /**
         * An index offset endpoint is relative to feed's current
         * index.
         */
        INDEX_OFFSET
    } // end of enum EndpointType

    /**
     * Specifies on which interval side this endpoint is placed.
     */
    public enum IntervalSide
    {
        /**
         * Endpoint is the interval beginning endpoint.
         */
        BEGINNING,

        /**
         * Endpoint is the interval ending endpoint.
         */
        ENDING
    } // end of enum IntervalSide

//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Endpoint type.
     */
    protected final EndpointType mType;

    /**
     * Endpoint is either inclusive or exclusive.
     */
    protected final Clusivity mClusivity;

    /**
     * Endpoint location within interval.
     */
    protected final IntervalLocation mLocation;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new interval endpoint with the given clusivity
     * and interval location.
     * @param builder endpoint instance builder.
     */
    protected EfsIntervalEndpoint(final EndpointBuilder<?> builder)
    {
        mType = builder.mType;
        mClusivity = builder.mClusivity;
        mLocation = builder.mLocation;
    } // end of EfsIntervalEndpoint(EndpointBuilder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns {@code true} if {@code o} is an
     * {@code EfsIntervalEndpoint} instance with equal
     * {@link Clusivity} and {@link IntervalLocation} as
     * {@code this EfsIntervalEndpoint} instance.
     * @param o comparison argument.
     * @return {@code true} if {@code o} equals
     * {@code this EfsIntervalEndpoint}.
     */
    @Override
    public boolean equals(final Object o)
    {
        boolean retcode = (this == o);

        if (!retcode && o instanceof EfsIntervalEndpoint)
        {
            final EfsIntervalEndpoint ep =
                (EfsIntervalEndpoint) o;

            retcode = (mClusivity == ep.mClusivity &&
                       mLocation == ep.mLocation);
        }

        return (retcode);
    } // end of equals(Object)

    /**
     * Returns a hash value based on endpoint clusivity and
     * interval location.
     * @return endpoint hash code.
     */
    @Override
    public int hashCode()
    {
        return (Objects.hash(mClusivity, mLocation));
    } // end of hashCode()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns endpoint clusivity.
     * @return endpoint clusivity.
     */
    public final Clusivity clusivity()
    {
        return (mClusivity);
    } // end of clusivity()

    /**
     * Returns endpoint interval location.
     * @return interval location.
     */
    public final IntervalLocation location()
    {
        return (mLocation);
    } // end of location()

    /**
     * Returns endpoint type.
     * @return endpoint type.
     */
    public final EndpointType endpointType()
    {
        return (mType);
    } // end of endpointType()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Outputs this endpoint in format appropriate to which
     * side of the interval the endpoint is on.
     * @param side interval side.
     * @return endpoint as text.
     */
    public final String toString(final IntervalSide side)
    {
        final char clusivity =
            (side == IntervalSide.BEGINNING ?
             mClusivity.mBeginSymbol :
             mClusivity.mEndSymbol);
        final String location =
            (mLocation == IntervalLocation.NOW ?
             mLocation.name() :
             this.toString());
        final String retval;

        // Which side?
        if (side == IntervalSide.BEGINNING)
        {
            // Left side.
            retval = String.format("%c%s", clusivity, location);
        }
        else
        {
            // Right side.
            retval = String.format("%s%c", location, clusivity);
        }

        return (retval);
    } // end of toString(IntervalSide)

    /**
     * Returns integer value &lt;, equal to, or &gt; zero if
     * {@code ep0} is &lt;, equal to, &gt; {@code ep1}.
     * Comparison is based on {@link IntervalLocation} and, if
     * zero, then on {@link Clusivity}.
     * @param ep0 first endpoint.
     * @param ep1 second endpoint.
     * @return integer value &lt;, equal to, or &gt; zero.
     */
    public static int compareEndpoints(@Nonnull final EfsIntervalEndpoint ep0,
                                       @Nonnull final EfsIntervalEndpoint ep1)
    {
        int retval = (ep0.location()).compareTo(ep1.location());

        if (retval == 0)
        {
            retval =
                (ep0.clusivity()).compareTo(ep1.clusivity());
        }

        return (retval);
    } // end of compareEndpoints(...)

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Base class for all endpoint builders. Contains endpoint
     * clusivity setting and interval location.
     *
     * @param <T> interval endpoint subclass.
     */
    protected abstract static class EndpointBuilder<T extends EfsIntervalEndpoint>
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
         * When attempting to set endpoint clusivity to
         * {@code null}, then {@code NullPointerException}
         * contains message {@value}.
         */
        public static final String CLUSIVITY_NULL =
            "clusivity is null";

        /**
         * When attempting to set endpoint location to
         * {@code null}, then {@code NullPointerException}
         * contains message {@value}.
         */
        public static final String LOCATION_NULL =
            "location is null";

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Specified endpoint type: fixed time, time offset, or
         * index offset.
         */
        protected final EndpointType mType;

        /**
         * Endpoint is either inclusive or exclusive.
         */
        protected Clusivity mClusivity;

        /**
         * Endpoint is either in the past, now, or in the future.
         */
        protected IntervalLocation mLocation;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates an endpoint builder for given endpoint type.
         * @param type endpoint type.
         */
        protected EndpointBuilder(final EndpointType type)
        {
            mType = type;
        } // end of EndpointBuilder(EndpointType)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Declarations.
        //

        /**
         * Returns a newly constructed endpoint. Builder settings
         * are validated as correct.
         * @return endpoint based on this builder's validated
         * settings.
         */
        protected abstract T buildImpl();

        //
        // end of Abstract Method Declarations.
        //-------------------------------------------------------

        /**
         * Returns a new endpoint constructed from this builder's
         * settings.
         * @return new endpoint instance.
         * @throws ValidationException
         * if this builder instance contains invalid settings.
         */
        public final T build()
        {
            final Validator problems = new Validator();

            validate(problems)
                .throwException(EfsIntervalEndpoint.class);

            return (buildImpl());
        } // end of build()

        /**
         * Verifies that clusivity and location are set.
         * @param problems append invalid setting messages to
         * this list.
         * @return {@code problems}.
         */
        protected Validator validate(final Validator problems)
        {
            return (problems.requireNotNull(mClusivity,
                                            "clusivity")
                            .requireNotNull(mLocation,
                                            "location"));
        } // end of validate(Validator)
    } // end of class EndpointBuilder
} // end of class EfsIntervalEndpoint
