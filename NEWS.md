# 0.7.4: September 7, 2026

  - org.efs.io.EfsFile
    * Added optional size policy and size limit.
        + Default is there is no enforced size limit allowing
          event file to continue growing.
        + Size limit is enforced via a FIFO strategy. When limit
          is reached, the oldest event is removed before new
          event is added.
        + Attempt to exceed size limit results in an
          IllegalStateException being thrown.
    * Added event file initializer to EfsFile.Builder. This
      initializer adds events to file while being built. Results
      in new event file containing rows from start. Note: size
      policy is applied to initializer. If size policy is
      "fail-on-limit-reached" and initializer attempts to exceed
      size limit, then event file build fails.

  - org.efs.io.EfsFileInitializationException
    Thrown when adding a new event would exceed event file size
    limit and size policy is "fail-on-limit-reached".

  - org.efs.dispatcher.EfsDispatcher
    * Added a new EFS_PINNED dispatcher type. A pinned dispatcher
      has a single dispatcher thread and a single agent. The
      agent constantly busy spins on its event queue, waiting for
      an event to arrive. There is no run queue.
    * Created class EfsDispatcherThreadPinned specifically for
      pinned dispatcher. This thread *must* have core affinity
      specified.
    * Pinned agents must be registered with its pinned dispatcher
      when dispatcher is built. This is only time an agent must
      be created prior to building the dispatcher. A pinned agent
      may *not* be deregistered from its dispatcher.

  - org.efs.event.EfsTopicKey
    Corrected synchronization error when creating a new topic
    key.

  - Continued correcting and improving javadoc comments.

# 0.7.3: August 9, 2026

  - EfsFile
    * Changed index on EfsRow publish timestamp from HashIndex to
      NavigableIndex which is superior in finding intervals.
    * EfsFile instances are now created using an `EfsFile.Builder`
      instance. A Builder instance is acquired via
      `EfsFile.builder(EfsTopicKey<E>)`.
    * EfsFile may now be configured with a exhaust agent which may
      be used to write all newly added rows to persistent store.
    * EfsFile may now be configured with an initializer `Supplier`
      lambda used to fill in a newly opened EfsFile with
    * EfsFile may now be configured to support a maximum number of
      simultaneous active connections and retrievals. Defaults to
      100 concurrent connections and 1,000 concurrent retrievals.
      Attempts to exceed these allowed maximums results a thrown
      IllegalStateException.
    * EfsFile may now be configured with a connection policy which
      decides which agents may connect with which access modes.
      Defaults to all agents using all modes. If a connections is
      attempted with a disallowed agent, access mode pair, the
      attempt fails with a thrown IllegalStateException.
    * EfsFile Clock is now configurable on a per file basis.
    * Added EfsFile.Metrics inner class used to track:
        + EfsFile opening timestamp.
        + Total number of events added since opening.
        + Total number of event retrievals started since opening.
        + Total number of event retrievals completed since opening.
        + Number of in-progress event retrievals.
        + Total number of event dispatch failures to retrieval
          agents since opening.

  - EfsFileConnection
    * Added method `rowCount` which returns a snapshot count of
      total number of rows added to event file.

  - EfsIntervalEndpoint
    * Added fixed row index interval type
      `org.efs.io.EfsIndexFixedEndpoint` This allows intervals
      to reference an event row with a concrete row index.

  - EfsEventBus
    * Event bus instances may now be configured with a
      `java.time.Clock` used to provide publish timestamps.
      Defaults to `Clock.systemUTC()`.
    * Events are now delivered within an `org.efs.bus.EfsEnvelope`
      which contains a wallclock publish timestamp, a logical
      timestamp, publishing agent, and the event. Subscription
      callback must now be `Consumer<EfsEnvelope<E>>`. This
      envelope supports a "Lamport timestamp" which can be used
      to order event delivery between agents within a JVM.
    * Event bus instances are now created using an
      `EfsEventBus.Builder` instance. A Builder instance is
      acquired via `EfsEventBus.builder(String busName)`.

  - ConflationEvent
    * Added metric tracking total number of missed events over an
      inbox subscription's lifetime.

# 0.7.2: July 11, 2026

  - Added `Set<Integer>` tags to `EfsRow`. These tags are set
    when row is added to `EfsFile` and cannot be modified
    afterwards.
  - Added `EfsFileConnection.retrieve(int, Consumer, Consumer)`
    method for retrieving event rows based on event tag. Unlike
    interval retrievals, retrieval-by-tag cannot be canceled and
    is matched only against historic rows.
  - Continue to improve unit test coverage and javadoc
    documentation.

