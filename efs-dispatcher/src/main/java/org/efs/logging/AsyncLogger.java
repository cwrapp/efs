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

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Consumer;
import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.EfsDispatcher.DispatcherType;
import org.efs.dispatcher.IEfsAgent;
import org.efs.dispatcher.config.ThreadType;
import org.efs.event.IEfsEvent;
import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * This class implements {@link Logger} interface with the goal
 * of performing the actual logging asynchronously on an efs
 * dispatcher thread. Normally a logger outputs the log message
 * in-line to the application code which can be an issue for
 * low-latency performance. Off-loading this logging to a
 * separate thread addresses the issue. The downside to this
 * solution is that the actual logging takes place some time
 * after the logging call. This means that trying to match
 * logging messages to when an application event occurred can be
 * problematic.
 * <p>
 * An {@code AsyncLogger} instance is created using
 * {@link AsyncLoggerFactory} {@code getLogger} method.
 * </p>
 * <p>
 * By default events dispatched to the {@code AsyncLoggerAgent}
 * instance are run on the default efs dispatcher. If there is a
 * need to associate {@code AsyncLoggerAgent} with a different
 * efs dispatcher, this can be achieved by adding the following
 * configuration to the {@code dispatchers} list in your efs
 * dispatcher configuration file:
 * </p>
 * <pre><code>{
    name : logDispatcher
    numberThreads : 1
    priority : 2
    quantum : 500 millis
    isDefault : false
    classes : [ "org.efs.logging.AsyncLogger$AsyncLoggerAgent" ]
}</code></pre>
 * <p>
 * Please note that the logging methods first verify that the log
 * message level is enabled <em>before</em> dispatching the
 * logging event. This check is deemed cheap enough not impact
 * application performance.
 * </p>
 * <p>
 * See
 * <a href="https://www.slf4j.org/" target="_blank">slf4j website</a>
 * for detailed explanation on using this logging framework.
 * </p>
 *
 * @see AsyncLoggerFactory
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class AsyncLogger
    implements Logger

