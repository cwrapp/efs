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
 * {@link org.efs.activator.EfsActivator#loadActivator(java.lang.String) EfsActivator.loadActivator(String)}
 * loads an activator typesafe configuration from the given file
 * name and returns a new {@code EfsActivator} instance based on
 * that configuration.
 * </p>
 * <p>
 * An example workflow configuration using typesafe follows:
 * </p>
 * <pre><code>workflows = [
  {
    name = "hot-start"
    stages = [
      {
        // Stage one: move market data agent to stand-by.
        steps = [
          {
            agent = "market-data-agent"
            beginState = STOPPED
            endState = STAND_BY
            allowedTransitionTime = 100 millis
          }
        ]
      },
      {
        // Stage two: activate market data agent and put algo
        // agent into stand-by.
        steps = [
          {
            agent = "market-data-agent"
            beginState = STAND_BY
            endState = ACTIVE
            allowedTransitionTime = 100 millis
          },
          {
            agent = "make-money-algo-agent"
            beginState = STOPPED
            endState = STAND_BY
            allowedTransitionTime = 100 millis
          }
        ]
      },
      {
        // Stage three: activate money making algo.
        steps = [
          {
            agent = "make-money-algo-agent"
            beginState = STAND_BY
            endState = ACTIVE
            allowedTransitionTime = 100 millis
          }
        ]
      }
    ]
  }
 ]</code></pre>
 * <p>
 * Please see {@link org.efs.activator} for a detailed
 * description on how to define a typesafe configuration in a
 * file.
 * </p>
 */

package org.efs.activator.config;
