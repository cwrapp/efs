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
import org.efs.feed.EfsIntervalEndpoint.EndpointType;
import org.efs.feed.EfsIntervalEndpoint.IntervalSide;

/**
 * An interval consists of a beginning and ending endpoint.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
public final class EfsInterval
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Beginning interval endpoint. Must be &le; to ending
     * endpoint.
     */
    private final EfsIntervalEndpoint mBeginning;

    /**
     * Ending interval endpoint. Must be &ge; to beginning
     * endpoint.
     */
    private final EfsIntervalEndpoint mEnding;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new interval instance based on the builder
     * configuration. {@code Builder} guarantees that the
     * interval settings are correct and consistent.
     * @param builder contains interval settings.
     */
    private EfsInterval(final Builder builder)
    {
        this.mBeginning = builder.mBeginning;
        this.mEnding = builder.mEnding;
    } // end of EfsInterval(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns textual representation of end point times and
     * clusivities. Uses default {@code Instant} time format.
     * @return interval as text.
     */
    @Override
    public String toString()
    {
        return (
            String.format(
                "%s, %s",
                mBeginning.toString(IntervalSide.BEGINNING),
                mEnding.toString(IntervalSide.ENDING)));
    } // end of toString()

    /**
     * <img src="doc-files/equals.png" alt="equals">:
     * Returns {@code true} if:
     * <ul>
     *   <li>
     *     {@code o} is the same {@code EfsInterval} instance as
     *     {@code this} interval or
     *   </li>
     *   <li>
     *     {@code o} is a non-{@code null EfsInterval} instance
     *     with the same end points and clusivities as
     *     {@code this EfsInterval} instance.
     *   </li>
     * </ul>
     * Otherwise returns {@code false}.
     * @param o comparison object.
     * @return {@code true} if {@code o} is a
     * non-{@code null EfsInterval} instance whose end points are
     * equal and have the same clusivities.
     */
    @Override
    public boolean equals(final Object o)
    {
        boolean retcode = (this == o);

        if (!retcode && o instanceof EfsInterval)
        {
            final EfsInterval interval = (EfsInterval) o;

            retcode = (mBeginning.equals(interval.mBeginning) &&
                       mEnding.equals(interval.mEnding));
        }

        return (retcode);
    } // end of equals(Object)

    /**
     * Returns hash code based on end point times and
     * clusivities.
     * @return hash code based on end point times and
     * clusivities.
     */
    @Override
    public int hashCode()
    {
        return (Objects.hash(mBeginning, mEnding));
    } // end of hashCode()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns interval begin endpoint.
     * @return interval begin endpoint.
     */
    public EfsIntervalEndpoint beginning()
    {
        return (mBeginning);
    } // end of beginning()

    /**
     * Returns interval ending endpoint.
     * @return interval ending endpoint.
     */
    public EfsIntervalEndpoint ending()
    {
        return (mEnding);
    } // end of ending()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Returns a new {@code EfsInterval} builder instance which
     * is used to create an {@code EfsInterval} object. It is
     * recommended that a new builder be used for each new
     * {@code EfsInterval} instance and not re-use the same
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
     * {@code EfsInterval} instances may be created only by using
     * a {@code Builder} instance. A {@code Builder} instance is
     * obtained by calling {@link #builder()} which returns a
     * newly instantiated {@code Builder} instance. It is
     * recommended that a new {@code Builder} instance be used to
     * create an interval rather than re-using a builder instance
     * to create a previous interval.
     */
    public static final class Builder
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Constants.
        //

        /**
         * When attempting to set interval's beginning endpoint
         * to {@code null}, then {@code NullPointerException}
         * contains message {@value}.
         */
        public static final String BEGINNING_ENDPOINT_NULL =
            "beginning endpoint is null";

        /**
         * When attempting to set interval's ending endpoint
         * to {@code null}, then {@code NullPointerException}
         * contains message {@value}.
         */
        public static final String ENDING_ENDPOINT_NULL =
            "ending endpoint is null";

        /**
         * When attempting to set interval with a beginning
         * endpoint &gt; ending endpoint, then
         * {@code ValidationException} contains message {@value}.
         */
        public static final String BEGINNING_GREATER_THAN_ENDING =
            "beginning > ending";

        //-------------------------------------------------------
        // Locals.
        //

        private EfsIntervalEndpoint mBeginning;
        private EfsIntervalEndpoint mEnding;

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
        // Set Methods.
        //

        /**
         * Sets interval beginning endpoint. Returns
         * {@code this Builder} instance so that builder method
         * calls can be chained.
         * @param ep interval beginning endpoint.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code ep} is {@code null}.
         *
         * @see #ending(EfsIntervalEndpoint)
         */
        public Builder beginning(final EfsIntervalEndpoint ep)
        {
            mBeginning =
                Objects.requireNonNull(
                    ep, BEGINNING_ENDPOINT_NULL);

            return (this);
        } // end of beginning(EfsIntervalEndpoint)

        /**
         * Sets interval ending endpoint. Returns
         * {@code this Builder} instance so that builder method
         * calls can be chained.
         * @param ep interval ending endpoint.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code } is {@code null}.
         */
        public Builder ending(final EfsIntervalEndpoint ep)
        {
            mEnding =
                Objects.requireNonNull(ep, ENDING_ENDPOINT_NULL);

            return (this);
        } // end of endTime(EfsIntervalEndpoint)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Returns a new interval instance based on this
         * builder's settings.
         * @return new interval instance.
         * @throws ValidationException
         * if this builder contains an incomplete or incorrect
         * interval settings.
         */
        @SuppressWarnings ({"java:S1067"})
        public EfsInterval build()
            throws ValidationException
        {
            final Validator problems = new Validator();

            problems.requireNotNull(mBeginning, "beginning")
                    .requireNotNull(mEnding, "ending")
                    .requireTrue(mBeginning == null ||
                                 mEnding == null ||
                                 compareEndpoints(mBeginning,
                                                  mEnding) <= 0,
                                 "beginning",
                                 BEGINNING_GREATER_THAN_ENDING)
                    .throwException(EfsInterval.class);

            return (new EfsInterval(this));
        } // end of build()

        /**
         * Returns integer value &lt;, equal to, or &gt; zero if
         * {@code begin} is &lt;, equal to, &gt; {@code end}.
         * @param begin beginning endpoint.
         * @param end ending endpoint.
         * @return integer value &lt;, equal to, or &gt; zero.
         */
        public int compareEndpoints(@Nonnull final EfsIntervalEndpoint begin,
                                    @Nonnull final EfsIntervalEndpoint end)
        {
            final EndpointType beginType = begin.endpointType();
            final int retval;

            // Are these endpoint of different types?
            if (beginType != end.endpointType())
            {
                // Yes, these different types.
                retval =
                    EfsIntervalEndpoint.compareEndpoints(
                        begin, end);
            }
            else if (beginType == EndpointType.FIXED_TIME)
            {
                final EfsTimeEndpoint tBegin =
                    (EfsTimeEndpoint) begin;
                final EfsTimeEndpoint tEnd =
                    (EfsTimeEndpoint) end;

                retval = tBegin.compareTo(tEnd);
            }
            else if (beginType == EndpointType.TIME_OFFSET)
            {
                final EfsDurationEndpoint dBegin =
                    (EfsDurationEndpoint) begin;
                final EfsDurationEndpoint dEnd =
                    (EfsDurationEndpoint) end;

                retval = dBegin.compareTo(dEnd);
            }
            else
            {
                final EfsIndexEndpoint iBegin =
                    (EfsIndexEndpoint) begin;
                final EfsIndexEndpoint iEnd =
                    (EfsIndexEndpoint) end;

                retval = iBegin.compareTo(iEnd);
            }

            return (retval);
        } // end of compareEndpoints(...)
    } // end of class Builder
} // end of class EfsInterval
