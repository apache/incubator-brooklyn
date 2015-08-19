---
title: Stop/start/restart behaviour
layout: website-normal
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

Many entities expose `start`, `stop` and `restart` effectors. The semantics of these operations (and the parameters they take) depends on the type of entity.

## Top-level applications
A top-level application is a grouping of other entities, pulling them together into the "application" of your choice. This could range from a single app-server, to an app that is a composite of a no-sql cluster (e.g. MongoDB sharded cluster, or Cassandra spread over multiple datacenters), a cluster of load-balanced app-servers, message brokers, etc.

### start(Collection<Location>)
This will start the application in the given location(s). Each child-entity within the application will be started concurrently, passing the location(s) to each child.
The start effector will be called automatically when the application is deployed through the catalog.
Is is strongly recommended to not call start again.

### stop()
Stop will terminate the application and all its child entities (including releasing all their resources).
The application will also be unmanaged, **removing** it from Brooklyn.

### restart()
This will invoke `restart()` on each child-entity concurrently (with the default values for the child-entity's restart effector parameters).
Is is strongly recommended to not call this, unless the application has been explicitly written to implement restart.

## Software Process (e.g MySql, Tomcat, JBoss app-server, MongoDB)

### start(Collection<Location>)
This will start the software process in the given location.
If a machine location is passed in, then the software process is started there.
If a cloud location is passed in, then a new VM will be created in that cloud - the software process will be **installed+launched** on that new VM.

The start effector will have been called automatically when deploying an application through the catalog.
In normal usage, do not invoke start again.

If calling `start()` a second time, with no locations passed in (e.g. an empty list), then it will go through the start sequence on the existing location from the previous call.
It will **install+customize+launch** the process.
For some entities, this could be *dangerous*. The customize step might execute a database initialisation script, which could cause data to be overwritten (depending how the initialisation script was written).

If calling `start()` a second time with additional locations, then these additional locations will be added to the set of locations.
In normal usage it is not recommended.
This could be desired behaviour if the entity had previously been entirely stopped (including its VM terminated) - but for a simple one-entity app then you might as well have deleted the entire app and created a new one.


### stop(boolean stopMachine)
If `stopMachine==true`, this effector will stop the software process and then terminate the VM (if a VM had been created as part of `start()`). This behaviour is the inverse of the first `start()` effector call.
When stopping the software process, it does not uninstall the software packages / files.

If `stopMachine==false`, this effector will stop just the software process (leaving the VM and all configuration files / install artifacts in place).

### restart(boolean restartMachine, boolean restartChildren)
This will restart the software process.

If `restartMachine==true`, it will also terminate the VM and create a new VM. It will then install+customize+launch the software process on the new VM. It is equivalent of invoking `stop(true)` and then `start(Collections.EMPTY_LIST)`.
If `restartMachine==false`, it will first attempt to stop the software process (which should be a no-op if the process is not running), and will then start the software process (without going through the **install+customize** steps).

If `restartChildren==true`, then after restarting itself it will call `restart(restartMachine, restartChildren)` on each child-entity concurrently.

## Recommended operations

The recommended operations to invoke to stop just the software process, and then to restart it are:

* Select the software process entity in the tree (*not* the parent application, but the child of that application).
* Invoke `stop(stopMachine=false)`
* Invoke `restart(restartMachine=false, restartChildren=false)`