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

package org.efs.event.type;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.efs.event.IEfsEvent;
import org.efs.event.IEfsEventBuilder;
import org.reflections.ReflectionUtils;


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
     * Boolean getter methods <em>may</em> start with {@value}.
     */
    private static final String IS_PREFIX = "is";

    /**
     * Length of "is".
     */
    private static final int IS_LENGTH = IS_PREFIX.length();

    /**
     * Builder setter method names start with {@value}.
     */
    private static final String SETTER_PREFIX = "set";

    /**
     * Length of "set".
     */
    private static final int SETTER_LENGTH =
        SETTER_PREFIX.length();

    /**
     * Event public static method {@value} used to retrieve event
     * builder instance.
     */
    private static final String BUILDER_METHOD = "builder";

    /**
     * Event builder method {@value} used to create event
     * instance.
     */
    private static final String BUILD_METHOD = "build";

    /**
     * Used to report an field name which is either {@code null}
     * or an empty string.
     */
    private static final String INVALID_FIELD_NAME =
        "name is either null or an empty string";

    /**
     * Used to report {@code IllegalAccessException}s.
     */
    private static final String INACCESSIBLE = "cannot access ";

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Maps event class name to its layout instance.
     */
    private static final ConcurrentHashMap<String, EfsEventLayout<?>> sLayouts =
        new ConcurrentHashMap<>();

    /**
     * Used to look up getter, setter, and static builder method
     * handles.
     */
    private static final MethodHandles.Lookup sMethodLookup;

    /**
     * Predicate used to retrieve event getter methods.
     */
    private static final Predicate<Method> sGetterNamePredicate;

    /**
     * Predicate used to retrieve event builder setter methods.
     */
    private static final Predicate<Method> sSetterNamePredicate;

    // Class static initialization.
    static
    {
        sMethodLookup = MethodHandles.publicLookup();

        final Predicate<Method> getterMethod =
            ReflectionUtils.withNamePrefix(GETTER_PREFIX);
        final Predicate<Method> getterLength =
            (m -> (m.getName()).length() > GETTER_LENGTH);
        final Predicate<Method> isMethod =
            ReflectionUtils.withNamePrefix(IS_PREFIX);
        final Predicate<Method> isLength =
            (m -> (m.getName()).length() > IS_LENGTH);
        final Predicate<Method> isBoolean =
            ReflectionUtils.withReturnType(boolean.class);

        sGetterNamePredicate =
            ((getterMethod.and(getterLength))
             .or((isMethod).and(isLength).and(isBoolean)));

        final Predicate<Method> setterMethod =
            ReflectionUtils.withNamePrefix(SETTER_PREFIX);
        final Predicate<Method> setterLength =
            (m -> (m.getName()).length() > SETTER_LENGTH);

        sSetterNamePredicate = setterMethod.and(setterLength);
    } // end of class static initialization.

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Layout applies to this event class.
     */
    private final Class<E> mEventClass;

    /**
     * Event's {@code public static} method used to retrieve an
     * event builder instance.
     */
    private final MethodHandle mBuilderMethod;

    /**
     * Event builder method used to create an event instance.
     */
    private final MethodHandle mBuildMethod;

    /**
     * Event class field names in sorted order.
     */
    private final Map<String, EfsEventField> mFields;

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
     * @param builderMethod efs event
     * {@code public static builder()} method handle.
     * @param buildMethod efs event builder class
     * {@code public build()} method handle.
     * @param fields sorted field names list. Note: this is
     * expected to be an immutable set.
     */
    private EfsEventLayout(@Nonnull final Class<E> eventClass,
                           @Nonnull final MethodHandle builderMethod,
                           @Nonnull final MethodHandle buildMethod,
                           @Nonnull final Map<String, EfsEventField> fields)
    {
        mEventClass = eventClass;
        mBuilderMethod = builderMethod;
        mBuildMethod = buildMethod;
        mFields = fields;
    } // end of EfsEventLayout(...)

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

        for (Map.Entry<String, EfsEventField> e : mFields.entrySet())
        {
            output.append(sep)
                  .append('(')
                  .append(e.getKey())
                  .append('=')
                  .append(e.getValue())
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
     * Returns event {@code public static builder()} method
     * handle.
     * @return {@code builder()} method handle.
     */
    public MethodHandle builderMethod()
    {
        return (mBuilderMethod);
    } // end of builderMethod()

    /**
     * Returns efs event builder class required {@code build()}
     * method handle.
     * @return {@code build()} method handle.
     */
    public MethodHandle buildMethod()
    {
        return (mBuildMethod);
    } // end of buildMethod()

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
    public @Nullable EfsEventField field(final String name)
    {
        if (Strings.isNullOrEmpty(name))
        {
            throw (
                new IllegalArgumentException(INVALID_FIELD_NAME));
        }

        return (mFields.get(name));
    } // end of field(String)

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Returns layout for the given efs event class.
     * @param <E> efs event type.
     * @param eventClass efs event class.
     * @return efs event class layout.
     * @throws InvalidEventException
     * if {@code eventClass} does not contain valid getter,
     * setter, builder, or build methods or event class is
     * abstract.
     */
    @SuppressWarnings ("unchecked")
    public static <E extends IEfsEvent> EfsEventLayout<E> getLayout(final Class<E> eventClass)
        throws InvalidEventException
    {
        Objects.requireNonNull(eventClass, "eventClass is null");

        final String className = eventClass.getName();
        final EfsEventLayout<E> retval;

        // Cannot use Map.computeIfAbsent because
        // IllegalAccessException is a checked exception.
        if (sLayouts.containsKey(className))
        {
            retval = (EfsEventLayout <E>) sLayouts.get(className);
        }
        // Event class not yet parsed. Is this class abstract?
        else if (isAbstract(eventClass))
        {
            // Yes and that is not allowed.
            throw (
                new InvalidEventException(
                    eventClass, "event class is abstract"));
        }
        else
        {
            retval = parseEvent(eventClass);
            sLayouts.put(className, retval);
        }

        return (retval);
    } // end of getLayout(Class)

    /**
     * Returns {@code true} if given class is abstract and
     * {@code false} otherwise.
     * @param <E> efs event class type.
     * @param eventClass efs event class.
     * @return {@code true} if {@code eventClass} is abstract.
     */
    private static <E extends IEfsEvent> boolean isAbstract(final Class<E> eventClass)
    {
        final int modifierFlags = eventClass.getModifiers();

        return ((modifierFlags & Modifier.ABSTRACT) != 0);
    } // end of isAbstract(Class)

    /**
     * Returns a new efs event layout for the given event class
     * and its field names.
     * @param <E> efs event type.
     * @param eventClass efs event class.
     * @return efs event layout.
     * @throws IllegalAccessException
     * if {@code eventClass} contains getter, setter, builder, or
     * build methods which are not publicly accessible.
     */
    @SuppressWarnings ("unchecked")
    private static <E extends IEfsEvent> EfsEventLayout<E> parseEvent(final Class<E> eventClass)
        throws InvalidEventException
    {
        final ImmutableMap.Builder<String, EfsEventField> fieldBuilder =
            ImmutableMap.builder();
        final Method eventBuilderMethod =
            loadEventBuilderMethod(eventClass);
        final MethodHandle builderMethod;
        final Class<?> builderClass =
            eventBuilderMethod.getReturnType();
        final Set<Method> build =
            ReflectionUtils.getAllMethods(
                builderClass,
                ReflectionUtils.withPublic(),
                ReflectionUtils.withName(BUILD_METHOD),
                ReflectionUtils.withParametersCount(0),
                ReflectionUtils.withReturnTypeAssignableFrom(
                    IEfsEvent.class));
        final MethodHandle buildMethod;
        final Map<String, GetterMethod> getters =
            loadGetters(eventClass);
        final Map<String, MethodHandle> setters =
            loadSetters(eventClass, builderClass, getters);
        String fieldName;
        GetterMethod getter;
        Method method = null;

        try
        {
            method = eventBuilderMethod;
            builderMethod =
                sMethodLookup.unreflect(eventBuilderMethod);

            method = (build.iterator()).next();
            buildMethod = sMethodLookup.unreflect(method);
        }
        // NOTE: this exception should never happen because only
        //       public methods are used.
        catch (IllegalAccessException accessex)
        {
            final String message = INACCESSIBLE + method;

            throw (
                new InvalidEventException(
                    eventClass, message, accessex));
        }

        // Fill in the field builder with feeds.
        for (Map.Entry<String, GetterMethod> e : getters.entrySet())
        {
            fieldName = e.getKey();
            getter = e.getValue();

            // Does this getter have a matching setter method?
            if (setters.containsKey(fieldName))
            {
                // Yes. Add field to field builder.
                fieldBuilder.put(
                    fieldName,
                    new EfsEventField(
                        getter, setters.get(fieldName)));
            }
            // No. Assume that the get method returns a generated
            // value rather than a stored value. For example, an
            // event may contain a list of numbers and has
            // methods getMin, getMax, and getAvg. These returned
            // values are not set in the builder but generated
            // from the number list set in the builder.
            // Ignore this getter.
        }

        return (new EfsEventLayout<>(eventClass,
                                     builderMethod,
                                     buildMethod,
                                     fieldBuilder.build()));
    } // end of parseEvent(Class)

    /**
     * Returns immutable map mapping event field name to its
     * getter methods.
     * @param <E> efs event type.
     * @param eventClass load getter methods from this map.
     * @return event getter methods map.
     * @throws IllegalAccessException
     * @throws IllegalAccessException
     * if {@code eventClass} contains getter, setter, builder, or
     * build methods which are not publicly accessible.
     */
    @SuppressWarnings ("unchecked")
    private static <E extends IEfsEvent> Map<String, GetterMethod> loadGetters(final Class<E> eventClass)
        throws InvalidEventException
    {
        final Set<Method> getters =
            ReflectionUtils.getAllMethods(
                eventClass,
                ReflectionUtils.withPublic(),
                ReflectionUtils.withStatic().negate(),
                sGetterNamePredicate,
                ReflectionUtils.withParametersCount(0));
        final ImmutableMap.Builder<String, GetterMethod> builder =
            ImmutableMap.builder();
        String fieldName;
        MethodHandle mHandle;
        Class<?> fieldType;

        // Fill in getters method map.
        for (Method m : getters)
        {
            fieldName = fieldName(m.getName());
            fieldType = m.getReturnType();

            try
            {
                mHandle = sMethodLookup.unreflect(m);
            }
            // NOTE: this exception should never happen because
            //       only public methods are used.
            catch (IllegalAccessException accessex)
            {
                final String msg = INACCESSIBLE + m;

                throw (
                    new InvalidEventException(
                        eventClass, msg, accessex));
            }

            builder.put(fieldName,
                        new GetterMethod(
                            fieldName, mHandle, fieldType));
        }

        return (builder.build());
    } // end of loadGetters(Class)

    /**
     * Returns event {@code public static builder} method.
     * @param <E> efs event type.
     * @param eventClass look for public static builder method
     * in this event class.
     * @return event builder method.
     */
    @SuppressWarnings ("unchecked")
    private static <E extends IEfsEvent> Method loadEventBuilderMethod(final Class<E> eventClass)
        throws InvalidEventException
    {
        final Set<Method> builder =
            ReflectionUtils.getAllMethods(
                eventClass,
                ReflectionUtils.withPublic(),
                ReflectionUtils.withStatic(),
                ReflectionUtils.withName(BUILDER_METHOD),
                ReflectionUtils.withParametersCount(0),
                ReflectionUtils.withReturnTypeAssignableFrom(
                    IEfsEventBuilder.class));

        if (builder.isEmpty())
        {
            final String msg =
                "event class has no public static method named " +
                BUILDER_METHOD;

            throw (new InvalidEventException(eventClass, msg));
        }

        return ((builder.iterator()).next());
    } // end of loadEventBuilderMethod(Class)

    @SuppressWarnings ("unchecked")
    private static <E extends IEfsEvent> Map<String, MethodHandle> loadSetters(final Class<E> eventClass,
                                                                               final Class<?> builderClass,
                                                                               final Map<String, GetterMethod> getters)
        throws InvalidEventException
    {
        final Set<Method> setters =
            ReflectionUtils.getAllMethods(
                builderClass,
                ReflectionUtils.withPublic(),
                ReflectionUtils.withStatic().negate(),
                sSetterNamePredicate,
                ReflectionUtils.withParametersCount(1));
        final ImmutableMap.Builder<String, MethodHandle> builder =
            ImmutableMap.builder();
        String fieldName;
        Class<?> fieldType;
        GetterMethod getter;

        // Fill in setters method map.
        for (Method m : setters)
        {
            fieldName = fieldName(m.getName());
            fieldType = (m.getParameterTypes())[0];
            getter = getters.get(fieldName);


            // Does this setter method match a known event field?
            if (getter != null &&
                fieldType.equals(getter.dataType()))
            {
                // Yes, this setter's field name and parameter
                // type exactly matches a getter method. Place
                // setter method in map.
                try
                {
                    builder.put(
                        fieldName, sMethodLookup.unreflect(m));
                }
                // NOTE: this exception should never happen
                //       because only public methods are used.
                catch (IllegalAccessException accessex)
                {
                    final String msg = INACCESSIBLE + m;

                    throw (
                        new InvalidEventException(
                            eventClass, msg, accessex));
                }
            }
            // Otherwise event builder contains a setter which
            // has no matching getter. That is wrong but not a
            // problem.
        }

        return (builder.build());
    } // end of loadSetters(Class, Map<>)

    /**
     * Converts a method name into field name. For example,
     * method named "getABC" into field name "aBC".
     * @param methodName getter, is, or setter method name.
     * @return event field name.
     */
    private static String fieldName(final String methodName)
    {
        final int prefixLength;
        final String fieldName;
        final char firstChar;

        if (methodName.startsWith(GETTER_PREFIX))
        {
            prefixLength = GETTER_LENGTH;
        }
        else if (methodName.startsWith(IS_PREFIX))
        {
            prefixLength = IS_LENGTH;
        }
        else
        {
            prefixLength = SETTER_LENGTH;
        }

        fieldName = methodName.substring(prefixLength);
        firstChar = Character.toLowerCase(fieldName.charAt(0));

        return (firstChar + fieldName.substring(1));
    } // end of fieldName(String)

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Final class containing a event field's name, getter method
     * handle, setter method handle, and data type.
     */
    public static final class EfsEventField
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Field name.
         */
        private final String mName;

        /**
         * Method handle to event field getter.
         */
        private final MethodHandle mGetter;

        /**
         * Method handle to event field setter.
         */
        private final MethodHandle mSetter;

        /**
         * Event field data type.
         */
        private final Class<?> mDataType;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        private EfsEventField(final GetterMethod getter,
                              final MethodHandle setter)
        {
            mName = getter.fieldName();
            mGetter = getter.getter();
            mSetter = setter;
            mDataType = getter.dataType();
        } // end of EfsEventField(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        @Override
        public String toString()
        {
            return (
                String.format(
                    "%s: getter=%s, setter=%s, data type=%s",
                    mName,
                    mGetter,
                    mSetter,
                    mDataType));
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns field name.
         * @return field name.
         */
        public String name()
        {
            return (mName);
        } // end of name()

        /**
         * Returns field getter method handle.
         * @return getter method handle.
         */
        public MethodHandle getter()
        {
            return (mGetter);
        } // end of getter()

        /**
         * Returns field setter method handle.
         * @return setter method handle.
         */
        public MethodHandle setter()
        {
            return (mSetter);
        } // end of setter()

        /**
         * Returns field Java type.
         * @return Java type.
         */
        public Class<?> dataType()
        {
            return (mDataType);
        } // end of dataType()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of class EfsEventField

    /**
     * Contains event getter method field name, method handle,
     * and data type.
     */
    private static final class GetterMethod
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        private final String mFieldName;
        private final MethodHandle mGetter;
        private final Class<?> mDataType;

    //-----------------------------------------------------------
    // Member methods.
    //
        //-------------------------------------------------------
        // Constructors.
        //

        private GetterMethod(final String name,
                             final MethodHandle getter,
                             final Class<?> dataType)
        {
            mFieldName = name;
            mGetter = getter;
            mDataType = dataType;
        } // end of EventField(String, MethodHandle, Class)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Object Method Overrides.
        //

        /**
         * Returns text containing field name, getter method
         * signature, and data type name.
         * @return textual representation of this object.
         */
        @Override
        public String toString()
        {
            return (String.format("%s: %s, %s",
                                  mFieldName,
                                  mGetter,
                                  mDataType.getName()));
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns event field name.
         * @return event field name.
         */
        public String fieldName()
        {
            return (mFieldName);
        } // end of fieldName()

        /**
         * Returns event getter method handle.
         * @return event getter method handle.
         */
        public MethodHandle getter()
        {
            return (mGetter);
        } // end of getter()

        /**
         * Returns event field data type.
         * @return event field data type.
         */
        public Class<?> dataType()
        {
            return (mDataType);
        } // end of javaType()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of class EventFieldMethod
} // end of class EfsEventLayout
