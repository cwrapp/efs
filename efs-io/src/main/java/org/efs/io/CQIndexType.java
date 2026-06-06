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
 * {@link com.googlecode.cqengine.index.AttributeIndex attribute index}
 * types supported by efs which are:
 * <ul>
 *   <li>
 *     {@link com.googlecode.cqengine.index.hash.HashIndex HashIndex}
 *   </li>
 *   <li>
 *     {@link com.googlecode.cqengine.index.navigable.NavigableIndex NavigableIndex}
 *   </li>
 *   <li>
 *     {@link com.googlecode.cqengine.index.radix.RadixTreeIndex RadixTreeIndex}
 *   </li>
 *   <li>
 *     {@link com.googlecode.cqengine.index.radixreversed.ReversedRadixTreeIndex ReversedRadixTreeIndex}
 *   </li>
 *   <li>
 *     {@link com.googlecode.cqengine.index.suffix.SuffixTreeIndex SuffixTreeIndex}
 *   </li>
 *   <li>
 *     {@link com.googlecode.cqengine.index.suffix.SuffixTreeIndex SuffixTreeIndex}
 *   </li>
 * </ul>
 * <p>
 * If no index is applied to an attribute, then {@code NO_INDEX}
 * is used.
 * </p>
 *
 * @author <a href="mailto:rapp@acm.org">Charles W. Rapp</a>
 */

public enum CQIndexType
{
    /**
     * Do not create an index for attribute.
     */
    NO_INDEX,

    /**
     * Create a
     * {@link com.googlecode.cqengine.index.hash.HashIndex HashIndex}
     * for attribute.
     */
    HASH_INDEX,

    /**
     * Create a
     * {@link com.googlecode.cqengine.index.navigable.NavigableIndex NavigableIndex}
     * for attribute.
     */
    NAVIGABLE_INDEX,

    /**
     * Create a
     * {@link com.googlecode.cqengine.index.radix.RadixTreeIndex RadixTreeIndex}
     * for attribute.
     */
    RADIX_TREE_INDEX,

    /**
     * Create a
     * {@link com.googlecode.cqengine.index.radixreversed.ReversedRadixTreeIndex ReversedRadixTreeIndex}
     * for attribute.
     */
    REVERSED_RADIX_INDEX,

    /**
     * Create a
     * {@link com.googlecode.cqengine.index.suffix.SuffixTreeIndex SuffixTreeIndex}
     * for attribute.
     */
    SUFFIX_RADIX_INDEX,

    /**
     * Create a
     * {@link com.googlecode.cqengine.index.suffix.SuffixTreeIndex SuffixTreeIndex}
     * for attribute.
     */
    UNIQUE_INDEX
} // end of enum CQIndexType
