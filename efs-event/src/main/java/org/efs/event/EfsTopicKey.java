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

import com.google.common.base.Strings;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;
import net.sf.eBus.util.MultiKey2;
import org.efs.event.IEfsEvent;

/**
 * Immutable key containing efs event class and topic uniquely
 * defining an efs bus feed instance.
 *
 * @param <E> efs event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
public final class EfsTopicKey<E extends IEfsEvent>
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Use {@value} as event class and topic separator.
     */
    public static final char KEY_IFS = '|';

    /**
     * Event key string format is {@value}.
     */
    public static final String KEY_FORMAT = "%s%c%s";

    /**
     * An invalid topic is reported with an
     * {@code IllegalArgumentException} containing the message
     * {@value}.
     */
    public static final String INVALID_TOPIC =
        "topic is either null, an empty string, or blank";

    /**
     * Feed key cache initial size is {@value}.
     */
    private static final int INITIAL_CACHE_SIZE = 1_024;

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Caches each uniquely defined efs feed key based on key
     * class and topic.
     */
    private static final Map<MultiKey2<Class<? extends IEfsEvent>, String>, EfsTopicKey<? extends IEfsEvent>> sKeyCache =
        new HashMap<>(INITIAL_CACHE_SIZE);

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Bus event class.
     */
    private final Class<E> mEventClass;

    /**
     * Bus topic.
     */
    private final String mTopic;

    /**
     * Compute hash code once on construction.
     */
    private final int mHashCode;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new efs bus topic key for given even class and
     * topic.
     * @param eventClass efs event class.
     * @param topic efs feed topic.
     */
    private EfsTopicKey(final Class<E> eventClass,
                        final String topic)
    {
        mEventClass = eventClass;
        mTopic = topic;
        mHashCode = Objects.hash(eventClass, topic);
    } // end of EfsTopicKey(Class, String)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns {@code true} if {@code o} is same
     * {@code EfsTopicKey} instance as {@code this EfsTopicKey}
     * instance.
     * @param o comparison object.
     * @return {@code true} if {@code o} is equivalent to
     * {@code this}.
     */
    @Override
    public boolean equals(final Object o)
    {
        return (this == o);
    } // end of equals(Object)

    /**
     * Returns efs topic key hash code based on efs event class
     * and topic.
     * @return efs topic key hash.
     */
    @Override
    public int hashCode()
    {
        return (mHashCode);
    } // end of hashCode()

    /**
     * Returns efs event key textual representation.
     * @return a textual representation.
     */
    @Override
    public String toString()
    {
        return (String.format(KEY_FORMAT,
                              mEventClass.getName(),
                              KEY_IFS,
                              mTopic));
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns feed key's efs event class.
     * @return efs event class.
     */
    public Class<? extends IEfsEvent> eventClass()
    {
        return (mEventClass);
    } // end of eventClass()

    /**
     * Returns feed key's efs event class name. This is the
     * {@code Class.getName()} value.
     * @return efs event class name.
     */
    public String eventClassName()
    {
        return (mEventClass.getName());
    } // end of eventClassName()

    /**
     * Returns feed key's topic.
     * @return efs feed topic.
     */
    public String topic()
    {
        return (mTopic);
    } // end of topic()

    /**
     * Returns a unique efs feed key instance for given efs event
     * class and feed topic.
     * @param <E> efs event type.
     * @param ec efs event class.
     * @param topic efs feed topic.
     * @return unique efs feed key.
     * @throws NullPointerException
     * if {@code ec} is {@code null}.
     * @throws IllegalArgumentException
     * if {@code topic} is either {@code null} or an empty
     * string.
     */
    @SuppressWarnings ("unchecked")
    public static <E extends IEfsEvent> EfsTopicKey<E> getKey(final Class<E> ec,
                                                              final String topic)
    {
        Objects.requireNonNull(ec, "ec is null");

        if (Strings.isNullOrEmpty(topic) || topic.isBlank())
        {
            throw (new IllegalArgumentException(INVALID_TOPIC));
        }

        final MultiKey2<Class<? extends IEfsEvent>, String> key =
            new MultiKey2<>(ec, topic);
        final EfsTopicKey<E> retval;

        // Create new efs feed key only if it does not already
        // exist.
        if (sKeyCache.containsKey(key))
        {
            retval = (EfsTopicKey<E>) sKeyCache.get(key);
        }
        else
        {
            retval = new EfsTopicKey<>(ec, topic);
            sKeyCache.put(key, retval);
        }

        return (retval);
    } // end of getKey(Class, String)

    //
    // end of Get Methods.
    //-----------------------------------------------------------
} // end of class EfsTopicKey
