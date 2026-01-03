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

import jakarta.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import org.efs.dispatcher.IEfsAgent;
import org.efs.event.IEfsEvent;
import org.efs.feed.EfsIntervalEndpoint.Clusivity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public class EfsSubscriptionTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String AGENT_NAME_PREFIX =
        "Subscriber-";
    private static final long TIME_DELTA = 5L;

    //-----------------------------------------------------------
    // Statics.
    //

    private static int sAgentIndex = 0;

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Subscriber making subscription.
     */
    private TestAgent mSubscriber;

    private Instant mCurrentTime;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void setUpClass()
    {
    } // end of setUpClass()

    @AfterAll
    public static void tearDownClass()
    {
    }

    @BeforeEach
    public void setUp()
    {
        final String agentName =
            AGENT_NAME_PREFIX + sAgentIndex++;

        mSubscriber = new TestAgent(agentName);
        mCurrentTime = Instant.now();
    } // end of setUp()

    @AfterEach
    public void tearDown()
    {
    }

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void createSubscription()
    {
        final Clusivity beginClusivity = Clusivity.INCLUSIVE;
        final Clusivity endClusivity = Clusivity.EXCLUSIVE;
        final int beginIndex = -100;
        final int endIndex = 100;
        final int currentIndex = 500;
        final EfsInterval interval =
            createInterval(
                createEndpoint(beginIndex, beginClusivity),
                createEndpoint(endIndex, endClusivity));
        final Consumer<TestEvent> callback =
            mSubscriber::onTestEvent;
        final EfsSubscription<TestEvent> subscription =
            new EfsSubscription<>(
                mSubscriber, callback, interval);

        subscription.setEndpoints(currentIndex, mCurrentTime);

        assertThat(subscription.subscriber())
            .isSameAs(mSubscriber);
        assertThat(subscription.eventCallback())
            .isSameAs(callback);
        assertThat(subscription.interval()).isEqualTo(interval);
        assertThat(subscription.isActive()).isTrue();
        assertThat(subscription.compare(
                       ((currentIndex + beginIndex) - 1),
                       mCurrentTime))
            .isLessThan(0);
        assertThat(subscription.compare(
                       (currentIndex + beginIndex), mCurrentTime))
            .isZero();
        assertThat(subscription.compare(
                       currentIndex, mCurrentTime))
            .isZero();
        assertThat(subscription.compare(
                       ((currentIndex + endIndex) - 1),
                       mCurrentTime))
            .isZero();
        assertThat(subscription.compare(
                       (currentIndex + endIndex), mCurrentTime))
            .isGreaterThan(0);
        assertThat(subscription.beginsPast(currentIndex,
                                           mCurrentTime))
            .isTrue();
        assertThat(subscription.endsFuture(currentIndex,
                                           mCurrentTime))
            .isTrue();

        subscription.close();

        assertThat(subscription.isActive()).isFalse();
    } // end of createSubscription()

    @Test
    public void subscriptionInPastIndexExclusive()
    {
        final Clusivity clusivity = Clusivity.EXCLUSIVE;
        final int beginIndex = -300;
        final int endIndex = -100;
        final int currentIndex = 500;
        final EfsInterval interval =
            createInterval(
                createEndpoint(beginIndex, clusivity),
                createEndpoint(endIndex, clusivity));
        final Consumer<TestEvent> callback =
            mSubscriber::onTestEvent;
        final EfsSubscription<TestEvent> subscription =
            new EfsSubscription<>(
                mSubscriber, callback, interval);

        subscription.setEndpoints(currentIndex, mCurrentTime);

        assertThat(subscription.compare(
                       (currentIndex + beginIndex),
                       mCurrentTime))
            .isLessThan(0);
        assertThat(subscription.compare(
                       ((currentIndex + beginIndex) + 1),
                       mCurrentTime))
            .isZero();
        assertThat(subscription.compare(
                       ((currentIndex + endIndex) - 1),
                       mCurrentTime))
            .isZero();
        assertThat(subscription.compare(
                       (currentIndex + endIndex),
                       mCurrentTime))
            .isGreaterThan(0);
        assertThat(subscription.beginsPast(currentIndex,
                                           mCurrentTime))
            .isTrue();
        assertThat(subscription.endsFuture(currentIndex,
                                           mCurrentTime))
            .isFalse();
    } // end of subscriptionInPastIndexExclusive()

    @Test
    public void subscriptionInPastIndexInclusive()
    {
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final int beginIndex = -300;
        final int endIndex = -100;
        final int currentIndex = 500;
        final EfsInterval interval =
            createInterval(
                createEndpoint(beginIndex, clusivity),
                createEndpoint(endIndex, clusivity));
        final Consumer<TestEvent> callback =
            mSubscriber::onTestEvent;
        final EfsSubscription<TestEvent> subscription =
            new EfsSubscription<>(
                mSubscriber, callback, interval);

        subscription.setEndpoints(currentIndex, mCurrentTime);

        assertThat(subscription.compare(
                       ((currentIndex + beginIndex) - 1),
                       mCurrentTime))
            .isLessThan(0);
        assertThat(subscription.compare(
                       (currentIndex + beginIndex),
                       mCurrentTime))
            .isZero();
        assertThat(subscription.compare(
                       (currentIndex + endIndex),
                       mCurrentTime))
            .isZero();
        assertThat(subscription.compare(
                       ((currentIndex + endIndex) + 1),
                       mCurrentTime))
            .isGreaterThan(0);
        assertThat(subscription.beginsPast(currentIndex,
                                           mCurrentTime))
            .isTrue();
        assertThat(subscription.endsFuture(currentIndex,
                                           mCurrentTime))
            .isFalse();
    } // end of subscriptionInPastIndexInclusive()

    @Test
    public void subscriptionInFutureIndexExclusive()
    {
        final Clusivity clusivity = Clusivity.EXCLUSIVE;
        final int beginIndex = 10;
        final int endIndex = 100;
        final int currentIndex = 500;
        final EfsInterval interval =
            createInterval(
                createEndpoint(beginIndex, clusivity),
                createEndpoint(endIndex, clusivity));
        final Consumer<TestEvent> callback =
            mSubscriber::onTestEvent;
        final EfsSubscription<TestEvent> subscription =
            new EfsSubscription<>(
                mSubscriber, callback, interval);

        subscription.setEndpoints(currentIndex, mCurrentTime);

        assertThat(subscription.compare(
                       (currentIndex + beginIndex),
                       mCurrentTime))
            .isLessThan(0);
        assertThat(subscription.compare(
                       ((currentIndex + beginIndex) + 1),
                       mCurrentTime))
            .isZero();
        assertThat(subscription.compare(
                       ((currentIndex + endIndex) - 1),
                       mCurrentTime))
            .isZero();
        assertThat(subscription.compare(
                       (currentIndex + endIndex),
                       mCurrentTime))
            .isGreaterThan(0);
        assertThat(subscription.beginsPast(currentIndex,
                                           mCurrentTime))
            .isFalse();
        assertThat(subscription.endsFuture(currentIndex,
                                           mCurrentTime))
            .isTrue();
    } // end of subscriptionInFutureIndexExclusive()

    @Test
    public void subscriptionInFutureIndexInclusive()
    {
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final int beginIndex = 10;
        final int endIndex = 100;
        final int currentIndex = 500;
        final EfsInterval interval =
            createInterval(
                createEndpoint(beginIndex, clusivity),
                createEndpoint(endIndex, clusivity));
        final Consumer<TestEvent> callback =
            mSubscriber::onTestEvent;
        final EfsSubscription<TestEvent> subscription =
            new EfsSubscription<>(
                mSubscriber, callback, interval);

        subscription.setEndpoints(currentIndex, mCurrentTime);

        assertThat(subscription.compare(
                       ((currentIndex + beginIndex) - 1),
                       mCurrentTime))
            .isLessThan(0);
        assertThat(subscription.compare(
                       (currentIndex + beginIndex),
                       mCurrentTime))
            .isZero();
        assertThat(subscription.compare(
                       (currentIndex + endIndex),
                       mCurrentTime))
            .isZero();
        assertThat(subscription.compare(
                       ((currentIndex + endIndex) + 1),
                       mCurrentTime))
            .isGreaterThan(0);
        assertThat(subscription.beginsPast(currentIndex,
                                           mCurrentTime))
            .isFalse();
        assertThat(subscription.endsFuture(currentIndex,
                                           mCurrentTime))
            .isTrue();
    } // end of subscriptionInFutureIndexInclusive()

    @Test
    public void subscriptionInPastTimeExclusive()
    {
        final Clusivity clusivity = Clusivity.EXCLUSIVE;
        final long beginDelta = -30L;
        final long endDelta = -10L;
        final int currentIndex = 500;
        final EfsInterval interval =
            createInterval(
                createEndpoint(beginDelta, clusivity),
                createEndpoint(endDelta, clusivity));
        final Consumer<TestEvent> callback =
            mSubscriber::onTestEvent;
        final EfsSubscription<TestEvent> subscription =
            new EfsSubscription<>(
                mSubscriber, callback, interval);

        subscription.setEndpoints(currentIndex, mCurrentTime);

        assertThat(
            subscription.compare(
                currentIndex, mCurrentTime.minusSeconds(35L)))
            .isLessThan(0);
        assertThat(
            subscription.compare(
                currentIndex, mCurrentTime.plusSeconds(beginDelta + 1L)))
            .isZero();
        assertThat(
            subscription.compare(
                currentIndex, mCurrentTime.plusSeconds(endDelta - 1L)))
            .isZero();
        assertThat(
            subscription.compare(
                currentIndex, mCurrentTime.minusSeconds(5L)))
            .isGreaterThan(0);
        assertThat(
            subscription.beginsPast(currentIndex, mCurrentTime))
            .isTrue();
        assertThat(
            subscription.endsFuture(currentIndex, mCurrentTime))
            .isFalse();
    } // end of subscriptionInPastTimeExclusive()

    @Test
    public void subscriptionInPastTimeInclusive()
    {
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final long beginDelta = -30L;
        final long endDelta = -10L;
        final int currentIndex = 500;
        final EfsInterval interval =
            createInterval(
                createEndpoint(beginDelta, clusivity),
                createEndpoint(endDelta, clusivity));
        final Consumer<TestEvent> callback =
            mSubscriber::onTestEvent;
        final EfsSubscription<TestEvent> subscription =
            new EfsSubscription<>(
                mSubscriber, callback, interval);

        subscription.setEndpoints(currentIndex, mCurrentTime);

        assertThat(
            subscription.compare(
                currentIndex, mCurrentTime.minusSeconds(35L)))
            .isLessThan(0);
        assertThat(
            subscription.compare(
                currentIndex,
                mCurrentTime.plusSeconds(beginDelta)))
            .isZero();
        assertThat(
            subscription.compare(
                currentIndex,
                mCurrentTime.plusSeconds(endDelta)))
            .isZero();
        assertThat(
            subscription.compare(
                currentIndex, mCurrentTime.minusSeconds(5L)))
            .isGreaterThan(0);
        assertThat(subscription.beginsPast(0, mCurrentTime))
            .isTrue();
        assertThat(subscription.endsFuture(0, mCurrentTime))
            .isFalse();
    } // end of subscriptionInPastTimeInclusive()

    @Test
    public void subscriptionInFutureTimeExclusive()
    {
        final Clusivity clusivity = Clusivity.EXCLUSIVE;
        final long beginDelta = 10L;
        final long endDelta = 30L;
        final int currentIndex = 500;
        final EfsInterval interval =
            createInterval(
                createEndpoint(beginDelta, clusivity),
                createEndpoint(endDelta, clusivity));
        final Consumer<TestEvent> callback =
            mSubscriber::onTestEvent;
        final EfsSubscription<TestEvent> subscription =
            new EfsSubscription<>(
                mSubscriber, callback, interval);

        subscription.setEndpoints(currentIndex, mCurrentTime);

        assertThat(
            subscription.compare(
                currentIndex, mCurrentTime.plusSeconds(5L)))
            .isLessThan(0);
        assertThat(
            subscription.compare(
                currentIndex,
                mCurrentTime.plusSeconds(beginDelta + 1L)))
            .isZero();
        assertThat(
            subscription.compare(
                currentIndex,
                mCurrentTime.plusSeconds(endDelta - 1L)))
            .isZero();
        assertThat(
            subscription.compare(
                currentIndex, mCurrentTime.plusSeconds(35L)))
            .isGreaterThan(0);
        assertThat(
            subscription.beginsPast(currentIndex, mCurrentTime))
            .isFalse();
        assertThat(
            subscription.endsFuture(currentIndex, mCurrentTime))
            .isTrue();
    } // end of subscriptionInFutureTimeExclusive()

    @Test
    public void subscriptionInFutureTimeInclusive()
    {
        final Clusivity clusivity = Clusivity.INCLUSIVE;
        final long beginDelta = 10L;
        final long endDelta = 30L;
        final int currentIndex = 500;
        final EfsInterval interval =
            createInterval(
                createEndpoint(beginDelta, clusivity),
                createEndpoint(endDelta, clusivity));
        final Consumer<TestEvent> callback =
            mSubscriber::onTestEvent;
        final EfsSubscription<TestEvent> subscription =
            new EfsSubscription<>(
                mSubscriber, callback, interval);

        subscription.setEndpoints(currentIndex, mCurrentTime);

        assertThat(
            subscription.compare(
                currentIndex, mCurrentTime.plusSeconds(5L)))
            .isLessThan(0);
        assertThat(
            subscription.compare(
                currentIndex,
                mCurrentTime.plusSeconds(beginDelta)))
            .isZero();
        assertThat(
            subscription.compare(
                currentIndex,
                mCurrentTime.plusSeconds(endDelta)))
            .isZero();
        assertThat(
            subscription.compare(
                currentIndex, mCurrentTime.plusSeconds(35L)))
            .isGreaterThan(0);
        assertThat(
            subscription.beginsPast(currentIndex, mCurrentTime))
            .isFalse();
        assertThat(
            subscription.endsFuture(currentIndex, mCurrentTime))
            .isTrue();
    } // end of subscriptionInFutureTimeInclusive()

    @Test
    public void beginningEndingIndexPastInterval()
    {
        final Clusivity beginClusivity = Clusivity.INCLUSIVE;
        final Clusivity endClusivity = Clusivity.EXCLUSIVE;
        final int numEvents = 15;
        final int beginIndex = -4;
        final int endIndex = -1;
        final int currentIndex = 5;
        final EfsInterval interval =
            createInterval(
                createEndpoint(beginIndex, beginClusivity),
                createEndpoint(endIndex, endClusivity));
        final Consumer<TestEvent> callback =
            mSubscriber::onTestEvent;
        final EfsSubscription<TestEvent> subscription =
            new EfsSubscription<>(
                mSubscriber, callback, interval);
        final SortedMap<Instant, EfsFeed.EventIndexEntry> eventIndex =
            createEventIndex(numEvents);
        final int beginningIndex;
        final int endingIndex;

        subscription.setEndpoints(currentIndex, mCurrentTime);

        beginningIndex =
            subscription.beginningIndex(
                currentIndex, eventIndex);
        endingIndex =
            subscription.endingIndex(currentIndex, eventIndex);

        assertThat(beginningIndex)
            .isEqualTo(currentIndex + beginIndex);
        assertThat(endingIndex)
            .isEqualTo((currentIndex + endIndex) - 1);
    } // end of beginningEndingIndexPastInterval()

    @Test
    public void beginningEndingIndexFutureInterval()
    {
        final Clusivity beginClusivity = Clusivity.EXCLUSIVE;
        final Clusivity endClusivity = Clusivity.INCLUSIVE;
        final int numEvents = 15;
        final int beginIndex = -2;
        final int endIndex = 3;
        final int currentIndex = 5;
        final EfsInterval interval =
            createInterval(
                createEndpoint(beginIndex, beginClusivity),
                createEndpoint(endIndex, endClusivity));
        final Consumer<TestEvent> callback =
            mSubscriber::onTestEvent;
        final EfsSubscription<TestEvent> subscription =
            new EfsSubscription<>(
                mSubscriber, callback, interval);
        final SortedMap<Instant, EfsFeed.EventIndexEntry> eventIndex =
            createEventIndex(numEvents);
        final int beginningIndex;
        final int endingIndex;

        subscription.setEndpoints(currentIndex, mCurrentTime);

        beginningIndex =
            subscription.beginningIndex(
                currentIndex, eventIndex);
        endingIndex =
            subscription.endingIndex(currentIndex, eventIndex);

        assertThat(beginningIndex)
            .isEqualTo(currentIndex + (beginIndex + 1));
        assertThat(endingIndex).isEqualTo(currentIndex);
    } // end of beginningEndingIndexFutureInterval()

    @Test
    public void beginningEndingTimePastInterval()
    {
        final Clusivity beginClusivity = Clusivity.INCLUSIVE;
        final Clusivity endClusivity = Clusivity.EXCLUSIVE;
        final int numEvents = 15;
        final int beginIndex = -10;
        final int endIndex = -2;
        final int currentIndex = (numEvents + 1);
        final EfsInterval interval =
            createInterval(
                createEndpoint(
                    (beginIndex * TIME_DELTA), beginClusivity),
                createEndpoint(
                    (endIndex * TIME_DELTA), endClusivity));
        final Consumer<TestEvent> callback =
            mSubscriber::onTestEvent;
        final EfsSubscription<TestEvent> subscription =
            new EfsSubscription<>(
                mSubscriber, callback, interval);
        final SortedMap<Instant, EfsFeed.EventIndexEntry> eventIndex =
            createEventIndex(numEvents);
        final int beginningIndex;
        final int endingIndex;

        subscription.setEndpoints(currentIndex, mCurrentTime);

        beginningIndex =
            subscription.beginningIndex(
                currentIndex, eventIndex);
        endingIndex =
            subscription.endingIndex(currentIndex, eventIndex);

        assertThat(beginningIndex)
            .isEqualTo((numEvents + beginIndex) + 1);
        assertThat(endingIndex)
            .isEqualTo((numEvents + endIndex) - 1);
    } // end of beginningEndingTimePastInterval()

    @Test
    public void beginningEndingTimeFutureInterval()
    {
        final Clusivity beginClusivity = Clusivity.INCLUSIVE;
        final Clusivity endClusivity = Clusivity.EXCLUSIVE;
        final int numEvents = 15;
        final int beginIndex = -5;
        final int endIndex = 5;
        final int currentIndex = (numEvents + 1);
        final EfsInterval interval =
            createInterval(
                createEndpoint(
                    (beginIndex * TIME_DELTA), beginClusivity),
                createEndpoint(
                    (endIndex * TIME_DELTA), endClusivity));
        final Consumer<TestEvent> callback =
            mSubscriber::onTestEvent;
        final EfsSubscription<TestEvent> subscription =
            new EfsSubscription<>(
                mSubscriber, callback, interval);
        final SortedMap<Instant, EfsFeed.EventIndexEntry> eventIndex =
            createEventIndex(numEvents);
        final int beginningIndex;
        final int endingIndex;

        subscription.setEndpoints(currentIndex, mCurrentTime);

        beginningIndex =
            subscription.beginningIndex(
                currentIndex, eventIndex);
        endingIndex =
            subscription.endingIndex(currentIndex, eventIndex);

        assertThat(beginningIndex)
            .isEqualTo((numEvents + beginIndex) + 1);
        assertThat(endingIndex).isEqualTo(currentIndex);
    } // end of beginningEndingTimeFutureInterval()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private static EfsInterval createInterval(final EfsIntervalEndpoint beginning,
                                              final EfsIntervalEndpoint ending)
    {
        final EfsInterval.Builder builder =
            EfsInterval.builder();

        return (builder.beginning(beginning)
                       .ending(ending)
                       .build());
    } // end of createInterval(...)

    private EfsDurationEndpoint createEndpoint(final long delta,
                                               final Clusivity clusivity)
    {
        final Duration offset = Duration.ofSeconds(delta);
        final EfsDurationEndpoint.Builder builder =
            EfsDurationEndpoint.builder();

        return (builder.timeOffset(offset, clusivity).build());
    } // end of createEndpoint(long, Clusivity)

    private EfsIndexEndpoint createEndpoint(final int offset,
                                            final Clusivity clusivity)
    {
        final EfsIndexEndpoint.Builder builder =
            EfsIndexEndpoint.builder();

        return (builder.indexOffset(offset, clusivity).build());
    } // end of createEndpoint(int, Clusivity)

    private SortedMap<Instant, EfsFeed.EventIndexEntry> createEventIndex(final int numEntries)
    {
        Instant timestamp =
            mCurrentTime.minusSeconds(
                (numEntries + 1) * TIME_DELTA);
        int index;
        EfsFeed.EventIndexEntry indexEntry;
        final SortedMap<Instant, EfsFeed.EventIndexEntry> retval =
            new TreeMap<>();

        // Fill in event index settings.
        for (index = 0;
             index < numEntries;
             ++index, timestamp = timestamp.plusSeconds(TIME_DELTA))
        {
            indexEntry =
                new EfsFeed.EventIndexEntry(timestamp, index);
            indexEntry.endIndex(index);
            retval.put(timestamp, indexEntry);
        }

        return (retval);
    } // end of createEventIndex(int, Instant)

//---------------------------------------------------------------
// Inner classes.
//

    private static final class TestAgent
        implements IEfsAgent
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Constants.
        //
        //-------------------------------------------------------
        // Statics.
        //

        //-------------------------------------------------------
        // Locals.
        //

        private final String mAgentName;
        private TestEvent mEvent;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private TestAgent(final String name)
        {
            mAgentName = name;
        } // end of TestAgent(String)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // IEfsAgent Interface Implemenation.
        //

        @Override
        public String name()
        {
            return (mAgentName);
        } // end of name()

        //
        // end of IEfsAgent Interface Implemenation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        @Nullable public TestEvent receivedEvent()
        {
            return (mEvent);
        } // end of receivedEvent()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //
        //
        // end of Set Methods.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Event Handler Methods.
        //

        private void onTestEvent(final TestEvent event)
        {
            mEvent = event;
        } // end of  onTestEvent(TestEvent)

        //
        // end of Event Handler Methods.
        //-------------------------------------------------------
    } // end of class TestAgent

    private static final class TestEvent
        implements IEfsEvent
    {
    //-----------------------------------------------------------
    // Member data.
    //

    //-----------------------------------------------------------
    // Member methods.
    //
    } // end of class TestEvent
} // end of class EfsSubscriptionTest