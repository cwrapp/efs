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

package org.efs.event;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

/**
 * This <em>mutable</em> event allows changing the encapsulated
 * event instance even when this {@code ConflationEvent} is
 * posted to an agent event queue. This means that the agent
 * received only the latest event when removed from the event
 * queue.
 * <p>
 * A {@code ConflationEvent} instance is generally created at
 * start up and re-used with a given "primary key" based on the
 * encapsulated event. The idea is that the code dispatching
 * the encapsulated event looks up the associated conflation
 * event based on the encapsulated event's primary key and calls
 * {@link #offer(IEfsEvent)}.
 * </p>
 *
 * @param <E> encapsulated event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class ConflationEvent<E extends IEfsEvent>
    implements IEfsEvent
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Encapsulated event.
     */
    private final AtomicReference<E> mEvent;

    /**
     * Contains number of missing events.
     */
    private final AtomicInteger mCurrentMissedCount;

    /**
     * Contains current missed count when conflation event is
     * {@link #poll() polled}.
     */
    private int mMissedCount;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new conflation event with no encapsulated event.
     */
    public ConflationEvent()
    {
        mEvent = new AtomicReference<>();
        mCurrentMissedCount = new AtomicInteger();
    } // end of ConflationEvent()

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns {@code true} if this conflation event does not
     * contain an encapsulated event and {@code false} if it
     * does.
     * @return {@code true} if there is no encapsulated event.
     *
     * @see #contains(Class)
     * @see #offer(E)
     */
    public boolean isEmpty()
    {
        return (mEvent.get() == null);
    } // end of isEmpty()

    /**
     * Return {@code true} if this conflation event is not
     * empty and the contained event is a class which is
     * assignable from the given class.
     * @param clazz check if non-{@code null} encapsulated event
     * is assignable from this class.
     * @return {@code true} if this conflation event contains
     * a non-{@code null} event assignable from {@code clazz}.
     */
    public boolean contains(final Class<?> clazz)
    {
        final E event = mEvent.get();

        return (event != null &&
                clazz.isAssignableFrom(event.getClass()));
    } // end of contains(Class)

    /**
     * Returns latest event contained in this conflation event
     * <em>without removing this latest event</em>. May return
     * {@code null} if there is no contained event.
     * @return latest event but does not remove it from
     * conflation event.
     *
     * @see #poll()
     * @see #offer(IEfsEvent)
     */
    @Nullable public E peek()
    {
        return (mEvent.get());
    } // end of peek()

    /**
     * Returns latest event contained in this conflation event.
     * May return {@code null} if there is no contained event.
     * @return latest encapsulated event.
     *
     * @see #peek()
     * @see #offer(IEfsEvent)
     */
    @Nullable public E poll()
    {
        mMissedCount = mCurrentMissedCount.getAndSet(0);

        return (mEvent.getAndSet(null));
    } // end of poll()

    /**
     * Returns number of missed events between initial
     * {@link #offer(IEfsEvent)} and before {@link #poll()}.
     * Note that this value is reset to zero when {@code poll()}
     * is called.
     * @return number of currently missed events.
     */
    public int currentMissedEventCount()
    {
        return (mCurrentMissedCount.get());
    } // end of currentMissedEventCount()

    /**
     * Returns missed count set after the most recent
     * {@link #poll() poll}. If no poll has yet been performed,
     * then returns zero.
     * @return missed event count.
     */
    public int missedEventCount()
    {
        return (mMissedCount);
    } // end of missedEventCount()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Set Methods.
    //

    /**
     * Sets encapsulate event and returns {@code true} if
     * encapsulated event was initially {@code null} and
     * {@code false} otherwise. If {@code true} is returned, then
     * {@code this ConflationEvent} should be dispatched to the
     * agent event queue because this conflation event is
     * currently not enqueued.
     * @param event encapsulated event.
     * @return {@code true} if {@code this ConflationEvent} needs
     * to be added to the agent event queue and {@code false}
     * if this conflation event is already enqueued.
     * @throws NullPointerException
     * if {@code event} is {@code null}.
     */
    public boolean offer(final E event)
    {
        Objects.requireNonNull(event, "event is null");

        final boolean retcode =
            (mEvent.getAndSet(event) == null);

        // Is this the initial event?
        if (!retcode)
        {
            // No and that means we overwrote and "missed" an
            // event.
            mCurrentMissedCount.incrementAndGet();
        }

        return (retcode);
    } // end of offer(E)

    //
    // end of Set Methods.
    //-----------------------------------------------------------
} // end of class ConflationEvent
