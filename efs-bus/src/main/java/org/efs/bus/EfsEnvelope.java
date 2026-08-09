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

import jakarta.annotation.Nonnull;
import java.time.Instant;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;
import org.efs.dispatcher.IEfsAgent;
import org.efs.event.IEfsEvent;

/**
 * Immutable wrapper for an event published on an event bus.
 * An envelope carries the bus identity, the publication
 * timestamp, the publishing agent, a Lamport-style logical
 * timestamp, and the event payload. Subscribers receive this
 * metadata together with the event so they can reason about
 * ordering and causality.
 *
 * @param <E> efs event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
public final class EfsEnvelope<E extends IEfsEvent>
    implements IEfsEvent,
               Comparable<EfsEnvelope<E>>
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * JVM-unique event bus name.
     */
    @Nonnull private final String mBusName;

    /**
     * Event published to bus at this timestamp.
     */
    @Nonnull private final Instant mPublishTimestamp;

    /**
     * Agent publishing this event.
     */
    @Nonnull private final IEfsAgent mPublisher;

    /**
     * Publishing agent logical (Lamport) timestamp.
     */
    private final long mLogicalTimestamp;

    /**
     * Event contained within this envelope.
     */
    @Nonnull private final E mEvent;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new envelope for an event published on the
     * given bus.
     * @param busName JVM-unique event bus name.
     * @param pubTimestamp wall-clock timestamp at which the
     * event was published.
     * @param publisher publishing agent that emitted the event.
     * @param logicalTimestamp Lamport-style logical timestamp
     * assigned by the publisher.
     * @param event event payload contained within this envelope.
     */
    /* package */ EfsEnvelope(@Nonnull final String busName,
                              @Nonnull final Instant pubTimestamp,
                              @Nonnull final IEfsAgent publisher,
                              final long logicalTimestamp,
                              @Nonnull final E event)
    {
        mBusName = busName;
        mPublishTimestamp = pubTimestamp;
        mPublisher = publisher;
        mLogicalTimestamp = logicalTimestamp;
        mEvent = event;
    } // end of EfsEnvelope(...)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Comparable Interface Implementation.
    //

    /**
     * Compares this envelope with another envelope using the
     * event bus name, logical timestamp, and publication
     * timestamp. The comparison is intended to provide a
     * deterministic ordering for envelopes that belong to the
     * same bus.
     * @param envelope envelope to compare against.
     * @return a negative integer, zero, or a positive integer
     * if this envelope sorts before, equal to, or after
     * {@code envelope}.
     */
    @Override
    public int compareTo(final EfsEnvelope<E> envelope)
    {
        int retcode = mBusName.compareTo(envelope.mBusName);

        if (retcode == 0)
        {
                retcode =
                    Long.compare(
                        mLogicalTimestamp,
                        envelope.mLogicalTimestamp);

                if (retcode == 0)
                {
                    retcode =
                        mPublishTimestamp.compareTo(
                            envelope.publishTimestamp());
                }
        }

        return (retcode);
    } //end of compareTo(EfsEnvelope<>)

    //
    // end of Comparable Interface Implementation.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns a readable string representation of this
     * envelope, including its bus name, publication
     * timestamp, publisher, logical timestamp, and payload.
     * @return string representation of this envelope.
     */
    @Override
    public String toString()
    {
        final StringBuilder output = new StringBuilder();

        return (output.append("[bus: ")
                      .append(mBusName)
                      .append(", pub timestamp: ")
                      .append(mPublishTimestamp)
                      .append(", publisher ID: ")
                      .append(mPublisher)
                      .append(", logical timestamp: ")
                      .append(mLogicalTimestamp)
                      .append(", event: ")
                      .append(mEvent)
                      .toString());
    } // end of toString()

    /**
     * Returns {@code true} if this envelope and the given
     * object represent the same bus event identity. Two
     * envelopes are considered equal when they share the same
     * bus name, publish timestamp, publisher, and logical
     * timestamp.
     * @param o object to compare with this envelope.
     * @return {@code true} if this envelope is equal to the
     *     given object.
     */
    @Override
    @SuppressWarnings ("unchecked")
    public boolean equals(final Object o)
    {
        boolean retcode = (this == o);

        if (!retcode && o instanceof EfsEnvelope)
        {
            final EfsEnvelope<E> e = (EfsEnvelope<E>) o;

            retcode =
                (mBusName.equals(e.mBusName) &&
                 mPublishTimestamp.equals(e.mPublishTimestamp) &&
                 mPublisher == e.mPublisher &&
                 mLogicalTimestamp == e.mLogicalTimestamp);
        }

        return (retcode);
    } // end of equals(Object)

    /**
     * Returns a hash code for this envelope based on the
     * values that define its identity.
     * @return hash code for this envelope.
     */
    @Override
    public int hashCode()
    {
        return (Objects.hash(mBusName,
                             mPublishTimestamp,
                             mPublisher,
                             mLogicalTimestamp));
    } // end of hashCode()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns the name of the event bus that published this
     * envelope.
     * @return event bus name.
     */
    public String busName()
    {
        return (mBusName);
    } // end of busName()

    /**
     * Returns the wall-clock time at which the event was
     * published.
     * @return publication timestamp.
     */
    public Instant publishTimestamp()
    {
        return (mPublishTimestamp);
    } // end of publishTimestamp()

    /**
     * Returns the agent that published this event.
     * @return publishing agent.
     */
    public IEfsAgent publisher()
    {
        return (mPublisher);
    } // end of publisher()

    /**
     * Returns the Lamport-style logical timestamp assigned to
     * this event.
     * @return logical timestamp.
     */
    public long logicalTimestamp()
    {
        return (mLogicalTimestamp);
    } //end of logicalTimestamp()

    /**
     * Returns the event payload carried by this envelope.
     * @return contained event.
     */
    public E event()
    {
        return (mEvent);
    } // end of event()

    //
    // end of Get Methods.
    //-----------------------------------------------------------
} // end of class EfsEnvelope
