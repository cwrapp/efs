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
 * Contains classes used to define
 * {@link org.efs.timer.EfsScheduledExecutor EfsScheduledExecutor}s. See
 * {@link org.efs.timer.config.EfsScheduledExecutorConfig EfsScheduledExecutorConfig}
 * and
 * {@link org.efs.timer.config.EfsScheduledExecutorsConfig EfsScheduledExecutorsConfig}
 * for a detailed explanation on how to use these classes to
 * create an {@code EfsScheduledExecutor}.
 * <p>
 * An example efs scheduled executor configuration follows:
 * </p>
 * <pre><code>executors : [
  {
    executorName = LowLatencyTimer
    threadType = SPINNING
    priority = 10
  },
  {
    executorName = AlgoTimer
    threadType = SPINPARK
    priority = 9
    spinLimit = 1000000
    parkTime = 500 nanos
  },
  {
    executorName = UtilityTimer
    threadType = BLOCKING
    priority = 2
  }
]</code></pre>
 */

package org.efs.timer.config;
