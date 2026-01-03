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
 * efs timer package provides
 * {@link org.efs.timer.EfsScheduledExecutor EfsScheduledExecutor}
 * which encapsulates a
 * {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService}.
 * The idea is that {@code EfsScheduledExecutor} translates timer
 * expirations into
 * {@link org.efs.timer.EfsTimerEvent timer events} dispatched to
 * an {@link org.efs.dispatcher.IEfsAgent efs agent}.
 * <p>
 * The reason for this efs scheduled executor is to make it
 * possible for efs agents to process timer events solely within
 * the agent's dispatcher. This means agents remain effectively
 * single threaded. If {@code ScheduledExecutorService} were used
 * to directly access an efs agent, then the efs agent is no
 * longer singled threaded and thread safety measures are needed.
 * </p>
 * <p>
 * Note that the user-provided {@code ScheduledExecutorService}
 * may be used independently of the {@code EfsScheduledExecutor}
 * to execute other tasks and even be shut down outside of the
 * efs scheduled executor.
 * </p>
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
 * The main differences between Java and efs schedulers are:
 * <ul>
 *   <li>
 *     efs requires {@code IEfsAgent} and
 *     {@code Consumer<EfsTimerEvent>} arguments and does not
 *     support scheduling
 *     {@link java.util.concurrent.Callable Callable} or
 *     {@link java.lang.Runnable Runnable} commands.
 *     <strong>Note: </strong> the provided efs agent must be
 *     registered with an efs dispatcher prior to using that
 *     agent to schedule a timer.
 *   </li>
 *   <li>
 *     {@code EfsScheduledExecutor} uses
 *     {@link java.time.Duration Duration} to specify
 *     delay/period values rather than a {@code long} and
 *     {@link java.util.concurrent.TimeUnit TimeUnit}.
 *   </li>
 * </ul>
 * <h2>Creating efs Scheduled Executors</h2>
 * <p>
 * Creating an efs scheduled executor is as simple as:
 * </p>
 * <ol>
 *   <li>
 *     Creating a {@code ScheduledExecutorService} instance and
 *   </li>
 *   <li>
 *     passing that {@code ScheduledExecutorService} instance to
 *     the efs scheduled executor constructor
 *     {@link org.efs.timer.EfsScheduledExecutor#EfsScheduledExecutor(java.util.concurrent.ScheduledExecutorService) EfsScheduleExecutor(ScheduledExecutorService)}.
 *   </li>
 * </ol>
 */

package org.efs.timer;
