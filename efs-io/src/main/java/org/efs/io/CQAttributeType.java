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

import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.MultiValueAttribute;
import com.googlecode.cqengine.attribute.MultiValueNullableAttribute;
import com.googlecode.cqengine.attribute.SimpleAttribute;
import com.googlecode.cqengine.attribute.SimpleNullableAttribute;

/**
 * Defines CQEngine
 * {@link com.googlecode.cqengine.attribute.Attribute attributes}
 * supported by efs which are:
 * <ul>
 *   <li>
 *     {@link com.googlecode.cqengine.attribute.SimpleAttribute SimpleAttribute}
 *   </li>
 *   <li>
 *     {@link com.googlecode.cqengine.attribute.SimpleNullableAttribute SimpleNullableAttribute}
 *   </li>
 *   <li>
 *     {@link com.googlecode.cqengine.attribute.MultiValueAttribute MultiValueAttribute}
 *   </li>
 *   <li>
 *     {@link com.googlecode.cqengine.attribute.MultiValueNullableAttribute MultiValueNullableAttribute}
 *   </li>
 * </ul>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public enum CQAttributeType
{
    /**
     * Create a
     * {@link com.googlecode.cqengine.attribute.SimpleAttribute SimpleAttribute}
     * for event field. This attribute type means that event
     * field is <em>not</em> {@code null}. If the field may be
     * {@code null}, then use {@link #SIMPLE_NULLABLE} attribute
     * type.
     *
     * @see #SIMPLE_NULLABLE
     */
    SIMPLE (false, SimpleAttribute.class),

    /**
     * Create a
     * {@link com.googlecode.cqengine.attribute.SimpleNullableAttribute SimpleNullableAttribute}
     * for event field. This attribute type means that event
     * field may be set to {@code null}.
     *
     * @see #SIMPLE
     */
    SIMPLE_NULLABLE (false, SimpleNullableAttribute.class),

    /**
     * Create a
     * {@link com.googlecode.cqengine.attribute.MultiValueAttribute MultiValueAttribute}
     * for event field. This attribute type means that event
     * field returns a non-{@code null Iterable} value. If field
     * may be {@code null}, then use {@link #MULTIVALUE_NULLABLE}
     * attribute type.
     *
     * @see #MULTIVALUE_NULLABLE
     */
    MULTIVALUE (true, MultiValueAttribute.class),

    /**
     * Create a
     * {@link com.googlecode.cqengine.attribute.MultiValueNullableAttribute MultiValueNullableAttribute}
     * for event field. This attribute type means that event
     * field returns a possibly {@code null Iterable} value. If
     * that iterable collection may contain {@code null} values,
     * then set {@link CQAttribute#nullValues()} to {@code true}.
     *
     * @see #MULTIVALUE
     */
    MULTIVALUE_NULLABLE (true, MultiValueNullableAttribute.class);

//---------------------------------------------------------------
// Member data.
//

    //-----------------------------------------------------------
    // Locals.
    //

    /**
     * Set to {@code true} if this is a multi-value attribute.
     */
    private final boolean mMultivalueFlag;

    /**
     * Associated cqengine attribute class.
     */
    private final Class<? extends Attribute> mAttributeClass;

//---------------------------------------------------------------
// Member methods.
//

    //-----------------------------------------------------------
    // Constructors.
    //

    /**
     * Sets attribute type values.
     * @param multivalueFlag {@code true} if this is a
     * multi-value attribute type.
     * @param attributeClass associated cqengine type.
     */
    private CQAttributeType(final boolean multivalueFlag,
                            final Class<? extends Attribute> attributeClass)
    {
        mMultivalueFlag = multivalueFlag;
        mAttributeClass = attributeClass;
    } // end of CQAttributeType(boolean, Class)

    //
    // end of Object Method Overrides.
    //-----------------------------------------------------------

    //-----------------------------------------------------------
    // Get Methods.
    //

    /**
     * Returns {@code true} if this is a multi-value attribute.
     * @return {@code true} if this is a multi-value attribute.
     */
    public boolean isMultiValue()
    {
        return (mMultivalueFlag);
    } // end of isMultiValue()

    /**
     * Returns cqengine attribute class.
     * @return cqengine attribute class.
     */
    public Class<? extends Attribute> attributeClass()
    {
        return (mAttributeClass);
    } // end of attributeClass()

    //
    // end of Get Methods.
    //-----------------------------------------------------------
} // end of enum CQAttributeType
