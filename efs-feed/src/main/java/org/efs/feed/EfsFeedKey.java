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

import com.google.common.base.Strings;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;
import net.sf.eBus.util.MultiKey2;
import org.efs.event.IEfsEvent;

/**
 * Immutable key containing efs event class and subject uniquely
 * defining an efs feed instance.
 *
 * @param <E> efs event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
public final class EfsFeedKey<E extends IEfsEvent>
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Use {@value} as event class and subject separator.
     */
    public static final char KEY_IFS = '|';

    /**
     * Event key string format is {@value}.
     */
    public static final String KEY_FORMAT = "%s%c%s";

    /**
     * Feed key cache initial size is {@value}.
     */
    private static final int INITIAL_CACHE_SIZE = 1_024;

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Caches each uniquely defined efs feed key based on key
     * class and subject.
     */
    private static final Map<MultiKey2<Class<? extends IEfsEvent>, String>, EfsFeedKey<? extends IEfsEvent>> sKeyCache =
        new HashMap<>(INITIAL_CACHE_SIZE);

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Feed event class.
     */
    private final Class<E> mEventClass;

    /**
     * Feed subject.
     */
    private final String mSubject;

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
     * Creates a new efs feed key for given even class and
     * subject.
     * @param ec efs event class.
     * @param subject efs feed subject.
     */
    private EfsFeedKey(final Class<E> ec,
                       final String subject)
    {
        mEventClass = ec;
        mSubject = subject;
        mHashCode = Objects.hash(ec, subject);
    } // end of EfsFeedKey(Class, String)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns {@code true} if {@code o} is same
     * {@code EfsFeedKey} instance as {@code this EfsFeedKey}
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
     * Returns efs feed key hash code based on efs event class
     * and subject
     * @return efs feed key hash.
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
                              mSubject));
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
     * Returns feed key's subject.
     * @return efs feed subject.
     */
    public String subject()
    {
        return (mSubject);
    } // end of subject()

    /**
     * Returns a unique efs feed key instance for given efs event
     * class and feed subject.
     * @param <E> efs event type.
     * @param ec efs event class.
     * @param subject efs feed subject.
     * @return unique efs feed key.
     * @throws NullPointerException
     * if {@code ec} is {@code null}.
     * @throws IllegalArgumentException
     * if {@code subject} is either {@code null} or an empty
     * string.
     */
    @SuppressWarnings ("unchecked")
    public static <E extends IEfsEvent> EfsFeedKey<E> getKey(final Class<E> ec,
                                                             final String subject)
    {
        Objects.requireNonNull(ec, "ec is null");

        if (Strings.isNullOrEmpty(subject))
        {
            throw (
                new IllegalArgumentException(
                    "subject is either null or an empty string"));
        }

        final MultiKey2<Class<? extends IEfsEvent>, String> key =
            new MultiKey2<>(ec, subject);
        final EfsFeedKey<E> retval;

        // Create new efs feed key only if it does not already
        // exist.
        if (sKeyCache.containsKey(key))
        {
            retval = (EfsFeedKey<E>) sKeyCache.get(key);
        }
        else
        {
            retval = new EfsFeedKey<>(ec, subject);
            sKeyCache.put(key, retval);
        }

        return (retval);
    } // end of getKey(Class, String)

    //
    // end of Get Methods.
    //-----------------------------------------------------------
} // end of class EfsFeedKey
