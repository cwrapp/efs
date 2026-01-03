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
import java.util.function.Consumer;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import org.efs.dispatcher.IEfsAgent;
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

public class EfsFeedTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    public static final String TEST_SUBJECT = "/test/subject";
    public static final EfsFeedKey<SampleEvent> TEST_KEY =
        EfsFeedKey.getKey(SampleEvent.class, TEST_SUBJECT);

    private static final String AGENT_NAME_PREFIX = "TestAgent-";

    //-----------------------------------------------------------
    // Statics.
    //

    private static int sAgentIndex = 0;

    //-----------------------------------------------------------
    // Locals.
    //

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
    }

    @AfterAll
    public static void tearDownClass()
    {
    }

    @BeforeEach
    public void setUp()
    {
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

    //
    // Open tests.
    //

    @Test
    public void openFeedNullKey()
    {
        final EfsFeedKey<SampleEvent> feedKey = null;

        try
        {
            EfsFeed.openFeed(feedKey);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(EfsFeed.INVALID_FEED_KEY);
        }
    } // end of openFeedNullKey()

    @Test
    public void openSuccess()
    {
        final EfsFeedKey<SampleEvent> feedKey = TEST_KEY;
        final EfsFeed<SampleEvent> feed0 =
            EfsFeed.openFeed(feedKey);

        assertThat(feed0).isNotNull();
        assertThat(feed0.key()).isEqualTo(feedKey);

        final EfsFeed<SampleEvent> feed1 =
            EfsFeed.openFeed(feedKey);

        assertThat(feed1).isSameAs(feed0);
    } // end of openSuccess()

    //
    // Publish tests.
    //

    @Test
    public void publishNullEvent()
    {
        final SampleEvent event = null;
        final EfsFeed<SampleEvent> feed =
            EfsFeed.openFeed(TEST_KEY);

        try
        {
            feed.publish(event);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(EfsFeed.INVALID_EVENT);
        }
    } // end of publishNullEvent()

    //
    // Subscribe tests.
    //

    @Test
    public void subscribeNullSubscriber()
    {
        final TestSubscriber subscriber = null;
        final Consumer<SampleEvent> callback = this::onEvent;
        final EfsInterval interval =
            createInterval(
                createEndpoint(-10, Clusivity.EXCLUSIVE),
                createEndpoint(10, Clusivity.INCLUSIVE));
        final EfsFeed<SampleEvent> feed =
            EfsFeed.openFeed(TEST_KEY);

        try
        {
            feed.subscribe(subscriber, callback, interval);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(EfsFeed.INVALID_SUBSCRIBER);
        }
    } // end of subscribeNullSubscriber()

    @Test
    public void subscribeNullCallback()
    {
        final TestSubscriber subscriber = createSubscriber();
        final Consumer<SampleEvent> callback = null;
        final EfsInterval interval =
            createInterval(
                createEndpoint(-10, Clusivity.EXCLUSIVE),
                createEndpoint(10, Clusivity.INCLUSIVE));
        final EfsFeed<SampleEvent> feed =
            EfsFeed.openFeed(TEST_KEY);

        try
        {
            feed.subscribe(subscriber, callback, interval);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(EfsFeed.INVALID_CALLBACK);
        }
    } // end of subscribeNullCallback()

    @Test
    public void subscribeNullInterval()
    {
        final TestSubscriber subscriber = createSubscriber();
        final Consumer<SampleEvent> callback =
            subscriber::onEvent;
        final EfsInterval interval = null;
        final EfsFeed<SampleEvent> feed =
            EfsFeed.openFeed(TEST_KEY);

        try
        {
            feed.subscribe(subscriber, callback, interval);
        }
        catch (NullPointerException nullex)
        {
            assertThat(nullex)
                .hasMessage(EfsFeed.INVALID_INTERVAL);
        }
    } // end of subscribeNullInterval()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

    private void onEvent(final SampleEvent event)
    {}

    private static TestSubscriber createSubscriber()
    {
        final String agentName =
            AGENT_NAME_PREFIX + sAgentIndex++;

        return (new TestSubscriber(agentName));
    } // end of createSubscriber()

    private static EfsInterval createInterval(final EfsIntervalEndpoint beginning,
                                              final EfsIntervalEndpoint ending)
    {
        final EfsInterval.Builder builder =
            EfsInterval.builder();

        return (builder.beginning(beginning)
                       .ending(ending)
                       .build());
    } // end of createInterval(...)

    private EfsTimeEndpoint createEndpoint(final long delta,
                                           final Clusivity clusivity)
    {
        final Instant time = mCurrentTime.plusSeconds(delta);
        final EfsTimeEndpoint.Builder builder =
            EfsTimeEndpoint.builder();

        return (builder.time(time, clusivity).build());
    } // end of createEndpoint(long, Clusivity)

    private EfsIndexEndpoint createEndpoint(final int offset,
                                            final Clusivity clusivity)
    {
        final EfsIndexEndpoint.Builder builder =
            EfsIndexEndpoint.builder();

        return (builder.indexOffset(offset, clusivity).build());
    } // end of createEndpoint(int, Clusivity)

//---------------------------------------------------------------
// Inner classes.
//

    private static final class TestSubscriber
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

        /**
         * Unique agent name.
         */
        private final String mAgentName;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private TestSubscriber(final String name)
        {
            mAgentName = name;
        } // end of TestSubscriber(String)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // IEfsAgent Interface Implementation.
        //

        @Override
        public String name()
        {
            return (mAgentName);
        } // end of name()

        //
        // end of IEfsAgent Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //
        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //
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
        // Event Handlers.
        //

        private void onEvent(final SampleEvent event)
        {
            // TODO
        } // end of onEvent(SampleEvent)

        //
        // end of Event Handlers.
        //-------------------------------------------------------
    } // end of class TestSubscriber
} // end of class EfsFeedTest