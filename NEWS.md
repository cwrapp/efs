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
