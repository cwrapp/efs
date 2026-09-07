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


/**
 * Dispatcher behavior interface. Provided for mocking purposes.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public interface IEfsDispatcher
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
        EFS (false, false),

        /**
         * An efs dispatcher containing a single, busy spin
         * thread (with core affinity) and a single agent.
         * In short, the agent is pinned to a single threaded
         * dispatcher.
         */
        EFS_PINNED (true, false),

        /**
         * Means that a user-defined dispatcher is being used.
         */
        SPECIAL (false, true);

    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Pinned dispatchers contain a single thread and agent.
         * New agents may not be registered with this dispatcher.
         */
        private final boolean mPinned;

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
         * Creates a new dispatcher type for the given run queue.
         * @param pinned marks this as a pinned efs dispatcher.
         * @param special marks this as a special dispatcher
         * which may not be configured.
         */
        private DispatcherType(final boolean pinned,
                               final boolean special)
        {
            mPinned = pinned;
            mSpecial = special;
        } // end of DispatcherType(boolean, boolean)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns {@code true} if this is a pinned efs
         * dispatcher and {@code false} if not.
         * @return {@code true} if dispatcher is pinned to a
         * single agent.
         */
        public boolean isPinned()
        {
            return (mPinned);
        } // end of isPinned()

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

//---------------------------------------------------------------
// Member methods.
//

    /**
     * Returns dispatcher's unique name.
     * @return dispatcher name.
     */
    String name();

    /**
     * Returns dispatcher type.
     * @return dispatcher type.
     */
    DispatcherType dispatcherType();

    /**
     * Returns dispatcher's subordinate thread count.
     * @return dispatcher thread count.
     */
    int threadCount();

    /**
     * Returns maximum number of events an agent is allowed to
     * process per callback.
     * @return maximum events per agent callback.
     */
    int maxEvents();

    /**
     * Returns configured agent event queue maximum capacity.
     * @return agent event queue maximum capacity.
     */
    int eventQueueCapacity();

    /**
     * Enqueues given agent to dispatcher run queue.
     * @param agent enqueue this efs agent.
     */
    void dispatch(EfsAgent agent);
} // end of interface IEfsDispatcher