# 0.7.1: July 1, 2026

  - Added class `EfsFileConnection` which contains method to add
    events to and retrieve events from `EfsFile`.
  - User cqengine Query instances must now use attributes
    generated by `EfsFile` using method
    `EfsFile.attribute("field name")`. This is due to events
    residing within an  `EfsRow`. `EfsFile`-Generated attributes
    provide access to event fields within the event row.
  - Provided `org.efs.io.CQAttribute` annotation to specify how
    an event field attribute should be generated and indexed.
    If an event field is not annotated with `@CQAttribute`, then
    no attribute is generated for that field.
  - Continue to improve unit test coverage and javadoc
    documentation.

# 0.7.0: June 6, 2026

  - Renamed `org.efs.feed` to `org.efs.io` and added `EfsFile`.
    This module is _a work in progress_ and changes which are
    incompatible with this current version will likely occur.
  - Corrected thread safety issues in `org.efs.bus`.
  - Continue to improve unit test coverage and javadoc
    documentation.

# 0.6.2: May 9, 2026

  - org.efs.activator changed activator listener list to
    java.util.concurrent.ConcurrentHashMap.
  - Deleted org.efs.event.EfsEventWrapper because unuseful.
  - Deleted org.efs.dispatcher.ReplyTo as duplicate of
    org.efs.dispatcher.EfsDispatchTarget.
  - Improved unit test coverage.
  - Deployed efs to Maven Central as io.github.cwrapp:efs-parent.

# 0.6.1: April 24,2026

  - Improved org.efs.bus documentation.
  - Improved org.efs.bus unit tests and code coverage.
  - Improved org.efs.bus.EfsEventBus performance.

# 0.6.0: April 11, 2026

  - Added org.efs.bus module which supports routing events
    between agents. Agents posting events to the bus must first
    advertise their ability to do so. Agents wanting to receive
    published events are required to subscribe first.
    Subscriptions come in the form of: concrete, inbox (only
    latest event is delivered), and routed (event is dynamically
    routed to a selected agent). Both advertise and subscribe
    support regular expression matching to topics.
    Note: bus uses type+topic allowing the same topic to be used
    with multiple events.
  - Continue to improve code performance, code correctness, and
    javadoc documentation.

# 0.5.0: January 17, 2026

  - Dropped -Dorg.efs.dispatcher.configFile support which
    automatically loaded dispatcher configurations and created
    EfsDispatcher instances. Users must now do this loading
    explicitly by calling
    EfsDispatcher.loadDispatchersConfigFile(File). EfsDispatchers
    may still be created programatically using
    EfsDispatcher.Builder.
  - Replaced roundToPowerOfTwo() static method with
    org.jctools.util.Pow2.roundToPowerOfTwo().
  - Added dispatcher name to org.efs.dispatcher.ThreadStartException.
    This allows user to determine to which dispatcher the failed
    EfsDispatcherThread belongs.
  - Added nullable Object datum to org.efs.timer.EfsTimerEvent.
    This allows user to pass through an object instance from
    timer scheduling code to timer processing code.
  - Continue to improve code performance, code correctness, and
    javadoc documentation.

# 0.4.0: January 3, 2026

  - Initial development of org.efs.feed module. Used to store
    published events and forward to subscribers.
  - Re-wrote org.efs.time.EfsScheduledExecutor so that it
    encapsulates a user-provided
    java.util.concurrent.ScheduledExecutorService instance.
    EfsScheduledExecutor API changed only with respect to new
    getter methods added.
  - Changed javax.annotation.Nullable and Nonnull import to
    jakarta.annotation package.
  - Improved code correctness in general.

# 0.3.0: December 17, 2025

  - Created org.efs.event.type package.
  - Create org.efs.event.type.EfsEventLayout class which provides
    a reflective view into an IEfsEvent class layout. The purpose
    is for future event encoding and decoding.
  - Improved code correctness in general.

# 0.2.0: Noveber 11, 2025

  - Created org.efs.event module and moved event-related classes
    to this new module. Added EfsEventLayout class which extracts
    all field names and types (both in class and inherited) from
    an IEfsEvent implementing class.
  - Created org.efs.timer module and moved
    EfsScheduledExecutor-related classes to this new module.
  - Created org.efs.timer module and moved
  - Improved code correctness in general.

# 0.1.0: October 11, 2025

  - Initial release
