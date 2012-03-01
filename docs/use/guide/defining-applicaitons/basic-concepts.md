---
title: Basic Concepts
layout: page
toc: ../../../toc.json
---

This introduces brooklyn and describes how it simplifies the deployment and management of big applications. It is
intended for people who are using brooklyn-supported application components (such as web/app servers, data stores)
to be able to use brooklyn to easily start their application in multiple locations with off-the-shelf management
policies.

The Basic Concepts - Entities and How to Use Them
-------------------------------------------------

At the heart of brooklyn is the concept of an **entity**. Some entities correspond to actual machines or processes such
as a ``JBossNode`` or a ``RabbitMqNode``, others correspond to a group of other entities, such as a ``TomcatCluster`` or
``MultiLocationMySqlTier``. Entities can be grouped or composed to form other entities, and are code so they
can be extended, overridden, and modified. Their main responsibilities are:

- Provisioning the entity in the given location of locations
- Holding configuration and state (attributes) for the entity
- Reporting monitoring data (sensors) about the status of the entity
- Exposing operations (effectors) that can be performed on the entity
- Hosting management policies and tasks related to the entity

Entities, Application, Ownership and Membership
-----------------------------------------------

All entities have an owner entity, which creates and manages it, with two exceptions: applications and templates. Top-level
``Application`` entities are created and managed externally, perhaps by a script. **Templates** are the other exception, and are covered in more detail later.

A **Group** is an entity to which other entities are memembers. Membership can be used for whatever purposes the application definer wishes. A typical use-case is to manage a collection of
entities together for one purpose (e.g. wide-area load-balancing between locations) even though they may have been
created by different **owners** (e.g. a multi-tier stack within a location).

Configuration, Sensors and Effectors
------------------------------------

All entities contain a map of config information. This can contain arbitrary values, typically keyed under static ``ConfigKey`` fields on the ``Entity`` sub-class. These values are inherited, so setting a configuration value at the
application will make it available in all entities underneath unless it is overridden.

Configuration is propagated when an application "goes live" (i.e. its ``deploy()`` or ``start()`` method is invoked), so config values must be set before this occurs. 

Configuration values can be specified in a configuration file (``~/.brooklyn/brooklyn.properties``)
to apply universally, and programmatically to a specific entity and its descendants using the ``entity.setConfig(KEY, VALUE)``
method. 
Many common configuration parameters are available as "flags" which can be supplied in the entity's constructor of the form ``new MyEntity(owner, config1: "value1", config2: "value2")``. 
Documentation of the flags available for individual constructors can be found in the javadocs, or by inspecting ``@SetFromFlag`` annotations on the ``ConfigKey`` definitions. 

**Sensors** (activity information and notifications) and **Effectors** (operations that can be invoked on the entity), are defined by entities as static fields on the ``Entity`` subclass. Sensors can be updated by the entity or associated tasks, and sensors from an entity can be subscribed to by its owner or other entities to track changes in an entity's activity. Effectors, can be invoked by an entity's owner remotely, and the invoker is able to track the execution of that effector. Effectors can be invoked by other entities, but use this functionality with care to prevent too many managers!

Entities are Java classes and data can also be stored in internal fields.
This data will not be inherited and will not be externally visible (and resilience is more limited), but the data will be moved when an entity's master location is changed. (See discussion of management below.)

