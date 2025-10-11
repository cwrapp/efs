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

package org.efs.activator;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.efs.activator.event.ActivatorEvent;
import org.efs.dispatcher.IEfsAgent;

/**
 * Collects {@code ActivatorEvent}s which are used to compare
 * against expected results.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class ActivatorListener
    implements IEfsAgent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Agent unique name.
     */
    private final String mAgentName;

    /**
     * Stores received activator events.
     */
    private final List<ActivatorEvent> mEvents;

    /**
     * Used to track activator changes.
     */
    private CountDownLatch mDoneSignal;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new activator listener with the given name.
     * @param agentName unique agent name.
     */
    public ActivatorListener(final String agentName)
    {
        mAgentName = agentName;
        mEvents = new ArrayList<>();
    } // end of ActivatorListener(String)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // IEfsAgent Interface Implementation.
    //

    @Override
    public String name()
    {
        return (mAgentName);
    } // end of name()

    //
    // end of IEfsAgent Interface Implementation.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns an immutable list for received activator events.
     * @return received activator events as an immutable list.
     */
    public List<ActivatorEvent> events()
    {
        return (ImmutableList.copyOf(mEvents));
    } // end of events()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets "all events received" signal.
     * @param doneSignal tracks activator changes.
     */
    public void doneSignal(final CountDownLatch doneSignal)
    {
        mDoneSignal = doneSignal;
    } // end of doneSignal(CountDownLatch)

    /**
     * Clears out collected activator events.
     */
    public void clearEvents()
    {
        mEvents.clear();
    } // end of clearEvent()

    //
    // end of Set Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Event Handler Methods.
    //

    private void onActivatorChange(final ActivatorEvent event)
    {
        mEvents.add(event);
        mDoneSignal.countDown();
    } // end of onActivatorChange(ActivatorEvent)

    //
    // end of Event Handler Methods.
    //-----------------------------------------------------------

    public void register(final EfsActivator activator)
    {
        activator.registerListener(
            this::onActivatorChange, this);
    } // end of register(EfsActivator)

    public void deregister(final EfsActivator activator)
    {
        activator.deregisterListener(
            this::onActivatorChange, this);
    } // end of deregister(EfsActivator)
} // end of class ActivatorListener
