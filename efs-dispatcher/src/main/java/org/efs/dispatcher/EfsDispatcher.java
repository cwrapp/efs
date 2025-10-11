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

package org.efs.dispatcher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigBeanFactory;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.sf.eBus.util.ValidationException;
import net.sf.eBus.util.Validator;
import org.efs.dispatcher.EfsDispatcherThread.DispatcherThreadStats;
import org.efs.dispatcher.config.EfsDispatcherConfig;
import org.efs.dispatcher.config.EfsDispatchersConfig;
import org.efs.dispatcher.config.ThreadAffinityConfig;
import org.efs.dispatcher.config.ThreadType;
import org.efs.event.IEfsEvent;
import org.efs.logging.AsyncLoggerFactory;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;
import org.jctools.queues.atomic.MpscAtomicArrayQueue;
import org.slf4j.Logger;

/**
 * This class forwards {@link IEfsEvent events} to
 * {@link IEfsAgent agents} and has each agent process those
 * events in a way that is effectively single-threaded. See the
 * {@link org.efs.dispatcher package info} for detailed
 * explanation on how dispatchers, agents, and events work
 * together to accomplish this.
 * <h2>Creating Dispatchers</h2>
 * There are four ways to create dispatchers in an application:
 * <ol>
 *   <li>
 *     creating a typesafe configuration file and have that
 *     file automatically loaded using the
 *     {@code -Dorg.efs.dispatcher.configFile=<file>} Java
 *     command line option, See
 *     {@link org.efs.dispatcher.config.EfsDispatchersConfig EfsDispatchersConfig}
 *     for an example typesafe dispatcher configurations.
 *   </li>
 *   <li>
 *     loading that typesafe configuration file manually via
 *     {@link org.efs.dispatcher.EfsDispatcher#loadDispatchersConfig(java.lang.String) EfsDispatcher.loadDispatchersConfg(String)},
 *   </li>
 *   <li>
 *     creating an
 *     {@link org.efs.dispatcher.config.EfsDispatchersConfig EfsDispatchersConfig}
 *     and loading if manually via
 *     {@link org.efs.dispatcher.EfsDispatcher#loadDispatchers(org.efs.dispatcher.config.EfsDispatchersConfig) EfsDispatcher.loadDispatchers(EfsDispatchersConfig)}
 *     (not recommend - next option preferred), and
 *   </li>
 *   <li>
 *     use
 *     {@link org.efs.dispatcher.EfsDispatcher.Builder EfsDispatcher.Builder}
 *     to programmatically create dispatchers. See {@code Builder}
 *     class javadoc for detailed explanation on building a
 *     dispatcher.
 *   </li>
 * </ol>
 *
 * @see EfsDispatcherConfig
 * @see EfsDispatcher.Builder
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsDispatcher
    implements IEfsDispatcher
{
//---------------------------------------------------------------
// Member Enums.
//

    /**
     * Enumerates the supported efs dispatcher thread types.
     * There are effectively two dispatcher types: efs and
     * GUI with GUI divided between Swing and JavaFX.
     */
    public enum DispatcherType
    {
        /**
         * Default efs dispatcher type.
         */
        EFS (false),

        /**
         * Means that a user-defined dispatcher is being used.
         */
        SPECIAL (true);

    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Special dispatchers may only be marked as the default
         * dispatcher. All other properties are ignored.
         */
        private final boolean mSpecial;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates a new dispatcher type for the given run queue
         * and handle.
         * @param special marks this as a special dispatcher
         * which may not be configured.
         * @param handle dispatcher method handle. Will be
         * {@code null} for efs dispatcher.
         */
        private DispatcherType(final boolean special)
        {
            mSpecial = special;
        } // end of DispatcherType(boolean)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns {@code true} if this dispatcher type is a
         * special, non-configurable dispatcher.
         * @return {@code true} if a special dispatcher.
         */
        public boolean isSpecial()
        {
            return (mSpecial);
        } // end of isSpecial()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of enum DispatcherType

    /**
     * A dispatcher is either stopped, started, or failed to
     * start.
     */
    public enum DispatcherState
    {
        /**
         * Dispatcher is stopped, has no subordinate threads, and
         * may be started.
         */
        STOPPED,

        /**
         * Dispatcher is successfully started and its subordinate
         * threads are running, and may be stopped.
         */
        STARTED,

        /**
         * Dispatcher failed to start and may not be re-started.
         */
        START_FAILED
    } // end of enum DispatcherState

//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Use command line option {@code -D}{@value}{@code=<file>}
     * to specify file containing a typesafe HOCON (a form of
     * JSON) configuration for one or more
     * {@code EfsDispatcher}s.
     */
    public static final String DISPATCHER_CONFIG_OPTION =
        "org.efs.dispatcher.configFile";

    //
    // Default values.
    //

    /**
     * Default dispatcher thread type is
     * {@link ThreadType#BLOCKING}.
     */
    public static final ThreadType DEFAULT_THREAD_TYPE =
        ThreadType.BLOCKING;

    /**
     * Default dispatcher has {@link Thread#NORM_PRIORITY}
     * priority.
     */
    public static final int DEFAULT_PRIORITY =
        Thread.NORM_PRIORITY;

    /**
     * Default dispatcher event queue capacity is {@value}.
     */
    public static final int DEFAULT_EVENT_QUEUE_CAPACITY = 1_024;

    /**
     * Event queue minimum allowed size is {@value}.
     */
    public static final int MIN_EVENT_QUEUE_SIZE = 2;

    /**
     * Default dispatcher run queue capacity is {@value}.
     */
    public static final int DEFAULT_RUN_QUEUE_CAPACITY = 64;

    /**
     * The default spin limit is {@value} calls to
     * {@link java.util.Queue#poll()} before parking or yielding.
     */
    public static final int DEFAULT_SPIN_LIMIT = 2_500_000;

    /**
     * The default park time after spinning is 1 microsecond.
     */
    public static final Duration DEFAULT_PARK_TIME =
        Duration.ofNanos(1_000L);

    /**
     * The default maximum events per call out is {@value}.
     */
    public static final int DEFAULT_MAX_EVENTS = 4;

    //
    // Property keys.
    //

    /**
     * Key {@value} sets dispatcher thread name prefix.
     */
    public static final String DISPATCHER_NAME_KEY =
        "dispatcherName";

    /**
     * Key {@value} sets dispatcher thread count;
     */
    public static final String NUM_THREADS_KEy = "numThreads";

    /**
     * Key {@value} sets dispatcher thread type.
     * <p>
     * The default value is {@link #DEFAULT_THREAD_TYPE}.
     * </p>
     */
    public static final String THREAD_TYPE_KEY = "threadType";

    /**
     * Key {@value} sets thread spin limit for
     * {@link ThreadType#SPINPARK spin+park} or
     * {@link ThreadType#SPINYIELD} thread types.
     * This setting defines the number of times the dispatcher
     * thread may call {@link java.util.Queue#poll()} before
     * parking or yielding.
     * <p>
     * This value must be &gt; zero.
     * </p>
     * <p>
     * Default values is {@link #DEFAULT_SPIN_LIMIT}.
     * </p>
     * <p>
     * This property is ignored is the thread type is not
     * spin+park or spin+yield.
     * </p>
     */
    public static final String SPIN_LIMIT_KEY = "spinLimit";

    /**
     * Key {@value} sets park time for
     * {@link ThreadType#SPINPARK spin+park} dispatcher thread.
     * This setting specifies <em>nanosecond</em> park time taken
     * between {@link java.util.Queue#poll() poll} spin cycles.
     * <p>
     * This value must be &gt; zero.
     * </p>
     * <p>
     * Default values is {@link #DEFAULT_PARK_TIME}.
     * </p>
     * <p>
     * This property is ignored is the thread type is not
     * spin+park.
     * </p>
     */
    public static final String PARK_TIME_KEY = "parkTime";

    /**
     * Key {@value} sets dispatcher thread priority.
     * <p>
     * The default value is {@link Thread#NORM_PRIORITY}.
     * </p>
     */
    public static final String PRIORITY_KEY = "priority";

    /**
     * Key {@value} set agent maximum events per call out.
     * When a agent hits this limit, that agent is
     * placed back on the run queue.
     */
    public static final String MAX_EVENTS_KEY = "maxEvents";

    /**
     * Key {@value} sets dispatcher thread type.
     * This property allows efs events to be executed on
     * supported third-party threads.
     * <p>
     * The default value is {@link DispatcherType#EFS}.
     * </p>
     */
    public static final String DISPATCHER_TYPE_KEY =
        "dispatcherType";

    /**
     * Key {@value} sets dispatcher method.
     */
    public static final String DISPATCHER_KEY = "dispatcher";

    /**
     * Key {@value} sets efs agent maximum event queue capacity.
     * This value is used only for non-blocking dispatcher
     * thread types.
     * <p>
     * Default value is {@link #DEFAULT_EVENT_QUEUE_CAPACITY}.
     * </p>
     */
    public static final String EVENT_QUEUE_CAPACITY_KEY =
        "eventQueueCapacity";

    /**
     * Key {@value} sets  run queue maximum size. Only used for
     * non-blocking thread types.
     * <p>
     * Default value is {@link #DEFAULT_RUN_QUEUE_CAPACITY}.
     * </p>
     */
    public static final String RUN_QUEUE_CAPACITY_KEY =
        "runQueueCapacity";

    //
    // Error text.
    //

    /**
     * Invalid name {@code IllegalArgumentException} message is
     * {@value}.
     */
    public static final String INVALID_NAME =
        "name is either null or an empty string";

    /**
     * Invalid dispatcher type {@code NullPointerException}
     * message is {@value}.
     */
    public static final String NULL_TYPE = "type is null";

    /**
     * Invalid efs agent {@code NullPointerException} message
     * is {@value}.
     */
    public static final String NULL_AGENT = "agent is null";

    /**
     * Invalid dispatch task {@code NullPointerException}
     * message is {@value}.
     */
    public static final String NULL_TASK = "task is null";

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Maps dispatcher name to its dispatcher instance.
     */
    private static final Map<String, IEfsDispatcher> sDispatchers =
        new ConcurrentHashMap<>();

    /**
     * Maps {@link IEfsAgent#name()} to its matching
     * {@code EfsAgent}. This is why efs object names must be
     * unique within the JVM.
     */
    private static final Map<String, EfsAgent> sAgents =
        new ConcurrentHashMap<>();

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger();

    // Class static initialization.
    static
    {
            loadDispatcherConfigFile();
    } // end of class static initialization.

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Thread name prefix which is combined with an integer index
     * to create {@link EfsDispatcherThread} name.
     */
    private final String mDispatcherName;

    /**
     * The dispatcher type. Defines the dispatch method handle.
     */
    private final DispatcherType mDispatcherType;

    /**
     * Dispatcher threads associated with an
     * {@link DispatcherType#EFS efs} dispatcher. Will be an
     * empty thread for a special dispatcher.
     */
    private final EfsDispatcherThread[] mThreads;

    /**
     * Thread runs at this priority. Must be
     * &ge; {@link Thread#MIN_PRIORITY} and
     * &le; {@link Thread#MAX_PRIORITY}.
     */
    private final int mPriority;

    /**
     * Specifies thread operation: blocking, spinning,
     * spin+park, or spin+yield.
     */
    private final ThreadType mThreadType;

    /**
     * If {@link #mThreadType} is
     * {@link ThreadType#SPINPARK spin+park} or
     * {@link ThreadType#SPINYIELD spin+yield}, then spin
     * this many times on trying to acquire the next agent
     * before parking/yielding.
     */
    private final long mSpinLimit;

    /**
     * If {@link #mThreadType} is
     * {@link ThreadType#SPINPARK spin+park}, then park for
     * this many time limit before returning to spinning.
     */
    private final Duration mParkTime;

    /**
     * Optional thread affinity configuration. Defaults to
     * {@code null}. Thread affinity should be considered
     * when using {@link ThreadType#SPINNING} thread type.
     */
    @Nullable private final ThreadAffinityConfig mAffinity;

    /**
     * Specifies efs agent event queue capacity.
     */
    private final int mEventQueueCapacity;

    /**
     * Maximum number of events a agent may execute per call out.
     */
    private final int mMaxEvents;

    /**
     * Dispatch run queue shared by dispatcher threads. This
     * value is {@code null} for special dispatcher type.
     */
    @Nullable private final Queue<EfsAgent> mRunQueue;

    /**
     * Used to post agent process event to special dispatcher
     * queue.
     */
    private final Consumer<Runnable> mEventDispatcher;

    /**
     * Method used to post agents to run queue. Defaults to
     * {@link #doDispatch(net.sf.efs.dispatcher.EfsClient) doDispatch(EfsAgent)}.
     */
    private final Consumer<EfsAgent> mDispatcher;

    /**
     * Tracks this dispatcher's current state. When dispatcher
     * is stopped, subordinate dispatcher threads do not
     * exist.
     */
    private volatile DispatcherState mState;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new efs dispatcher instance based on builder
     * settings.
     * @param builder contains valid efs dispatcher settings.
     */
    private EfsDispatcher(final Builder builder)
    {
        mDispatcherName = builder.mDispatcherName;
        mThreadType = builder.mThreadType;
        mSpinLimit = builder.mSpinLimit;
        mParkTime = builder.mParkTime;
        mPriority = builder.mPriority;
        mAffinity = builder.mAffinity;
        mDispatcherType = builder.mDispatcherType;
        mEventQueueCapacity = builder.mEventQueueCapacity;
        mMaxEvents = builder.mMaxEvents;
        mRunQueue = builder.mRunQueue;
        mEventDispatcher = builder.mSpecialDispatcher;
        mDispatcher = (mDispatcherType == DispatcherType.EFS ?
                       this::doDispatch :
                       this::doSpecialDispatch);
        mThreads = (mDispatcherType == DispatcherType.EFS ?
                    new EfsDispatcherThread[builder.mNumThreads] :
                    new EfsDispatcherThread[0]);

        // A special dispatcher type is considered already
        // started.
        mState = (mDispatcherType == DispatcherType.SPECIAL ?
                  DispatcherState.STARTED :
                  DispatcherState.STOPPED);
    } // end of EfsDispatcher(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // IEfsDispatcher Interface Implementation.
    //

    /**
     * Returns dispatcher name.
     * @return dispatcher name.
     */
    @Override
    public String name()
    {
        return (mDispatcherName);
    } // end of name()

    /**
     * Returns dispatcher's subordinate thread count.
     * @return dispatcher thread count.
     */
    @Override
    public int threadCount()
    {
        return (mThreads.length);
    } // end of threadCount()

    /**
     * Returns maximum number of events an agent is allowed to
     * process per callback.
     * @return maximum events per agent callback.
     */
    @Override
    public int maxEvents()
    {
        return (mMaxEvents);
    } // end of maxEvents()

    /**
     * Returns configured agent event queue maximum capacity.
     * @return agent event queue maximum capacity.
     */
    @Override
    public int eventQueueCapacity()
    {
        return (mEventQueueCapacity);
    } // end of eventQueueCapacity()

    /**
     * Enqueues given agent to this dispatcher's run queue.
     * @param agent enqueue this efs agent.
     */
    @Override
    public void dispatch(final EfsAgent agent)
    {
        // Note: this consumer lambda posts agent to the
        // dispatcher's agent run queue.
        mDispatcher.accept(agent);
    } // end of dispatch(IEfsAgent)

    //
    // end of IEfsDispatcher Interface Implementation.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns text containing efs dispatcher settings.
     * @return efx dispatcher settings in text.
     */
    @Override
    public String toString()
    {
        final StringBuilder output = new StringBuilder();

        output.append('[').append(mDispatcherName)
              .append(" type=").append(mDispatcherType)
              .append(", # threads=").append(mThreads.length)
              .append(", priority=").append(mPriority)
              .append(", max events=").append(mMaxEvents)
              .append(", thread type=").append(mThreadType);

        if (mThreadType == ThreadType.SPINPARK ||
            mThreadType == ThreadType.SPINYIELD)
        {
            output.append(", spin limit=").append(mSpinLimit);

            if (mThreadType == ThreadType.SPINPARK)
            {
                output.append(", park time=").append(mParkTime);
            }
        }

        return (output.append(']').toString());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns current dispatcher agentState.
     * @return dispatcher agentState.
     */
    public DispatcherState dispatcherState()
    {
        return (mState);
    } // end of dispatcherState()

    /**
     * Returns underlying thread type.
     * @return underlying thread type.
     */
    public ThreadType threadType()
    {
        return (mThreadType);
    } // end of threadType()

    /**
     * Returns priority of dispatcher threads.
     * @return dispatcher thread priorty.
     */
    public int priority()
    {
        return (mPriority);
    } // end of priority()

    /**
     * Returns {@code true} if given agent is currently
     * registered with an efs dispatcher and {@code false} if not
     * registered.
     * @param agent check if this agent is registered with an
     * efs dispatcher.
     * @return {@code true} if {@code agent} is currently
     * registered.
     *
     * @see #register(IEfsAgent, String)
     * @see #deregister(IEfsAgent)
     */
    public static boolean isRegistered(final IEfsAgent agent)
    {
        return (sAgents.containsKey(agent.name()));
    } // end of isRegistered(IEfsAgent)

    /**
     * Returns efs agent instance with the given name. If there
     * is no such named agent, then returns {@code null}.
     * @param agentName find agent with this name.
     * @return efs agent named {@code agentName}.
     */
    @Nullable public static IEfsAgent agent(final String agentName)
    {
        final EfsAgent agent = sAgents.get(agentName);

        return (agent == null ? null : agent.agent());
    } // end of agent(String)

    /**
     * Returns agent's assigned dispatcher's name. If agent is
     * not currently registered, then returns {@code null}.
     * @param agent registered agent.
     * @return agent's assigned dispatcher's name.
     * @throws NullPointerException
     * if {@code agent} is {@code null}.
     */
    @Nullable public static String dispatcher(final IEfsAgent agent)
    {
        String retval = null;

        Objects.requireNonNull(agent, NULL_AGENT);

        final EfsAgent efsAgent = sAgents.get(agent.name());

        if (efsAgent != null)
        {
            retval = (efsAgent.dispatcher()).name();
        }

        return (retval);
    } // end of dispatcher(IEfsAgent)

    /**
     * Returns an immutable copy of dispatcher names.
     * @return dispatcher names list.
     */
    public static List<String> dispatcherNames()
    {
        return (ImmutableList.copyOf(sDispatchers.keySet()));
    } // end of dispatcherNames()

    /**
     * Returns dispatcher associated with the given name. Returns
     * {@code null} if no dispatcher is associated with the name.
     * @param name dispatcher name.
     * @return dispatcher named {@code name}.
     * @throws IllegalArgumentException
     * if {@code name} is {@code null} or an empty string.
     */
    public static @Nullable IEfsDispatcher getDispatcher(final String  name)
    {
        if (Strings.isNullOrEmpty(name))
        {
            throw (
                new IllegalArgumentException(
                    "name is null or an empty string"));
        }

        return (sDispatchers.get(name));
    } // end of getDispatcher(String)

    /**
     * Returns named dispatcher's performance statistics
     * snapshot. This snapshot is <em>not</em> dynamically
     * updated. This method must be called to receive changes
     * in dispatcher's performance.
     * @param name dispatcher name.
     * @return named dispatcher's performance.
     * @throws IllegalArgumentException
     * if {@code name} is either {@code null} or an empty string.
     */
    public static @Nullable DispatcherStats performanceStats(final String name)
    {
        final EfsDispatcher dispatcher;

        if (Strings.isNullOrEmpty(name))
        {
            throw (
                new IllegalArgumentException(INVALID_NAME));
        }

        dispatcher = (EfsDispatcher) sDispatchers.get(name);

        return (dispatcher == null ?
                null :
                dispatcher.performanceStats());
    } // end of performanceStats(String)

    /**
     * Returns current efs agents in an immutable list.
     * @return immutable list of efs agents.
     */
    /* package */ static List<EfsAgent> agents()
    {
        return (ImmutableList.copyOf(sAgents.values()));
    } // end of agents()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Registers agent with named dispatcher. Allows programmatic
     * matching of agents with dispatcher.
     * @param agent assign this agent with named dispatcher.
     * @param dispatcherName unique dispatcher name.
     * @throws NullPointerException
     * if {@code agent} is {@code null}.
     * @throws IllegalArgumentException
     * if {@code agent.name()} returns a {@code null} or empty
     * string or dispatcher name is {@code null} or empty string]
     * or an unknown dispatcher.
     * @throws IllegalStateException
     * if {@code agent} is already registered.
     */
    public static void register(final IEfsAgent agent,
                                final String dispatcherName)
    {
        Objects.requireNonNull(agent, NULL_AGENT);
        validateAgentRegister(agent);
        validateDispatcherName(dispatcherName);

        doRegister(agent, sDispatchers.get(dispatcherName));
    } // end of register(IEfsAgent, String)

    /**
     * Forwards event to agent using given callback.
     * <p>
     * {@code callback} should reference a method in agent but
     * this is not enforced.
     * </p>
     * @param <E> event class.
     * @param callback {@code Consumer} instance used to forward
     * {@code event} to {@code agent}.
     * @param event send this event to {@code agent}.
     * @param agent efs agent to receive given event.
     * @throws NullPointerException
     * if either {@code callback}, {@code event}, or
     * {@code agent} is {@code null}.
     * @throws IllegalStateException
     * if {@code agent} is not
     * {@link #register(IEfsAgent, String) registered} or if
     * {@code agent}'s event queue is full preventing
     * {@code event} from being enqueued.
     */
    public static <E extends IEfsEvent> void dispatch(final Consumer<E> callback,
                                                      final E event,
                                                      final IEfsAgent agent)
    {
        Objects.requireNonNull(callback, "callback is null");
        Objects.requireNonNull(event, "event is null");
        Objects.requireNonNull(agent, NULL_AGENT);
        validateAgentDispatch(agent);

        (sAgents.get(agent.name())).dispatch(callback, event);
    } // end of dispatch(Consumer<>, E, IEfsAgent)

    /**
     * Executes given task in agent dispatcher inline with
     * dispatched tasks. This means task is performed in agent's
     * virtually single threaded manner.
     * @param task perform this task inline with events.
     * @param agent run {@code task} inline with this agent's
     * events.
     * @throws IllegalStateException
     * if {@code agent}'s event queue is full preventing
     * {@code task} from being enqueued.
     */
    public static void dispatch(final Runnable task,
                                final IEfsAgent agent)
    {
        Objects.requireNonNull(task, NULL_TASK);
        Objects.requireNonNull(agent, NULL_AGENT);
        validateAgentDispatch(agent);

        (sAgents.get(agent.name())).dispatch(task);
    } // end of dispatch(Runnable, IEfsAgent)

    /**
     * De-registers agent from its dispatcher. Any pending events
     * will not be delivered to agent.
     * @param agent de-register this agent from its dispatcher.
     */
    public static void deregister(final IEfsAgent agent)
    {
        // Ignore null efs agent.
        if (agent != null)
        {
            // Remove from agents map.
            final EfsAgent efsAgent =
                sAgents.remove(agent.name());

            efsAgent.deregister();
        }
    } // end of deregister(IEfsAgent)

    /**
     * Returns a new dispatcher configuration builder. It is
     * <em>strongly</em> that a new builder be used for each
     * new dispatcher configuration.
     * @return a new dispatcher configuration builder.
     */
    public static Builder builder()
    {
        return (new Builder());
    } // end of builder()

    /**
     * Loads dispatcher definitions from named file and then
     * creates and starts dispatchers based on those definitions.
     * @param fileName name of file containing efs dispatcher
     * definitions.
     * @throws ConfigException
     * if {@code fileName} contains an invalid dispatcher
     * configuration.
     */
    public static void loadDispatchersConfig(final String fileName)
    {
        final File configFile = new File(fileName);
        final Config configSource =
            ConfigFactory.parseFile(configFile);
        final EfsDispatchersConfig dispatchersConfig =
            ConfigBeanFactory.create(
                configSource, EfsDispatchersConfig.class);

        loadDispatchers(dispatchersConfig);
    } // end of loadDispatchersConfig(String)

    /**
     * Loads dispatcher definitions from named file and then
     * creates and starts dispatchers based on those definitions.
     * @param dispatchersConfig dispatcher configurations.
     */
    public static void loadDispatchers(final EfsDispatchersConfig dispatchersConfig)
    {
        for (EfsDispatcherConfig c :
                 dispatchersConfig.getDispatchers())
        {
            createDispatcher(c);
        }
    } // end of loadDispatchers(EfsDispatchersConfig)

    /**
     * Adds a mocked dispatcher with the given name to
     * dispatchers map.
     * @param dispatcherName dispatcher name.
     * @param dispatcher dispatcher instance.
     */
    @VisibleForTesting
    public static void addDispatcher(final String dispatcherName,
                                     final IEfsDispatcher dispatcher)
    {
        sDispatchers.put(dispatcherName, dispatcher);
    } // end of addDispatcher(String, IEfsDispatcher)

    /**
     * Clears the dispatcher map. Needed for testing purposes
     * only.
     */
    @VisibleForTesting
    /* package */ static void clearDispatchers()
    {
        sDispatchers.clear();
    } // end of clearDispatchers()

    /**
     * Performs the actual work to start up an efs dispatcher by
     * creating underlying dispatcher threads and starting them.
     * @throws ThreadStartException
     * if an efs dispatcher thread fails to start.
     */
    private void startup()
    {
        final int numThreads = mThreads.length;
        int index;
        String threadName;

        // Yes. Then create dispatcher threads and start them
        // running.
        // Note: if dispatcher type is special then
        // mThreads.length is zero - meaning no threads are
        // created.
        for (index = 0; index < numThreads; ++index)
        {
            threadName = generateThreadName(index);
            mThreads[index] =
                createDispatcherThread(threadName);

            // Make sure this thread is daemon otherwise
            // it may hang application on shut down.
            mThreads[index].setDaemon(true);

            mThreads[index].start();
        }
    } // end of startup()

    /**
     * Validates that {@code agent} has a non-{@code null},
     * non-empty, unique name. This method is called for effect
     * only.
     * @param agent validate agent's name.
     * @throws IllegalArgumentException
     * if {@code agent.name()} returns a {@code null} or empty
     * string.
     * @throws IllegalStateException
     * if {@code agent} is already registered.
     */
    private static void validateAgentRegister(final IEfsAgent agent)
    {
        final String agentName = agent.name();

        if (Strings.isNullOrEmpty(agentName))
        {
            throw (
                new IllegalArgumentException(
                    "agent name is either null or an empty string"));
        }

        // Is this efs agent currently registered?
        if (sAgents.containsKey(agentName))
        {
            // Yes, and that is not allowed.
            throw (
                new IllegalStateException(
                    "efs agent \"" +
                    agentName +
                    "\" currently registered"));
        }
    } // end of validateAgentRegister(IEfsAgent)

    /**
     * Validates that {@code agent} is registered with its
     * dispatcher. This method is called for effect only.
     * @param agent validate agent's registration.
     * @throws IllegalStateException
     * if {@code agent} is not registered with a dispatcher.
     */
    private static void validateAgentDispatch(final IEfsAgent agent)
    {
        final String agentName = agent.name();

        // Is this a registered efs agent?
        if (!sAgents.containsKey(agentName))
        {
            // Nope. Can't dispatch events to un-registered
            // agents.
            throw (
                new IllegalStateException(
                    "efs agent " +
                    agentName +
                    " not registered"));
        }
    } // end of validateAgentDispatch(IEfsAgent)

    /**
     * Validates that {@code dispatcherName} has a
     * non-{@code null}, non-empty, unique name, and references
     * a known dispatcher.
     * @param dispatcherName validate this dispatcher name.
     * @throws IllegalArgumentException
     * if {@code dispatcherName} is either {@code null}, an
     * empty string, or does not reference a known dispatcher.
     */
    private static void validateDispatcherName(final String dispatcherName)
    {
        if (Strings.isNullOrEmpty(dispatcherName))
        {
            throw (
                new IllegalArgumentException(
                    "dispatcherName is either null or an empty string"));
        }

        // Is this a known dispatcher?
        if (!sDispatchers.containsKey(dispatcherName))
        {
            throw (
                new IllegalArgumentException(
                    "unknown dispatcher \"" +
                    dispatcherName +
                    "\""));
        }
    } // end of validateDispatcherName(String)

    /**
     * Creates a {@link EfsAgent} instance encapsulating given
     * efs object and associated with the given dispatcher.
     * efs agent is stopped and must be explicitly
     * {@link #startup(net.sf.efs.dispatcher.IEfsObject...) started}.
     * @param eobj register this efs object.
     * @param dispatcher associated {@code eobj} with this
     * dispatcher.
     */
    private static void doRegister(final IEfsAgent eobj,
                                   final IEfsDispatcher dispatcher)
    {
        final String efsName = eobj.name();
        final EfsAgent.Builder builder = EfsAgent.builder();

        sAgents.put(efsName,
                     builder.agent(eobj)
                            .dispatcher(dispatcher)
                            .maxEvents(dispatcher.maxEvents())
                            .eventQueueCapacity(
                                dispatcher.eventQueueCapacity())
                            .build());
    } // end of doRegister(IEfsAgent, EfsDispatcher)

    /**
     * Returns a new dispatcher thread for the given index.
     * Returned thread is <em>not</em> started.
     * @param threadName unique dispatcher thread name.
     * @return new dispatcher thread.
     */
    private EfsDispatcherThread createDispatcherThread(final String threadName)
    {

        final EfsDispatcherThread.Builder builder =
            EfsDispatcherThread.builder();

        builder.threadName(threadName)
               .threadType(mThreadType)
               .priority(mPriority)
               .maxEvents(mMaxEvents)
               .runQueue(mRunQueue)
               .affinity(mAffinity);

        if (mThreadType == ThreadType.SPINPARK ||
            mThreadType == ThreadType.SPINYIELD)
        {
            builder.spinLimit(mSpinLimit);
        }

        if (mThreadType == ThreadType.SPINPARK)
        {
            builder.parkTime(mParkTime);
        }

        return (builder.build());
    } // end of createDispatcherThread(String)

    /**
     * Generates dispatcher thread name based on thread type,
     * thread index, and dispatcher name.
     * @param index dispatcher thread index.
     * @return dispatcher thread name.
     */
    private String generateThreadName(final int index)
    {
        final String temperature =
            (mThreadType == ThreadType.BLOCKING ?
             "Cold" :
             "Hot");

        return (String.format("[[%s-%d]%s]",
                              temperature,
                              index,
                              mDispatcherName));
    } // end of generateThreadName(int)

    /**
     * Posts agent to run queue so that it may be picked up by
     * {@link EfsDispatcherThread dispatcher thread}.
     * @param agent post this agent to run queue.
     */
    private void doDispatch(final EfsAgent agent)
    {
        // Put event on agent's queue.
        if (!mRunQueue.offer(agent))
        {
            sLogger.warn("{}: failed to post {} to run queue.",
                         mDispatcherName,
                         agent.agentName());
        }
    } // end of doDispatch(EfsAgent)

    /**
     * Posts agent to special dispatcher run queue so that it may
     * be picked up by the non-efs dispatcher thread.
     * @param agent post this agent to run queue.
     */
    private void doSpecialDispatch(final EfsAgent agent)
    {
        // Wrap EfsAgent processEvents method in a Runnable task.
        mEventDispatcher.accept(agent::processEvents);
    } // end of doSpecialDispatch(EfsAgent)

    /**
     * Returns a snapshot of this dispatcher's current
     * performance statistics. The returned
     * {@code DispatcherStats} is <em>not</em> dynamically
     * updated. This method should be called again to get
     * changes to performance statistics.
     * @return dispatcher performance statistics snapshot.
     */
    private DispatcherStats performanceStats()
    {
        return (new DispatcherStats());
    } // end of performanceStats()

    /**
     * Creates and starts an efs dispatcher based on given
     * dispatcher configuration. Created dispatcher is
     * automatically stored in dispatchers map and started.
     * @param dc dispatcher definition.
     */
    private static void createDispatcher(final EfsDispatcherConfig dc)
    {
        final ThreadType threadType = dc.getThreadType();
        final Builder builder = builder();

        builder.dispatcherName(dc.getDispatcherName())
               .dispatcherType(DispatcherType.EFS)
               .threadType(threadType)
               .numThreads(dc.getNumThreads());

        if (threadType == ThreadType.SPINPARK ||
            threadType == ThreadType.SPINYIELD)
        {
            builder.spinLimit(dc.getSpinLimit());

            if (threadType == ThreadType.SPINPARK)
            {
                builder.parkTime(dc.getParkTime());
            }
        }

        builder.threadAffinity(dc.getAffinity())
               .eventQueueCapacity(dc.getEventQueueCapacity())
               .maxEvents(dc.getMaxEvents())
               .runQueueCapacity(dc.getRunQueueCapacity())
               .build();
    } // end of createDispatcher(EfsDispatcherConfig)

    /**
     * Loads dispatcher configuration file named in
     * {@code System} property
     * {@link #DISPATCHER_CONFIG_OPTION}. This static method
     * is called only from {@code EfsDispatcher} class static
     * initialization block and is separated out for test
     * purposes.
     */
    /* package */ static void loadDispatcherConfigFile()
    {
        final String configFile =
            System.getProperty(DISPATCHER_CONFIG_OPTION);

        // Was a -D option provided for dispatcher configuration
        // file?
        if (!Strings.isNullOrEmpty(configFile))
        {
            // Yes. Load the file and then define dispatchers
            // from configuration.
            try
            {
                loadDispatchersConfig(configFile);
            }
            catch (Exception jex)
            {
                sLogger.error(
                    "attempt to load EfsDispatchersConfig from {} failed",
                    configFile,
                    jex);
            }
        }
    } // end of loadDispatcherConfigFile()

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Builder for constructing {@link EfsDispatcher} instance.
     * <table class="builder-properties">
     *   <caption>EfsDispatcher Properties</caption>
     *   <tr>
     *     <th>Name</th>
     *     <th>Type</th>
     *     <th>Required?</th>
     *     <th>Default Value</th>
     *     <th>Description</th>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">dispatcher name</td>
     *     <td>{@code String}</td>
     *     <td>Yes</td>
     *     <td>NA</td>
     *     <td>
     *       Unique dispatcher name within JVM. May not be
     *       {@code null} or an empty string.
     *     </td>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">dispatcher type</td>
     *     <td>{@link DispatcherType DispatcherType}</td>
     *     <td>Yes</td>
     *     <td>NA</td>
     *     <td>
     *       Specifies whether this is an efs dispatcher or a
     *       special dispatcher (such as Swing or JavaFX GUI
     *       thread).
     *     </td>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">thread type</td>
     *     <td>{@link ThreadType ThreadType}</td>
     *     <td>No</td>
     *     <td>
     *       {@link #DEFAULT_THREAD_TYPE DEFAULT_THREAD_TYPE}
     *     </td>
     *     <td>
     *       Specifies whether this thread is blocking, spinning,
     *       spin+park, or spin+yield.
     *     </td>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">
     *       dispatcher thread count
     *     </td>
     *     <td>{@code int}</td>
     *     <td>Yes</td>
     *     <td>NA</td>
     *     <td>
     *       Dispatcher's subordinate thread count. Ignored if
     *       dispatcher type is
     *       {@link DispatcherType#SPECIAL special}.
     *     </td>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">thread priority</td>
     *     <td>{@code int}</td>
     *     <td>No</td>
     *     <td>{@link #DEFAULT_PRIORITY DEFAULT_PRIORITY}</td>
     *     <td>
     *       Each dispatcher subordinate thread is set to this
     *       priority. Values must be
     *       {@link Thread#MIN_PRIORITY} &le; priority &le;
     *       {@link Thread#MAX_PRIORITY}.
     *     </td>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">spin limit</td>
     *     <td>{@code long}</td>
     *     <td>
     *       No
     *     </td>
     *     <td>{@link #DEFAULT_SPIN_LIMIT DEFAULT_SPIN_LIMIT}</td>
     *     <td>
     *       Each dispatcher thread spins this many times
     *       attempting to acquire next runnable agent before
     *       parking or yield. Property only used if thread type
     *       is spin+park or spin+yield.
     *     </td>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">park time</td>
     *     <td>{@code Duration}</td>
     *     <td>No</td>
     *     <td>{@link #DEFAULT_PARK_TIME DEFAULT_PARK_TIME}</td>
     *     <td>
     *       Each dispatcher thread parks this amount of time
     *       before spinning on agent queue again. Property only
     *       used if thread type is spin+park.
     *     </td>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">affinity</td>
     *     <td>{@link ThreadAffinityConfig}</td>
     *     <td>No</td>
     *     <td>{@code null}</td>
     *     <td>
     *       Thread affinity for dispatcher subordinate threads.
     *       Ignored if dispatcher type is special.
     *     </td>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">event queue capacity</td>
     *     <td>{@code int}</td>
     *     <td>No</td>
     *     <td>unlimited queue size</td>
     *     <td>
     *       Agent maximum event queue size assigned to agents
     *       registered with this dispatcher. Defaults to an
     *       unlimited event queue.
     *     </td>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">agent queue capacity</td>
     *     <td>{@code int}</td>
     *     <td>Yes</td>
     *     <td>Na</td>
     *     <td>
     *       Maximum number of runnable agents which may be
     *       posted to dispatcher run queue.
     *     </td>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">
     *         maximum events per agent callout
     *     </td>
     *     <td>{@code int}</td>
     *     <td>Yes</td>
     *     <td>NA</td>
     *     <td>
     *       An agent may process up to this many events in a
     *       single run. This is to prevent one agent from
     *       dominating its dispatcher, prevents other agents
     *       from processing events.
     *     </td>
     *   </tr>
     *   <tr>
     *     <td style="font-weight:bold;">dispatcher lambda</td>
     *     <td>{@code Consumer<Runnable>}</td>
     *     <td>Yes if dispatcher type is special</td>
     *     <td>NA</td>
     *     <td>
     *       Method used to post a {@code Runnable} task to
     *       special dispatcher thread.
     *     </td>
     *   </tr>
     * </table>
     * <h2>Example Building EfsDispatcher</h2>
     * <pre><code>import org.efs.dispatcher.EfsDispatcher;
import org.efs.dispatcher.EfsDispatcher.DispatcherType;
import org.efs.dispatcher.config.ThreadType;

    // EfsDispatcher.Builder automatically stores dispatcher
    // instance in dispatcher map using its unique name as key
    // and starts dispatcher.
    // Therefore there is no reason to place built EfsDispatcher
    // in a local variable since that dispatcher instance can be
    // readily accessed by its name via
    // EfsDispatcher.getDispatcher(name).
    (EfsDispatcher.builder()).dispatcherName("FastAlgoDispatcher")
                             .dispatcherType(DispatcherType.EFS)
                             .threadType(ThreadType.SPINNING)
                             .numThreads(1)
                             .priority(Thread.MAX_PRIORITY)
                             // See {@link ThreadAffinityConfig}.
                             .threadAffinity(sFastAlgoAffinity)
                             // Unlimited event queue capacity.
                             .eventQueueCapacity(0)
                             .runQueueCapacity(64)
                             .maxEvents(16)
                             .build();
    (EfsDispatcher.builder()).dispatcherName("SlowAlgoDispatcher")
                             .dispatcherType(DispatcherType.EFS)
                             .threadType(ThreadType.SPINPARK)
                             .numThreads(1)
                             .priority(Thread.MAX_PRIORITY - 1)
                             // Spin 1,000,000 times then park for 1 microsecond.
                             .spinLimit(1_000_000L)
                             .parkTime(Duration.ofNanos(1_000L)
                             // See {@link ThreadAffinityConfig}.
                             .threadAffinity(sSlotAlgoffinity)
                             // Unlimited event queue capacity.
                             .eventQueueCapacity(0)
                             .runQueueCapacity(64)
                             .maxEvents(16)
                             .build();
    (EfsDispatcher.builder()).dispatcherName("DefaultDispatcher")
                             .dispatcherType(DispatcherType.EFS)
                             .threadType(ThreadType.BLOCKING)
                             .numThreads(8)
                             .priority(Thread.MIN_PRIORITY)
                             // Unlimited event queue capacity.
                             .eventQueueCapacity(0)
                             .runQueueCapacity(128)
                             .maxEvents(8)
                             .build();</code></pre>
     */
    public static final class Builder
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private String mDispatcherName;
        private DispatcherType mDispatcherType;
        private ThreadType mThreadType;
        private int mNumThreads;
        private int mPriority;
        private long mSpinLimit;
        private Duration mParkTime;
        @Nullable private ThreadAffinityConfig mAffinity;
        private int mEventQueueCapacity;
        private int mRunQueueCapacity;
        private int mMaxEvents;
        @Nullable private Queue<EfsAgent> mRunQueue;
        private Consumer<Runnable> mSpecialDispatcher;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates dispatcher builder with default settings.
         */
        private Builder()
        {
            mNumThreads = -1;
            mThreadType = DEFAULT_THREAD_TYPE;
            mPriority = DEFAULT_PRIORITY;
            mEventQueueCapacity = 0;
            mParkTime = DEFAULT_PARK_TIME;
            mSpinLimit = DEFAULT_SPIN_LIMIT;
            mMaxEvents = 0;
        } // end of Builder()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets unique dispatcher name. Used for logging and to
         * create dispatcher thread name.
         * @param name unique dispatcher name.
         * @return {@code this} dispatcher builder.
         * @throws IllegalArgumentException
         * if {@code name} is {@code null} or an empty string.
         */
        public Builder dispatcherName(final String name)
        {
            if (Strings.isNullOrEmpty(name))
            {
                throw (
                    new IllegalArgumentException(
                        INVALID_NAME));
            }

            mDispatcherName = name;

            return (this);
        } // end of dispatcherName(String)

        /**
         * Sets dispatcher type.
         * @param type dispatcher type.
         * @return {@code this} dispatcher builder.
         * @throws NullPointerException
         * if {@code type} is {@code null}.
         */
        public Builder dispatcherType(final DispatcherType type)
        {
            mDispatcherType =
                Objects.requireNonNull(type, NULL_TYPE);

            return (this);
        } // end of dispatcherType(DispatcherType)

        /**
         * Sets dispatcher thread type.
         * @param type thread type.
         * @return {@code this} dispatcher builder.
         * @throws NullPointerException
         * if {@code type} is {@code null}.
         */
        public Builder threadType(final ThreadType type)
        {
            mThreadType =
                Objects.requireNonNull(type, NULL_TYPE);

            return (this);
        } // end of threadType(ThreadType)

        /**
         * Sets dispatcher thread count.
         * @param numThreads number of threads in dispatcher.
         * @return {@code this} dispatcher builder.
         * @throws IllegalArgumentException
         * if {@code numThreads} is &le; zero.
         */
        public Builder numThreads(final int numThreads)
        {
            if (numThreads <= 0)
            {
                throw (
                    new IllegalArgumentException(
                        "numThreads <= zero"));
            }

            mNumThreads = numThreads;

            return (this);
        } // end of numThreads(int)

        /**
         * Sets the thread priority. Must be
         * &ge; {@link Thread#MIN_PRIORITY} and &le;
         * {@link Thread#MAX_PRIORITY}. If not set, then defaults
         * to {@link Thread#NORM_PRIORITY}.
         * @param priority assigned thread priority for scheduled
         * executor thread.
         * @return {@code this} dispatcher builder.
         * @throws IllegalArgumentException
         * if {@code priority} &lt; {@code Threads.MIN_PRIORITY}
         * or &gt; {@code Thread.MAX_PRIORITY}.
         */
        public Builder priority(final int priority)
        {
            if (priority < Thread.MIN_PRIORITY ||
                priority > Thread.MAX_PRIORITY)
            {
                throw (
                    new IllegalArgumentException(
                        "priority out of bounds"));
            }

            mPriority = priority;

            return (this);
        } // end of priority(int)

        /**
         * Sets {@link ThreadType#SPINPARK} or
         * {@link ThreadType#SPINYIELD} spin limit. This setting
         * is ignored for any other dispatcher thread type.
         * @param limit spin limit.
         * @return {@code this} dispatcher builder.
         * @throws IllegalArgumentException
         * if {@code limit} &le; zero.
         */
        public Builder spinLimit(final long limit)
        {
            if (limit <= 0L)
            {
                throw (
                    new IllegalArgumentException(
                        "limit <= zero"));
            }

            mSpinLimit = limit;

            return (this);
        } // end of spinLimit(long)

        /**
         * Sets {@link ThreadType#SPINPARK spin+park} park time
         * limit. This setting is ignored for any other
         * dispatcher thread type.
         * @param time park time limit.
         * @return {@code this} dispatcher builder.
         * @throws NullPointerException
         * if {@code time} is {@code null}.
         * @throws IllegalArgumentException
         * if {@code time} is &le; zero.
         */
        public Builder parkTime(final Duration time)
        {
            Objects.requireNonNull(time, "time is null");

            if (time.compareTo(Duration.ZERO) <= 0)
            {
                throw (
                    new IllegalArgumentException(
                        "time <= zero"));
            }

            mParkTime = time;

            return (this);
        } // end of parkTime(Duration)

        /**
         * Sets optional thread affinity to the given
         * configuration list. Thread affinity should be
         * considered when using spinning thread types.
         * @param affinity thread affinity configuration.
         * @return {@code this} thread builder.
         */
        public Builder threadAffinity(@Nullable final ThreadAffinityConfig affinity)
        {
            mAffinity = affinity;

            return (this);
        } // end of threadAffinity(ThreadAffinityConfig)

        /**
         * Sets efs agent event queue capacity. This capacity
         * is applied to each efs agent
         * <em>associated with this dispatcher</em>.
         * <p>
         * {@code capacity} should be a 2 power value. If not,
         * event queue capacity is set to the next 2 power value
         * &gt; given capacity.
         * </p>
         * @param capacity event queue maximum capacity.
         * @return {@code this Builder} instance.
         * @throws IllegalArgumentException
         * if {@code capacity} is &lt;
         * {@link #MIN_EVENT_QUEUE_SIZE}.
         */
        public Builder eventQueueCapacity(final int capacity)
        {
            if (capacity < MIN_EVENT_QUEUE_SIZE)
            {
                throw (
                    new IllegalArgumentException(
                        "capacity < " + MIN_EVENT_QUEUE_SIZE));
            }

            mEventQueueCapacity = nextPowerOfTwo(capacity);

            return (this);
        } // end of eventQueueCapacity(int)

        /**
         * Sets efs dispatcher agent queue capacity.
         * <p>
         * The capacity should be a 2 power value. If not, agent
         * queue capacity is set to the next 2 power value &gt;
         * given capacity.
         * </p>
         * @param capacity run queue maximum capacity.
         * @return {@code this} dispatcher builder.
         * @throws IllegalArgumentException
         * if {@code capacity} is &le; zero.
         */
        public Builder runQueueCapacity(final int capacity)
        {
            if (capacity <= 0)
            {
                throw (
                    new IllegalArgumentException(
                        "capacity <= zero"));
            }

            mRunQueueCapacity = nextPowerOfTwo(capacity);

            return (this);
        } // end of runQueueCapacity(int)

        /**
         * Sets maximum number of events agent is allowed to
         * process per call out.
         * @param maxEvents maximum events per call out..
         * @return {@code this} dispatcher builder.
         * @throws IllegalArgumentException
         * if {@code maxEvents} is &le; zero.
         */
        public Builder maxEvents(final int maxEvents)
        {
            if (maxEvents <= 0)
            {
                throw (
                    new IllegalArgumentException(
                        "maxEvents <= zero"));
            }

            mMaxEvents = maxEvents;

            return (this);
        } // end of maxEvents(int)

        /**
         * Sets dispatcher method used to post efs agent to
         * dispatcher run queue
         * <em>for a
         * {@link DispatcherType#SPECIAL special dispatcher}
         * only</em>.
         * This value is ignored for
         * {@link DispatcherType#EFS efs dispatcher}.
         * @param dispatcher dispatcher method.
         * @return {@code this Builder} instance.
         * @throws NullPointerException
         * if {@code dispatcher} is {@code null}.
         */
        public Builder dispatcher(final Consumer<Runnable> dispatcher)
        {
            mSpecialDispatcher =
                Objects.requireNonNull(
                    dispatcher, "dispatcher is null");

            return (this);
        } // end of dispatcher(Consumer<>)

        /**
         * Configures efs dispatcher as per given configuration.
         * Note that this configuration only supports
         * {@link DispatcherType#EFS efs dispatcher type}.
         * @param config efs dispatcher configuration.
         * @return {@code this Builder} instance.
         * @throws ClassNotFoundException
         * if {@code config.getClasses()} contains a class name
         * which cannot be converted into a Java class instance.
         */
        public Builder set(final EfsDispatcherConfig config)
            throws ClassNotFoundException
        {
            dispatcherName(config.getDispatcherName());
            mDispatcherType = DispatcherType.EFS;
            threadType(config.getThreadType());
            numThreads(config.getNumThreads());
            priority(config.getPriority());

            if (mThreadType == ThreadType.SPINPARK ||
                mThreadType == ThreadType.SPINYIELD)
            {
                spinLimit(config.getSpinLimit());
            }

            if (mThreadType == ThreadType.SPINPARK)
            {
                parkTime(config.getParkTime());
            }

            if (config.getAffinity() != null)
            {
                threadAffinity(config.getAffinity());
            }

            eventQueueCapacity(config.getEventQueueCapacity());
            runQueueCapacity(config.getRunQueueCapacity());
            maxEvents(config.getMaxEvents());

            return (this);
        } // end of set(EfsDispatcherConfig)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Creates a new {@code EfsDispatcher} instance based on
         * this builder's settings and stores in dispatcher
         * map.
         * <p>
         * <strong>Note:</strong> this method does <em>not</em>
         * return a {@code EfsDispatcher} instance. User accesses
         * this instance using the configured dispatcher name.
         * </p>
         * @return newly built dispatcher instance.
         * @throws ValidationException
         * if {@code this Builder} instance contains one or more
         * invalid settings.
         * @throws ThreadStartException
         * if an underlying dispatcher thread fails to start.
         */
        public EfsDispatcher build()
        {
            final EfsDispatcher retval;

            validate();

            // Is this an efs dispatcher?
            if (mDispatcherType == DispatcherType.EFS)
            {
                // Then create the run queue based on the thread
                // type. Set queue capacity to configured
                // maximum.
                mRunQueue = createRunQueue();
            }

            retval = new EfsDispatcher(this);

            sDispatchers.put(mDispatcherName, retval);

            // Now start the dispatcher running - if it is *not*
            // a special dispatcher.
            if (mDispatcherType == DispatcherType.EFS)
            {
                retval.startup();
            }

            return (retval);
        } // end of build()

        /**
         * Validates the {@code EfsDispatcher} builder settings.
         * This validation is "fail slow" meaning that a single
         * validation call will determine all configuration
         * errors.
         * @throws ValidationException
         * if {@this Builder} instance contains one or more
         * invalid settings.
         */
        @SuppressWarnings ({"java:S1067"})
        private void validate()
        {
            final Validator problems = new Validator();

            problems.requireNotNull(mDispatcherName,
                                    DISPATCHER_NAME_KEY)
                    .requireTrue(mDispatcherName == null ||
                                 !sDispatchers.containsKey(
                                     mDispatcherName),
                                 DISPATCHER_NAME_KEY,
                                 "\"" +
                                 mDispatcherName +
                                 "\" is not unique")
                    .requireTrue((mDispatcherType ==
                                      DispatcherType.SPECIAL ||
                                  mNumThreads > 0),
                                 NUM_THREADS_KEy,
                                 Validator.NOT_SET)
                    .requireTrue((mDispatcherType ==
                                      DispatcherType.SPECIAL ||
                                  mThreadType != null),
                                 THREAD_TYPE_KEY,
                                 Validator.NOT_SET)
                    .requireTrue((mMaxEvents > 0),
                                 MAX_EVENTS_KEY,
                                 Validator.NOT_SET)
                    .requireTrue((mDispatcherType ==
                             DispatcherType.SPECIAL ||
                         mEventQueueCapacity >=
                             MIN_EVENT_QUEUE_SIZE),
                        EVENT_QUEUE_CAPACITY_KEY,
                        Validator.NOT_SET)
                    .requireTrue(
                        ((mThreadType != ThreadType.SPINPARK &&
                          mThreadType != ThreadType.SPINYIELD) ||
                         mSpinLimit > 0L),
                         SPIN_LIMIT_KEY,
                         "not set for spin+park or spin+yield thread type")
                    .requireTrue(
                        (mThreadType != ThreadType.SPINPARK ||
                         (mParkTime != null &&
                          mParkTime.isPositive())),
                        PARK_TIME_KEY,
                        "not set for spin+park thread type")
                    .requireNotNull(mDispatcherType,
                                    DISPATCHER_TYPE_KEY)
                    .requireTrue(
                        (mDispatcherType == DispatcherType.EFS ||
                         mSpecialDispatcher != null),
                        DISPATCHER_KEY,
                        "not set for non-efs dispatcher")
                    // Make sure that is dispatcher is set for
                    // a special dispatcher type.
                    .requireTrue(mDispatcherType != null &&
                                 (mDispatcherType ==
                                      DispatcherType.EFS ||
                                  mSpecialDispatcher != null),
                                 DISPATCHER_TYPE_KEY,
                                 "either dispatcher type is EFS and dispatcher not set " +
                                 "or dispatcher type is SPECIAL and dispatcher must be set")
                    .throwException(EfsDispatcher.class);
        } // end of validate()

        /**
         * Returns a new agent run queue based on configured
         * thread type and number of dispatcher threads.
         * @return new agent run queue.
         */
        private Queue<EfsAgent> createRunQueue()
        {
            final Queue<EfsAgent> retval;

            // Is this a blocking dispatcher?
            if (mThreadType == ThreadType.BLOCKING)
            {
                // Yes, blocking.
                retval =
                    new LinkedBlockingDeque<>(mRunQueueCapacity);

            }
            // No, not blocking.
            // How many threads does this dispatcher have?
            else if (mNumThreads == 1)
            {
                // Only one, Then use a multi-producer,
                // single consumer queue.
                retval =
                    new MpscAtomicArrayQueue<>(
                        mRunQueueCapacity);
            }
            // Multiple threads. Use a multi-producer,
            // multi-consumer queue.
            else
            {
                retval =
                     new MpmcAtomicArrayQueue<>(
                         mRunQueueCapacity);
            }

            return (retval);
        } // end of createRunQueue()

        /**
         * Returns a {@code 2 ^ n} value &ge; given value.
         * @param n initial integer value.
         * @return {@code 2 ^ n} value.
         */
        private int nextPowerOfTwo(final int n)
        {
            final double nextNum =
                Math.ceil(Math.log(n) / Math.log(2));
            final double retval = Math.pow(2, nextNum);

            return ((int) retval);
        } // end of nextPowerOfTwo(int)
    } // end of class Builder

    /**
     * Contains {@link DispatcherThreadStats} for each dispatcher
     * thread.
     * <p>
     * <em>Note:</em> instances are not "live" meaning this is
     * a snapshot of dispatcher's current performance statistics
     * and is not updated. A new instance must be acquired to
     * get the latest statistics.
     * </p>
     */
    public final class DispatcherStats
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Performance statistics for each subordinate dispatcher
         * threads.
         */
        private final DispatcherThreadStats[] mThreadStats;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private DispatcherStats()
        {
            final int numThreads = mThreads.length;
            int ti;

            mThreadStats = new DispatcherThreadStats[numThreads];

            for (ti = 0; ti < numThreads; ++ti)
            {
                mThreadStats[ti] =
                    mThreads[ti].performanceStats();
            }
        } // end of DispatcherStats()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        @Override
        public String toString()
        {
            final StringBuilder retval = new StringBuilder();

            return (retval.append("[dispatcher=")
                          .append(mDispatcherName)
                          .append(", thread type=")
                          .append(mThreadType)
                          .append(", thread count=")
                          .append(mThreadStats.length)
                          .append(", max events=")
                          .append(mMaxEvents)
                          .append(", agent run count=")
                          .append(totalAgentRunCount())
                          .append(",\n")
                          .append(agentReadyTimeStats())
                          .append('\n')
                          .append(agentRunTimeStats())
                          .append('\n')
                          .append(agentEventStats())
                          .append(']')
                          .toString());
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns dispatcher name.
         * @return dispatcher name.
         */
        public String dispatcherName()
        {
            return (mDispatcherName);
        } // end of dispatcherName()

        /**
         * Returns maximum events per agent call out.
         * @return maximum events.
         */
        public int maxEvents()
        {
            return (mMaxEvents);
        } // end of maxEvents()

        /**
         * Returns underlying dispatcher thread type.
         * @return dispatcher thread type.
         */
        public ThreadType threadType()
        {
            return (mThreadType);
        } // end of threadType()

        /**
         * Returns a copy of the dispatcher thread stats array.
         * The returned {@code DispatchThreadStats} are alive
         * which means the performance statistics are dynamically
         * updated by the owning {@link EfsDispatcherThread}
         * instance.
         * @return array of dispatcher threads performance
         * statistics.
         */
        public DispatcherThreadStats[] dispatcherThreadStats()
        {
            return (
                Arrays.copyOf(
                    mThreadStats, mThreadStats.length));
        } // end of dispatcherThreadStats()

        /**
         * Returns total agent run count across all dispatcher
         * threads.
         * @return total agent run count.
         */
        public int totalAgentRunCount()
        {
            final int numThreads = mThreadStats.length;
            int ti;
            int retval = 0;

            for (ti = 0; ti < numThreads; ++ti)
            {
                retval += mThreadStats[ti].agentRunCount();
            }

            return (retval);
        } // end of totalAgentRunCount()

        /**
         * Returns aggregated dispatcher thread statistics for
         * agent ready time (time spent on dispatcher ready
         * queue).
         * <p>
         * This is a snapshot view of these statistics and is
         * <em>not</em> updated after method returns. Therefore
         * getting the latest results requires calling this
         * method repeatedly. Care must be taken how frequently
         * this method is called since it is not time and space
         * optimized.
         * </p>
         * @return aggregated agent ready time statistics.
         *
         * @see #agentRunTimeStats()
         * @see #agentEventStats()
         */
        public EfsDispatcher.AgentStats agentReadyTimeStats()
        {
            return (
                mergeAgentStats(s -> s.agentReadyTimeStats()));
        } // end of agentReadyTimeStats()

        /**
         * Returns aggregated dispatcher thread statistics for
         * agent run time (time spent processing events).
         * <p>
         * This is a snapshot view of these statistics and is
         * <em>not</em> updated after method returns. Therefore
         * getting the latest results requires calling this
         * method repeatedly. Care must be taken how frequently
         * this method is called since it is not time and space
         * optimized.
         * </p>
         * @return aggregated agent run time statistics.
         *
         * @see #agentReadyTimeStats()
         * @see #agentEventStats()
         */
        public EfsDispatcher.AgentStats agentRunTimeStats()
        {
            return (mergeAgentStats(s -> s.agentRunTimeStats()));
        } // end of agentRunTimeStats()

        /**
         * Returns aggregated dispatcher thread statistics for
         * agent event count (number of events processed per
         * run).
         * <p>
         * This is a snapshot view of these statistics and is
         * <em>not</em> updated after method returns. Therefore
         * getting the latest results requires calling this
         * method repeatedly. Care must be taken how frequently
         * this method is called since it is not time and space
         * optimized.
         * </p>
         * @return aggregated agent run time statistics.
         *
         * @see #agentReadyTimeStats()
         * @see #agentRunTimeStats()
         */
        public EfsDispatcher.AgentStats agentEventStats()
        {
            return (mergeAgentStats(s -> s.agentEventStats()));
        } // end of agentEventStats()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        /**
         * Returns aggregated dispatcher thread performance stats
         * into a single result. Total number of stats is equal
         * to summation of all dispatcher thread stats.
         * @param stats
         * @return
         */
        private EfsDispatcher.AgentStats mergeAgentStats(final Function<DispatcherThreadStats, EfsDispatcherThread.AgentStats> f)
        {
            final List<EfsDispatcherThread.AgentStats> stats =
                collectAgentStats(f);
            final EfsDispatcherThread.AgentStats firstStats =
                stats.getFirst();
            final String statsName = firstStats.statsName();
            final String unit = firstStats.unit();
            long[] data = new long[0];
            int agentRunCount = 0;

            for (EfsDispatcherThread.AgentStats as : stats)
            {
                data = mergeSortedArrays(data, as.stats());
                agentRunCount += as.agentRunCount();
            }

            return (new EfsDispatcher.AgentStats(statsName,
                                                 unit,
                                                 agentRunCount,
                                                 data));
        } // end of mergeAgentStats(List<>)

        /**
         * Returns list of dispatcher thread agent performance
         * stats of the appropriate type.
         * @param f extracts appropriate agent stats from
         * dispatcher thread stats.
         * @return agent performance stats.
         */
        private List<EfsDispatcherThread.AgentStats> collectAgentStats(final Function<DispatcherThreadStats, EfsDispatcherThread.AgentStats> f)
        {
            final int numThreads = mThreadStats.length;
            int ti;
            final ImmutableList.Builder<EfsDispatcherThread.AgentStats> builder =
                ImmutableList.builder();

            for (ti = 0; ti < numThreads; ++ti)
            {
                builder.add(f.apply(mThreadStats[ti]));
            }

            return (builder.build());
        } // end of collectAgentStats(Supplier<>)

        /**
         * Returns a sorted array containing elements merged from
         * two sorted arrays.
         * @param a0 first sorted array.
         * @param a1 second sorted array.
         * @return merged sorted array containing elements from
         * arrays {@code a0} and {@code a1}.
         */
        private long[] mergeSortedArrays(final long[] a0,
                                         final long[] a1)
        {
            final int size0 = a0.length;
            final int size1 = a1.length;
            final int totalSize = (size0 + size1);
            int i0;
            int i1;
            int i2;
            final long[] retval = new long[totalSize];

            for (i0 = 0, i1 = 0, i2 = 0;
                 i0 < size0 && i1 < size1;
                  ++i2)
            {
                // If the first array element is <= second array
                // element, then put that element into the next
                // merged array spot and advance to the next
                // first array element.
                if (a0[i0] <= a1[i1])
                {
                    retval[i2] = a0[i0];
                    ++i0;
                }
                // Otherwise the second array element is < first
                // array element. Insert second array element
                // into merged array.
                else
                {
                    retval[i2] = a1[i1];
                    ++i1;
                }
            }

            // Copy any remaining first and second array elements
            // to the merged array.
            // Note: the above for loop terminates when either
            // arrays a0 or a1 are fully consumed. That means
            // only one of the two next for loops will actually
            // do anything. There is no need for an if statement
            // checking for array consumption - the for loop
            // condition does that.
            for (; i0 < size0; ++i0, ++i2)
            {
                retval[i2] = a0[i0];
            }

            for (; i1 < size1; ++i1, ++i2)
            {
                retval[i2] = a1[i1];
            }

            return (retval);
        } // end of mergeSortedArrays(long[], long[])
    } // end of class DispatcherStats

    /**
     * Immutable class containing aggregated
     * {@link EfsDispatcherThread.AgentStats} providing a
     * snapshot of efs dispatcher's performance. This class is
     * not updated which means that a new instance must be
     * retrieved to see the latest aggregated performance
     * statistics.
     */
    public final class AgentStats
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Unique name identifying these statistics.
         */
        private final String mStatsName;

        /**
         * Agent statistics are in these units.
         */
        private final String mUnit;

        /**
         * Number of agent stats inserted into {@link #mStats}.
         */
        private volatile int mCount;

        /**
         * Raw stats used to calculate the moving average.
         */
        private final long[] mStats;

        /**
         * Agent stats average.
         */
        private final long mAverage;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private AgentStats(final String statsName,
                           final String unit,
                           final int agentRunCount,
                           final long[] stats)
        {
            mStatsName = statsName;
            mUnit = unit;
            mCount = agentRunCount;
            mStats = stats;
            mAverage = (long) Arrays.stream(stats)
                                    .average()
                                    .orElse(0d);
        } // end of AgentStats(String, String, int, long[])

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        @Override
        public String toString()
        {
            final int p50 = (int) (mCount * 0.5d);
            final int p75 = (int) (mCount * 0.75d);
            final int p90 = (int) (mCount * 0.9d);
            final int p95 = (int) (mCount * 0.95d);
            final int p99 = (int) (mCount * 0.99d);
            final String retval;

            try (final Formatter output = new Formatter())
            {
                output.format("%s %s stats:%n",
                              mDispatcherName,
                              mStatsName);
                output.format(" min: %,d %s%n",
                              mStats[0],
                              mUnit);
                output.format(" max: %,d %s%n",
                              mStats[mCount - 1],
                              mUnit);
                output.format(" med: %,d %s%n",
                              mStats[p50],
                              mUnit);
                output.format(" 75%%: %,d %s%n",
                              mStats[p75],
                              mUnit);
                output.format(" 90%%: %,d %s%n",
                              mStats[p90],
                              mUnit);
                output.format(" 95%%: %,d %s%n",
                              mStats[p95],
                              mUnit);
                output.format(" 99%%: %,d %s%n",
                              mStats[p99],
                              mUnit);
                output.format(" avg: %,d %s",
                              mAverage,
                              mUnit);

                retval = output.toString();
            }

            return (retval);
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns agent statistic's name.
         * @return statistic name.
         */
        public String statsName()
        {
            return (mStatsName);
        } // end of statsName()

        /**
         * Returns unit name for stored data.
         * @return data unit name.
         */
        public String unit()
        {
            return (mUnit);
        } // end of unit()

        /**
         * Returns number of agents run on this thread.
         * @return agents run o this thread.
         */
        public int agentRunCount()
        {
            return (mCount);
        } // end of agentRunCount()

        /**
         * Returns copy of the collected agent statistics
         * collected by all subordinate dispatcher threads.
         * Array is sorted from minimum value to maximum.
         * @return sorted copy of collected agent data points.
         */
        public long[] stats()
        {
            return (Arrays.copyOf(mStats, mStats.length));
        } // end of stats()

        /**
         * Returns average value for all stats.
         * @return agent stats average.
         */
        public long average()
        {
            return (mAverage);
        } // end of average()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of class AgentStats
} // end of class EfsDispatcher
