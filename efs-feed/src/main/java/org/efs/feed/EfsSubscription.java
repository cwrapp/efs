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
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.efs.dispatcher.IEfsAgent;
import org.efs.event.IEfsEvent;
import org.efs.feed.EfsIntervalEndpoint.Clusivity;
import static org.efs.feed.EfsIntervalEndpoint.EndpointType.FIXED_TIME;
import static org.efs.feed.EfsIntervalEndpoint.EndpointType.TIME_OFFSET;
import org.efs.feed.EfsIntervalEndpoint.IntervalSide;


/**
 * {@link EfsFeed#subscribe(IEfsAgent, Consumer, EfsInterval)}
 * returns an {@code EfsSubscription} instance which may be used
 * to {@link #close()} the subscription.
 *
 * @param <E> efs event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsSubscription<E extends IEfsEvent>
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    //-----------------------------------------------------------
    // Statics.
    //

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Forward events to this subscribing agent.
     */
    private final IEfsAgent mSubscriber;

    /**
     * Forward events to this lambda callback.
     */
    private final Consumer<E> mCallback;

    /**
     * Subscribing to this event interval.
     */
    private final EfsInterval mInterval;

    /**
     * Set to {@code true} if this subscription is still active.
     * A subscription becomes inactive when it reaches interval
     * endpoint or is {@link #close() closed}.
     */
    private final AtomicBoolean mActiveFlag;

    /**
     * Used to determine if feed reached beginning of
     * subscription interval.
     */
    private ISubEndpoint mBeginning;

    /**
     * Used to determine if feed reached ending of subscription
     * interval.
     */
    private ISubEndpoint mEnding;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new subscription instance for given subscriber,
     * interval, and set active flag to {@code true}.
     * @param subscriber efs agent placing this subscription.
     * @param callback forward event to efs agent using this
     * callback.
     * @param interval subscription interval.
     */
    /* package */ EfsSubscription(final IEfsAgent subscriber,
                                  final Consumer<E> callback,
                                  final EfsInterval interval)
    {
        mSubscriber = subscriber;
        mCallback = callback;
        mInterval = interval;
        mActiveFlag = new AtomicBoolean(true);
    } // end of EfsSubscription(IEfsAgent, Consumer, EfsInterval)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns subscribing agent.
     * @return subscribing agent.
     */
    public IEfsAgent subscriber()
    {
        return (mSubscriber);
    } // end of subscriber()

    /**
     * Returns subscriber's event lambda callback.
     * @return event lambda callback.
     */
    public Consumer<E> eventCallback()
    {
        return (mCallback);
    } // end of eventCallback()

    /**
     * Returns subscription interval.
     * @return subscription interval.
     */
    public EfsInterval interval()
    {
        return (mInterval);
    } // end of interval()

    /**
     * Returns {@code true} if this subscription is active and
     * {@code false} if inactive.
     * @return {@code true} if subscription is active.
     */
    public boolean isActive()
    {
        return (mActiveFlag.get());
    } // end of isActive()

    /**
     * Returns integer value &lt;, equal to, or &gt; zero if
     * given event index or time (based on endpoint type) is
     * before, during, or after this subscription's interval.
     * @param index latest feed event index.
     * @param time latest feed event timestamp.
     * @return integer value &lt;, equal to, or &gt; zero.
     */
    public int compare(final int index,
                       final Instant time)
    {
        final int retval;

        if (mBeginning.compare(index, time) < 0)
        {
            retval = -1;
        }
        else if (mEnding.compare(index, time) > 0)
        {
            retval = 1;
        }
        else
        {
            retval = 0;
        }

        return (retval);
    } // end of compare(int, Instant)

    /**
     * Returns {@code true} if this subscription begins in the
     * past and {@code false} otherwise.
     * @param index  current feed index.
     * @param time current feed time.
     * @return {@code true} if subscription begins in the past.
     */
    public boolean beginsPast(final int index,
                              final Instant time)
    {
        return (mBeginning.inPast(index, time));
    } // end of beginPast(int, Instant)

    /**
     * Returns {@code true} if this subscription ends in the
     * future and {@code false} otherwise.
     * @param index current feed index.
     * @param time current feed time.
     * @return {@code true} if subscription ends in the future.
     */
    public boolean endsFuture(final int index,
                              final Instant time)
    {
        return (mEnding.inFuture(index, time));
    } // end of endsFuture(int, Instant)

    /**
     * Returns beginning index value based on either the
     * beginning endpoint index or time and clusivity.
     * <p>
     * This method is called only if this subscription begins
     * in the past.
     * </p>
     * @param index current feed index.
     * @param eventIndex timestamp to feed index mapping.
     * @return subscription beginning index.
     */
    /* package */ int beginningIndex(final int index,
                                     final SortedMap<Instant, EfsFeed.EventIndexEntry> eventIndex)
    {
        final int retval;

        // Is this an index-based endpoint?
        if (mBeginning instanceof IndexEndpoint)
        {
            // Yes, then use the raw index.
            retval = beginningIndexByIndex();
        }
        // This is a time-based endpoint.
        else
        {
            retval = beginningIndexByTime(index, eventIndex);
        }

        return (retval);
    } // end of beginningIndex(int, SortedMap<>)

    /**
     * Returns ending index value based on either the ending
     * endpoint index or time and clusivity.
     * <p>
     * This method may be called only if the subscription ends
     * in the past. It is possible this subscription ends in the
     * future.
     * </p>
     * @param feedIndex current feed index.
     * @param feedTime current feed time.
     * @param eventIndex timestamp to feed index mapping.
     * @return subscription ending index.
     */
    /* package */ int endingIndex(final int feedIndex,
                                  final SortedMap<Instant, EfsFeed.EventIndexEntry> eventIndex)
    {
        final int retval;

        // Is this an index-based endpoint?
        if (mEnding instanceof IndexEndpoint)
        {
            retval = endingIndexByIndex(feedIndex);
        }
        // this is a time-based endpoint.
        else
        {
            retval = endingIndexByTime(feedIndex, eventIndex);
        }

        return (retval);
    } // end of endingIndex(int, SortedMap<>)

    /**
     * Returns beginning interval index based on the beginning
     * endpoint index and clusivity. If beginning endpoint is
     * exclusive, the returns index value that is one greater
     * than endpoint index.
     * @return beginning interval index.
     */
    private int beginningIndexByIndex()
    {
        final IndexEndpoint ep = (IndexEndpoint) mBeginning;
        int retval = ep.mIndex;

        // Is this beginning endpoint exclusive?
        if (ep.mClusivity == Clusivity.EXCLUSIVE)
        {
            // Yes, then add one to the index.
            ++retval;
        }

        return (retval);
    } // end of beginningIndexByIndex()

    /**
     * Returns beginning index interval based on the beginning
     * endpoint time. If beginning endpoint time does not refer
     * to any past events, then returns either {@code index} or
     * {@code index + 1} based on endpoint clusivity.
     * <p>
     * Otherwise returns indexed event initial index if inclusive
     * and indexed event final index plus one.
     * </p>
     * @param index current feed index.
     * @param eventIndex event time to event index mapping.
     * @return beginning index based on time.
     */
    private int beginningIndexByTime(final int index,
                                     final SortedMap<Instant, EfsFeed.EventIndexEntry> eventIndex)
    {
        final TimeEndpoint ep = (TimeEndpoint) mBeginning;
        final Instant time = ep.mTime;
        final Clusivity clusivity = ep.mClusivity;
        final SortedMap<Instant, EfsFeed.EventIndexEntry> tailIndex =
            eventIndex.tailMap(time);
        int retval;

        // Are there any entries for this beginning index?
        if (tailIndex == null)
        {
            // Set beginning index to current feed index.
            // Note: since the caller has already determined
            // this subscription starts in the past, there
            // is no need to check clusivity.
            retval = index;
        }
        // Yes, there are entries for this time index.
        else
        {
            final EfsFeed.EventIndexEntry indexEntry =
                (tailIndex.firstEntry()).getValue();

            // Is this endpoint inclusive?
            if (clusivity == Clusivity.INCLUSIVE)
            {
                // Yes. Use the index beginning index.
                retval = indexEntry.beginIndex();
            }
            // No, exclusive. Use the index after the index
            // entry end.
            else
            {
                retval = ((indexEntry.endIndex()) + 1);
            }
        }

        return (retval);
    } // end of beginningIndexByTime(int, SortedMap<>)

    /**
     * Returns ending interval index based on the ending endpoint
     * index and clusivity. If ending endpoint is
     * exclusive, the returns index value that is one less than
     * endpoint index.
     * @param index current feed index.
     * @return beginning interval index.
     */
    private int endingIndexByIndex(final int index)
    {
        final IndexEndpoint ep = (IndexEndpoint) mEnding;
        int retval;

        // Does this subscription end in the future?
        if (ep.mIndex > index)
        {
            // Yes. Return the current index.
            retval = index;
        }
        // No, ends at current index or before.
        else
        {
            retval = ep.mIndex;

            // Is this ending endpoint exclusive?
            if (ep.mClusivity == Clusivity.EXCLUSIVE)
            {
                // Yes, then subtract one from the index.
                --retval;
            }
        }

        return (retval);
    } // end of endingIndexByIndex(int)

    /**
     * Returns ending index interval based on the ending endpoint
     * time. If ending endpoint time does not refer to any past
     * events, then returns either {@code index} or
     * {@code index - 1} based on endpoint clusivity.
     * <p>
     * Otherwise returns indexed event final index if inclusive
     * and indexed event final index minus one.
     * </p>
     * @param feedIndex current feed index.
     * @param feedTime current feed time.
     * @param eventIndex event time to event index mapping.
     * @return ending index based on time.
     */
    private int endingIndexByTime(final int feedIndex,
                                  final SortedMap<Instant, EfsFeed.EventIndexEntry> eventIndex)
    {
        final TimeEndpoint ep = (TimeEndpoint) mEnding;
        final Clusivity clusivity = ep.mClusivity;
        final Instant firstKey = eventIndex.firstKey();
        final Instant lastKey = eventIndex.lastKey();
        // SortedMap.headMap returns entries strictly less than
        // time. So if ending endpoint is inclusive, then add one
        // nanosecond to time so keys == endpoint time are
        // retrieved.
        final Instant time = (clusivity == Clusivity.INCLUSIVE ?
                              (ep.mTime).plusNanos(1L) :
                              ep.mTime);
        int retval;

        // Does this subscription end *before* the first event?
        if (time.compareTo(firstKey) < 0)
        {
            // Yes. Return -1 to signify that this interval does
            // not match any existing events.
            retval = -1;
        }
        // Does this subscription end after the last event?
        else if (time.compareTo(lastKey) > 0)
        {
            // Yes. Return current feed index.
            retval = feedIndex;
        }
        // No, subscription ends at current time or before.
        else
        {
            final SortedMap<Instant, EfsFeed.EventIndexEntry> headIndex =
                eventIndex.headMap(time);
                final EfsFeed.EventIndexEntry indexEntry =
                    (headIndex.lastEntry()).getValue();

            // Is this endpoint inclusive?
            if (clusivity == Clusivity.INCLUSIVE)
            {
                // Yes. Use the index ending index.
                retval = indexEntry.endIndex();
            }
            // No, exclusive. Use index before index entry
            // beginning.
            else
            {
                retval = (indexEntry.beginIndex() - 1);
            }
        }

        return (retval);
    } // end of endingIndexByTime(int, Instant, SortedMap<>)

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Marks this subscription as inactive. This does
     * <em>not</em> mean a subscribing agent will cease receiving
     * events after this call as there may be in-flight events
     * yet to be delivered to subscriber.
     * <p>
     * Once a subscription is closed it cannot be re-opened. A
     * new subscription must be made in order to receive feed
     * events again.
     * </p>
     */
    public void close()
    {
        mActiveFlag.set(false);
    } // end of close()

    /**
     * Sets subscription endpoints based on current index and
     * time.
     * @param index current index.
     * @param now current time
     */
    /* package */ void setEndpoints(final int index,
                                    final Instant now)
    {
        mBeginning = createEndpoint(mInterval.beginning(),
                                    IntervalSide.BEGINNING,
                                    index,
                                    now);
        mEnding = createEndpoint(mInterval.ending(),
                                 IntervalSide.ENDING,
                                 index,
                                 now);
    } // end of setEndpoints(int, Instant)

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    private ISubEndpoint createEndpoint(final EfsIntervalEndpoint ep,
                                        final IntervalSide side,
                                        final int index,
                                        final Instant now)
    {
        return (
            switch (ep.endpointType())
            {
                case FIXED_TIME ->
                    createEndpoint((EfsTimeEndpoint) ep, side);
                case TIME_OFFSET ->
                    createEndpoint(
                        (EfsDurationEndpoint) ep, side, now);
                default ->
                    createEndpoint(
                        (EfsIndexEndpoint) ep, side, index);
            });
    } // end of createEndpoint(...)

    /**
     * Creates endpoint based on fixed time interval endpoint.
     * @param ep fixed time endpoint.
     * @param side interval side.
     * @return time endpoint.
     */
    private ISubEndpoint createEndpoint(final EfsTimeEndpoint ep,
                                        final IntervalSide side)
    {
        return (
            new TimeEndpoint(ep.time(), ep.clusivity(), side));
    } // end of createEndpoint(EfsTimeEndpoint, IntervalSide)

    private ISubEndpoint createEndpoint(final EfsDurationEndpoint ep,
                                        final IntervalSide side,
                                        final Instant now)
    {
        final Instant time = now.plus(ep.timeOffset());

        return (new TimeEndpoint(time, ep.clusivity(), side));
    } // end of createEndpoint(...)

    private ISubEndpoint createEndpoint(final EfsIndexEndpoint ep,
                                        final IntervalSide side,
                                        final int index)
    {
        final int intervalIndex = (index + ep.indexOffset());

        return (
            new IndexEndpoint(
                intervalIndex, ep.clusivity(), side));
    } // end of createEndpoint(EfsIndexEndpoint,IntervalSide,int)

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Index and time subscription endpoints implement this
     * interface to determine
     */
    private static interface ISubEndpoint
    {
    //-----------------------------------------------------------
    // Member data.
    //

    //-----------------------------------------------------------
    // Member methods.
    //

        /**
         * Returns integer value &lt;, equal to, or &gt; zero
         * depending on whether feed index or time is &lt;, eaual
         * to, or &gt; endpoint setting.
         * @param index current feed index.
         * @param time current feed time.
         * @return integer value &lt;, equal to, or &gt; zero.
         */
        int compare(final int index,
                    final Instant time);

        /**
         * Returns {@code true} if this endpoint is in the
         * past based on the current feed index and time;
         * {@code false} otherwise.
         * @param index  current feed index.
         * @param time current feed time.
         * @return {@code true} if endpoint is in the past.
         */
        boolean inPast(final int index,
                       final Instant time);

        /**
         * Returns {@code true} if this endpoint is in the
         * future based on the current feed index and time;
         * {@code false} otherwise.
         * @param index current feed index.
         * @param time current feed time.
         * @return {@code true} if endpoint is in the future.
         */
        boolean inFuture(final int index,
                         final Instant time);
    } // end of interface ISubEndpoint

    /**
     * Subscription interval endpoint based on feed time.
     */
    private static final class TimeEndpoint
        implements ISubEndpoint
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Endpoint beginning or ending time. Note that this is a
         * fixed time set when {@code EfsFeed} processed the
         * subscription.
         */
        private final Instant mTime;

        /**
         * Endpoint either includes or excludes {@link #mTime}.
         */
        private final Clusivity mClusivity;

        /**
         * Endpoint is on this interval side.
         */
        private final IntervalSide mIntervalSide;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private TimeEndpoint(final Instant time,
                             final Clusivity clusivity,
                             final IntervalSide side)
        {
            mTime = time;
            mClusivity = clusivity;
            mIntervalSide = side;
        } // end of TimeEndpoint(Instant,Clusivity,IntervalSide)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // ISubEndpoint Interface Implementation.
        //

        /**
         * Returns integer value &lt;, equal to, or &gt; zero if
         * this endpoint's time is &lt;, equal to, or &gt;
         * given time.
         * @param index current feed index.
         * @param time current feed time;
         * @return integer value &lt;, equal to, or &gt; zero.
         */
        @Override
        public int compare(final int index,
                           final Instant time)
        {
            int retval = time.compareTo(mTime);

            // Is time right on the endpoint?
            // Is this endpoint exclusive?
            if (retval == 0 &&
                mClusivity == Clusivity.EXCLUSIVE)
            {
                // This is index/time is outside of the interval.
                retval =
                    (mIntervalSide == IntervalSide.BEGINNING ?
                     -1 :
                     1);
            }

            return (retval);
        } // end of compare(int, Instant)

        /**
         * Returns {@code true} if endpoint time is &le; given
         * time.
         * @param index current feed index.
         * @param time current feed time.
         * @return {@code true} if endpoint time is &le; given
         * time.
         */
        @Override
        public boolean inPast(final int index,
                              final Instant time)
        {
            final int compare = mTime.compareTo(time);

            return ((compare < 0) ||
                    (compare == 0 &&
                     mClusivity == Clusivity.INCLUSIVE));
        } // end of inPast(

        /**
         * Returns {@code true} if endpoint time is &gt; given
         * time.
         * @param index current feed index.
         * @param time current feed time.
         * @return {@code true} if endpoint time is &gt; given
         * time.
         */
        @Override
        public boolean inFuture(final int index,
                                final Instant time)
        {
            return (mTime.compareTo(time) > 0);
        } // end of inFuture(int, Instant)

        //
        // end of ISubEndpoint Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        @Override
        public String toString()
        {
            return (
                String.format(
                    "[side=%s, time=%s, clusivity=%s]",
                    mIntervalSide,
                    mTime,
                    mClusivity));
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------
    } // end of class TimeEndpoint

    /**
     * Subscription interval endpoint based on feed index.
     */
    private static final class IndexEndpoint
        implements ISubEndpoint
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Endpoint beginning or ending feed index.
         */
        private final int mIndex;

        /**
         * Endpoint either includes or excludes {@link #mIndex}.
         */
        private final Clusivity mClusivity;

        /**
         * Endpoint is on this interval side.
         */
        private final IntervalSide mIntervalSide;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private IndexEndpoint(final int index,
                            final Clusivity clusivity,
                            final IntervalSide side)
        {
            mIndex = index;
            mClusivity = clusivity;
            mIntervalSide = side;
        } // end of IndexEndpoint(int, Clusivity, IntervalSide)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // ISubEndpoint Interface Implementation.
        //

        /**
         * Returns integer value &lt;, equal to, or &gt; zero if
         * this endpoint's index is &lt;, equal to, or &gt;
         * given index.
         * @param index current feed index.
         * @param time current feed time;
         * @return integer value &lt;, equal to, or &gt; zero.
         */
        @Override
        public int compare(final int index,
                           final Instant time)
        {
            int retval = (index - mIndex);

            // Is index right on the endpoint?
            // Is this endpoint exclusive?
            if (retval == 0 &&
                mClusivity == Clusivity.EXCLUSIVE)
            {
                // This is index is outside of the interval.
                retval =
                    (mIntervalSide == IntervalSide.BEGINNING ?
                     -1 :
                     1);
            }

            return (retval);
        } // end of compare(int, Instant)

        /**
         * Returns {@code true} if endpoint index is &le; given
         * index.
         * @param index current feed index.
         * @param time current feed time.
         * @return {@code true} if endpoint index is &le; given
         * index.
         */
        @Override
        public boolean inPast(final int index,
                              final Instant time)
        {
            final int compare = (index - mIndex);

            return ((mIndex < index) ||
                    (mIndex == index &&
                     mClusivity == Clusivity.INCLUSIVE));
        } // end of inPast(int, Instant)

        /**
         * Returns {@code true} if endpoint index is &gt; given
         * index.
         * @param index current feed index.
         * @param time current feed time.
         * @return {@code true} if endpoint index is &gt; given
         * index.
         */
        @Override
        public boolean inFuture(final int index,
                                final Instant time)
        {
            return (mIndex > index);
        } // end of inFuture(int, Instant)

        //
        // end of ISubEndpoint Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        @Override
        public String toString()
        {
            return (
                String.format(
                    "[side=%s, index=%,d, clusivity=%s]",
                    mIntervalSide,
                    mIndex,
                    mClusivity));
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------
    } // end of class IndexEndpoint
} // end of class EfsSubscription
