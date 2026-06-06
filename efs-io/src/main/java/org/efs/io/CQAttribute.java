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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level, run-time annotation defining two attributes:
 * <ol>
 *   <li>
 *     {@code attribute}: defines CQEngine attribute type
 *     associated with get method. CQEngine uses this attribute
 *     to retrieve an event value.
 *   </li>
 *   <li>
 *     {@code index}: defines CQEngine index for attribute. If
 *     not defined, then attribute has no index.
 *   </li>
 * </ol>
 * <p>
 * If a {@code public returntype getXXX()} method is not
 * annotated with {@code @CQAttribute}, then no CQEngine
 * attribute is generated for the method.
 * </p>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CQAttribute
{
    /**
     * Returns
     * {@link com.googlecode.cqengine.attribute.Attribute CQEngine attribute}
     * type used for a {@code public getXXX()} method. There is
     * no default value. If this annotation is not provided for a
     * get method, then no CQEngine attribute is created for that
     * field.
     * @return CQEngine attribute type.
     *
     * @see CQAttributeType
     */
    CQAttributeType attribute();

    /**
     * Returns
     * {@link com.googlecode.cqengine.index.AttributeIndex CQEngine attribute index}
     * type associated with the get method attribute. If no
     * specified, then defaults to
     * {@link CQIndexType#NO_INDEX NO_INDEX}.
     * @return CQEngine index type.
     *
     * @see CQIndexType
     */
    CQIndexType index() default CQIndexType.NO_INDEX;
} // end of annotation CQAttribute
