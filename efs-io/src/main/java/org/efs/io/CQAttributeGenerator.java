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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.MultiValueNullableAttribute;
import com.googlecode.cqengine.index.hash.HashIndex;
import com.googlecode.cqengine.index.navigable.NavigableIndex;
import com.googlecode.cqengine.index.radix.RadixTreeIndex;
import com.googlecode.cqengine.index.radixreversed.ReversedRadixTreeIndex;
import com.googlecode.cqengine.index.suffix.SuffixTreeIndex;
import com.googlecode.cqengine.index.unique.UniqueIndex;
import com.googlecode.cqengine.query.option.QueryOptions;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import static javassist.CtNewConstructor.PASS_PARAMS;
import javassist.CtNewMethod;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.SignatureAttribute;
import org.efs.event.IEfsEvent;
import org.efs.io.EfsEventLayout.GetterMethod;
import org.efs.logging.AsyncLoggerFactory;
import org.slf4j.Logger;

/**
 * Generates CQEngine {@link Attribute} implementations for a given
 * {@link EfsEventLayout} using Javassist bytecode manipulation.
 * <p>
 * For each getter method in the layout, a corresponding Attribute
 * implementation is generated based on the {@link CQAttributeType}
 * specified in the {@link CQAttribute} annotation. The generated
 * class extends the appropriate CQEngine attribute superclass
 * (SimpleAttribute, SimpleNullableAttribute, MultiValueAttribute,
 * or MultiValueNullableAttribute) and implements the getValue method.
 * </p>
 * <p>
 * Generated attributes support the generic type parameter
 * {@code E extends IEfsEvent} to work with arbitrary event types
 * stored in {@link EfsRow}.
 * </p>
 *
 * @param <E> efs event type.
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@SuppressWarnings({"java:S1192", "java:S3740"})
/* package */ final class CQAttributeGenerator<E extends IEfsEvent>
{
//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Constants.
    //

    /**
     * Javassist class pool for bytecode generation.
     */
    private static final ClassPool CLASS_POOL =
        initializeClassPool();

    /**
     * Package name for generated attribute classes.
     */
    private static final String GENERATED_PACKAGE =
        "org.efs.io.generated";

    /**
     * Prefix for generated attribute class names.
     */
    private static final String CLASS_NAME_PREFIX = "Attr_";

    /**
     * Class name field separator.
     */
    private static final String CLASS_NAME_SEPARATOR = "_";

    /**
     * Override {@value} method in single-value attribute class.
     */
    private static final String GET_VALUE_METHOD = "getValue";

    /**
     * Override {@value} method in multi-value attribute class.
     */
    private static final String GET_VALUES_METHOD = "getValues";

    /**
     * Fully-qualified {@code EfsRow} class name.
     */
    private static final String EFS_ROW_CLASS_NAME =
        EfsRow.class.getName();

    // Exception messages.

    /**
     * A {@code null EfsEventLayout} argument results in a
     * {@code NullPointerException} with message {@value}.
     */
    public static final String NULL_LAYOUT = "layout is null";

    //-----------------------------------------------------------
    // Statics.
    //

    /**
     * Maps fully-qualified class name to its attributes map.
     * Synchronize on this map prior to using, either for
     * retrieval or insertion.
     */
    private static final Map sAttributes = new HashMap<>();

    /**
     * Maps primitive class name to its Object equivalent class
     * name.
     */
    private static final Map<String, String> sPrimitiveMap;

    /**
     * Logging subsystem interface.
     */
    private static final Logger sLogger =
        AsyncLoggerFactory.getLogger(CQAttributeGenerator.class);

    // Class static initialization.
    static
    {
        final Class[] primitiveClasses =
        {
            boolean.class,
            byte.class,
            char.class,
            double.class,
            float.class,
            int.class,
            long.class,
            short.class
        };
        final Class[] objectClasses =
        {
            Boolean.class,
            Byte.class,
            Character.class,
            Double.class,
            Float.class,
            Integer.class,
            Long.class,
            Short.class
        };
        final ImmutableMap.Builder<String, String> builder =
            ImmutableMap.builder();
        final int numPrimitives = primitiveClasses.length;
        int index;
        String primitiveName;
        String objectName;

        for (index = 0; index < numPrimitives; ++index)
        {
            primitiveName = primitiveClasses[index].getName();
            objectName = objectClasses[index].getName();

            builder.put(primitiveName, objectName);
        }

        sPrimitiveMap = builder.build();
    } // end of class static initialization.

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Event layout this generator is for.
     */
    private final EfsEventLayout<E> mLayout;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Creates a new CQAttribute generator for the given event
     * layout.
     * @param layout generate attributes for this event layout.
     */
    private CQAttributeGenerator(final EfsEventLayout<E> layout)
    {
        mLayout = layout;
    } // end of CQAttributeGenerator(EfsEventLayout)

    //
    // end of Constructors.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Public Methods.
    //

    /**
     * Returns mapping from event getter field name to
     * {@code Attribute} instance based on event class layout.
     * @param layout generate attributes for this event layout.
     * @return map of field names to Attribute instances.
     * @throws NullPointerException
     * if {@code layout} is {@code null}.
     * @throws Exception
     * if attribute instantiation fails.
     */
    @SuppressWarnings ("unchecked")
    public static <E extends IEfsEvent> Map<String, Attribute<EfsRow<E>, ?>> createAttributeMap(final EfsEventLayout<E> layout)
        throws Exception
    {
        final Map<String, Attribute<EfsRow<E>, ?>> retval;

        Objects.requireNonNull(layout, NULL_LAYOUT);

        synchronized (sAttributes)
        {
            final String key = (layout.eventClass()).getName();

            // Have cqengine attributes previously been generated
            // for this class?
            if (sAttributes.containsKey(key))
            {
                // Yes. Return this attributes map.
                retval =
                    (Map<String, Attribute<EfsRow<E>, ?>>)
                        sAttributes.get(key);
            }
            // No. Generate attributes and store in map.
            else
            {
                final CQAttributeGenerator<E> generator =
                    new CQAttributeGenerator<>(layout);

                sLogger.debug(
                    "Generating attributes for efs event layout {}",
                    layout);


                retval = generator.createAttributeMap();
                sAttributes.put(key, retval);
            }
        }

        return (retval);
    } // end of createAttributeMap(EfsEventLayout)

    //
    // end of Public Methods.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Private Methods.
    //

    /**
     * Returns mapping from getter field name to
     * {@code Attribute} instances based on event class layout.
     * @return field name to attribute instance mapping.
     * @throws Exception
     * if bytecode generation fails.
     */
    private Map<String, Attribute<EfsRow<E>, ?>> createAttributeMap()
        throws Exception
    {
        final List<AttributeInfo> attributes =
            generateAttributes();
        final ImmutableMap.Builder<String, Attribute<EfsRow<E>, ?>> builder =
            ImmutableMap.builder();

        for (AttributeInfo attrInfo : attributes)
        {
            builder.put(attrInfo.attributeName(),
                        attrInfo.createAttributeInstance());
        }

        return (builder.build());
    } // end of createAttributeMap()

    /**
     * Returns a mapping from event getter method names to
     * field's generated {@code Attribute} class.
     * @return map of field names to generated Attribute classes.
     * @throws Exception
     * if bytecode generation fails.
     */
    @SuppressWarnings ("unchecked")
    private List<AttributeInfo> generateAttributes()
        throws Exception
    {
        final Map<String, GetterMethod> getters =
            mLayout.getters();
        final ImmutableList.Builder<AttributeInfo> builder =
            ImmutableList.builder();

        for (GetterMethod getter : getters.values())
        {
            builder.add(
                generateAttributeForGetter(getter));
        }

        return (builder.build());
    } // end of generateAttributes(EfsEventLayout)

    /**
     * Generates a single CQEngine attribute for the given getter
     * method and caches the result.n
     * @param getter getter method information.
     * @return attribute information based on getter method.
     * @throws Exception
     * if bytecode generation fails.
     */
    private AttributeInfo generateAttributeForGetter(final GetterMethod getter)
        throws Exception
    {
        final Class<?> thisClass = this.getClass();
        final String fieldName = getter.fieldName();
        final Class<E> eventClass = mLayout.eventClass();
        final CQAttributeType attrType =
            getter.annotation().attribute();
        final String superclassName =
            (attrType.attributeClass()).getName();
        final String generatedClassName =
            generateClassName(eventClass, fieldName);
        final CtClass generatedClass =
            generateClass(generatedClassName,
                          superclassName,
                          attrType,
                          getter,
                          eventClass);
        final Class<?> attributeClass =
            generatedClass.toClass(
                thisClass.getClassLoader(),
                thisClass.getProtectionDomain());
        final CQAttribute annotation = getter.annotation();

        return (
            new AttributeInfo(
                fieldName,
                attributeClass,
                getter.dataType(),
                annotation.nullValues(),
                annotation.index()));
    } // end of generateAttributeForGetter(...)

    /**
     * Generates a unique class name for the generated attribute.
     * Format: org.efs.io.generated.Attr_EventClassName_fieldName
     * @param eventClass event class being processed.
     * @param fieldName field name.
     * @return generated class name.
     */
    private String generateClassName(final Class<E> eventClass,
                                     final String fieldName)
    {
        final String eventClassName =
            eventClass.getSimpleName();

        return (
            String.format("%s.%s%s%s%s",
                          GENERATED_PACKAGE,
                          CLASS_NAME_PREFIX,
                          eventClassName,
                          CLASS_NAME_SEPARATOR,
                          fieldName));
    } // end of generateClassName(Class, String)

    /**
     * Generates the Javassist CtClass for a CQEngine attribute.
     * @param className generated class name.
     * @param superclassName CQEngine superclass name.
     * @param attributeType cqengine attribute type.
     * @param getter getter method information.
     * @param eventClass event class type.
     * @return generated CtClass.
     * @throws Exception
     * if bytecode generation fails.
     */
    private CtClass generateClass(final String className,
                                  final String superclassName,
                                  final CQAttributeType attributeType,
                                  final GetterMethod getter,
                                  final Class<E> eventClass)
        throws Exception
    {
        final CtClass superclass =
            CLASS_POOL.get(superclassName);
        final CtClass generatedClass =
            CLASS_POOL.makeClass(className, superclass);

        // Add constructor that calls super(fieldName)
        addConstructor(generatedClass, attributeType);

        // Add getValue method implementation
        addGetValueMethod(generatedClass,
                          attributeType,
                          getter,
                          eventClass);

        return (generatedClass);
    } // end of generateClass(...)

    /**
     * Adds a constructor to the generated class that accepts
     * the attribute name and calls the superclass constructor.
     * @param clazz generated class.
     * @throws Exception
     * if constructor creation fails.
     */
    private void addConstructor(final CtClass clazz,
                                final CQAttributeType attributeType)
        throws Exception
    {
        final CtClass classClass =
            CLASS_POOL.get(Class.class.getName());
        final CtClass stringClass =
            CLASS_POOL.get(String.class.getName());
        final CtConstructor constructor;

        // Is this a nullable multi-value attribute?
        if (attributeType == CQAttributeType.MULTIVALUE_NULLABLE)
        {
            // Yes. That takes a fourth boolean argument.
            final CtClass booleanClass =
                CLASS_POOL.get(boolean.class.getName());

            constructor =
                CtNewConstructor.make(
                    new CtClass[] // argTypes
                    {
                        classClass,   // objectType
                        classClass,   // attributeType
                        stringClass,  // attributeName
                        booleanClass, // null values flag
                    },
                    new CtClass[] {}, // exception types
                    PASS_PARAMS,
                    null,
                    null,
                    clazz);
        }
        // Otherwise has only three args.
        else
        {
            constructor =
                CtNewConstructor.make(
                    new CtClass[] // argTypes
                    {
                        classClass, // objectType
                        classClass, // attributeType
                        stringClass // attributeName
                    },
                    new CtClass[] {}, // exception types
                    PASS_PARAMS,
                    null,
                    null,
                    clazz);
        }

        clazz.addConstructor(constructor);
    } // end of addConstructor(CtClass, String)

    /**
     * Adds the getValue method implementation to the generated
     * class. The method extracts the event from the EfsRow,
     * casts it to the event type, and calls the getter method.
     * @param attrClass generated attribute class.
     * @param attributeType cqengine attribute type.
     * @param getter getter method information.
     * @param eventClass event class type.
     * @throws Exception
     * if method creation fails.
     */
    private void addGetValueMethod(final CtClass attrClass,
                                   final CQAttributeType attributeType,
                                   final GetterMethod getter,
                                   final Class<E> eventClass)
        throws Exception
    {
        final String methodName = getter.methodName();
        final Class<?> returnType = getter.dataType();

        attrClass.addMethod(buildGetterMethod(attrClass,
                                              attributeType,
                                              eventClass,
                                              methodName,
                                              returnType));

        // A bridge method is needed for the getter method to
        // account for type erasure
        // (see see https://docs.oracle.com/javase/tutorial/java/generics/bridgeMethods.html).
        attrClass.addMethod(buildBridgeMethod(attrClass,
                                              attributeType,
                                              returnType));
    } // end of addGetValueMethod(...)

    /**
     * Returns attribute getter method based on whether this is
     * a single-value or multi-value attribute type.
     * @param attributeClass method is applied to this class.
     * @param attributeType specifies if single- or multi-value
     * return type.
     * @param eventClass event type encapsulated in row.
     * @param methodName attribute getter method name.
     * @param returnType attribute return type.
     * @return attribute getter method.
     * @throws Exception
     * if method build fails.
     */
    private CtMethod buildGetterMethod(final CtClass attributeClass,
                                       final CQAttributeType attributeType,
                                       final Class<E> eventClass,
                                       final String methodName,
                                       final Class<?> returnType)
        throws Exception
    {
        final CtMethod retval;

        // Is this a multi-value attribute?
        if (attributeType.isMultiValue())
        {
            // Yes. Create the multi-value getter method.
            retval =
                buildMultiValueGetter(attributeClass,
                                      eventClass,
                                      methodName,
                                      returnType);
        }
        // Otherwise this is a single value getter method.
        else
        {
            retval = buildSingleValueGetter(attributeClass,
                                            eventClass,
                                            methodName,
                                            returnType);
        }

        return (retval);
    } // end of buildGetterMethod(...)

    /**
     * Returns single-value getter method for attribute.
     * @param attributeClass method is applied to this class.
     * @param eventClass event type encapsulated in row.
     * @param methodName attribute getter method name.
     * @param returnType attribute return type.
     * @return single-value attribute getter method.
     * @throws Exception
     * if method build fails.
     */
    private CtMethod buildSingleValueGetter(final CtClass attributeClass,
                                            final Class<E> eventClass,
                                            final String methodName,
                                            final Class<?> returnType)
        throws Exception
    {
        final String eventClassName = eventClass.getName();
        final CtMethod retval =
            CtNewMethod.make(
                "public " + returnType.getName() + " " + GET_VALUE_METHOD + "(" +
                EFS_ROW_CLASS_NAME + " row, " +
                QueryOptions.class.getName() + " queryOptions) {\n" +
                "    final " + eventClassName + " event = (" +
                eventClassName + ") row.getEvent();\n" +
                "    return (event." + methodName + "());\n}",
                attributeClass);

        return (retval);
    } // end of buildSingleValueGetter(CtClass,Class,String,Class)

    /**
     * Returns multi-value getter method for attribute.
     * @param attributeClass method is applied to this class.
     * @param eventClass event type encapsulated in row.
     * @param methodName attribute getter method name.
     * @param returnType attribute return type.
     * @return multi-value attribute getter method.
     * @throws Exception
     * if method build fails.
     */
    private CtMethod buildMultiValueGetter(final CtClass attributeClass,
                                           final Class<E> eventClass,
                                           final String methodName,
                                           final Class<?> returnType)
        throws Exception
    {
        final String eventClassName = eventClass.getName();
        final String iterableClassName = Iterable.class.getName();
        final CtMethod retval =
            CtNewMethod.make(
                "public " + iterableClassName + " " +
                GET_VALUES_METHOD + "(" +
                EFS_ROW_CLASS_NAME + " row, " +
                QueryOptions.class.getName() + " queryOptions) {\n" +
                "    final " + eventClassName + " event = (" +
                eventClassName + ") row.getEvent();\n" +
                "    return (event." + methodName + "());\n}",
                attributeClass);

        retval.setGenericSignature(
            new SignatureAttribute.MethodSignature(
                new SignatureAttribute.TypeParameter[0],
                new SignatureAttribute.Type[]
                {
                    new SignatureAttribute.ClassType(
                        returnType.getName())
                },
                new SignatureAttribute.ClassType(
                    iterableClassName,
                    new SignatureAttribute.TypeArgument[]
                    {
                        new SignatureAttribute.TypeArgument(
                            new SignatureAttribute.ClassType(
                                EFS_ROW_CLASS_NAME))
                    }),
                new SignatureAttribute.ObjectType[0]).encode());

        return (retval);
    } // end of buildMultiValueGetter(CtClass,Class,String,Class)

    /**
     * Returns attribute getter bridge method.
     * @param attributeClass bridge method is part of this
     * attribute class.
     * @param attributeType specifies whether this is a single-
     * or multi-value attribute.
     * @param returnType attribute getter return type.
     * @return attribute getter bridge.
     * @throws Exception
     * if method build fails.
     */
    private CtMethod buildBridgeMethod(final CtClass attributeClass,
                                       final CQAttributeType attributeType,
                                       final Class<?> returnType)
        throws Exception
    {
        final CtMethod retval;

        // Is this a multi-value attribute?
        if (attributeType.isMultiValue())
        {
            // Yes. Create bridge for multi-value getter.
            retval = buildMultiValueBridge(attributeClass);
        }
        // No, single-value.
        // Is this a primitive value?
        else if (returnType.isPrimitive())
        {
            // Yes. Create bridge for a primitive getter.
            retval =
                buildPrimitiveBridge(attributeClass, returnType);
        }
        // No, not primitive.
        else
        {
            // Create bridge for an object getter.
            retval = buildObjectBridge(attributeClass);
        }

        retval.setModifiers(
            retval.getModifiers() | AccessFlag.BRIDGE);

        return (retval);
    } // end of buildBridgeMethod(CtClass,CQAttributeType,Class)

    /**
     * Returns getter bridge method which translates a primitive
     * value into its object equivalent.
     * @param attributeClass method is for this attribute class.
     * @param returnType attribute return type.
     * @return attribute getter bridge method.
     * @throws Exception
     * if method build fails.
     */
    private CtMethod buildPrimitiveBridge(final CtClass attributeClass,
                                          final Class<?> returnType)
        throws Exception
    {
        final String objectName =
            sPrimitiveMap.get(returnType.getName());

        return (
            CtMethod.make(
                "public java.lang.Object getValue(java.lang.Object object, " +
                QueryOptions.class.getName() + " queryOptions) {\n" +
                "    return (" + objectName + ".valueOf(getValue((" +
                EFS_ROW_CLASS_NAME + ") object, queryOptions)));\n}",
                attributeClass));
    } // end of buildPrimitiveBridge(CtClass, Class)

    /**
     * Returns getter bridge method for an object return type.
     * @param attributeClass method is for this attribute class.
     * @return attribute getter bridge method.
     * @throws Exception
     * if method build fails.
     */
    private CtMethod buildObjectBridge(final CtClass attributeClass)
        throws Exception
    {
        return (
            CtMethod.make(
                "public java.lang.Object getValue(java.lang.Object object, " +
                QueryOptions.class.getName() + " queryOptions) {\n" +
                "    return (getValue((" + EFS_ROW_CLASS_NAME +
                ") object, queryOptions));\n}",
                attributeClass));
    } // end of buildObjectBridge(CtClass, Class)

    /**
     * Returns getter bridge method for an multi-value return
     * type.
     * @param attributeClass method is for this attribute class.
     * @return attribute getter bridge method.
     * @throws Exception
     * if method build fails.
     */
    private CtMethod buildMultiValueBridge(final CtClass attributeClass)
        throws Exception
    {
        return (
            CtMethod.make(
                "public " + Iterable.class.getName() + " " +
                GET_VALUES_METHOD + "(" +
                Object.class.getName() + " object, " +
                QueryOptions.class.getName() + " queryOptions) {\n" +
                "    return (getValues((" + EFS_ROW_CLASS_NAME +
                ") object, queryOptions));\n}",
                attributeClass));
    } // end of buildMultiValueBridge(CtClass)

    /**
     * Initializes the Javassist ClassPool with necessary
     * classpaths for CQEngine, EFS, and other required types.
     * @return initialized ClassPool.
     */
    private static ClassPool initializeClassPool()
    {
        final ClassPool retval = ClassPool.getDefault();

        // Add classpaths for required classes
        retval.insertClassPath(
            new ClassClassPath(Attribute.class));
        retval.insertClassPath(
            new ClassClassPath(EfsRow.class));
        retval.insertClassPath(
            new ClassClassPath(IEfsEvent.class));
        retval.insertClassPath(
            new ClassClassPath(QueryOptions.class));

        return (retval);
    } // end of initializeClassPool()

    //
    // end of Private Methods.
    //-----------------------------------------------------------

//---------------------------------------------------------------
// Inner classes.
//

    /**
     * Contains information required to generate an cqengine
     * attribute instance. This includes the attribute class and
     * attribute type.
     */
    private static final class AttributeInfo
    {
    //-----------------------------------------------------------
    // Member data.
    //

        //-------------------------------------------------------
        // Locals.
        //

        /**
         * Attribute field name.
         */
        private final String mAttributeName;

        /**
         * Attribute class.
         */
        private final Class<?> mAttributeClass;

        /**
         * Attribute field type.
         */
        private final Class<?> mAttributeType;

        /**
         * Specifies whether a
         * {@link CQAttributeType#MULTIVALUE_NULLABLE} contains
         * null values ({@code true}) or not ({@code false}).
         */
        private final boolean mNullValuesFlag;

        /**
         * CQEngine index associated with this attribute. Index
         * is created after creating an attribute instance.
         */
        private final CQIndexType mAttributeIndex;

    //-----------------------------------------------------------
    // Member methods.
    //

        //-------------------------------------------------------
        // Constructors.
        //

        /**
         * Creates attribute information instance for given
         * attribute name and name.
         * @param attributeName attribute field name.
         * @param attributeClass attribute class.
         * @param attributeType attribute field type.
         * @param nullValuesFlag {@code true} if a nullable
         * multi-value type contains {@code null} values.
         * @param attributeIndex attribute index.
         */
        private AttributeInfo(final String attributeName,
                              final Class<?> attributeClass,
                              final Class<?> attributeType,
                              final boolean nullValuesFlag,
                              final CQIndexType attributeIndex)
        {
            mAttributeName = attributeName;
            mAttributeClass = attributeClass;
            mAttributeType = attributeType;
            mNullValuesFlag = nullValuesFlag;
            mAttributeIndex = attributeIndex;
        } // end of AttributeInfo(...)

        //
        // end of Constructors.
        //-------------------------------------------------------

        //-------------------------------------------------------
        // Get Methods.
        //

        /**
         * Returns attribute name.
         * @return attribute name.
         */
        public String attributeName()
        {
            return (mAttributeName);
        } // end of attributeName()

        //
        // end of Get Methods.
        //-------------------------------------------------------

        /**
         * Creates an instance of the generated attribute class,
         * properly instantiating it with the object type,
         * attribute type, and attribute name.
         * @return new Attribute instance.
         * @throws Exception
         * if attribute instantiation fails.
         */
        @SuppressWarnings ("unchecked")
        public <E extends IEfsEvent> Attribute<EfsRow<E>, ?> createAttributeInstance()
            throws NoSuchMethodException,
                   InstantiationException,
                   IllegalAccessException,
                   InvocationTargetException
        {
            final Constructor<?> ctor;
            final Attribute<EfsRow<E>, ?> retval;

            // Is this a nullable multi-value type?
            if (MultiValueNullableAttribute.class.isAssignableFrom(
                    mAttributeClass))
            {
                // Yes.
                ctor =
                    mAttributeClass.getConstructor(
                        Class.class,
                        Class.class,
                        String.class,
                        boolean.class);

                retval =
                    (Attribute<EfsRow<E>, ?>)
                        ctor.newInstance(
                            EfsRow.class,
                            mAttributeType,
                            mAttributeName,
                            mNullValuesFlag);
            }
            // No, this not a nullable multi-value type.
            else
            {
                ctor =
                    mAttributeClass.getConstructor(
                        Class.class, Class.class, String.class);

                retval =
                    (Attribute<EfsRow<E>, ?>) ctor.newInstance(
                        EfsRow.class,
                        mAttributeType,
                        mAttributeName);
            }

            // Create cqengine index associated with this
            // attribute.
            createIndex(retval);

            return (retval);
        } // end of createAttributeInstance()

        /**
         * Creates a cqengine index for given attribute based on
         * {@link CQIndexType} associated with this attribute.
         * @param attribute generate index for this attribute.
         */
        @SuppressWarnings ("unchecked")
        private <E extends IEfsEvent> void createIndex(final Attribute<EfsRow<E>, ?> attribute)
        {
            switch (mAttributeIndex)
            {
                case HASH_INDEX -> HashIndex.onAttribute(attribute);

                case NAVIGABLE_INDEX -> {
                        final Attribute<EfsRow<E>, ? extends Comparable> navAttribute =
                            (Attribute<EfsRow<E>, ? extends Comparable>) attribute;

                        NavigableIndex.onAttribute(navAttribute);
                    }

                case RADIX_TREE_INDEX -> {
                        final Attribute<EfsRow<E>, ? extends CharSequence> seqAttribute =
                            (Attribute<EfsRow<E>, ? extends CharSequence>)
                                attribute;

                        RadixTreeIndex.onAttribute(seqAttribute);
                    }

                case REVERSED_RADIX_INDEX -> {
                        final Attribute<EfsRow<E>, ? extends CharSequence> seqAttribute =
                            (Attribute<EfsRow<E>, ? extends CharSequence>)
                                attribute;

                        ReversedRadixTreeIndex.onAttribute(
                            seqAttribute);
                    }

                case SUFFIX_RADIX_INDEX -> {
                        final Attribute<EfsRow<E>, ? extends CharSequence> seqAttribute =
                            (Attribute<EfsRow<E>, ? extends CharSequence>)
                                attribute;

                        SuffixTreeIndex.onAttribute(
                            seqAttribute);
                    }

                case UNIQUE_INDEX -> UniqueIndex.onAttribute(attribute);

                // This leaves index type NO_INDEX.
                default -> {}
            }
        } // end of createIndex(Attribute<>)
    } // end of class AttributeInfo
} // end of class CQAttributeGenerator
