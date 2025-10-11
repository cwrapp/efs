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
 * This package provides
 * <a href="https://github.com/lightbend/config" target="_blank">typesafe</a>
 * configuration bean classes which may be loaded from a file as
 * follows:
 * <pre><code>    final File configFile = new File("filename");
    final com.typesafe.config.Config configSource = com.typesafe.config.ConfigFactory.parseFile(configFile);
    final EfsActivatorConfig.create(configSource, EfsActivatorConfig.class);</code></pre>
 * <p>
 * The following is an example dispatcher configuration in
 * typesafe JSONf format:
 * </p>
 * <pre><code>dispatchers : [
  {
    dispatcherName = AlgoDispatcher
    threadType = SPINNING
    numThreads = 1
    priority = 10
    eventQueueCapacity = 128
    maxEvents = 128
    runQueueCapacity = 8
    affinity {       // optional, selector thread core affinity
        affinityType = CPU_ID // required, core selection type.
        cpuId = 7             // required for CPU_ID affinity type
        bindFlag = true       // optional, defaults to false
        wholeCoreFlag = true  // optional, defaults to false
    }
  },
  {
    dispatcherName = MonitorDispatcher
    threadType = SPINPARK
    numThreads = 1
    priority = 8
    spinLimit = 1000000
    parkTime = 500 nanos
    eventQueueCapacity = 256
    maxEvents = 64
    runQueueCapacity = 16
  },
  {
    dispatcherName = UtilityDispatcher
    threadType = BLOCKING
    numThreads = 4
    priority = 1
    eventQueueCapacity = 0 <em>// Unlimited event queue size.</em>
    maxEvents = 8
    runQueueCapacity = 64
  }
]</code></pre>
 * <p>
 * Please see the configuration class documentation for detailed
 * explanations on how to define typesafe configuration in a
 * file.
 * </p>
 */

package org.efs.dispatcher.config;
