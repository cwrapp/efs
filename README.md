A high-performance event processing framework for Java 21 that guarantees virtual single-threaded access to agents, eliminating synchronization overhead while delivering low-latency, reliable event persistence and delivery.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Quick Start](#quick-start)
- [Essential Information](#essential-information)
- [Learning efs](#learning-efs)
- [efs Background](#efs-background)
- [Glossary](#glossary)

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

## Features

- **Virtual Single-Threaded Execution**: Agents are accessed by only one thread at any given moment, eliminating synchronization overhead
- **Low-Latency Event Delivery**: Optimized for high-performance event processing
- **Event Persistence**: Support for historic, live, and combined event streams
- **Java 21 Native**: Built for modern Java with support for the latest language features
- **Async Logging**: Integrated async logging via `EfsDispatcher` for non-blocking log operations
- **Flexible Configuration**: JSON-based configuration using Typesafe Config
- **Activation Management**: Thread-safe agent lifecycle management (stopped, standby, active states)

## Quick Start

### Installation

efs is built for Java 21. Binary releases are available from GitHub Package Registry.

**Maven:**

```xml
<dependency>
    <groupId>com.github.cwrapp</groupId>
    <artifactId>efs-bom</artifactId>
    <version>0.6.2</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

For more installation details, see the [Binary Releases](#binary-releases) section.

### Basic Example

For a comprehensive example, explore the javadoc packages in the order listed in the [Learning efs](#learning-efs) section, starting with `org.efs.dispatcher`.

## Essential Information

### Binary Releases

efs is built for Java 21. Published releases are available from [GitHub Package Registry](https://github.com/cwrapp/efs/packages).

The project uses a BOM (Bill of Materials) for dependency management, making it easy to include all necessary components:

```xml
<dependency>
    <groupId>com.github.cwrapp</groupId>
    <artifactId>efs-bom</artifactId>
    <version>0.6.2</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

### Release Notes

Please see [NEWS.md](https://github.com/cwrapp/efs/blob/main/NEWS.md) in this directory for detailed release notes and changelog.

### API Documentation

Complete API documentation is available at: https://cwrapp.github.io/efs

## Learning efs

efs package javadocs explain how to use the package together with example code. You are encouraged to explore the javadoc packages in the following order:

### 1. `org.efs.dispatcher`
Explains the dispatcher architecture and how to use it. Compares efs dispatcher with LMAX Disruptor and SEDA (Staged Event Driven Architecture).

**Key Topics:**
- Virtual single-threading guarantee
- Event delivery mechanisms
- Performance characteristics

### 2. `org.efs.dispatcher.config`
Explains how to configure dispatchers using [Typesafe Config](https://github.com/lightbend/config/) configuration bean classes stored in JSON file format.

**Key Topics:**
- JSON configuration structure
- Dispatcher tuning parameters
- Configuration best practices

### 3. `org.efs.timer`
Contains the `EfsScheduledExecutor`, which _somewhat_ follows the `java.util.concurrent.ScheduledExecutorService` interface but does not implement it. Instead of using `ScheduledFuture`, it uses `EfsDispatcher` for timer delivery, ensuring consistent event processing semantics.

An efs scheduled executor may be created either programmatically or by Typesafe configuration file.

**Key Topics:**
- Timer scheduling
- Event-driven timer delivery
- Configuration options

### 4. `org.efs.activator`
This service steps an efs agent through stopped, standby, and active states in a thread-safe manner using efs dispatcher's virtual single-thread environment. User-defined `org.efs.activator.Workflow` implementations provide the order in which efs agents are stepped through their states.

**Key Topics:**
- Agent lifecycle management
- Workflow definitions
- State transitions

### 5. `org.efs.activator.config`
Explains how to define one or more activation workflows using a Typesafe configuration file.

**Key Topics:**
- Workflow configuration
- Agent ordering
- State machine definitions

### 6. `org.efs.logging`
Implements `org.slf4j.Logger` and `org.slf4j.LoggerFactory` with `org.efs.logging.AsyncLogger` and `org.efs.logging.AsyncLoggerFactory`. This logging uses efs dispatcher to perform the actual logging on a dispatcher thread rather than inline with application code.

**Key Topics:**
- Async logging performance
- Integration with SLF4J
- Non-blocking log operations

### efs Background

efs is a direct descendant of the 25-year-old [eBus](https://sourceforge.net/projects/ebus/) project. The goal is to extract the best of that work, forming a better API using Java 21 features. This work is _not_ a re-implementation of eBus but a step beyond.

efs is based on the observation that applications require:

- **Historic events**: Access to past events for replay and analysis
- **Live events**: Real-time event delivery to active subscribers
- **Combined events**: Seamless handling of both historic and live event streams

eBus started with live event distribution only. A later attempt to add historic events to eBus, designed to fit into the eBus framework, was less than satisfactory. Hence, the need for a new framework supporting both historic and live event distribution from the start.

Designing a new efs event persistence and distribution API is ongoing. Release 0.6.2 provides the event delivery and processing API known as Dispatcher. It is similar to Disruptor and SEDA (Staged Event-Driven Architecture) but simpler and more focused. Dispatcher is designed to be a stand-alone API and useful in its own right.

## Glossary

- **Virtual Single-Threading**: A guarantee that an agent is accessed by only one thread at any given moment, even if it may run on different threads over its lifespan. Eliminates the need for locks.

- **Dispatcher**: The core event delivery engine that ensures virtual single-threaded access and low-latency event processing.

- **Activator**: A service that manages agent lifecycle transitions (stopped → standby → active) in a thread-safe manner.

- **Agent**: A component that receives and processes events delivered by the dispatcher.

- **Event Persistence**: The ability to store events for later retrieval and replay, supporting both historic and live event streams.

- **Async Logging**: Logging that occurs asynchronously on a dispatcher thread, avoiding blocking of application code.

---

For more information, visit the [efs repository](https://github.com/cwrapp/efs) or the [API documentation](https://cwrapp.github.io/efs).
