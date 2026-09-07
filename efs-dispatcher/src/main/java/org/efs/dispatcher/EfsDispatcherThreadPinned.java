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

package org.efs.dispatcher;

import jakarta.annotation.Nonnull;
import java.time.Instant;
import java.util.Objects;
import net.openhft.affinity.AffinityLock;
import net.sf.eBus.util.Validator;
import static org.efs.dispatcher.EfsDispatcherThreadAbstract.NO_EFS_AGENT;
import org.efs.dispatcher.config.ThreadType;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * Dispatcher thread that runs a single pinned efs agent on a
 * dedicated busy-spin loop.
 * <p>
 * This implementation binds the thread to a single agent and
 * continuously drains that agent's event queue while the agent
 * remains registered. It is intended for the pinned-agent model,
 * where the thread does not participate in a shared run queue and
 * instead owns the full execution cycle for one agent instance.
 * </p>
 * <p>
 * Pinned dispatchers are an advanced feature, requiring
 * knowledge of isolated cores and thread affinity for cores. It
 * is recommended that this feature be used with care.
 * </p>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsDispatcherThreadPinned
    extends EfsDispatcherThreadAbstract
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    // Exception messages.

    /**
     * If a {@code null} agent is specified, this results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_AGENT =
        "pinned agent is null";

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(
            EfsDispatcherThreadPinned.class);

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Single efs agent pinned to this dispatcher thread.
     */
    private final EfsAgentAbstract mPinnedAgent;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new instance of EfsDispatcherThreadPinned.
     */
    private EfsDispatcherThreadPinned(final Builder builder)
    {
        super (builder);

        mPinnedAgent = builder.mPinnedAgent;
    } // end of EfsDispatcherThreadPinned(Builder)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Thread Method Overrides.
    //

    /**
     * Runs the pinned dispatcher thread's event loop.
     * <p>
     * The thread attaches to the configured CPU affinity, marks
     * itself busy while the pinned agent is active, and repeatedly
     * invokes the agent's event-processing method until the run
     * flag is cleared or the agent is de-registered. Each cycle
     * records the thread state and agent statistics before the
     * next iteration begins.
     * </p>
     * <p>
     * <em>Note:</em> it is fully expected that once
     * {@link EfsAgentPinned#processEvents()} is called, that
     * method will not return while the dispatcher is running.
     * In short, there will be not iterating over the agent and
     * no agent statistics will be collected.
     * </p>
     */
    @Override
    public void run()
    {
        final String threadName = this.getName();
        final AffinityLock affinityLock;
        long busyStart;
        int eventCount;

        mStats.startTime(Instant.now());
        mStats.updateState(DispatcherThreadState.BUSY,
                           mPinnedAgent.agentName());

        // Note: mAffinity is guaranteed to be not null.
        affinityLock = setAffinity();

        sLogger.debug("{}: running agent {}.",
                      threadName,
                      mPinnedAgent);

        while (mRunFlag && mPinnedAgent.isRegistered())
        {
            busyStart = System.nanoTime();
            eventCount = 0;

            try
            {
                eventCount = mPinnedAgent.processEvents();
            }
            catch (Exception jex)
            {
                sLogger.warn(
                    "{}: agent {} event processing exception.",
                    threadName,
                    mPinnedAgent.agentName(),
                    jex);
            }
            finally
            {
                final long busyStop = System.nanoTime();
                final long busyTime = (busyStop - busyStart);

                // Agent finished with its event processing.
                mStats.updateState(DispatcherThreadState.IDLE,
                                   NO_EFS_AGENT);
                mStats.updateAgentStats(0L,
                                        busyTime,
                                        eventCount);
            }
        }

        releaseAffinity(affinityLock);

        sLogger.debug("{}: stopped.", threadName);
    } // end of run()

    //
    // end of Thread Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // EfsDispatcherThreadAbstract Method Overrides.
    //

    /**
     * First de-registers pinned agent first and then sets thread
     * run flag to {@code false}.
     */
    @Override
    /* package */ void shutdown()
    {
        mPinnedAgent.deregister();

        super.shutdown();
    } // end of shutdown()

    //
    // end of EfsDispatcherThreadAbstract Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    @SuppressWarnings({"java:S1192"})
    @Override
    public String toString()
    {
        final StringBuilder output = new StringBuilder();

        return (output.append('[')
                      .append(getName())
                      .append(", type=")
                      .append(mThreadType)
                      .append(", state=")
                      .append(mStats.threadState())
                      .append(']').toString());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    /**
     * Returns a new pinned dispatcher thread builder instance.
     * @return pinned dispatcher thread builder.
     */
    /* package */ static Builder builder()
    {
        return (new Builder());
    } // end of builder()

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Builder for creating a dispatcher thread dedicated to a
     * single pinned agent.
     * <p>
     * This builder enforces the pinned-thread execution model by
     * associating one {@link EfsAgentPinned} with a dedicated
     * dispatcher thread and validating that the required agent
     * has been configured before the thread instance is created.
     * </p>
     */
    /* package */ static final class Builder
        extends ThreadBuilder<EfsDispatcherThreadPinned, Builder>
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Single agent instance pinned to this dispatcher
         * thread.
         */
        private EfsAgentPinned mPinnedAgent;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private Builder()
        {
            super (EfsDispatcherThreadPinned.class);

            mThreadType = ThreadType.SPINNING;
        } // end of Builder()

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Abstract Method Implementations.
        //

        @Override
        protected Builder self()
        {
            return (this);
        } // end of self()

        @Override
        protected EfsDispatcherThreadPinned buildImpl()
        {
            return (new EfsDispatcherThreadPinned(this));
        } // end of buildImpl()

        //
        // end of Abstract Method Implementations.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Set Methods.
        //

        /**
         * Sets single agent pinned to this dispatcher thread.
         * @param agent agent pinned to target dispatcher
         * thread.
         * @return {@code this Builder} instance.
         */
        /* package */ Builder pinnedAgent(@Nonnull final EfsAgentPinned agent)
        {
            mPinnedAgent =
                Objects.requireNonNull(agent, NULL_AGENT);

            return (this);
        } // end of pinnedAgent(EfsAgentPinned)

        //
        // end of Set Methods.
        //-------------------------------------------------------

        /**
         * Enhances {@link ThreadBuilder#validate(Validator)}
         * with pinned agent is set check.
         * @param problems store invalid builder settings into
         * this list.
         * @return {@code problems}.
         */
        @Override
        protected Validator validate(final Validator problems)
        {
            return (
                super.validate(problems)
                     .requireNotNull(mPinnedAgent,
                                     "pinnedAgent"));
        } // end of validate(Validator)
    } // end of class Builder
} // end of class EfsDispatcherThreadPinned
