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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;


/**
 * Contains "getter" field names for a given efs event class.
 *
 * @param <E> efs event class
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public final class EfsEventLayout<E extends IEfsEvent>
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Getter method names start with {@value}.
     */
    private static final String GETTER_PREFIX = "get";

    /**
     * Length of "get".
     */
    private static final int GETTER_LENGTH =
        GETTER_PREFIX.length();

    /**
     * {@code Object.getClass()} method.
     */
    private static final String CLASS_GETTER = "getClass";

    private static final String INVALID_FIELD_NAME =
        "name is either null or an empty string";

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Contains parsed event layout instances.
     */
    private static final ConcurrentHashMap<String, EfsEventLayout<?>> sLayouts =
        new ConcurrentHashMap<>();

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Layout applies to this event class.
     */
    private final Class<E> mEventClass;

    /**
     * Event class field names in sorted order.
     */
    private final Map<String, Class<?>> mFields;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new event layout instance for given event class
     * and field names sorted set.
     * @param eventClass efs event class.
     * @param fields sorted field names list. Note: this is
     * expected to be an immutable set.
     */
    private EfsEventLayout(@Nonnull final Class<E> eventClass,
                           @Nonnull final Map<String, Class<?>> fields)
    {
        mEventClass = eventClass;
        mFields = fields;
    } // end of EfsEventLayout(Class, SortedSet)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * Returns textual representation of this efs event layout.
     * Text is on a single line and formatted:
     * <pre><code>[class=&lt;<em>class name</em>&gt;, field={<em>field name list</em>}]</code></pre>.
     * @return textual representation of this object.
     */
    @Override
    public String toString()
    {
        String sep = "";
        final StringBuilder output = new StringBuilder();

        output.append("[class=")
              .append(mEventClass.getName())
              .append(", fields={");

        for (Map.Entry<String, Class<?>> e : mFields.entrySet())
        {
            output.append(sep)
                  .append('(')
                  .append(e.getKey())
                  .append('=')
                  .append((e.getValue()).getName())
                  .append(')');

            sep = ",";
        }

        return (output.append("}]").toString());
    } // end of toString()

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns efs event class.
     * @return efs event class.
     */
    public Class<E> eventClass()
    {
        return (mEventClass);
    } // end of eventClass()

    /**
     * Returns an immutable list of event class field names.
     * @return event class field names list.
     */
    public List<String> fields()
    {
        return (ImmutableList.copyOf(mFields.keySet()));
    } // end of fields()

    /**
     * Returns {@code true} if given name is an event field and
     * {@code false} otherwise.
     * @param name event field name.
     * @return {@code true} if {@code name} is an event field
     * for this layout class.
     * @throws IllegalArgumentException
     * if {@code name} is either {@code null} or an empty string.
     */
    public boolean containsField(final String name)
    {
        if (Strings.isNullOrEmpty(name))
        {
            throw (
                new IllegalArgumentException(INVALID_FIELD_NAME));
        }

        return (mFields.containsKey(name));
    } // end of containsField(String)

    /**
     * Returns event field's type. Will return {@code null} if
     * there is no such named field.
     * @param name event field name.
     * @return efs event field type for given named field.
     * @throws IllegalArgumentException
     * if {@code name} is either {@code null} or an empty string.
     */
    public @Nullable Class<?> fieldType(final String name)
    {
        if (Strings.isNullOrEmpty(name))
        {
            throw (
                new IllegalArgumentException(INVALID_FIELD_NAME));
        }

        return (mFields.get(name));
    } // end of fieldType(String)

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Returns layout for the given efs event class.
     * @param <E> efs event type.
     * @param eventClass efs event class.
     * @return efs event class layout.
     */
    @SuppressWarnings ("unchecked")
    public static <E extends IEfsEvent> EfsEventLayout<E> getLayout(final Class<E> eventClass)
    {
        Objects.requireNonNull(eventClass, "eventClass is null");

        final String className = eventClass.getName();

        return (
            (EfsEventLayout<E>) (
                sLayouts.computeIfAbsent(
                    className, c -> parseFields(eventClass))));
    } // end of getLayout(Class)

    /**
     * Returns a new efs event layout for the given event class
     * and its field names.
     * @param <E> efs event type.
     * @param eventClass efs event class.
     * @return efs event layout.
     */
    private static <E extends IEfsEvent> EfsEventLayout<E> parseFields(final Class<E> eventClass)
    {
        final ImmutableMap.Builder<String, Class<?>> fieldBuilder =
            ImmutableMap.builder();
        final Method[] methods = eventClass.getMethods();
        final int numFields = methods.length;
        int mi;
        String methodName;

        for (mi = 0; mi < numFields; ++mi)
        {
            methodName = methods[mi].getName();

            // Does this method take any arguments? Must be zero.
            // Does method name start with "get"?
            // Is method name more than just "get"?
            // Is this *not* "getClass"?
            if (methods[mi].getParameterCount() == 0 &&
                methodName.startsWith(GETTER_PREFIX) &&
                methodName.length() >= GETTER_LENGTH &&
                !methodName.equals(CLASS_GETTER))
            {
                fieldBuilder.put(getFieldName(methodName),
                                 methods[mi].getReturnType());
            }
        }

        return (new EfsEventLayout<>(eventClass,
                                     fieldBuilder.build()));
    } // end of parseFields(Class)

    /**
     * Converts a getter method name of the form "getABC" into
     * a field name "aBC".
     * @param methodName getter method name.
     * @return event field name.
     */
    private static String getFieldName(final String methodName)
    {
        final char firstChar = methodName.charAt(GETTER_LENGTH);
        final String fieldName =
          methodName.substring(GETTER_LENGTH + 1);

        return (Character.toLowerCase(firstChar) + fieldName);
    } // end of getFieldName(String)
} // end of class EfsEventLayout
