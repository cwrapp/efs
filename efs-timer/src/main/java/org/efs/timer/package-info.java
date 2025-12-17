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
 * efs provides a high precision timer via
 * {@link org.efs.timer.EfsScheduledExecutor} which provides a
 * similar functionality as
 * {@link java.util.concurrent.ScheduledExecutorService} with the
 * difference that expired timer tasks are dispatched to an efs
 * agent's event queue. This means timer events are processed
 * on a efs dispatcher thread which means the efs agent remains
 * effectively single threaded.
 * <h2>Scheduling Timers</h2>
 * <p>
 * {@code EfsScheduledExecutor} provides timer scheduling methods
 * similar to {@code ScheduledExecutorService}:
 * </p>
 *  <ul>
 *    <li>
 *      {@code EfScheduledExecutor.schedule(String timerName, Consumer<EfsTimerEvent> callback, IEfsAgent agent, Duration delay)}
 *    </li>
 *    <li>
 *      {@code EfsScheduledExecutor.scheduleAtFixedRate(String timerName, Consumer<EfsTimerEvent> callback, IEfsAgent agent, Duration initialDelay, Duration period)}
 *    </li>
 *    <li>
 *      {@code EfsScheduledExecutor.scheduleWithFixedDelay(String timerName, Consumer<EfsTimerEvent> callback, IEfsAgent agent, Duration initialDelay, Duration delay)}
 *    </li>
 *  </ul>
 * <p>
 * The main difference between Java and efs schedulers is that
 * efs requires an {@code IEfsAgent } argument and does not
 * support scheduling a {@link java.util.concurrent.Callable}.
 * </p>
 * <h2>Creating efs Scheduled Executors</h2>
 * <p>
 * {@link org.efs.timer.EfsScheduledExecutor} class
 * documentation provides detailed description on how to create a
 * new efs scheduled executor. Like efs dispatcher threads,
 * efs schedulers come in four flavor: blocking, spinning,
 * spin+park, and spin+yield.
 * </p>
 * <p>
 * <strong>Note:</strong> a default efs scheduled executor is
 * <em>not</em> provided.
 * </p>
 */

package org.efs.timer;
