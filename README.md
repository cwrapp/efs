A high-performance event-processing framework for Java 21 that guarantees virtual single-threaded access to agents, reducing synchronization overhead while supporting low-latency, reliable event persistence and delivery.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Quick Start](#quick-start)
- [Essential Information](#essential-information)
- [Learning efs](#learning-efs)
- [efs Background](#efs-background)
- [Glossary](#glossary)

## Overview

efs - Event File System is an _evolving_ framework for reliable,
low-latency event persistence and delivery between publishing
and subscribing agents.

The name "evolving" reflects the fact that the project is still
maturing: enough functionality is already in place to justify an
initial release, but the design and implementation continue to grow.
This first release centers on the dispatcher framework, which delivers
events to agents in a virtual single-threaded manner. Virtual
single-threading means that although an agent may run on different
threads over its lifetime, it is guaranteed to be accessed by only
one thread at any given moment. That removes the need for most
synchronization primitives and avoids the contention and context-switching
penalties that often come with locking.

A second key component is the activator, which uses the dispatcher to
start and activate agents (`IEfsActivateAgent` extends `IEfsAgent`).
Activator addresses a common problem: an agent may begin activation on one
thread while also receiving events on dispatcher threads before that
activation sequence has fully completed. By moving activation through the
agent's dispatcher, efs preserves the virtual single-threading guarantee.
Any events posted while activation is still in progress are delivered only
after activation has completed.

## Features

- **Virtual Single-Threaded Execution**: Agents are accessed by
  only one thread at any given moment, reducing synchronization overhead.
- **Low-Latency Event Delivery**: Optimized for high-performance
  event processing.
- **Event Persistence**: Supports historic, live, and combined
  event streams.
- **Java 21 Native**: Built for modern Java and its latest
  language features.
- **Async Logging**: Integrated asynchronous logging via `EfsDispatcher`
  for non-blocking log operations.
- **Flexible Configuration**: JSON-based configuration using
  Typesafe Config.
- **Activation Management**: Thread-safe agent lifecycle
  management across stopped, standby, and active states.

## Quick Start

### Installation

efs requires Java 21. Binary releases are available from
[Maven Central Repository](https://mvnrepository.com/repos/central).

**Maven:**

The project uses a BOM (Bill of Materials) for dependency
management, making it easy to pull in the required components:

```xml
<dependency>
    <groupId>io.github.cwrapp</groupId>
    <artifactId>efs-bom</artifactId>
    <version>0.7.4</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

### Basic Example

For a more complete introduction, explore the Javadoc packages in the order
listed in the [Learning efs](#learning-efs) section, beginning with
`org.efs.dispatcher`.

### Release Notes

Please see [NEWS.md](https://github.com/cwrapp/efs/blob/main/NEWS.md)
in this directory for detailed release notes and changelog.

### API Documentation

Complete API documentation is available at: https://cwrapp.github.io/efs

## Learning efs

efs package javadocs explain how to use the package together with example code. You are encouraged to explore the javadoc packages in the following order:

### 1. `org.efs.dispatcher`
Explains the dispatcher architecture and how to use it. It also compares
the efs dispatcher with LMAX Disruptor and SEDA (Staged Event-Driven Architecture).

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

### 3. `org.efs.bus`
Provides the event bus layer for type+topic-based, loosely
coupled communication between agents. `EfsEventBus` allows
producers to publish specific event types to named topics without
direct knowledge of downstream consumers, while subscribers
register interest in specific topics and receive matching events
through the dispatcher runtime.

**Key Topics:**
- Type+topic-based publish/subscribe routing
- Loose producer-consumer decoupling
- Dispatcher-backed event delivery

### 4. `org.efs.timer`
Contains `EfsScheduledExecutor`, which _roughly_ follows the
`java.util.concurrent.ScheduledExecutorService` interface but does not implement it.
Instead of using `ScheduledFuture`, it uses `EfsDispatcher` for timer delivery,
providing consistent event-processing semantics.

An efs scheduled executor may be created either programmatically or through a
Typesafe configuration file.

**Key Topics:**
- Timer scheduling
- Event-driven timer delivery
- Configuration options

### 5. `org.efs.activator`
This service steps an efs agent through stopped, standby, and active states in a thread-safe manner using efs dispatcher's virtual single-thread environment. User-defined `org.efs.activator.Workflow` implementations provide the order in which efs agents are stepped through their states.

**Key Topics:**
- Agent lifecycle management
- Workflow definitions
- State transitions

### 6. `org.efs.activator.config`
Explains how to define one or more activation workflows using a Typesafe configuration file.

**Key Topics:**
- Workflow configuration
- Agent ordering
- State machine definitions

### 7. `org.efs.logging`
Implements `org.slf4j.Logger` and `org.slf4j.LoggerFactory` with
`org.efs.logging.AsyncLogger` and `org.efs.logging.AsyncLoggerFactory`.
This logging uses the efs dispatcher to perform the actual logging work on a
dispatcher thread rather than inline with application code.

**Key Topics:**
- Async logging performance
- Integration with SLF4J
- Non-blocking log operations

### 8. `org.efs.io`
Provides `org.efs.io.EfsFile`, which supports access to past and future events
using [CQEngine](https://github.com/npgall/cqengine?tab=readme-ov-file) for
querying over a specified `EfsInterval`. `EfsFile` associates a publish
timestamp and a monotonic, continuous index with each event posted to the event
file.

Agents access an `EfsFile` through `org.efs.io.EfsFileConnection` using an
`EfsFile.AccessMode`. The framework automatically defines
`com.googlecode.cqengine.attribute.Attribute` instances based on the
`org.efs.io.CQAttribute` annotation. Only fields annotated with `@CQAttribute`
have a CQEngine attribute generated for them.

**Key Topics:**
- Stores events for a given event class and topic.
- Uses CQEngine `Query` class to retrieve events over a given interval (based on event row index or publish timestamp) and matching user query.
- User queries _must_ use CQEngine attributes provided by `EfsFile.attribute(String)`. Failure to do so causes the query to be ineffective.
- Currently does not persist events but goal is to do so in future releases. Consider `EfsFile` a work-in-progress.

### efs Background

efs is a direct descendant of the 25-year-old [eBus](https://sourceforge.net/projects/ebus/) project. The goal is to preserve the best aspects of that work while forming a cleaner API that takes advantage of Java 21 features. This work is _not_ a re-implementation of eBus, but a step beyond it.

efs is based on the observation that applications require:

- **Historic events**: Access to past events for replay and analysis
- **Live events**: Real-time event delivery to active subscribers
- **Combined events**: Seamless handling of both historic and live event streams

eBus started with live event distribution only. A later attempt to add historic events to eBus, designed to fit into the eBus framework, was less than satisfactory. Hence, the need for a new framework supporting both historic and live event distribution from the start.

Designing a new efs event-persistence and distribution API is still ongoing. Release 0.6.2 introduced the event-delivery and processing API known as Dispatcher. It is similar to Disruptor and SEDA (Staged Event-Driven Architecture), but simpler and more focused. Dispatcher is designed to be a stand-alone API and useful on its own.

## Glossary

- **Virtual Single-Threading**: A guarantee that an agent is accessed by only one thread at any given moment, even if it may run on different threads over its lifespan. Eliminates the need for locks.

- **Dispatcher**: The core event delivery engine that ensures virtual single-threaded access and low-latency event processing.

- **Activator**: A service that manages agent lifecycle transitions (stopped → standby → active) in a thread-safe manner.

- **Agent**: A component that receives and processes events delivered by the dispatcher.

- **Event Persistence**: The ability to store events for later retrieval and replay, supporting both historic and live event streams.

- **Async Logging**: Logging that occurs asynchronously on a dispatcher thread, avoiding blocking of application code.

---

For more information, visit the [efs repository](https://github.com/cwrapp/efs) or the [API documentation](https://cwrapp.github.io/efs).
