# 0.1.0: October 11, 2025

  - Initial release

# 0.2.0: Noveber 11, 2025

  - Created org.efs.event module and moved event-related classes
    to this new module. Added EfsEventLayout class which extracts
    all field names and types (both in class and inherited) from
    an IEfsEvent implementing class.
  - Created org.efs.timer module and moved
    EfsScheduledExecutor-related classes to this new module.
  - Created org.efs.timer module and moved
  - Improved code correctness in general.

# 0.3.0: December 17, 2025

  - Created org.efs.event.type package.
  - Create org.efs.event.type.EfsEventLayout class which provides
    a reflective view into an IEfsEvent class layout. The purpose
    is for future event encoding and decoding.
  - Improved code correctness in general.
