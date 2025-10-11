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
 * Provides ability to transition efs agents between
 * stopped, stand by, and active states according to user-defined
 * workflow.
 * An {@link org.efs.dispatcher.IEfsAgent agent} is an object
 * which reacts to asynchronous
 * {@link org.efs.event.IEfsEvent event}s. An agent's
 * initialization consists of data member initialization and
 * plugging into asynchronous event feeds. Object-oriented
 * languages provide an class constructor for data member
 * initialization. But this constructor should not be used for
 * attaching to event feeds because it could lead to the agent
 * being called back when it is only partially constructed.
 * <p>
 * A simple solution to this is to use the Factory pattern. The
 * factory creates a new agent instance and then has that agent
 * attach to event feeds. But this leads to problem with
 * {@link org.efs.dispatcher.EfsDispatcher efs dispatcher}:
 * agent event feed initialization is occurring on the factory
 * thread while events arrive on the a dispatcher thread. As
 * pointed out in {@code efs-dispatcher}, dispatcher guarantees
 * an agent is accessed in a virtual single-threaded manner. This
 * guarantee may only be kept if agent event feed attachment is
 * also done via the dispatcher.
 * </p>
 * <p>
 * This is where
 * {@link org.efs.activator.EfsActivator EfsActivator} comes in.
 * It is used to trigger agent event initialization using the
 * agent's dispatcher. An agent must implement
 * {@link org.efs.activator.IEfsActivateAgent IEfsActivatorAgent}
 * (which extends {@code IEfsAgent}) to make itself accessible to
 * the activator. {@code EfsActivator} steps an activate agent
 * through three
 * {@link org.efs.activator.EfsAgentState agent states}:
 * </p>
 * <ol>
 *   <li>
 *     {@code STOPPED}: agent is not attached to any event feeds
 *     and should not expect to receive any events (but may
 *     receive previously posted events).
 *   </li>
 *   <li>
 *     {@code STAND_BY}: agent <em>may</em> be attached to event
 *     feeds (at its discretion) and may receive events. The idea
 *     here is not fully initialized but may need to process
 *     certain events in anticipation of full activation.
 *   </li>
 *   <li>
 *     {@code ACTIVE}: agent is fully activated and is
 *     processing all necessary events.
 *   </li>
 * </ol>
 * <p>
 * Activator steps an agent through these states by calling
 * the following {@code IEfsActiveAgent} methods from the agent's
 * dispatcher:
 * </p>
 * <ul>
 *   <li>
 *     {@link org.efs.activator.IEfsActivateAgent#startup startup}:
 *     takes agent from {@code STOPPED} to {@code STAND_BY} state
 *     - if successfully completed. If an exception is thrown,
 *     then agent remains in initial state. This applies for all
 *     following method calls.
 *   </li>
 *   <li>
 *     {@link org.efs.activator.IEfsActivateAgent#activate activate}:
 *     takes agent from {@code STAND_BY} to {@code ACTIVE}
 *     state.
 *   </li>
 *   <li>
 *     {@link org.efs.activator.IEfsActivateAgent#deactivate deactivate}:
 *     takes agent from {@code ACITVE} to {@code STAND_BY}
 *     state.
 *   </li>
 *   <li>
 *     {@link org.efs.activator.IEfsActivateAgent#stop stop}:
 *     takes agent from {@code STAND_BY} to {@code STOPPED}
 *     state.
 *   </li>
 * </ul>
 * <p>
 * {@code EfsActivator} is designed to transition multiple agents
 * in an application from start-up to fully running and back to
 * shutdown. Activator does this using one or more
 * {@link org.efs.activator.Workflow workflow}s. Each workflow
 * consists of one or more
 * {@link org.efs.activator.WorkflowStage stage}s, and each stage
 * consists of one or more
 * {@link org.efs.activator.WorkflowStep step}s. A workflow step
 * takes a specific agent from its current state to an adjacent
 * state but gives that agent only such much time to complete the
 * transition. If the agent exceeds this time limit, the
 * transition is considered failed and the agent remains in the
 * initial state.
 * </p>
 * <p>
 * Note: when activator is initialized, all agents named in the
 * workflows are considered to be in the {@code STOPPED} state.
 * </p>
 * <p>
 * <strong>NOTE</strong>: activate agents must be registered with
 * their respective dispatchers <em>before</em> any
 * {@code EfsActivate} execution methods are called.
 * </p>
 * <h2>Example Workflows</h2>
 * <p>
 * The following {@code typesafe} configuration file defining
 * an activator's workflows used for a trading application:
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
  },
  {
    name = "warm-start"
    stages = [
      {
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
      }
    ]
  },
  // Note: shutdown stages should mirror start up.
  {
    name = "hot-shutdown"
    stages = [
      {
        steps = [
          {
            agent = "make-money-algo-agent"
            beginState = ACTIVE
            endState = STAND_BY
            allowedTransitionTime = 100 millis
          }
        ]
      },
      {
        steps = [
          {
            agent = "make-money-algo-agent"
            beginState = STAND_BY
            endState = STOPPED
            allowedTransitionTime = 100 millis
          },
          {
            agent = "market-data-agent"
            beginState = ACTIVE
            endState = STAND_BY
            allowedTransitionTime = 100 millis
          }
        ]
      },
      {
        steps = [
          {
            agent = "market-data-agent"
            beginState = STAND_BY
            endState = STOPPED
            allowedTransitionTime = 100 millis
          }
        ]
      }
    ]
  },
  {
    name = "warm-shutdown"
    stages = [
      {
        steps = [
          {
            agent = "make-money-algo-agent"
            beginState = STAND_BY
            endState = STOPPED
            allowedTransitionTime = 100 millis
          },
          {
            agent = "market-data-agent"
            beginState = ACTIVE
            endState = STAND_BY
            allowedTransitionTime = 100 millis
          }
        ]
      },
      {
        steps = [
          {
            agent = "market-data-agent"
            beginState = STAND_BY
            endState = STOPPED
            allowedTransitionTime = 100 millis
          }
        ]
      }
    ]
  },
  {
    name = "warm-to-hot"
    stages = [
      {
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
  },
  {
    name = "hot-to-warm"
    stages = [
      {
        steps = [
          {
            agent = "make-money-algo-agent"
            beginState = ACTIVE
            endState = STAND_BY
            allowedTransitionTime = 100 millis
          }
        ]
      }
    ]
  }
]</code></pre>
 * <p>
 * Workflow "hot-start" brings the application to a fully
 * active state where it is processing market data and the algo
 * is making money based on that market data. Workflow
 * "warm-start" brings market data agent up fully but the algo
 * is left in stand-by. These two workflows would be used to
 * initialize two separate instances of the application: one in
 * a "hot" active state and the other in a "warm" stand-by state.
 * </p>
 * <p>
 * The remaining workflows are used to bring the application down
 * (all agents back to a stopped state) or to transition between
 * hot and warm.
 * </p>
 * <h2>Activator Execution</h2>
 * <p>
 * Activator provides different execution granularity levels. In
 * all cases the first step is to define the executed workflow
 * via
 * {@link org.efs.activator.EfsActivator#initializeWorkflow(java.lang.String) EfsActivator.initializeWorkflow}.
 * This step must be performed prior to the following workflow
 * execution types.
 * </p>
 * <ul>
 *   <li>
 *     {@link org.efs.activator.EfsActivator#executeWorkflow() EfsActivator.executeWorkflow()}:
 *     activator executes all remaining workflow steps until
 *     completion or a agent transition failure is detected.
 *     If this method is called immediately after setting the
 *     workflow, then the entire workflow is executed. If
 *     workflow was partially executed, then executes the
 *     remainder.
 *   </li>
 *   <li>
 *     {@link org.efs.activator.EfsActivator#executeNextStage() EfsActivator.executeNextStage()}:
 *     activates all remaining workflow steps in current stage
 *     until stage's completion. Again, if workflow is at the
 *     stage's first step, then entire stage is executed. If
 *     stage is partially completed, then executes remainder of
 *     stage steps.
 *   </li>
 *   <li>
 *     {@link org.efs.activator.EfsActivator#executeNextStep() EfsActivator.executeNextStep()}:
 *     activates next step and stops.
 *   </li>
 * </ul>
 * <p>
 * <strong>NOTE:</strong> {@code EfsActivator} keeps track of
 * each {@code IEfsActivateAgent}'s current state according to
 * the above execution methods. If all goes well, then workflows
 * move agents to the desired state. But in the wild agents don't
 * behave as expected. The {@code IEfsActiveAgent} method may
 * throw a recoverable exception or take unexpectedly long to
 * complete. {@code EfsActivator} provides two methods to
 * get an agent back to its expected workflow state:
 * </p>
 * <ol>
 *   <li>
 *     {@link org.efs.activator.EfsActivator#execute(java.lang.String, org.efs.activator.EfsAgentState, org.efs.activator.EfsAgentState, java.time.Duration) EfsActivator.execute(String, EfsAgentState, EfsAgentState, Duration)}:
 *     transitions named agent from a begin state to an adjacent
 *     end state with a given allowed transition time. It is
 *     expected that this begin state matches what activator
 *     thinks is the agent state. So if an agent fails a
 *     transition due to exceeding allowed transition time, then
 *     that transition may be attempted again with an increased
 *     transition time.
 *   </li>
 *   <li>
 *     {@link org.efs.activator.EfsActivator#agentState(java.lang.String, org.efs.activator.EfsAgentState) EfsActivator.agentState(String, EfsAgentState}:
 *     explicitly sets current agent state in activator. If it is
 *     determined that a failed transition actually succeeded,
 *     then allows agent state to be correctly set.
 *   </li>
 * </ol>
 * <p>
 * These methods should be used with care and only after
 * determining that external workflow intervention is required.
 * As stated above, activator executions terminate immediately
 * upon the first failed transition. The best response to this
 * failure would be to call
 * {@code EfsActivator.executeNextStep()} and retry that failed
 * step. If that is successful, then continue with executing
 * the remainder of the stage or entire workflow.
 * </p>
 * <h2>Monitor EfsActivator</h2>
 * <p>
 * As {@code EfsActivator} steps through a workflow, it informs
 * registered listeners of an agent's transition to a new state.
 * An activator listener is registered via
 * {@link org.efs.activator.EfsActivator#registerListener(java.util.function.Consumer, org.efs.dispatcher.IEfsAgent) EfsActivator.registerListener(Consumer&lt;ActivatorEvent&gt;, IEfsAgent)}.
 * The {@code ActivatorEvent} is delivered to listener agent via
 * the {@code Consumer<ActivatorEvent>} callback. The activator
 * listener is de-registered via
 * {@link org.efs.activator.EfsActivator#deregisterListener(java.util.function.Consumer, org.efs.dispatcher.IEfsAgent) EfsActivator.deregisterListener(Consumer&lt;ActivatorEvent&gt;, IEfsAgent)}.
 * Note: an agent may not register with a different callback
 * if already currently registered.
 * </p>
 */

package org.efs.activator;
