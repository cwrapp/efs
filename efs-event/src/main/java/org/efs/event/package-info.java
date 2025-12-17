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
 * Contains classes which define efs events. This includes:
 * <ul>
 *   <li>
 *     {@link IEfsEvent}: marker interface which all efs event
 *     classes are required to implement.
 *   </li>
 *   <li>
 *     {@link ConflationEvent}: used to dispatch only latest
 *     event to an agent. The idea is that if an event for a
 *     given subject is dispatched to an agent and another event
 *     arrives before the agent receives that first event, then
 *     the first event is dropped and replaced with the second
 *     event. This means that the agent never sees the first
 *     event but only the second event.
 *   </li>
 *   <li>
 *     {@link EfsEventWrapper}: Enriches an encapsulated event
 *     with: publish timestamp, event identifier, publisher
 *     identifier, and the number of subscribers receiving the
 *     event <em>when event was published.</em> The idea is that
 *     publish timestamp and event identifier together uniquely
 *     identify an event.
 *   </li>
 * </ul>
 */

package org.efs.event;
