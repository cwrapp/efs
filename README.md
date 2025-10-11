# efs - Event File System

## Overview

efs - Event File System is _envisioned_ as a reliable, robust,
and low-latency event persistence and delivery API from
publishing agent to subscribing agent.

"Envision" means that efs is not yet implemented to this point -
but enough is implemented to warrant an initial release. This
first release is the dispatcher framework used to delivery events
to agents in a virtual single-threaded manner. Virtual
single-threading means that while an agent may run on different
threads over its lifespan it is guaranteed to be accessed by only
one thread at any given moment. This means there is no need for
synchronization/locks which can lead to a thread losing core
while waiting to acquire a lock.

The second component is the activator which uses dispatcher to
start and activate agents (`IEfsActivateAgent` is an interface
extension to `IEfsAgent`);
Activator solves the problem where an agent is activated on one
thread where it connects into a reactive framework but receives
events on dispatcher threads
_while still activating on the original thread_.
The virtual single-threading guarantee provided by dispatcher is
lost in this scenario. Activator has agent activation performed
by the agent's dispatcher. Any events posted to the agent while
activating are delivered _after_ activation is completed.

## Essential Information

### Binary Releases

esf is built for Java 21. You can find published releases are
available from GitHub Package Registry

    <dependency>
        <groupId>org.efs</groupId>
        <artifactId>efs-bom</artifactId>
        <version>0.1.0</version>
    </dependency>

### Release Notes

Please set NEWS.md in this directory
https://github.com/cwrapp/efs

### API docs

https://efs.github.io

### Learning efs

efs package javadocs explain how to use the package together with
example code. You are encourged to explore the javadoc packages
in the following order:

  - `org.efs.dispatcher`: explains explains dispatcher
    architecture and how to use. Compares efs dispatcher with
    LMAX Disruptor and SEDA (staged event driven architecture).
  - `org.efs.dispatcher.config`: explains how to configure
    dispatchers using https://github.com/lightbend/config/
    configuration bean classes storeed in a JSON file format.

    Please note that efs uses typesafe for all its configuration.
  - `org.efs.timer`: contains the `EfsScheduledExecutor` which
    _somewhat_ follows the
    `java.util.concurrent.ScheduledExecutorService` interface
    but does not implement that interface because it does not
    deliver expired timers using a `ScheduledFuture` but instead
    uses `EfsDispatcher` for delivery.

    An efs scheduled executor may be created either
    programatically or by typesafe configuration file.
  - `org.efs.activator`: this service steps an efs agent through
    stopped, stand by, and active states in a thread-safe manner
    using efs dispatcher's virtual single-thread environment.
    User defined `org.efs.activator.Workflow` provide the order
    in which efs agents are stepped through their states.
  - `org.efs.activator.config`: explains how to define one or
    more activation workflows using a typesafe configuration
    file.
  - `org.efs.logging`: implements `org.slf4j.Logger` and
    `org.slf4j.LoggerFactory` with `org.efs.logging.AsyncLogger`
    and `org.efs.logging.AsyncLoggerFactory`. This logging uses
    efs dispatcher to perform the actual logging on a dispatcher
    thread rather than inline with application code.

### efs Background

efs is a direct descendant of the 25 year old
https://sourceforge.net/projects/ebus/ project. The goal here is
to tease out the best of that work, forming a better API using
Java 21 features. This work is _not_ a re-implementation of eBus
but a step beyond. efs is based on the observation that
applications require:

  - historic events,
  - live events, and
  - combination of historic and live events.

eBus started with live event distribution only. A later attempt
to add historic events to eBus designed to fit into the eBus
framework was less than satisfactory. Hence, a need for a new
framework supporting both historic and live event distribution
from the start.

Designing a new efs event persistence and distribution API is
on-going. This initial release 0.1.0 provides the event delivery
and processing API known as Dispatcher. It is similar to
Disruptor and SEDA (staged event-driven architecture) but
simpler. Dispatcher is designed to be a stand-alone API and
useful in its own right.