{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Asynchronous logging is performed on dispatcher {@value}.
     */
    public static final String LOGGER_DISPATCHER_NAME =
        "AsyncLoggerDispatcher";

    /**
     * Log up to {@value} messages at one time.
     */
    private static final int MAX_LOGGING_EVENTS = 64;

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Post logging events to this async logging agent.
     */
    private static final AsyncLoggerAgent sAsyncLogger;

    /**
     * Lambda expression for
     * {@link AsyncLoggerAgent#onLogEvent(IEfsEvent)} callback.
     */
    private static final Consumer<IEfsEvent> sCallback;

    // Class static initialization.
    static
    {
        final EfsDispatcher.Builder builder =
            EfsDispatcher.builder();

        // The act of building a Dispatcher result in the
        // instance being added to the dispatcher list.
        builder.dispatcherName(LOGGER_DISPATCHER_NAME)
               .numThreads(1)
               .threadType(ThreadType.BLOCKING)
               .priority(Thread.MIN_PRIORITY)
               .dispatcherType(DispatcherType.EFS)
               .eventQueueCapacity(MAX_LOGGING_EVENTS)
               .runQueueCapacity(1)
               .maxEvents(MAX_LOGGING_EVENTS)
               .build();

        // Create the logging agent, register it and then get
        // its associated dispatcher.
        sAsyncLogger = new AsyncLoggerAgent();

        // Lambda used to pass LogEvents to AsyncLoggerAgent.
        sCallback = sAsyncLogger::onLogEvent;

        EfsDispatcher.register(
            sAsyncLogger, LOGGER_DISPATCHER_NAME);
    } // end of class static initialization.

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Create a {@code Runnable} task containing this logger to
     * perform a logging task asynchronously.
     */
    private final Logger mNestedLogger;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new asynchronous logger containing the given
     * nested logger.
     * @param logger nested logger.
     */
    /* package */ AsyncLogger(final Logger logger)
    {
        mNestedLogger = logger;
    } // end of AsyncLogger(Logger)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Logger Interface Implementation.
    //

    /**
     * Returns logger instance's name.
     * @return logger instance name.
     */
    @Override
    public String getName()
    {
        return (mNestedLogger.getName());
    } // end of getName()

    @Override
    public boolean isTraceEnabled()
    {
        return (mNestedLogger.isTraceEnabled());
    } // end of isTraceEnabled()

    @Override
    public void trace(final String message)
    {
        // Is trace logging enabled?
        if (mNestedLogger.isTraceEnabled())
        {
            // Yes.
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.trace(message)));
        }
    } // end of trace(String)

    @Override
    public void trace(final String format,
                      final Object arg)
    {
        if (mNestedLogger.isTraceEnabled())
        {
            dispatch(
                new LogEvent(
                    () ->  mNestedLogger.trace(format, arg)));
        }
    } // end of trace(String, Object)

    @Override
    public void trace(final String format,
                      final Object arg0,
                      final Object arg1)
    {
        if (mNestedLogger.isTraceEnabled())
        {
            dispatch(
                new LogEvent(
                    () ->
                        mNestedLogger.trace(
                            format, arg0, arg1)));
        }
    } // end of trace(String, Object, Object)

    @Override
    public void trace(final String format,
                      final Object... args)
    {
        if (mNestedLogger.isTraceEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.trace(format, args)));
        }
    } // end of trace(String, Object...)

    @Override
    public void trace(final String msg,
                      final Throwable t)
    {
        if (mNestedLogger.isTraceEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.trace(msg, t)));
        }
    } // end of trace(String, Throwable)

    @Override
    public boolean isTraceEnabled(final Marker marker)
    {
        return (mNestedLogger.isTraceEnabled(marker));
    } // end of isTraceEnabled(Marker)

    @Override
    public void trace(final Marker marker,
                      final String msg)
    {
        if (mNestedLogger.isTraceEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.trace(marker, msg)));
        }
    } // end of trace(Marker, String)

    @Override
    public void trace(final Marker marker,
                      final String format,
                      final Object arg)
    {
        if (mNestedLogger.isTraceEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () ->
                        mNestedLogger.trace(
                            marker, format, arg)));
        }
    } // end of trace(Marker, String, Object)

    @Override
    public void trace(final Marker marker,
                      final String format,
                      final Object arg0,
                      final Object arg1)
    {
        if (mNestedLogger.isTraceEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.trace(marker,
                                              format,
                                              arg0,
                                              arg1)));
        }
    } // end of trace(Marker, String, Object, Object)

    @Override
    public void trace(final Marker marker,
                      final String format,
                      final Object... args)
    {
        if (mNestedLogger.isTraceEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () ->
                        mNestedLogger.trace(
                            marker, format, args)));
        }
    } // end of trace(Marker, String, Object...)

    @Override
    public void trace(final Marker marker,
                      final String msg,
                      final Throwable t)
    {
        if (mNestedLogger.isTraceEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.trace(marker, msg, t)));
        }
    } // end of trace(Marker, String, Throwable)

    @Override
    public boolean isDebugEnabled()
    {
        return (mNestedLogger.isDebugEnabled());
    } // end of isDebugEnabled()

    @Override
    public void debug(final String msg)
    {
        if (mNestedLogger.isDebugEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.debug(msg)));
        }
    } // end of debug(String)

    @Override
    public void debug(final String format,
                      final Object arg)
    {
        if (mNestedLogger.isDebugEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.debug(format, arg)));
        }
    } // end of debug(String, Object)

    @Override
    public void debug(final String format,
                      final Object arg0,
                      final Object arg1)
    {
        if (mNestedLogger.isDebugEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.debug(format,
                                              arg0,
                                              arg1)));
        }
    } // end of debug(String, Object, Object)

    @Override
    public void debug(final String format,
                      final Object... args)
    {
        if (mNestedLogger.isDebugEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.debug(format, args)));
        }
    } // end of debug(String, Object...)

    @Override
    public void debug(final String msg,
                      final Throwable t)
    {
        if (mNestedLogger.isDebugEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.debug(msg, t)));
        }
    } // end of debug(String, Throwable)

    @Override
    public boolean isDebugEnabled(final Marker marker)
    {
        return (mNestedLogger.isDebugEnabled(marker));
    } // end of isDebugEnabled(Marker)

    @Override
    public void debug(final Marker marker,
                      final String msg)
    {
        if (mNestedLogger.isDebugEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.debug(marker, msg)));
        }
    } // end of debug(Marker, String)

    @Override
    public void debug(final Marker marker,
                      final String format,
                      final Object arg)
    {
        if (mNestedLogger.isDebugEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.debug(marker,
                                              format,
                                              arg)));
        }
    } // end of debug(Marker, String, Object)

    @Override
    public void debug(final Marker marker,
                      final String format,
                      final Object arg0,
                      final Object arg1)
    {
        if (mNestedLogger.isDebugEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.debug(marker,
                                              format,
                                              arg0,
                                              arg1)));
        }
    } // end of debug(Marker, String, Object, Object)

    @Override
    public void debug(final Marker marker,
                      final String format,
                      final Object... args)
    {
        if (mNestedLogger.isDebugEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.debug(marker,
                                              format,
                                              args)));
        }
    } // end of debug(Marker, String, Object...)

    @Override
    public void debug(final Marker marker,
                      final String msg,
                      final Throwable t)
    {
        if (mNestedLogger.isDebugEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.debug(marker, msg, t)));
        }
    } // end of debug(Marker, String, Throwable)

    @Override
    public boolean isInfoEnabled()
    {
        return (mNestedLogger.isInfoEnabled());
    } // end of isInfoEnabled()

    @Override
    public void info(final String msg)
    {
        if (mNestedLogger.isInfoEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.info(msg)));
        }
    } // end of info(String)

    @Override
    public void info(final String format,
                     final Object arg)
    {
        if (mNestedLogger.isInfoEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.info(format, arg)));
        }
    } // end of info(String, Object)

    @Override
    public void info(final String format,
                     final Object arg0,
                     final Object arg1)
    {
        if (mNestedLogger.isInfoEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.info(format,
                                             arg0,
                                             arg1)));
        }
    } // end of info(String, Object, Object)

    @Override
    public void info(final String format,
                     final Object... args)
    {
        if (mNestedLogger.isInfoEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.info(format, args)));
        }
    } // end of info(String, Object...)

    @Override
    public void info(final String msg,
                     final Throwable t)
    {
        if (mNestedLogger.isInfoEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.info(msg, t)));
        }
    } // end of info(String, Throwable)

    @Override
    public boolean isInfoEnabled(final Marker marker)
    {
        return (mNestedLogger.isInfoEnabled(marker));
    } // end of isInfoEnabled(Marker)

    @Override
    public void info(final Marker marker,
                     final String msg)
    {
        if (mNestedLogger.isInfoEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.info(marker, msg)));
        }
    } // end of info(Marker, String)

    @Override
    public void info(final Marker marker,
                     final String format,
                     final Object arg)
    {
        if (mNestedLogger.isInfoEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.info(marker,
                                             format,
                                             arg)));
        }
    } // end of info(Marker, String, Object)

    @Override
    public void info(final Marker marker,
                     final String format,
                     final Object arg0,
                     final Object arg1)
    {
        if (mNestedLogger.isInfoEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.info(marker,
                                             format,
                                             arg0,
                                             arg1)));
        }
    } // end of info(Marker, String, Object, Object)

    @Override
    public void info(final Marker marker,
                     final String format,
                     final Object... args)
    {
        if (mNestedLogger.isInfoEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.info(marker,
                                             format,
                                             args)));
        }
    } // end of info(Marker, String, Object...)

    @Override
    public void info(final Marker marker,
                     final String msg,
                     final Throwable t)
    {
        if (mNestedLogger.isInfoEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.info(marker, msg, t)));
        }
    } // end of info(Marker, String, Throwable)

    @Override
    public boolean isWarnEnabled()
    {
        return (mNestedLogger.isInfoEnabled());
    } // end of isWarnEnabled()

    @Override
    public void warn(final String msg)
    {
        if (mNestedLogger.isWarnEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.warn(msg)));
        }
    } // end of warn(String)

    @Override
    public void warn(final String format,
                     final Object arg)
    {
        if (mNestedLogger.isWarnEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.warn(format, arg)));
        }
    } // end of warn(String, Object)

    @Override
    public void warn(final String format,
                     final Object arg0,
                     final Object arg1)
    {
        if (mNestedLogger.isWarnEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.warn(format,
                                             arg0,
                                             arg1)));
        }
    } // end of warn(String, object, Object)

    @Override
    public void warn(final String format,
                     final Object... args)
    {
        if (mNestedLogger.isWarnEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.warn(format, args)));
        }
    } // end of warn(String, Object...)

    @Override
    public void warn(final String msg,
                     final Throwable t)
    {
        if (mNestedLogger.isWarnEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.warn(msg, t)));
        }
    } // end of warn(String, Throwable)

    @Override
    public boolean isWarnEnabled(final Marker marker)
    {
        return (mNestedLogger.isInfoEnabled(marker));
    } // end of isWarnEnabled(Marker)

    @Override
    public void warn(final Marker marker,
                     final String msg)
    {
        if (mNestedLogger.isWarnEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.warn(marker, msg)));
        }
    } // end of warn(Marker, String)

    @Override
    public void warn(final Marker marker,
                     final String format,
                     final Object arg)
    {
        if (mNestedLogger.isWarnEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.warn(marker,
                                             format,
                                             arg)));
        }
    } // end of warn(Marker, String, Object)

    @Override
    public void warn(final Marker marker,
                     final String format,
                     final Object arg0,
                     final Object arg1)
    {
        if (mNestedLogger.isWarnEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.warn(marker,
                                             format,
                                             arg0,
                                             arg1)));
        }
    } // end of warn(Marker, String, Object, Object)

    @Override
    public void warn(final Marker marker,
                     final String format,
                     final Object... args)
    {
        if (mNestedLogger.isWarnEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.warn(marker,
                                             format,
                                             args)));
        }
    } // end of warn(Marker, String, Object...)

    @Override
    public void warn(final Marker marker,
                     final String msg,
                     final Throwable t)
    {
        if (mNestedLogger.isWarnEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.warn(marker, msg, t)));
        }
    } // end of warn(Marker, String, Throwable)

    @Override
    public boolean isErrorEnabled()
    {
        return (mNestedLogger.isErrorEnabled());
    } // end of isErrorEnabled()

    @Override
    public void error(final String msg)
    {
        if (mNestedLogger.isErrorEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.error(msg)));
        }
    } // end of error(String)

    @Override
    public void error(final String format,
                      final Object arg)
    {
        if (mNestedLogger.isErrorEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.error(format, arg)));
        }
    } // end of error(String, Object)

    @Override
    public void error(final String format,
                      final Object arg0,
                      final Object arg1)
    {
        if (mNestedLogger.isErrorEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.error(format,
                                              arg0,
                                              arg1)));
        }
    } // end of error(String, Object, Object)

    @Override
    public void error(final String format,
                      final Object... args)
    {
        if (mNestedLogger.isErrorEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.error(format, args)));
        }
    } // end of error(String, Object...)

    @Override
    public void error(final String msg,
                      final Throwable t)
    {
        if (mNestedLogger.isErrorEnabled())
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.error(msg, t)));
        }
    } // end of error(String, Throwable)

    @Override
    public boolean isErrorEnabled(final Marker marker)
    {
        return (mNestedLogger.isErrorEnabled(marker));
    } // end of isErrorEnabled(Marker)

    @Override
    public void error(final Marker marker,
                      final String msg)
    {
        if (mNestedLogger.isErrorEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.error(marker, msg)));
        }
    } // end of error(Marker, String)

    @Override
    public void error(final Marker marker,
                      final String format,
                      final Object arg)
    {
        if (mNestedLogger.isErrorEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.error(marker,
                                              format,
                                              arg)));
        }
    } // end of error(Marker, String, Object)

    @Override
    public void error(final Marker marker,
                      final String format,
                      final Object arg0,
                      final Object arg1)
    {
        if (mNestedLogger.isErrorEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.error(marker,
                                              format,
                                              arg0,
                                              arg1)));
        }
    } // end of error(Marker, String, Object, Object)

    @Override
    public void error(final Marker marker,
                      final String format,
                      final Object... args)
    {
        if (mNestedLogger.isErrorEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.error(marker,
                                              format,
                                              args)));
        }
    } // end of error(Marker, String, Object...)

    @Override
    public void error(final Marker marker,
                      final String msg,
                      final Throwable t)
    {
        if (mNestedLogger.isErrorEnabled(marker))
        {
            dispatch(
                new LogEvent(
                    () -> mNestedLogger.error(marker, msg, t)));
        }
    } // end of error(Marker, String, Throwable)

    //
    // end of Logger Interface Implementation.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns nested logger. Allows nested logger to be
     * configured independently of this asynchronous logger.
     * @return nested logger.
     */
    @VisibleForTesting
    public Logger nestedLogger()
    {
        return (mNestedLogger);
    } // end of nestedLogger()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Dispatches given log event to async logging agent. Catches
     * {@code IllegalStateException} signaling dispatch failure
     * and logs event by executing log event task.
     * @param logEvent dispatch this logging event to async
     * logging agent.
     */
    private void dispatch(final LogEvent logEvent)
    {
        try
        {
            EfsDispatcher.dispatch(
                sCallback, logEvent, sAsyncLogger);
        }
        catch (IllegalStateException statex)
        {
            (logEvent.logTask()).run();
        }
    } // end of dispatch(LogEvent)

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Logs messages using the nested logger instance. This
     * class actually does nothing. All logging work is performed
     * by the {@code Runnable} task posted to this efs agent's
     * event queue. This class is public to allow users to
     * associate {@code AsyncLoggerAgent} to a specific efs
     * dispatcher thread rather than the default dispatcher
     * thread.
     */
    private static final class AsyncLoggerAgent
        implements IEfsAgent
    {
    //-----------------------------------------------------------
    // Member data.
    //

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Private constructor to restrict instantiation to
         * {@code AsyncLogger}.
         */
        private AsyncLoggerAgent()
        {}

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // IEfsAgent Interface Implementation.
        //

        @Override
        public String name()
        {
            return ("AsyncLogger");
        } // end of name()

        //
        // end of IEfsAgent Interface Implementation.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Event Callback.
        //

        /**
         * Runs task contained in {@code LogEvent}.
         * @param event {@code LogEvent} containing runnable
         * logging task.
         */
        private void onLogEvent(final IEfsEvent event)
        {
            (((LogEvent) event).logTask()).run();
        } // end of onLogEvent(IEfsEvent)

        //
        // end of Event Callback.
        //-------------------------------------------------------
    } // end of class AsyncLoggerAgent

    /**
     * Encapsulates nested logger task which performs the actual
     * logging.
     */
    private static final class LogEvent
        implements IEfsEvent
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private final Runnable mLogTask;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private LogEvent(final Runnable task)
        {
            mLogTask = task;
        } // end of LogEvent(Runnable)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        private Runnable logTask()
        {
            return (mLogTask);
        } // end of logTask()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of class LogEvent
} // end of class AsyncLogger
