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
     * for event field.
     */
    SIMPLE,

    /**
     * Create a
     * {@link com.googlecode.cqengine.attribute.SimpleNullableAttribute SimpleNullableAttribute}
     * for event field.
     */
    SIMPLE_NULLABLE,

    /**
     * Create a
     * {@link com.googlecode.cqengine.attribute.MultiValueAttribute MultiValueAttribute}
     * for event field.
     */
    MULTIVALUE,

    /**
     * Create a
     * {@link com.googlecode.cqengine.attribute.MultiValueNullableAttribute MultiValueNullableAttribute}
     * for event field.
     */
    MULTIVALUE_NULLABLE
} // end of enum CQAttributeType
