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

/**
 * Provides core event framework infrastructure for the efs
 * system.
 * <p>
 * This package defines the contract and mechanisms for efs
 * events, including event definition, building, and
 * de-duplication strategies.
 * </p>
 *
 * <h2>Core Components</h2>
 *
 * <h3>Event Contract</h3>
 * <ul>
 *   <li>
 *     {@link IEfsEvent}: Marker interface that all efs events
 *     must implement. Provides the foundational contract for
 *     polymorphic event handling.
 *   </li>
 *   <li>
 *     {@link IEfsEventBuilder}: Generic builder interface for
 *     constructing {@link IEfsEvent} instances using the fluent
 *     builder pattern.
 *   </li>
 * </ul>
 *
 * <h3>Event De-duplication</h3>
 * <ul>
 *   <li>
 *     {@link ConflationEvent}: Mutable event wrapper
 *     implementing event conflation (de-duplication) for
 *     high-frequency event scenarios. When multiple events
 *     arrive for the same topic before processing, only the
 *     latest event is retained and forwarded to subscribers.
 *     Tracks the count of missed events between polling
 *     intervals.
 *   </li>
 * </ul>
 *
 * <h3>Feed Management</h3>
 * <ul>
 *   <li>
 *     {@link EfsTopicKey}: Immutable key that uniquely
 *     identifies an efs feed by combining event class and topic.
 *     Uses instance caching to ensure singleton behavior for
 *     identical combinations, enabling efficient feed lookup and
 *     management.
 *   </li>
 * </ul>
 * <h3>Metadata Annotations</h3>
 * <ul>
 *   <li>
 *     {@link EfsCollectionInfo}: Method-level annotation
 *     specifying maximum allowed size for collection or array
 *     return types.
 *   </li>
 *   <li>
 *     {@link EfsStringInfo}: Method-level annotation specifying
 *     the character encoding and maximum byte size for string
 *     return types.
 *   </li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 * <p>
 * Typical usage involves:
 * </p>
 * <ol>
 *   <li>
 *     Implement {@link IEfsEvent} to define domain-specific
 *     event types.
 *   </li>
 *   <li>
 *     Create a corresponding {@link IEfsEventBuilder}
 *     implementation to construct event instances.
 *   </li>
 *   <li>
 *     For high-frequency updates, use {@link ConflationEvent} to
 *     de-duplicate events by topic, ensuring only the latest
 *     event is processed.
 *   </li>
 *   <li>
 *     Use {@link EfsTopicKey#getKey(Class, String)} to obtain
 *     cached feed keys for event routing and subscription
 *     management.
 *   </li>
 * </ol>
 */

package org.efs.event;
