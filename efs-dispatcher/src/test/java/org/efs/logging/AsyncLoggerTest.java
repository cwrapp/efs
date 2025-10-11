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
package org.efs.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Marker;

/**
 *
 * @author charlesr
 */
public class AsyncLoggerTest
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    private org.slf4j.Logger mLogger;
    private ListAppender<ILoggingEvent> mAppender;

//---------------------------------------------------------------
// Member functions.
//

    //-----------------------------------------------------------
    // JUnit Initialization.
    //

    @BeforeEach
    public void setUp()
    {
        // Create and start list appender.
        mAppender = new ListAppender<>();
        mAppender.start();
    } // end of setUp()

    @AfterEach
    public void tearDown()
    {
        if (mLogger != null)
        {
            final Logger nestedLogger =
                (Logger) ((AsyncLogger) mLogger).nestedLogger();

            if (nestedLogger != null)
            {
                nestedLogger.detachAndStopAllAppenders();
            }
        }
    } // end of tearDown()

    //
    // end of JUnit Initialization.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // JUnit Tests.
    //

    @Test
    public void factoryGetLoggerNoArg()
    {
        mLogger = AsyncLoggerFactory.getLogger();

        assertThat(mLogger).isNotNull();
        assertThat(mLogger.getName())
            .isEqualTo((AsyncLoggerTest.class).getName());
    } // end of factoryGetLoggerNoArg()

    @Test
    public void factoryGetLoggerClass()
    {
        final Class<?> clazz = AsyncLoggerTest.class;

        mLogger = AsyncLoggerFactory.getLogger(clazz);

        assertThat(mLogger).isNotNull();
        assertThat(mLogger.getName())
            .isEqualTo((AsyncLoggerTest.class).getName());
    } // end of factoryGetLoggerClass()

    @Test
    public void factoryGetLoggerName()
    {
        final String name = "fubar";

        mLogger = AsyncLoggerFactory.getLogger(name);

        assertThat(mLogger).isNotNull();
        assertThat(mLogger.getName()).isEqualTo(name);
    } // end of factoryGetLoggerName()

    @Test
    public void loggingTest()
    {
        final Logger nestedLogger;
        final Marker marker = new TestMarker();

        mLogger = AsyncLoggerFactory.getLogger();

        nestedLogger =
            (Logger) ((AsyncLogger) mLogger).nestedLogger();
        nestedLogger.addAppender(mAppender);
        nestedLogger.setLevel(Level.TRACE);

        assertThat(mLogger.isTraceEnabled()).isTrue();
        assertThat(mLogger.isTraceEnabled(marker)).isTrue();
        assertThat(mLogger.isDebugEnabled()).isTrue();
        assertThat(mLogger.isDebugEnabled(marker)).isTrue();
        assertThat(mLogger.isInfoEnabled()).isTrue();
        assertThat(mLogger.isInfoEnabled(marker)).isTrue();
        assertThat(mLogger.isWarnEnabled()).isTrue();
        assertThat(mLogger.isWarnEnabled(marker)).isTrue();
        assertThat(mLogger.isErrorEnabled()).isTrue();
        assertThat(mLogger.isErrorEnabled(marker)).isTrue();

        final int numMessages = 50;
        int index;
        final String[] messages = new String[numMessages];
        final String format0 = "{}";
        final String format1 = "[{}] {}";
        final String format2 = "[{}] {} {}";
        final String arg = "fubar";
        final Throwable t =
            new NullPointerException("test exception");

        for (index = 0; index < numMessages; ++index)
        {
            messages[index] = "Message " + index;
        }

        index = 0;

        // trace logging.
        mLogger.trace(messages[index++]);
        mLogger.trace(format0, messages[index++]);
        mLogger.trace(format1, index, messages[index++]);
        mLogger.trace(format2, index, messages[index++], arg);
        mLogger.trace(messages[index++], t);

        // trace logging with marker.
        mLogger.trace(marker, messages[index++]);
        mLogger.trace(marker, format0, messages[index++]);
        mLogger.trace(marker, format1, index, messages[index++]);
        mLogger.trace(
            marker, format2, index, messages[index++], arg);
        mLogger.trace(marker, messages[index++], t);

        // debug logging.
        mLogger.debug(messages[index++]);
        mLogger.debug(format0, messages[index++]);
        mLogger.debug(format1, index, messages[index++]);
        mLogger.debug(format2, index, messages[index++], arg);
        mLogger.debug(messages[index++], t);

        // debug logging with marker.
        mLogger.debug(marker, messages[index++]);
        mLogger.debug(marker, format0, messages[index++]);
        mLogger.debug(marker, format1, index, messages[index++]);
        mLogger.debug(
            marker, format2, index, messages[index++], arg);
        mLogger.debug(marker, messages[index++], t);

        // info logging.
        mLogger.info(messages[index++]);
        mLogger.info(format0, messages[index++]);
        mLogger.info(format1, index, messages[index++]);
        mLogger.info(format2, index, messages[index++], arg);
        mLogger.info(messages[index++], t);

        // info logging with marker.
        mLogger.info(marker, messages[index++]);
        mLogger.info(marker, format0, messages[index++]);
        mLogger.info(marker, format1, index, messages[index++]);
        mLogger.info(
            marker, format2, index, messages[index++], arg);
        mLogger.info(marker, messages[index++], t);

        // warn logging.
        mLogger.warn(messages[index++]);
        mLogger.warn(format0, messages[index++]);
        mLogger.warn(format1, index, messages[index++]);
        mLogger.warn(format2, index, messages[index++], arg);
        mLogger.warn(messages[index++], t);

        // warn logging with marker.
        mLogger.warn(marker, messages[index++]);
        mLogger.warn(marker, format0, messages[index++]);
        mLogger.warn(marker, format1, index, messages[index++]);
        mLogger.warn(
            marker, format2, index, messages[index++], arg);
        mLogger.warn(marker, messages[index++], t);

        // error logging.
        mLogger.error(messages[index++]);
        mLogger.error(format0, messages[index++]);
        mLogger.error(format1, index, messages[index++]);
        mLogger.error(format2, index, messages[index++], arg);
        mLogger.error(messages[index++], t);

        // error logging with marker.
        mLogger.error(marker, messages[index++]);
        mLogger.error(marker, format0, messages[index++]);
        mLogger.error(marker, format1, index, messages[index++]);
        mLogger.error(
            marker, format2, index, messages[index++], arg);
        mLogger.error(marker, messages[index++], t);

        // Wait for asynchronous logging to complete.
        await().atLeast(60L, TimeUnit.SECONDS);

        final List<ILoggingEvent> logs = mAppender.list;
        ILoggingEvent event;
        String message;

        assertThat(logs).isNotNull();

        for (index = 0; index < logs.size(); ++index)
        {
            event = logs.get(index);
            message = event.getFormattedMessage();

            assertThat(message).contains(messages[index]);
        }
    } // end of loggingTest()

    //
    // end of JUnit Tests.
    //-----------------------------------------------------------

