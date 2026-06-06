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

package org.efs.io;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import jakarta.annotation.Nonnull;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.efs.event.IEfsEvent;
import org.reflections.ReflectionUtils;

/**
 * For a given efs event class, maps its field names to its
 * "getter " method information. This information includes the
 * full method name, return type, and {@link CQAttribute}
 * annotation. A "getter" method is one of two signatures:
 * "{@code &lt;return type&gt; getXXX()}" or
 * "{@code [bB]oolean isXXX()}" where "XXX" is the field name
 * (with first character in lower case).
 * <p>
 * This class is used to generate
 * {@link com.googlecode.cqengine.attribute.Attribute CQEngine attribute}
 * and
 * {@link com.googlecode.cqengine.index.AttributeIndex CQEngine attribute index}
 * instances in {@link EfsEventFile} indexed collection. For
 * this reason, if a "getter" method does <em>not</em> have an
 * associated {@code CQAttribute} annotation, that method is
 * ignored.
 * </p>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Immutable
/* package */ final class EfsEventLayout<E extends IEfsEvent>
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * If a {@code null} event class is provided, then a
     * {@code NullPointerException} is thrown with message
     * {@value}.
     */
    public static final String NULL_EVENT_CLASS =
        "eventClass is null";

    /**
     * If an abstract event class is provided, then an
     * {@code IllegalArgumentException} is thrown with message
     * {@value}.
     */
    public static final String ABSTRACT_EVENT_CLASS =
        "event class \"%s\" is abstract";

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

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Maps event class name to its layout instance.
     */
    private static final ConcurrentHashMap<String, EfsEventLayout<?>> sLayouts =
        new ConcurrentHashMap<>();

    /**
     * Predicate used to retrieve event getter methods.
     */
    private static final Predicate<Method> sGetterNamePredicate;

    // Class static initialization.
    static
    {
        final Predicate<Method> getterMethod =
            ReflectionUtils.withNamePrefix(GETTER_PREFIX);
        final Predicate<Method> getterLength =
            (m -> (m.getName()).length() > GETTER_LENGTH);
        final Predicate<Method> isMethod =
            ReflectionUtils.withNamePrefix(IS_PREFIX);
        final Predicate<Method> isLength =
            (m -> (m.getName()).length() > IS_LENGTH);
        final Predicate<Method> isBoolean =
            ReflectionUtils.withReturnType(boolean.class)
            .or(ReflectionUtils.withReturnType(Boolean.class));

        // Note: length predicates make sure the method name is
        // more than just "get" or "is".
        sGetterNamePredicate =
            ((getterMethod.and(getterLength))
             .or((isMethod).and(isLength).and(isBoolean)));
    } // end of class static initialization.

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
    private final Map<String, GetterMethod> mGetters;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new efs event class layout instance for given
     * event class.
     * @param eventClass layout is for this efs event class.
     * @param getters immutable mapping from event field to its
     * getter method.
     */
    private EfsEventLayout(@Nonnull final Class<E> eventClass,
                           @Nonnull final Map<String, GetterMethod> getters)
    {
        mEventClass = eventClass;
        mGetters = getters;
    } // end of EfsEventLayout(Class)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Object Method Overrides.
    //

    /**
     * @inheritDoc
     */
    @Override
    public String toString()
    {
        String sep = "";
        final StringBuilder output = new StringBuilder();

        output.append("[class=")
              .append(mEventClass.getName())
              .append(", fields={");

        for (Map.Entry<String, GetterMethod> e : mGetters.entrySet())
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
    /* package */ Class<E> eventClass()
    {
        return (mEventClass);
    } // end of eventClass()

    /**
     * Returns immutable map of event field names to getter
     * method information.
     * @return event value getter methods.
     */
    /* package */ Map<String, GetterMethod> getters()
    {
        return (mGetters);
    } // end of getters()

    //
    // end of Get Methods.
    //-----------------------------------------------------------

    /**
     * Returns get method layout for the given efs event class.
     * @param <E> efs event type.
     * @param eventClass efs event class.
     * @return efs event class layout.
     * @throws NullPointerException
     * if {@code eventClass} is {@code null}.
     * @throws IllegalArgumentException
     * if {@code eventClass} is abstract.
     */
    @SuppressWarnings ("unchecked")
    @Nonnull
    public static <E extends IEfsEvent> EfsEventLayout<E> getLayout(final Class<E> eventClass)
    {
        Objects.requireNonNull(eventClass, NULL_EVENT_CLASS);

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
                new IllegalArgumentException(
                    String.format(
                        ABSTRACT_EVENT_CLASS,
                        eventClass.getName())));
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
     * and its getter methods.
     * @param <E> efs event type.
     * @param eventClass efs event class.
     * @return efs event layout.
     */
    @SuppressWarnings ("unchecked")
    private static <E extends IEfsEvent> EfsEventLayout<E> parseEvent(final Class<E> eventClass)
    {
        // Set contains only those methods named
        // "returnType getXXX()" or "boolean isXXX()".
        final Set<Method> getters =
            ReflectionUtils.getAllMethods(
                eventClass,
                ReflectionUtils.withPublic(),
                ReflectionUtils.withStatic().negate(),
                sGetterNamePredicate,
                ReflectionUtils.withParametersCount(0));
        final ImmutableMap.Builder<String, GetterMethod> builder =
            ImmutableMap.builder();
        CQAttribute attribute;
        String methodName;
        String fieldName;

        // Fill in getters method map.
        for (Method m : getters)
        {
            attribute = m.getAnnotation(CQAttribute.class);

            // Does this getter method have an associated
            // @CQAttribute annotation?
            if (attribute != null)
            {
                // Yes, add this getter to the map.
                methodName = m.getName();
                fieldName = fieldName(methodName);

                builder.put(fieldName,
                            new GetterMethod(methodName,
                                            fieldName,
                                            m.getReturnType(),
                                            attribute));
            }
            // No, this getter has no attribute. Since this
            // event layout is used to generate CQEngine
            // attributes and indices, we don't care about
            // getters without an attribute annotation.
        }

        return (
            new EfsEventLayout<>(eventClass, builder.build()));
    } // end of parseEvent(Class)

    /**
     * Converts a method name into field name. For example,
     * method named "getABC" into field name "aBC".
     * @param methodName "get" or "is" method name.
     * @return event field name.
     */
    private static String fieldName(final String methodName)
    {
        final int prefixLength;
        final String fieldName;
        final char firstChar;

        // Does this method start with "get"?
        if (methodName.startsWith(GETTER_PREFIX))
        {
            // Yes, subtract this length.
            prefixLength = GETTER_LENGTH;
        }
        // Else this method starts with "is".
        else
        {
            prefixLength = IS_LENGTH;
        }

        fieldName = methodName.substring(prefixLength);
        firstChar = Character.toLowerCase(fieldName.charAt(0));

        return (firstChar + fieldName.substring(1));
    } // end of fieldName(String)

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Contains event getter method field name, method handle,
     * and data type.
     */
    /* package */ static final class GetterMethod
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Full {@code getXXX()} or {@code isXXX()} method name.
         */
        @Nonnull private final String mMethodName;

        /**
         * Field name extracted from method name with first
         * character converted to lower case.
         */
        @Nonnull private final String mFieldName;

        /**
         * Get method return type.
         */
        private final Class<?> mDataType;

        /**
         * Associated {@link CQAttribute} annotation.
         */
        private final CQAttribute mAnnotation;

    //-----------------------------------------------------------
    // Member methods.
    //
        //-------------------------------------------------------
        // Constructors.
        //

        private GetterMethod(final String methodName,
                             final String name,
                             final Class<?> dataType,
                             final CQAttribute annotation)
        {
            mMethodName = methodName;
            mFieldName = name;
            mDataType = dataType;
            mAnnotation = annotation;
        } // end of EventField(String,String,Class,CQAttribute)

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
            return (
                String.format("%s: @CQAttribute(%s, %s) %s %s()",
                              mFieldName,
                              mAnnotation.attribute(),
                              mAnnotation.index(),
                              mDataType.getName(),
                              mMethodName));
        } // end of toString()

        //
        // end of Object Method Overrides.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns "getter" method name which starts with either
         * "get" or "is".
         * @return Full "getter" method name.
         */
        public String methodName()
        {
            return (mMethodName);
        } // end of methodName()

        /**
         * Returns event field name.
         * @return event field name.
         */
        public String fieldName()
        {
            return (mFieldName);
        } // end of fieldName()

        /**
         * Returns event field data type.
         * @return event field data type.
         */
        public Class<?> dataType()
        {
            return (mDataType);
        } // end of javaType()

        /**
         * Returns CQEngine attribute annotation.
         * @return CQEngine attribute annotation.
         */
        public CQAttribute annotation()
        {
            return (mAnnotation);
        } // end of annotation()

        //
        // end of Get Methods.
        //-------------------------------------------------------
    } // end of class GetterMethod
} // end of class EfsEventLayout
