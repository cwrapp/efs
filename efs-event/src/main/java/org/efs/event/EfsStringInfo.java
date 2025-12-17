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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level, run-time annotation defining two attributes:
 * <ol>
 *   <li>
 *     {@code charset}: defines {@link java.nio.charset.Charset}
 *     used to encode/decode associated {@code String} type. If
 *     not defined, then defaults to
 *     {@link java.nio.charset.Charset#defaultCharset}.
 *   </li>
 *   <li>
 *     {@code maximumSize}: defines string field's maximum
 *     character count allowed.
 *   </li>
 * </ol>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface EfsStringInfo
{
    /**
     * Returns character set name used to encode/decode
     * {@code String} field. This name must satisfy
     * {@link java.nio.charset.Charset#forName(String)}. Failing
     * that then {@link java.nio.charset.Charset#defaultCharset}
     * is used.
     * @return character set name.
     */
    String charset() default "(not set)";

    /**
     * Returns {@code String} maximum size in bytes. If string
     * encoding exceeds this size, then encoding results in a
     * {@code BufferOverflowException}.
     * @return maximum string size in bytes.
     */
    int maximumSize();
} // end of annotation EfsStringInfo
