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

package org.efs.bus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.efs.bus.EfsEventBus.Advertisement;
import org.efs.bus.EfsEventBus.Subscription;
import org.efs.bus.EfsEventBus.WildcardAdvertisement;
import org.efs.bus.EfsEventBus.WildcardSubscription;
import org.efs.dispatcher.EfsDispatchTarget;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.EfsDispatcher.DispatcherType;
import org.efs.dispatcher.IEfsAgent;
import org.efs.dispatcher.config.ThreadType;
import org.efs.event.EfsTopicKey;
import org.efs.event.IEfsEvent;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.AssertionsKt.assertNotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Comprehensive unit tests for {@link EfsEventBus} with maximal
 * code coverage.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@ExtendWith(MockitoExtension.class)
@DisplayName("EfsEventBus Unit Tests")
public class EfsEventBusTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    private static final String PUBLISHER_NAME_0 =
        "MockPublisher";
    private static final String PUBLISHER_NAME_1 =
        "AnotherPublisher";
    private static final String SUBSCRIBER_NAME_0 =
        "MockSubscriber";
    private static final String SUBSCRIBER_NAME_1 =
        "AnotherSubscriber";
    private static final String UNREGISTERED_NAME =
        "unregistered";
    private static final String BUS_NAME_PREFIX = "test-bus-";
    private static final String TOPIC_0 = "test-topic";
    private static final String TOPIC_1 = "another-topic";
    private static final String TEST_DISPATCHER_NAME =
        "test-dispatcher";
    private static final String REGEX_TOPIC = "test-.*";

    //-----------------------------------------------------------
    // Statics.
    //

    private static IEfsAgent sMockPublisher;
    private static IEfsAgent sMockAnotherPublisher;
    private static IEfsAgent sMockSubscriber;
    private static IEfsAgent sMockAnotherSubscriber;

    //-----------------------------------------------------------
    // Locals.
    //

    private EfsEventBus mEventBus;
    private EfsTopicKey<TestEvent> mTestTopicKey;
    private EfsTopicKey<AnotherTestEvent> mAnotherTopicKey;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeAll
    public static void setUpClass()
    {
        final int numThreads = 1;
        final ThreadType threadType = ThreadType.BLOCKING;
        final int priority = 3;
        final DispatcherType dispatcherType = DispatcherType.EFS;
        final int eventQueueCapacity = 128;
        final int runQueueCapacity = 32;
        final int maxEvents = eventQueueCapacity;
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder(TEST_DISPATCHER_NAME);

        builder.numThreads(numThreads)
               .threadType(threadType)
               .priority(priority)
               .dispatcherType(dispatcherType)
               .eventQueueCapacity(eventQueueCapacity)
               .runQueueCapacity(runQueueCapacity)
               .maxEvents(maxEvents)
               .build();

        sMockPublisher = mock(IEfsAgent.class);
        sMockAnotherPublisher = mock(IEfsAgent.class);
        sMockSubscriber = mock(IEfsAgent.class);
        sMockAnotherSubscriber = mock(IEfsAgent.class);

        // Register mock agents with dispatcher
        when(sMockPublisher.name()).thenReturn(PUBLISHER_NAME_0);
        when(sMockAnotherPublisher.name())
            .thenReturn(PUBLISHER_NAME_1);
        when(sMockSubscriber.name()).thenReturn(SUBSCRIBER_NAME_0);
        when(sMockAnotherSubscriber.name())
            .thenReturn(SUBSCRIBER_NAME_1);

        EfsDispatcher.register(sMockPublisher,
                               TEST_DISPATCHER_NAME);
        EfsDispatcher.register(sMockAnotherPublisher,
                               TEST_DISPATCHER_NAME);
        EfsDispatcher.register(sMockSubscriber,
                               TEST_DISPATCHER_NAME);
        EfsDispatcher.register(sMockAnotherSubscriber,
                               TEST_DISPATCHER_NAME);
    } // end of setUpClass()

    @BeforeEach
    public void setUp()
    {
        // Create event bus and topic keys
        mEventBus =
            EfsEventBus.findOrCreateBus(
                BUS_NAME_PREFIX + System.nanoTime());
        mTestTopicKey =
            EfsTopicKey.getKey(TestEvent.class, TOPIC_0);
        mAnotherTopicKey =
            EfsTopicKey.getKey(AnotherTestEvent.class, TOPIC_1);
    } // end of setUp()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    // ========== Bus Creation and Management ==========

    @Nested
    @DisplayName("Bus Creation and Retrieval")
    public final class BusCreationTests
    {

        @Test
        @DisplayName("Should create bus with unique name")
        public void createBusWithUniqueNameTest()
        {
            final String name1 = "unique-bus-1";
            final String name2 = "unique-bus-2";
            final EfsEventBus bus1 =
                EfsEventBus.findOrCreateBus(name1);
            final EfsEventBus bus2 =
                EfsEventBus.findOrCreateBus(name2);

            assertThat(bus1).isNotNull();
            assertThat(bus2).isNotNull();
            assertThat(bus1).isNotSameAs(bus2);
            assertThat(bus1.busName()).isEqualTo(name1);
            assertThat(bus2.busName()).isEqualTo(name2);
        } // end of createBusWithUniqueNameTest()

        @Test
        @DisplayName("Should return same bus instance for same name")
        public void findOrCreateBusSameNameTest()
        {
            final String busName =
                "same-bus-" + System.nanoTime();
            final EfsEventBus bus1 =
                EfsEventBus.findOrCreateBus(busName);
            final EfsEventBus bus2 =
                EfsEventBus.findOrCreateBus(busName);

            assertThat(bus1).isSameAs(bus2);
        } // end of findOrCreateBusSameNameTest()

        @Test
        @DisplayName("Should throw IllegalArgumentException for null bus name")
        public void nullBusNameTest()
        {
            final String busName = null;

            assertThatThrownBy(
                () ->
                {
                    EfsEventBus.findOrCreateBus(busName);
                })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsEventBus.INVALID_BUS_NAME);
        } // end of nullBusNameTest()

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty bus name")
        public void emptyBusNameTest()
        {
            final String busName = "";

            assertThatThrownBy(
                () ->
                {
                    EfsEventBus.findOrCreateBus(busName);
                })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsEventBus.INVALID_BUS_NAME);
        } // end of emptyBusNameTest()

        @Test
        @DisplayName("Should throw IllegalArgumentException for blank bus name")
        public void blankBusNameTest()
        {
            final String busName = "\t";

            assertThatThrownBy(
                () -> EfsEventBus.findOrCreateBus(busName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsEventBus.INVALID_BUS_NAME);
        } // end of blankBusNameTest()
    } // end of class BusCreationTests

    // ========== Topic Management ==========

    @Nested
    @DisplayName("Topic Management")
    public final class TopicManagementTests
    {
        @Test
        @DisplayName("Should add single topic")
        public void addSingleTopicTest()
        {
            mEventBus.addTopic(mTestTopicKey);
        } // end of addSingleTopicTest()

        @Test
        @DisplayName("Should add multiple topics")
        public void addMultipleTopicsTest()
        {
            final List<String> topics =
                List.of("topic1", "topic2", "topic3");

            mEventBus.addTopics(TestEvent.class, topics);
        } // end of addMultipleTopicsTest()

        @Test
        @DisplayName("Should throw NullPointerException when adding null topic key")
        public void addNullTopicKeyTest()
        {
            final EfsTopicKey<TestEvent> topic = null;

            assertThatThrownBy(() -> mEventBus.addTopic(topic))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_TOPIC_KEY);
        } // end of addNullTopicKeyTest()

        @Test
        @DisplayName("Should throw NullPointerException when adding topics with null event class")
        public void addTopicsWithNullEventClassTest()
        {
            final Class<TestEvent> ec = null;
            final List<String> topics = List.of("topic1");

            assertThatThrownBy(
                () -> mEventBus.addTopics(ec, topics))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_EVENT_CLASS);
        } // end of addTopicsWithNullEventClassTest()

        @Test
        @DisplayName("Should throw NullPointerException when adding topics with null list")
        public void addTopicsWithNullListTest()
        {
            final Class<TestEvent> ec = TestEvent.class;
            final List<String> topics = null;

            assertThatThrownBy(
                () -> mEventBus.addTopics(ec, topics))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_TOPICS);
        } // end of addTopicsWithNullListTest()

        @Test
        @DisplayName("Should throw IllegalArgumentException for null topic in list")
        public void addTopicsWithNullTopicInListTest()
        {
            final Class<TestEvent> ec = TestEvent.class;
            final List<String> topics = new ArrayList<>();

            topics.add(null);

            assertThatThrownBy(
                () -> mEventBus.addTopics(ec, topics))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsEventBus.INVALID_TOPIC);
        } // end of addTopicsWithNullTopicInListTest()

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty topic string")
        public void addTopicsWithEmptyTopicTest()
        {
            final Class<TestEvent> ec = TestEvent.class;
            final List<String> topics = List.of("");

            assertThatThrownBy(
                () -> mEventBus.addTopics(ec, topics))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsEventBus.INVALID_TOPIC);
        } // end of addTopicsWithEmptyTopicTest()

        @Test
        @DisplayName("Should throw IllegalArgumentException for blank topic string")
        public void addTopicsWithBlankTopicTest()
        {
            final Class<TestEvent> ec = TestEvent.class;
            final List<String> topics = List.of("\t");

            assertThatThrownBy(
                () -> mEventBus.addTopics(ec, topics))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsEventBus.INVALID_TOPIC);
        } // end of addTopicsWithBlankTopicTest()
    } // end of class TopicManagementTests

    // ========== Advertisement Tests ==========

    @Nested
    @DisplayName("Advertisement Management")
    public class AdvertisementTests
    {
        @Test
        @DisplayName("Should advertise concrete topic")
        public void advertiseConcreteTopicTest()
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final IEfsAgent publisher = sMockPublisher;
            Advertisement<TestEvent> ad =
                mEventBus.advertise(topicKey, sscb, publisher);

            assertThat(ad).isNotNull();
            assertThat(ad.topicKey()).isEqualTo(topicKey);
            assertThat(ad.isOpen()).isTrue();
            assertThat(ad.isPublished()).isFalse();
            assertThat(ad.eventCount()).isZero();
            assertThat(ad.latestEventTimestamp()).isZero();

            ad.publishStatus(true);
            assertThat(ad.isPublished()).isTrue();

            ad.publishStatus(false);
            assertThat(ad.isPublished()).isFalse();

            assertThat(mEventBus.advertisementCreationCount())
                .isOne();
        } // end of advertiseConcreteTopicTest()

        @Test
        @DisplayName("Should throw NullPointerException when advertising with null topic key")
        public void advertiseNullTopicKeyTest()
        {
            final EfsTopicKey<TestEvent> topicKey = null;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final IEfsAgent publisher = sMockPublisher;

            assertThatThrownBy(
                () -> mEventBus.advertise(topicKey,
                                          sscb,
                                          publisher))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_TOPIC_KEY);
        } // end of advertiseNullTopicKeyTest()

        @Test
        @DisplayName("Should throw NullPointerException when advertising with null callback")
        public void advertiseNullCallbackTest()
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                null;
            final IEfsAgent publisher = sMockPublisher;

            assertThatThrownBy(
                () -> mEventBus.advertise(topicKey,
                                          sscb,
                                          publisher))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_CALLBACK);
        } // end of advertiseNullCallbackTest()

        @Test
        @DisplayName("Should throw NullPointerException when advertising with null agent")
        public void advertiseNullAgentTest()
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final IEfsAgent publisher = null;

            assertThatThrownBy(
                () -> mEventBus.advertise(topicKey,
                                          sscb,
                                          publisher))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_AGENT);
        } // end of advertiseNullAgentTest()

        @Test
        @DisplayName("Should throw IllegalStateException when advertising with unregistered agent")
        public void advertiseUnregisteredAgentTest()
        {
            final String agentName = UNREGISTERED_NAME;
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final IEfsAgent publisher = mock(IEfsAgent.class);

            when(publisher.name()).thenReturn(agentName);

            assertThatThrownBy(
                () -> mEventBus.advertise(topicKey,
                                          sscb,
                                          publisher))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(EfsEventBus.UNREGISTERED_AGENT,
                            agentName);
        } // end of advertiseUnregisteredAgentTest()

        @Test
        @DisplayName("Should throw IllegalStateException when agent advertises twice on same topic key")
        public void advertiseTwiceTest()
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final IEfsAgent publisher = sMockPublisher;

            mEventBus.advertise(topicKey, sscb, publisher);

            assertThatThrownBy(
                () -> mEventBus.advertise(topicKey, sscb, publisher))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                    String.format(
                        "agent %s already advertised for %s",
                        publisher.name(),
                        topicKey));
        } // end of advertiseTwice()

        @Test
        @DisplayName("Should close advertisement")
        public void closeAdvertisementTest()
            throws Exception
        {
            final Advertisement<TestEvent> ad =
                mEventBus.advertise(mTestTopicKey,
                                    status -> {},
                                    sMockPublisher);

            assertThat(ad.isOpen()).isTrue();

            ad.close();

            assertThat(ad.isOpen()).isFalse();
        } // end of closeAdvertisementTest()

        @Test
        @DisplayName("Should idempotently close advertisement")
        public void closeAdvertisementIdempotentTest()
            throws Exception
        {
            final Advertisement<TestEvent> ad =
                mEventBus.advertise(mTestTopicKey,
                                    status -> {},
                                    sMockPublisher);

            // Make sure multiple ad closings do not throw an
            // exception.
            ad.close();
            ad.close();
        } // end of closeAdvertisementIdempotentTest()

        @Test
        @DisplayName("Should throw IllegalStateException when updating publish status on closed advertisement")
        public void publishStatusOnClosedAdvertisementTest()
            throws Exception
        {
            final Advertisement<TestEvent> ad =
                mEventBus.advertise(mTestTopicKey,
                                    status -> {},
                                    sMockPublisher);

            ad.close();

            assertThatThrownBy(() -> ad.publishStatus(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(EfsEventBus.ADVERTISEMENT_CLOSED);
        } // end of publishStatusOnClosedAdvertisementTest()

        @Test
        @DisplayName("Should throw IllegalStateException when publishing on closed advertisement")
        public void publishOnClosedAdvertisementTest()
            throws Exception
        {
            final TestEvent event = mock(TestEvent.class);
            final Advertisement<TestEvent> ad =
                mEventBus.advertise(mTestTopicKey,
                                    status -> {},
                                    sMockPublisher);

            ad.close();

            assertThatThrownBy(() -> ad.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(EfsEventBus.ADVERTISEMENT_CLOSED);
        } // end of publishOnClosedAdvertisementTest()
    } // end of class AdvertisementTests

    // ========== Subscription Tests ==========

    @Nested
    @DisplayName("Subscription Management")
    public final class SubscriptionTests
    {
        @Test
        @DisplayName("Should subscribe to concrete topic")
        public void subscribeConcreteopicTest()
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final IEfsAgent subscriber = sMockSubscriber;
            final Subscription<TestEvent> sub =
                mEventBus.subscribe(topicKey,
                                   pscb,
                                   ecb,
                                   subscriber);

            assertThat(sub).isNotNull();
            assertThat(sub.topicKey()).isEqualTo(topicKey);
            assertThat(sub.isOpen()).isTrue();
            assertThat(mEventBus.subscriptionCreationCount())
                .isOne();
        } // end of subscribeConcreteopicTest()

        @Test
        @DisplayName("Should subscribe with inbox mode")
        public void testSubscribeInbox()
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final IEfsAgent subscriber = sMockSubscriber;
            final Subscription<TestEvent> sub =
                mEventBus.subscribeInbox(topicKey,
                                         pscb,
                                         ecb,
                                         subscriber);

            assertThat(sub).isNotNull();
            assertThat(sub.topicKey()).isEqualTo(topicKey);
            assertThat(sub.isOpen()).isTrue();
        } // end of testSubscribeInbox()

        @Test
        @DisplayName("Should throw NullPointerException when subscribing with null topic key")
        public void subscribeNullTopicKeyTest()
        {
            final EfsTopicKey<TestEvent> topicKey = null;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final IEfsAgent subscriber = sMockSubscriber;

            assertThatThrownBy(
                () -> mEventBus.subscribe(topicKey,
                                          pscb,
                                          ecb,
                                          subscriber))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_TOPIC_KEY);
        } // end of subscribeNullTopicKeyTest()

        @Test
        @DisplayName("Should throw NullPointerException when subscribing with null publish status callback")
        public void subscribeNullPublishStatusCallbackTest()
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                null;
            final Consumer<TestEvent> ecb = event -> {};
            final IEfsAgent subscriber = sMockSubscriber;

            assertThatThrownBy(
                () -> mEventBus.subscribe(topicKey,
                                          pscb,
                                          ecb,
                                          subscriber))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_CALLBACK);
        } // end of subscribeNullPublishStatusCallbackTest()

        @Test
        @DisplayName("Should throw NullPointerException when subscribing with null event callback")
        public void subscribeNullEventCallbackTest()
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = null;
            final IEfsAgent subscriber = sMockSubscriber;

            assertThatThrownBy(
                () -> mEventBus.subscribe(topicKey,
                                          pscb,
                                          ecb,
                                          subscriber))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_CALLBACK);
        } // end of subscribeNullEventCallbackTest()

        @Test
        @DisplayName("Should throw NullPointerException when subscribing with null agent")
        public void subscribeNullAgentTest()
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final IEfsAgent subscriber = null;

            assertThatThrownBy(
                () -> mEventBus.subscribe(topicKey,
                                          pscb,
                                          ecb,
                                          subscriber))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_AGENT);
        } // end of subscribeNullAgentTest()

        @Test
        @DisplayName("Should throw IllegalStateException when subscribing with unregistered agent")
        public void subscribeUnregisteredAgentTest()
        {
            final String agentName = UNREGISTERED_NAME;
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final IEfsAgent subscriber = mock(IEfsAgent.class);

            when(subscriber.name()).thenReturn(agentName);

            assertThatThrownBy(
                () -> mEventBus.subscribe(topicKey,
                                          pscb,
                                          ecb,
                                          subscriber))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(EfsEventBus.UNREGISTERED_AGENT,
                            agentName);
        } // end of subscribeUnregisteredAgentTest()

        @Test
        @DisplayName("Should close subscription")
        public void closeSubscriptionTest()
            throws Exception
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final IEfsAgent subscriber = sMockSubscriber;
            final  Subscription<TestEvent> sub =
                mEventBus.subscribe(topicKey,
                                    pscb,
                                    ecb,
                                    subscriber);

            assertThat(sub.isOpen()).isTrue();

            sub.close();

            assertThat(sub.isOpen()).isFalse();
        } // end of closeSubscriptionTest()

        @Test
        @DisplayName("Should idempotently close subscription")
        public void closeSubscriptionIdempotentTrue()
            throws Exception
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final IEfsAgent subscriber = sMockSubscriber;
            final  Subscription<TestEvent> sub =
                mEventBus.subscribe(topicKey,
                                    pscb,
                                    ecb,
                                    subscriber);

            sub.close();
            sub.close();
        } // end of closeSubscriptionIdempotentTrue()

        @Test
        @DisplayName("Should track subscription count in advertisements")
        public void subscriptionCountTrackingTest()
            throws Exception
        {
            final AtomicInteger subscriberCount =
                new AtomicInteger();
            final CountDownLatch signal = new CountDownLatch(2);
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status ->
                {
                    subscriberCount.set(
                        status.activeSubscribers());
                    signal.countDown();
                };
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final IEfsAgent publisher = sMockPublisher;
            final IEfsAgent subscriber = sMockSubscriber;
            try (Advertisement<TestEvent> ad =
                     mEventBus.advertise(topicKey,
                                         sscb,
                                         publisher))
            {
                assertThat(ad.isOpen()).isTrue();
                assertThat(subscriberCount.get()).isZero();

                try (Subscription<TestEvent> sub =
                         mEventBus.subscribe(topicKey,
                                             pscb,
                                             ecb,
                                             subscriber))
                {
                    assertThat(sub.isOpen()).isTrue();

                    try
                    {
                        signal.await();
                    }
                    catch (InterruptedException interrupt)
                    {}

                    assertThat(subscriberCount.get()).isOne();
                }
            }
        } // end of subscriptionCountTrackingTest()
    } // end of class SubscriptionTests

    // ========== Event Publishing Tests ==========

    @Nested
    @DisplayName("Event Publishing")
    public final class EventPublishingTests
    {
        @Test
        @DisplayName("Should publish event to subscriber")
        public void publishEventToSubscriberTest()
            throws Exception
        {
            final AtomicReference<TestEvent> receivedEvent =
                new AtomicReference<>();
            final CountDownLatch signal = new CountDownLatch(1);
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb =
                event ->
                {
                    receivedEvent.set(event);
                    signal.countDown();
                };
            final IEfsAgent publisher = sMockPublisher;
            final IEfsAgent subscriber = sMockSubscriber;
            final TestEvent event = mock(TestEvent.class);

            try (Advertisement<TestEvent> ad =
                     mEventBus.advertise(topicKey,
                                         sscb,
                                         publisher);
                 Subscription<TestEvent> sub =
                     mEventBus.subscribe(topicKey,
                                         pscb,
                                         ecb,
                                         subscriber))
            {
                assertThat(ad.isOpen()).isTrue();
                assertThat(sub.isOpen()).isTrue();
                ad.publishStatus(true);
                ad.publish(event);

                // Event dispatch happens asynchronously via
                // EfsDispatcher.
                try
                {
                    signal.await();
                }
                catch (InterruptedException interrupt)
                {}

                assertThat(receivedEvent.get()).isNotNull()
                                               .isSameAs(event);
            }
        } // end of publishEventToSubscriberTest()

        @Test
        @DisplayName("Publish event to inbox subscriber")
        public void publishEventToInboxSubscriberTest()
            throws Exception
        {
            final AtomicReference<TestEvent> receivedEvent =
                new AtomicReference<>();
            final CountDownLatch signal = new CountDownLatch(1);
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb =
                event ->
                {
                    receivedEvent.set(event);
                    signal.countDown();
                };
            final IEfsAgent publisher = sMockPublisher;
            final IEfsAgent subscriber = sMockSubscriber;
            final TestEvent event = mock(TestEvent.class);

            try (Advertisement<TestEvent> ad =
                     mEventBus.advertise(topicKey,
                                         sscb,
                                         publisher);
                 Subscription<TestEvent> sub =
                     mEventBus.subscribeInbox(topicKey,
                                              pscb,
                                              ecb,
                                              subscriber))
            {
                assertThat(ad.isOpen()).isTrue();
                assertThat(sub.isOpen()).isTrue();
                ad.publishStatus(true);
                ad.publish(event);

                // Event dispatch happens asynchronously via
                // EfsDispatcher.
                try
                {
                    signal.await();
                }
                catch (InterruptedException interrupt)
                {}

                assertThat(receivedEvent.get()).isNotNull()
                                               .isSameAs(event);
            }
        } // end of publishEventToInboxSubscriberTest()

        @Test
        @DisplayName("Publish two events to inbox subscriber")
        public void publishTwoEventsToInboxSubscriberTest()
            throws Exception
        {
            final AtomicReference<TestEvent> receivedEvent =
                new AtomicReference<>();
            final CountDownLatch inSignal =
                new CountDownLatch(1);
            final CountDownLatch outSignal =
                new CountDownLatch(1);
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb =
                event ->
                {
                    receivedEvent.set(event);
                    outSignal.countDown();
                };
            final Runnable hangTask =
                () ->
                {
                    try
                    {
                        inSignal.await();
                    }
                    catch (InterruptedException interrupt)
                    {}
                };
            final IEfsAgent publisher = sMockPublisher;
            final IEfsAgent subscriber = sMockSubscriber;
            final TestEvent event0 = mock(TestEvent.class);
            final TestEvent event1 = mock(TestEvent.class);

            try (Advertisement<TestEvent> ad =
                     mEventBus.advertise(topicKey,
                                         sscb,
                                         publisher);
                 Subscription<TestEvent> sub =
                     mEventBus.subscribeInbox(topicKey,
                                              pscb,
                                              ecb,
                                              subscriber))
            {
                assertThat(ad.isOpen()).isTrue();
                assertThat(sub.isOpen()).isTrue();

                ad.publishStatus(true);

                // Dispatch a task which hangs the subscriber
                // until released. This allows inbox to deliver
                // second event replacing the first.
                EfsDispatcher.dispatch(hangTask, subscriber);

                ad.publish(event0);
                ad.publish(event1);

                // Release subscriber.
                inSignal.countDown();

                // Event dispatch happens asynchronously via
                // EfsDispatcher.
                try
                {
                    outSignal.await();
                }
                catch (InterruptedException interrupt)
                {}

                assertThat(receivedEvent.get()).isNotNull()
                                               .isSameAs(event1);
            }
        } // end of publishTwoEventsToInboxSubscriberTest()

        @Test
        @DisplayName("Should require publish status to be set before publishing")
        public void publishRequiresPublishStatusTest()
            throws Exception
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final IEfsAgent publisher = sMockPublisher;
            final IEfsAgent subscriber = sMockSubscriber;
            final Advertisement<TestEvent> ad =
                mEventBus.advertise(topicKey,
                                    sscb,
                                    publisher);
            final TestEvent event = mock(TestEvent.class);

            mEventBus.subscribe(topicKey,
                                pscb,
                                ecb,
                                subscriber);


            // Should fail because publish status is false.
            assertThatThrownBy(() -> ad.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(EfsEventBus.PUBLISH_STATUS_DOWN);
        } // end of publishRequiresPublishStatusTest()

        @Test
        @DisplayName("Should track event count on advertisements")
        public void advertisementEventCountTest()
            throws Exception
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final IEfsAgent publisher = sMockPublisher;
            final IEfsAgent subscriber = sMockSubscriber;
            final Advertisement<TestEvent> ad =
                mEventBus.advertise(topicKey, sscb, publisher);
            final TestEvent event1 = mock(TestEvent.class);
            final TestEvent event2 = mock(TestEvent.class);

            mEventBus.subscribe(topicKey,
                                pscb,
                                ecb,
                                subscriber);

            assertThat(ad.eventCount()).isZero();

            ad.publishStatus(true);

            ad.publish(event1);
            assertThat(ad.eventCount()).isOne();

            ad.publish(event2);
            assertThat(ad.eventCount()).isEqualTo(2);
        } // end of advertisementEventCountTest()

        @Test
        @DisplayName("Should track latest event timestamp")
        public void latestEventTimestampTest()
            throws Exception
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final IEfsAgent publisher = sMockPublisher;
            final IEfsAgent subscriber = sMockSubscriber;
            final Advertisement<TestEvent> ad =
                mEventBus.advertise(topicKey,
                                    sscb,
                                    publisher);
            final TestEvent event = mock(TestEvent.class);
            final long before;
            final long after;

            mEventBus.subscribe(topicKey,
                                pscb,
                                ecb,
                                subscriber);

            before = System.currentTimeMillis();
            ad.publishStatus(true);
            ad.publish(event);
            after = System.currentTimeMillis();

            assertThat(ad.latestEventTimestamp())
                .isGreaterThanOrEqualTo(before)
                .isLessThanOrEqualTo(after);
        } // end of latestEventTimestampTest()
    } // end of class EventPublishingTests

    // ========== Router Subscription Tests ==========

    @Nested
    @DisplayName("Router Subscriptions")
    @SuppressWarnings ("unchecked")
    public final class RouterSubscriptionTests
    {
        @Test
        @DisplayName("Should create router subscription")
        public void createRouterSubscriptionTest()
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final IEventRouter<TestEvent> router =
                mock(IEventRouter.class);
            final IEfsAgent subscriber = sMockSubscriber;
            final Subscription<TestEvent> sub =
                mEventBus.subscribeRouter(topicKey,
                                          pscb,
                                          router,
                                          subscriber);

            assertThat(sub).isNotNull();
            assertThat(sub.topicKey()).isEqualTo(topicKey);
            assertThat(sub.isOpen()).isTrue();
        } // end of createRouterSubscriptionTest()

        @Test
        @DisplayName("Should throw NullPointerException when creating router subscription with null topic key")
        public void routerSubscriptionNullTopicKeyTest()
        {
            final EfsTopicKey<TestEvent> topicKey = null;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final IEventRouter<TestEvent> router =
                mock(IEventRouter.class);
            final IEfsAgent subscriber = sMockSubscriber;

            assertThatThrownBy(
                () -> mEventBus.subscribeRouter(topicKey,
                                                pscb,
                                                router,
                                                subscriber))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_TOPIC_KEY);
        } // end of routerSubscriptionNullTopicKeyTest()

        @Test
        @DisplayName("Should throw NullPointerException when creating router subscription with null callback")
        public void routerSubscriptionNullCallbackTest()
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                null;
            final IEventRouter<TestEvent> router =
                mock(IEventRouter.class);
            final IEfsAgent subscriber = sMockSubscriber;

            assertThatThrownBy(
                () -> mEventBus.subscribeRouter(topicKey,
                                                pscb,
                                                router,
                                                subscriber))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_CALLBACK);
        } // end of routerSubscriptionNullCallbackTest()

        @Test
        @DisplayName("Should throw NullPointerException when creating router subscription with null router")
        public void routerSubscriptionNullRouterTest()
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final IEventRouter<TestEvent> router = null;
            final IEfsAgent subscriber = sMockSubscriber;

            assertThatThrownBy(
                () -> mEventBus.subscribeRouter(topicKey,
                                                pscb,
                                                router,
                                                subscriber))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_ROUTER);
        } // end of routerSubscriptionNullRouterTest()

        @Test
        @DisplayName("Should throw NullPointerException when creating router subscription with null agent")
        public void routerSubscriptionNullAgentTest()
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final IEventRouter<TestEvent> router =
                mock(IEventRouter.class);
            final IEfsAgent subscriber = null;

            assertThatThrownBy(
                () -> mEventBus.subscribeRouter(topicKey,
                                                pscb,
                                                router,
                                                subscriber))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_AGENT);
        } // end of routerSubscriptionNullAgentTest()

        @Test
        @DisplayName("Publish event, router returns null target")
        public void routerSubscriptionNoTargetAgent()
            throws Exception
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final IEventRouter<TestEvent> router =
                mock(IEventRouter.class);
            final IEfsAgent publisher = sMockPublisher;
            final IEfsAgent subscriber = sMockSubscriber;
            final TestEvent event = mock(TestEvent.class);

            when(router.routeTo(event)).thenReturn(null);

            try (Advertisement<TestEvent> ad =
                     mEventBus.advertise(topicKey,
                                         sscb,
                                         publisher);
                 Subscription<TestEvent> sub =
                     mEventBus.subscribeRouter(topicKey,
                                               pscb,
                                               router,
                                               subscriber))
            {
                ad.publishStatus(true);
                ad.publish(event);
            }
        } // end of routerSubscriptionNoTargetAgent()

        @Test
        @DisplayName("Publish event, router returns target")
        public void routerSubscriptionTargetAgent()
            throws Exception
        {
            final AtomicReference<TestEvent> receivedEvent =
                new AtomicReference<>();
            final CountDownLatch signal = new CountDownLatch(1);
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb =
                event ->
                {
                    receivedEvent.set(event);
                    signal.countDown();
                };
            final IEventRouter<TestEvent> router =
                mock(IEventRouter.class);
            final IEfsAgent publisher = sMockPublisher;
            final IEfsAgent subscriber = sMockSubscriber;
            final TestEvent event = mock(TestEvent.class);

            when(router.routeTo(event))
                .thenReturn(
                    new EfsDispatchTarget<>(ecb, subscriber));

            try (Advertisement<TestEvent> ad =
                     mEventBus.advertise(topicKey,
                                         sscb,
                                         publisher);
                 Subscription<TestEvent> sub =
                     mEventBus.subscribeRouter(topicKey,
                                               pscb,
                                               router,
                                               subscriber))
            {
                ad.publishStatus(true);
                ad.publish(event);

                // Event dispatch happens asynchronously via
                // EfsDispatcher.
                try
                {
                    signal.await();
                }
                catch (InterruptedException interrupt)
                {}

                assertThat(receivedEvent.get()).isNotNull()
                                               .isSameAs(event);
            }
        } // end of routerSubscriptionTargetAgent()
    } // end of class RouterSubscriptionTests

    // ========== Wildcard Subscription Tests ==========

    @Nested
    @DisplayName("Wildcard Subscriptions")
    public final class WildcardSubscriptionTests
    {
        @Test
        @DisplayName("Should create wildcard subscription with concrete delivery")
        public void createWildcardSubscriptionConcreteTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = REGEX_TOPIC;
            final boolean inboxFlag = false;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent subscriber = sMockSubscriber;
            final WildcardSubscription<TestEvent> wildSub =
                mEventBus.subscribeAll(eventClass,
                                       regexTopic,
                                       inboxFlag,
                                       pscb,
                                       ecb,
                                       topicUpdate,
                                       subscriber);

            assertThat(wildSub).isNotNull();
            assertThat(wildSub.isOpen()).isTrue();
            assertThat(wildSub.isInbox()).isEqualTo(inboxFlag);
            assertThat(wildSub.eventClass())
                .isEqualTo(eventClass);
            assertThat(wildSub.wildcardTopic())
                .isEqualTo(regexTopic);
        } // end of createWildcardSubscriptionConcreteTest()

        @Test
        @DisplayName("Should create wildcard subscription with inbox delivery")
        public void createWildcardSubscriptionInboxTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = REGEX_TOPIC;
            final boolean inboxFlag = true;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent subscriber = sMockSubscriber;
            final WildcardSubscription<TestEvent> wildSub =
                mEventBus.subscribeAll(eventClass,
                                       regexTopic,
                                       inboxFlag,
                                       pscb,
                                       ecb,
                                       topicUpdate,
                                       subscriber);


            assertThat(wildSub).isNotNull();
            assertThat(wildSub.isOpen()).isTrue();
            assertThat(wildSub.isInbox()).isEqualTo(inboxFlag);
        } // end of createWildcardSubscriptionInboxTest()

        @Test
        @DisplayName("Should throw NullPointerException for null event class")
        public void wildcardSubscriptionNullEventClassTest()
        {
            final Class<TestEvent> eventClass = null;
            final String regexTopic = REGEX_TOPIC;
            final boolean inboxFlag = false;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent subscriber = sMockSubscriber;

            assertThatThrownBy(
                () -> mEventBus.subscribeAll(eventClass,
                                             regexTopic,
                                             inboxFlag,
                                             pscb,
                                             ecb,
                                             topicUpdate,
                                             subscriber))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_EVENT_CLASS);
        } // end of wildcardSubscriptionNullEventClassTest()

        @Test
        @DisplayName("Should throw IllegalArgumentException for null regex topic")
        public void wildcardSubscriptionNullRegexTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = null;
            final boolean inboxFlag = false;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent subscriber = sMockSubscriber;

            assertThatThrownBy(
                () -> mEventBus.subscribeAll(eventClass,
                                             regexTopic,
                                             inboxFlag,
                                             pscb,
                                             ecb,
                                             topicUpdate,
                                             subscriber))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsEventBus.INVALID_TOPIC);
        } // end of wildcardSubscriptionNullRegexTest()

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty regex topic")
        public void wildcardSubscriptionEmptyRegexTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = "";
            final boolean inboxFlag = false;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent subscriber = sMockSubscriber;

            assertThatThrownBy(
                () -> mEventBus.subscribeAll(eventClass,
                                             regexTopic,
                                             inboxFlag,
                                             pscb,
                                             ecb,
                                             topicUpdate,
                                             subscriber))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsEventBus.INVALID_TOPIC);
        } // end of wildcardSubscriptionEmptyRegexTest()

        @Test
        @DisplayName("Should throw IllegalArgumentException for blank regex topic")
        public void wildcardSubscriptionBlankRegexTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = "\t";
            final boolean inboxFlag = false;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent subscriber = sMockSubscriber;

            assertThatThrownBy(
                () -> mEventBus.subscribeAll(eventClass,
                                             regexTopic,
                                             inboxFlag,
                                             pscb,
                                             ecb,
                                             topicUpdate,
                                             subscriber))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsEventBus.INVALID_TOPIC);
        } // end of wildcardSubscriptionBlankRegexTest()

        @Test
        @DisplayName("Should throw IllegalArgumentException for overly complex regex topic")
        public void wildcardSubscriptionComplexRegexText()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic =
                "a".repeat(EfsEventBus.MAX_REGEX_SIZE + 1);
            final boolean inboxFlag = false;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent subscriber = sMockSubscriber;

            assertThatThrownBy(
                () -> mEventBus.subscribeAll(eventClass,
                                             regexTopic,
                                             inboxFlag,
                                             pscb,
                                             ecb,
                                             topicUpdate,
                                             subscriber))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsEventBus.TOPIC_COMPLEXITY);
        } // end of wildcardSubscriptionComplexRegexText()

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid regex")
        public void wildcardSubscriptionInvalidRegexTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = "[invalid(regex";
            final boolean inboxFlag = false;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent subscriber = sMockSubscriber;

            assertThatThrownBy(
                () -> mEventBus.subscribeAll(eventClass,
                                             regexTopic,
                                             inboxFlag,
                                             pscb,
                                             ecb,
                                             topicUpdate,
                                             subscriber))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                    String.format(EfsEventBus.INVALID_REGEX,
                                  regexTopic));
        } // end of wildcardSubscriptionInvalidRegexTest()

        @Test
        @DisplayName("Should throw NullPointerException for null publish status callback")
        public void wildcardSubscriptionNullPublishStatusCallbackTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = REGEX_TOPIC;
            final boolean inboxFlag = false;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                null;
            final Consumer<TestEvent> ecb = event -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent subscriber = sMockSubscriber;

            assertThatThrownBy(
                () -> mEventBus.subscribeAll(eventClass,
                                             regexTopic,
                                             inboxFlag,
                                             pscb,
                                             ecb,
                                             topicUpdate,
                                             subscriber))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_CALLBACK);
        } // end of wildcardSubscriptionNullPublishStatusCallbackTest()

        @Test
        @DisplayName("Should throw NullPointerException for null event callback")
        public void wildcardSubscriptionNullEventCallbackTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = REGEX_TOPIC;
            final boolean inboxFlag = false;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = null;
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent subscriber = sMockSubscriber;

            assertThatThrownBy(
                () -> mEventBus.subscribeAll(eventClass,
                                             regexTopic,
                                             inboxFlag,
                                             pscb,
                                             ecb,
                                             topicUpdate,
                                             subscriber))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_CALLBACK);
        } // end of wildcardSubscriptionNullEventCallbackTest()

        @Test
        @DisplayName("Should throw NullPointerException for null topic update callback")
        public void wildcardSubscriptionNullTopicUpdateCallbackTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = REGEX_TOPIC;
            final boolean inboxFlag = true;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                null;
            final IEfsAgent subscriber = sMockSubscriber;

            assertThatThrownBy(
                () -> mEventBus.subscribeAll(eventClass,
                                             regexTopic,
                                             inboxFlag,
                                             pscb,
                                             ecb,
                                             topicUpdate,
                                             subscriber))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_CALLBACK);
        } // end of wildcardSubscriptionNullTopicUpdateCallbackTest()

        @Test
        @DisplayName("Should throw NullPointerException for null agent")
        public void wildcardSubscriptionNullAgentTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = REGEX_TOPIC;
            final boolean inboxFlag = false;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent subscriber = null;

            assertThatThrownBy(
                () -> mEventBus.subscribeAll(eventClass,
                                             regexTopic,
                                             inboxFlag,
                                             pscb,
                                             ecb,
                                             topicUpdate,
                                             subscriber))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_AGENT);
        } // end of wildcardSubscriptionNullAgentTest()

        @Test
        @DisplayName("Should throw IllegalStateException for unregistered agent")
        public void wildcardSubscriptionUnregisteredAgentTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = REGEX_TOPIC;
            final boolean inboxFlag = false;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent subscriber = mock(IEfsAgent.class);
            final String agentName = UNREGISTERED_NAME;

            when(subscriber.name()).thenReturn(agentName);

            assertThatThrownBy(
                () -> mEventBus.subscribeAll(eventClass,
                                             regexTopic,
                                             inboxFlag,
                                             pscb,
                                             ecb,
                                             topicUpdate,
                                             subscriber))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                    String.format(EfsEventBus.UNREGISTERED_AGENT,
                                  agentName));
        } // end of wildcardSubscriptionUnregisteredAgentTest()

        @Test
        @DisplayName("Should close wildcard subscription")
        public void closeWildcardSubscriptionTest()
            throws Exception
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = REGEX_TOPIC;
            final boolean inboxFlag = false;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent subscriber = sMockSubscriber;
            final WildcardSubscription<TestEvent> wildSub =
                mEventBus.subscribeAll(eventClass,
                                       regexTopic,
                                       inboxFlag,
                                       pscb,
                                       ecb,
                                       topicUpdate,
                                       subscriber);

            assertThat(wildSub.isOpen()).isTrue();

            wildSub.close();

            assertThat(wildSub.isOpen()).isFalse();
        } // end of closeWildcardSubscriptionTest()

        @Test
        @DisplayName("Should match wildcard sub topics on creation")
        public void wildcardTopicMatchingOnCreationTest()
            throws Exception
        {
            final List<EfsTopicKey<TestEvent>> discoveredTopics =
                new ArrayList<>();
            final CountDownLatch signal = new CountDownLatch(2);
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = "product-.*";
            final boolean inboxFlag = false;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k ->
                {
                    discoveredTopics.add(k);
                    signal.countDown();
                };
            final IEfsAgent subscriber = sMockSubscriber;
            final EfsTopicKey<TestEvent> key0 =
                EfsTopicKey.getKey(
                    eventClass, "product-service");
            final EfsTopicKey<TestEvent> key1 =
                EfsTopicKey.getKey(
                    eventClass, "order-service");
            final EfsTopicKey<TestEvent> key2 =
                EfsTopicKey.getKey(
                    eventClass, "product-catalog");
            final WildcardSubscription<TestEvent> wildSub;

            // Add topics that should match and should not match.
            // Note: In real implementation, topic matching
            // happens during wildcard subscription creation
            // This test verifies the infrastructure supports it.
            mEventBus.addTopic(key0);
            mEventBus.addTopic(key1);
            mEventBus.addTopic(key2);

            wildSub = mEventBus.subscribeAll(eventClass,
                                             regexTopic,
                                             inboxFlag,
                                             pscb,
                                             ecb,
                                             topicUpdate,
                                             subscriber);

            try
            {
                signal.await();
            }
            catch (InterruptedException interruption)
            {}

            assertThat(discoveredTopics.size()).isEqualTo(2);
            assertThat(discoveredTopics).contains(key0, key2)
                                        .doesNotContain(key1);
            assertThat(wildSub.subscriptionKeys())
                .contains(key0, key2)
                .doesNotContain(key1);
            assertThat(wildSub.subscriptionTopics())
                .contains(key0.topic(), key2.topic())
                .doesNotContain(key1.topic());
            assertThat(mEventBus.wildcardMatchCount())
                .isEqualTo(2);

            wildSub.close();

            assertThat(wildSub.isOpen()).isFalse();
        } // end of wildcardTopicMatchingOnCreationTest()

        @Test
        @DisplayName("Should match wildcard sub topics on demand")
        public void wildcardTopicMatchingTest()
            throws Exception
        {
            final List<EfsTopicKey<TestEvent>> discoveredTopics =
                new ArrayList<>();
            final CountDownLatch signal = new CountDownLatch(2);
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = "product-.*";
            final boolean inboxFlag = false;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k ->
                {
                    discoveredTopics.add(k);
                    signal.countDown();
                };
            final IEfsAgent subscriber = sMockSubscriber;
            final EfsTopicKey<TestEvent> key0 =
                EfsTopicKey.getKey(
                    eventClass, "product-service");
            final EfsTopicKey<TestEvent> key1 =
                EfsTopicKey.getKey(
                    eventClass, "order-service");
            final EfsTopicKey<TestEvent> key2 =
                EfsTopicKey.getKey(
                    eventClass, "product-catalog");
            final WildcardSubscription<TestEvent> wildSub =
                mEventBus.subscribeAll(eventClass,
                                       regexTopic,
                                       inboxFlag,
                                       pscb,
                                       ecb,
                                       topicUpdate,
                                       subscriber);

            // Add topics that should match and should not match.
            // Note: In real implementation, topic matching
            // happens during wildcard subscription creation
            // This test verifies the infrastructure supports it.
            mEventBus.addTopic(key0);
            mEventBus.addTopic(key1);
            mEventBus.addTopic(key2);

            try
            {
                signal.await();
            }
            catch (InterruptedException interruption)
            {}

            assertThat(discoveredTopics.size()).isEqualTo(2);
            assertThat(discoveredTopics).contains(key0, key2)
                                        .doesNotContain(key1);
            assertThat(wildSub.subscriptionKeys())
                .contains(key0, key2)
                .doesNotContain(key1);
            assertThat(wildSub.subscriptionTopics())
                .contains(key0.topic(), key2.topic())
                .doesNotContain(key1.topic());
            assertThat(mEventBus.wildcardMatchCount())
                .isEqualTo(2);

            wildSub.close();

            assertThat(wildSub.isOpen()).isFalse();
        } // end of wildcardTopicMatchingTest()
    } // end of class WildcardSubscriptionTests

    // ========== Wildcard Advertisement Tests ==========

    @Nested
    @DisplayName("Wildcard Advertisements")
    public final class WildcardAdvertisementTests
    {
        @Test
        @DisplayName("Should create wildcard advertisement")
        public void createWildcardAdvertisementTest()
            throws Exception
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = REGEX_TOPIC;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent publisher = sMockPublisher;
            final WildcardAdvertisement<TestEvent> wildAd =
                mEventBus.advertiseAll(eventClass,
                                       regexTopic,
                                       sscb,
                                       topicUpdate,
                                       publisher);

            assertThat(wildAd).isNotNull();
            assertThat(wildAd.isOpen()).isTrue();
        } // end of createWildcardAdvertisementTest()

        @Test
        @DisplayName("Should throw NullPointerException for null event class")
        public void wildcardAdvertisementNullEventClassTest()
        {
            final Class<TestEvent> eventClass = null;
            final String regexTopic = REGEX_TOPIC;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent publisher = sMockPublisher;

            assertThatThrownBy(
                () -> mEventBus.advertiseAll(eventClass,
                                             regexTopic,
                                             sscb,
                                             topicUpdate,
                                             publisher))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_EVENT_CLASS);
        } // end of wildcardAdvertisementNullEventClassTest()

        @Test
        @DisplayName("Should throw IllegalArgumentException for null regex")
        public void wildcardAdvertisementNullRegexTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = null;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent publisher = sMockPublisher;

            assertThatThrownBy(
                () -> mEventBus.advertiseAll(eventClass,
                                             regexTopic,
                                             sscb,
                                             topicUpdate,
                                             publisher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsEventBus.INVALID_TOPIC);
        } // end of wildcardAdvertisementNullRegexTest()

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty regex")
        public void wildcardAdvertisementEmptyRegexTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = "";
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent publisher = sMockPublisher;

            assertThatThrownBy(
                () -> mEventBus.advertiseAll(eventClass,
                                             regexTopic,
                                             sscb,
                                             topicUpdate,
                                             publisher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsEventBus.INVALID_TOPIC);
        } // end of wildcardAdvertisementEmptyRegexTest()

        @Test
        @DisplayName("Should throw IllegalArgumentException for blank regex")
        public void wildcardAdvertisementBlankRegexTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = "\t";
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent publisher = sMockPublisher;

            assertThatThrownBy(
                () -> mEventBus.advertiseAll(eventClass,
                                             regexTopic,
                                             sscb,
                                             topicUpdate,
                                             publisher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsEventBus.INVALID_TOPIC);
        } // end of wildcardAdvertisementBlankRegexTest()

        @Test
        @DisplayName("Should throw IllegalArgumentException for overly complex regex")
        public void wildcardAdvertisementOverlyComplexRegexTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic =
                "a".repeat(EfsEventBus.MAX_REGEX_SIZE + 1);
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent publisher = sMockPublisher;

            assertThatThrownBy(
                () -> mEventBus.advertiseAll(eventClass,
                                             regexTopic,
                                             sscb,
                                             topicUpdate,
                                             publisher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EfsEventBus.TOPIC_COMPLEXITY);
        } // end of wildcardAdvertisementOverlyComplexRegexTest()

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid regex")
        public void wildcardAdvertisementInvalidRegexTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = "[unclosed";
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent publisher = sMockPublisher;

            assertThatThrownBy(
                () -> mEventBus.advertiseAll(eventClass,
                                             regexTopic,
                                             sscb,
                                             topicUpdate,
                                             publisher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                    String.format(EfsEventBus.INVALID_REGEX,
                                  regexTopic));
        } // end of wildcardAdvertisementInvalidRegexTest()

        @Test
        @DisplayName("Should throw NullPointerException for null callback")
        public void wildcardAdvertisementNullCallbackTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = REGEX_TOPIC;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                null;
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent publisher = sMockPublisher;

            assertThatThrownBy(
                () -> mEventBus.advertiseAll(eventClass,
                                             regexTopic,
                                             sscb,
                                             topicUpdate,
                                             publisher))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_CALLBACK);
        } // end of wildcardAdvertisementNullCallbackTest()

        @Test
        @DisplayName("Should throw NullPointerException for null topic updatge callback")
        public void wildcardAdvertisementNullTopicUpateCallbackTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = REGEX_TOPIC;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                null;
            final IEfsAgent publisher = sMockPublisher;

            assertThatThrownBy(
                () -> mEventBus.advertiseAll(eventClass,
                                             regexTopic,
                                             sscb,
                                             topicUpdate,
                                             publisher))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_CALLBACK);
        } // end of wildcardAdvertisementNullTopicUpateCallbackTest()

        @Test
        @DisplayName("Should throw NullPointerException for null agent")
        public void wildcardAdvertisementNullAgentTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = REGEX_TOPIC;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent publisher = null;

            assertThatThrownBy(
                () -> mEventBus.advertiseAll(eventClass,
                                             regexTopic,
                                             sscb,
                                             topicUpdate,
                                             publisher))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(EfsEventBus.NULL_AGENT);
        } // end of wildcardAdvertisementNullAgentTest()

        @Test
        @DisplayName("Should throw IllegalStateException for unregistered agent")
        public void wildcardAdvertisementUnregisteredAgentTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = REGEX_TOPIC;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent publisher = mock(IEfsAgent.class);
            final String agentName = UNREGISTERED_NAME;

            when(publisher.name()).thenReturn(agentName);

            assertThatThrownBy(
                () -> mEventBus.advertiseAll(eventClass,
                                             regexTopic,
                                             sscb,
                                             topicUpdate,
                                             publisher))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                    String.format(EfsEventBus.UNREGISTERED_AGENT,
                                  agentName));
        } // end of wildcardAdvertisementUnregisteredAgentTest()

        @Test
        @DisplayName("Should close wildcard advertisement")
        public void closeWildcardAdvertisementTest()
            throws Exception
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = REGEX_TOPIC;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent publisher = sMockPublisher;
            final WildcardAdvertisement<TestEvent> wildAd =
                mEventBus.advertiseAll(eventClass,
                                       regexTopic,
                                       sscb,
                                       topicUpdate,
                                       publisher);

            assertThat(wildAd.isOpen()).isTrue();

            wildAd.close();

            assertThat(wildAd.isOpen()).isFalse();
        } // end of closeWildcardAdvertisementTest()

        @Test
        @DisplayName("Should match wildcard ad topics on creation")
        public void wildcardAdvertisementTopicMatchingOnCreationTest()
            throws Exception
        {
            final List<EfsTopicKey<TestEvent>> discoveredTopics =
                new ArrayList<>();
            final CountDownLatch signal = new CountDownLatch(2);
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = "/acme/equity/A.*";
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k ->
                {
                    discoveredTopics.add(k);
                    signal.countDown();
                };
            final IEfsAgent publisher = sMockPublisher;
            final EfsTopicKey<TestEvent> key0 =
                EfsTopicKey.getKey(
                    eventClass, "/acme/equity/ABLE");
            final EfsTopicKey<TestEvent> key1 =
                EfsTopicKey.getKey(
                    eventClass, "/acme/equity/BRV");
            final EfsTopicKey<TestEvent> key2 =
                EfsTopicKey.getKey(
                    eventClass, "/acme/equity/ABA");
            final WildcardAdvertisement<TestEvent> wildAd;

            // Add topics that should match and should not match.
            // Note: In real implementation, topic matching
            // happens during wildcard subscription creation
            // This test verifies the infrastructure supports it.
            mEventBus.addTopic(key0);
            mEventBus.addTopic(key1);
            mEventBus.addTopic(key2);

            wildAd = mEventBus.advertiseAll(eventClass,
                                            regexTopic,
                                            sscb,
                                            topicUpdate,
                                            publisher);

            try
            {
                signal.await();
            }
            catch (InterruptedException interruption)
            {}

            assertThat(discoveredTopics.size()).isEqualTo(2);
            assertThat(discoveredTopics).contains(key0, key2)
                                        .doesNotContain(key1);
            assertThat(wildAd.advertisementKeys())
                .contains(key0, key2)
                .doesNotContain(key1);
            assertThat(wildAd.advertisementTopics())
                .contains(key0.topic(), key2.topic())
                .doesNotContain(key1.topic());

            assertThat(
                wildAd.advertisement(
                    discoveredTopics.getFirst())).isNotNull();

            wildAd.close();
            assertThat(wildAd.isOpen()).isFalse();
        } // end of wildcardAdvertisementTopicMatchingOnCreationTest()

        @Test
        @DisplayName("Should match wildcard ad topics on demand")
        public void wildcardAdvertisementTopicMatchingTest()
            throws Exception
        {
            final List<EfsTopicKey<TestEvent>> discoveredTopics =
                new ArrayList<>();
            final CountDownLatch signal = new CountDownLatch(2);
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic = "/acme/equity/A.*";
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k ->
                {
                    discoveredTopics.add(k);
                    signal.countDown();
                };
            final IEfsAgent publisher = sMockPublisher;
            final EfsTopicKey<TestEvent> key0 =
                EfsTopicKey.getKey(
                    eventClass, "/acme/equity/ABLE");
            final EfsTopicKey<TestEvent> key1 =
                EfsTopicKey.getKey(
                    eventClass, "/acme/equity/BRV");
            final EfsTopicKey<TestEvent> key2 =
                EfsTopicKey.getKey(
                    eventClass, "/acme/equity/ABA");
            final WildcardAdvertisement<TestEvent> wildAd =
                mEventBus.advertiseAll(eventClass,
                                       regexTopic,
                                       sscb,
                                       topicUpdate,
                                       publisher);

            // Add topics that should match and should not match.
            // Note: In real implementation, topic matching
            // happens during wildcard subscription creation
            // This test verifies the infrastructure supports it.
            mEventBus.addTopic(key0);
            mEventBus.addTopic(key1);
            mEventBus.addTopic(key2);

            try
            {
                signal.await();
            }
            catch (InterruptedException interruption)
            {}

            assertThat(discoveredTopics.size()).isEqualTo(2);
            assertThat(discoveredTopics).contains(key0, key2)
                                        .doesNotContain(key1);
            assertThat(wildAd.advertisementKeys())
                .contains(key0, key2)
                .doesNotContain(key1);
            assertThat(wildAd.advertisementTopics())
                .contains(key0.topic(), key2.topic())
                .doesNotContain(key1.topic());

            wildAd.close();
            assertThat(wildAd.isOpen()).isFalse();
        } // end of wildcardAdvertisementTopicMatchingTest()
    } // end of class WildcardAdvertisementTests

    // ========== Multiple Topics and Agents ==========

   @Nested
    @DisplayName("Multiple Topics and Agents")
    public final class MultipleTopicsAndAgentsTests
   {
        @Test
        @DisplayName("Should handle multiple advertisers for same topic")
        public void multipleAdvertisersForSameTopicTest()
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final IEfsAgent publisher1 = sMockPublisher;
            final IEfsAgent publisher2 = sMockAnotherPublisher;
            final Advertisement<TestEvent> ad1 =
                mEventBus.advertise(topicKey,
                                    sscb,
                                    publisher1);
            final Advertisement<TestEvent> ad2 =
                mEventBus.advertise(topicKey,
                                    sscb,
                                    publisher2);

            assertThat(ad1).isNotNull();
            assertThat(ad2).isNotNull();
            assertThat(ad1).isNotSameAs(ad2);
        } // end of multipleAdvertisersForSameTopicTest()

        @Test
        @DisplayName("Should handle multiple subscribers for same topic")
        public void multipleSubscribersForSameTopicTest()
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final IEfsAgent subscriber1 = sMockSubscriber;
            final IEfsAgent subscriber2 = sMockAnotherSubscriber;
            final Subscription<TestEvent> sub1 =
                mEventBus.subscribe(topicKey,
                                    pscb,
                                    ecb,
                                    subscriber1);
            final  Subscription<TestEvent> sub2 =
                mEventBus.subscribe(topicKey,
                                    pscb,
                                    ecb,
                                    subscriber2);

            assertThat(sub1).isNotNull();
            assertThat(sub2).isNotNull();
            assertThat(sub1).isNotSameAs(sub2);
        } // end of multipleSubscribersForSameTopicTest()

        @Test
        @DisplayName("Should handle multiple topics")
        public void multipleTopicsTest()
        {
            final EfsTopicKey<TestEvent> topicKey1 =
                mTestTopicKey;
            final EfsTopicKey<AnotherTestEvent> topicKey2 =
                mAnotherTopicKey;
            final IEfsAgent publisher = sMockPublisher;
            final Advertisement<TestEvent> ad1 =
                mEventBus.advertise(topicKey1,
                                    status -> {},
                                    publisher);
            final Advertisement<AnotherTestEvent> ad2 =
                mEventBus.advertise(topicKey2,
                                    status -> {},
                                    publisher);

            assertNotNull(ad1);
            assertNotNull(ad2);
            assertNotEquals(ad1.topicKey(), ad2.topicKey());
        } // end of multipleTopicsTest()
    } // end of class MultipleTopicsAndAgentsTests

    // ========== Edge Cases and Stress Tests ==========

    @Nested
    @DisplayName("Edge Cases and Stress Tests")
    public final class EdgeCasesAndStressTests
    {
        @Test
        @DisplayName("Should handle rapid advertisement and subscription cycles")
        public void rapidCyclesTest()
            throws Exception
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final IEfsAgent publisher = sMockPublisher;
            final IEfsAgent subscriber = sMockSubscriber;
            Advertisement<TestEvent> ad;
            Subscription<TestEvent> sub;

            for (int i = 0; i < 100; i++) {
                ad =
                    mEventBus.advertise(
                        topicKey, sscb, publisher);

                sub =
                    mEventBus.subscribe(
                        topicKey, pscb, ecb, subscriber);

                ad.close();
                sub.close();
            }
        } // end of rapidCyclesTest()

        @Test
        @DisplayName("Should handle complex regex patterns")
        public void complexRegexPatternsTest()
        {
            final Class<TestEvent> eventClass = TestEvent.class;
            final String regexTopic =
                "^(service|event|message)-(prod|staging|dev)-\\d+$";
            final boolean inboxFlag = false;
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final Consumer<EfsTopicKey<TestEvent>> topicUpdate =
                k -> {};
            final IEfsAgent subscriber = sMockSubscriber;
            final WildcardSubscription<TestEvent> wildsub =
                mEventBus.subscribeAll(eventClass,
                                       regexTopic,
                                       inboxFlag,
                                       pscb,
                                       ecb,
                                       topicUpdate,
                                       subscriber);

            assertThat(wildsub).isNotNull();
            assertThat(wildsub.isOpen()).isTrue();
        } // end of complexRegexPatternsTest()

        @Test
        @DisplayName("Should handle special characters in topic names")
        public void specialCharactersInTopicNamesTest()
        {
            final EfsTopicKey<TestEvent> topicKey =
                EfsTopicKey.getKey(
                    TestEvent.class,
                    "topic-with-special_chars.123");
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final IEfsAgent publisher = sMockPublisher;
            final Advertisement<TestEvent> ad =
                mEventBus.advertise(topicKey,
                                    sscb,
                                    publisher);

            assertThat(ad).isNotNull();
            assertThat(ad.isOpen()).isTrue();
        } // end of specialCharactersInTopicNamesTest()

        @Test
        @DisplayName("Should handle publishing without subscribers")
        public void publishWithoutSubscribersTest()
        {
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> {};
            final IEfsAgent publisher = sMockPublisher;
            final Advertisement<TestEvent> ad =
                mEventBus.advertise(topicKey,
                                    sscb,
                                    publisher);
            final TestEvent event = mock(TestEvent.class);

            ad.publishStatus(true);

            // Should not throw even though there are no subscribers
            ad.publish(event);
        } // end of publishWithoutSubscribersTest()

        @Test
        @DisplayName("Should check if hasSubscribers")
        public void hasSubscribersTest()
            throws Exception
        {
            final Lock statusLock = new ReentrantLock(true);
            final Condition signal = statusLock.newCondition();
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status ->
                {
                    statusLock.lock();
                    try
                    {
                        signal.signal();
                    }
                    finally
                    {
                        statusLock.unlock();
                    }
                };
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final IEfsAgent publisher = sMockPublisher;
            final IEfsAgent subscriber = sMockSubscriber;
            final Advertisement<TestEvent> ad =
                mEventBus.advertise(topicKey, sscb, publisher);
            final Subscription<TestEvent> sub;

            assertThat(ad.hasSubscribers()).isFalse();

            // Note: hasSubscribers() result depends on timing and
            // EfsDispatcher implementation.
            statusLock.lock();
            try
            {
                sub =
                    mEventBus.subscribe(
                        topicKey, pscb, ecb, subscriber);
                signal.await();
            }
            finally
            {
                statusLock.unlock();
            }

            assertThat(ad.hasSubscribers()).isTrue();

            statusLock.lock();
            try
            {
                sub.close();
                signal.await();
            }
            finally
            {
                statusLock.unlock();
            }

            assertThat(ad.hasSubscribers()).isFalse();
        } // end of hasSubscribersTest()

        @Test
        @DisplayName("Should handle large number of subscriptions")
        public void largeNumberOfSubscriptionsTest()
            throws Exception
        {
            final int numSubs = 50;
            final List<Subscription<TestEvent>> subs =
                new ArrayList<>(numSubs);
            final CountDownLatch signal =
                new CountDownLatch(numSubs * 2);
            final EfsTopicKey<TestEvent> topicKey =
                mTestTopicKey;
            final Consumer<EfsSubscribeStatus<TestEvent>> sscb =
                status -> signal.countDown();
            final Consumer<EfsPublishStatus<TestEvent>> pscb =
                status -> {};
            final Consumer<TestEvent> ecb = event -> {};
            final IEfsAgent publisher = sMockPublisher;
            IEfsAgent subscriber;
            String subName;

            try (Advertisement<TestEvent> ad =
                     mEventBus.advertise(topicKey, sscb, publisher))
            {
                for (int i = 0; i < numSubs; ++i)
                {
                    subscriber = mock(IEfsAgent.class);
                    subName = "Subscriber-" + i;

                    when(subscriber.name()).thenReturn(subName);
                    EfsDispatcher.register(
                        subscriber, TEST_DISPATCHER_NAME);

                    subs.add(
                        mEventBus.subscribe(
                            topicKey, pscb, ecb, subscriber));
                }
                assertThat(ad.hasSubscribers()).isTrue();
                for (Subscription<TestEvent> sub : subs)
                {
                    sub.close();
                }
                try
                {
                    signal.await();
                }
                catch (InterruptedException interrupt)
                {}
                assertThat(ad.hasSubscribers()).isFalse();
            }        } // end of largeNumberOfSubscriptionsTest()
    } // end of class EdgeCasesAndStressTests

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

//---------------------------------------------------------------
// Inner classes.
//

    private interface TestEvent
        extends IEfsEvent
    {}

    private interface AnotherTestEvent
        extends IEfsEvent
    {}
} // end of class EfsEventBusTest