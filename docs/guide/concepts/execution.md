---
title: Execution
layout: website-normal
---

All processing, whether an effector invocation or a policy cycle, are tracked as ***tasks***. This allows several important capabilities:

*	active and historic processing can be observed by operators
*	the invocation context is available in the thread, to check entitlement (permissions) and maintain a
hierarchical causal chain even when operations are run in parallel
*	processing can be managed across multiple management nodes

Some executions create new entities, which can then have tasks associated with them, and the system will record, for example, that a start effector on the new entity is a task associated with that entity, with that task
created by a task associated with a different entity.

The execution of a typical overall start-up sequence is shown below:

[![Brooklyn Flow Diagram](brooklyn-flow-websequencediagrams.com-w400.png "Brooklyn Flow Diagram" )](brooklyn-flow-websequencediagrams.com.png)


## Integration

One vital aspect of Brooklyn is its ability to communicate with the systems it starts. This is abstracted using a ***driver*** facility in Brooklyn, where a
driver describes how a process or service can be installed and managed using a particular technology.

For example, a ``TomcatServer`` may implement start and other effectors using a ``TomcatSshDriver`` which inherits from ``JavaSoftwareProcessSshDriver`` (for JVM and JMX start confguration), inheriting from ``AbstractSoftwareProcessSshDriver``
(for SSH scripting support).

Particularly for sensors, some technologies are used so frequently that they are
packaged as ***feeds*** which can discover their configuration (including from drivers). These include JMX and HTTP (see ``JmxFeed`` and ``HttpFeed``).

Brooklyn comes with entity implementations for a growing number of commonly used systems, including various web application servers, databases and NoSQL data stores, and messaging systems.