//---------------------------------------------------------------
// Inner classes.
//

    private static final class TestMarker
        implements Marker
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Constants.
        //

        private static final long serialVersionUID = 1L;

        //-------------------------------------------------------
        // Locals.
        //

        private final String mThreadName;
        private final Instant mTimestamp;
        private final String mMarkerName;
        private final ArrayList<Marker> mNestedMarkers;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private TestMarker()
        {
            mThreadName = (Thread.currentThread()).getName();
            mTimestamp = Instant.now();
            mMarkerName =
                String.format("%s-%s", mThreadName, mTimestamp);
            mNestedMarkers = new ArrayList<>();
        } // end of TestMarker()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Marker Interface Implmentation.
        //

        @Override
        public String getName()
        {
            return (mMarkerName);
        } // end of getName()

        @Override
        public void add(final Marker marker)
        {
            if (marker == null)
            {
                throw (
                    new IllegalArgumentException(
                        "marker is null"));
            }

            if (contains(marker))
            {
                throw (
                    new IllegalStateException(
                        "marker already listed"));
            }

            mNestedMarkers.add(marker);
        } // end of add(Marker)

        @Override
        public boolean remove(final Marker marker)
        {
            boolean retcode = (marker != null);

            if (retcode)
            {
                retcode = mNestedMarkers.remove(this);
            }

            return (retcode);
        } // end of remove(Marker)

        @Override
        // Deprecated interface methods still need to be implemented.
        @SuppressWarnings({"deprecation"})
        public boolean hasChildren()
        {
            return (!mNestedMarkers.isEmpty());
        } // end of hasChildren()

        @Override
        public boolean hasReferences()
        {
            return (!mNestedMarkers.isEmpty());
        } // end of hasReferences()

        @Override
        public Iterator<Marker> iterator()
        {
            return (mNestedMarkers.iterator());
        } // end of iterator()

        @Override
        public boolean contains(final Marker marker)
        {
            boolean retcode = (this == marker);

            if (!retcode)
            {
                final Iterator<Marker> mIt =
                    mNestedMarkers.iterator();

                while (!retcode && mIt.hasNext())
                {
                    retcode = (mIt.next()).contains(marker);
                }
            }

            return (retcode);
        } // end of contains(Marker)

        @Override
        public boolean contains(final String name)
        {
            boolean retcode;

            if (name == null)
            {
                retcode = false;
            }
            else if (mMarkerName.equals(name))
            {
                retcode = true;
            }
            else
            {
                final Iterator<Marker> mIt =
                    mNestedMarkers.iterator();

                retcode = false;

                while (!retcode && mIt.hasNext())
                {
                    retcode = (mIt.next()).contains(name);
                }
            }

            return (retcode);
        } // end of contains(String)

        //
        // end of Marker Interface Implmentation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        @Override
        public boolean equals(final Object o)
        {
            boolean retcode = super.equals(o);

            if (!retcode && o instanceof TestMarker)
            {
                retcode =
                    mMarkerName.equals(
                        ((TestMarker) o).mMarkerName);
            }

            return (retcode);
        } // end of equals(Object)

        @Override
        public int hashCode()
        {
            return (mMarkerName.hashCode());
        } // end of hashCode()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------
    } // end of class TestMarker
} // end of class AsyncLoggerTest
