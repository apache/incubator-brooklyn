---
layout: page
title: Writing an Entity
toc: /toc.json
---

## Ways to write an entity

There are several ways to write a new entity:

* Write pure-java, extending existing base-classes and using utilities such as `HttpTool` and `BashCommands`.
* Write scripts, and configure (e.g. using YAML) a **`VanillaSoftwareProcess`**.
* Use Chef recipes, and wire these into the entity by using `ChefConfig` and `ChefLifecycleEffectorTasks`.
* Use an equivalent of Chef (e.g. Salt or Puppet; support for these is currently less mature than for Chef)

The rest of this section covers writing an entity in pure-java (or other JVM languages).


## Things To Know

All entities have an interface and an implementation. The methods on the interface 
are its effectors; the interface also defines its sensors.

Entities are created through the management context (rather than calling the  
constructor directly). This returns a proxy for the entity rather than the real 
instance, which is important in a distributed management plane.

All entity implementations inherit from `AbstractEntity`, 
often through one of the following:

* **`SoftwareProcessImpl`**:  if it's a software process
* **`VanillaJavaAppImpl`**:  if it's a plain-old-java app
* **`JavaWebAppSoftwareProcessImpl`**:  if it's a JVM-based web-app
* **`DynamicClusterImpl`**, **`DynamicGroupImpl`** or **`AbstractGroupImpl`**:  if it's a collection of other entities

Software-based processes tend to use *drivers* to install and
launch the remote processes onto *locations* which support that driver type.
For example, `AbstractSoftwareProcessSshDriver` is a common driver superclass,
targetting `SshMachineLocation` (a machine to which Brooklyn can ssh).
The various `SoftwareProcess` entities above (and some of the exemplars 
listed at the end of this page) have their own dedicated drivers.

Finally, there are a collection of *traits*, such as `Resizable`, 
in the package ``brooklyn.entity.trait``. These provide common
sensors and effectors on entities, supplied as interfaces.
Choose one (or more) as appropriate.



## Key Steps

So to get started:

1. Create your entity interface, extending the appropriate selection from above,
   to define the effectors and sensors.
2. Include an annotation like `@ImplementedBy(YourEntityImpl.class)` on your interface,
   where `YourEntityImpl` will be the class name for your entity implementation.
3. Create your entity class, implementing your entity interface and extending the 
   classes for your chosen entity super-types. Naming convention is a suffix "Impl"
   for the entity class, but this is not essential.
4. Create a driver interface, again extending as appropriate (e.g. `SoftwareProcessDriver`).
   The naming convention is to have a suffix "Driver". 
5. Create the driver class, implementing your driver interface, and again extending as appropriate.
   Naming convention is to have a suffix "SshDriver" for an ssh-based implementation.
   The correct driver implementation is found using this naming convention, or via custom
   namings provided by the `BasicEntityDriverFactory`.
6. Wire the `public Class getDriverInterface()` method in the entity implementation, to specify
   your driver interface.
7. Provide the implementation of missing lifecycle methods in your driver class (details below)
8. Connect the sensors from your entity (e.g. overriding `connectSensors()` of `SoftwareProcessImpl`)..
   See the sensor feeds, such as `HttpFeed` and `JmxFeed`.

Any JVM language can be used to write an entity. However use of pure Java is encouraged for
entities in core brooklyn. 


## Helpful References

A few handy pointers will help make it easy to build your own entities.
Check out some of the exemplar existing entities
(note, some of the other entities use deprecated utilities and a deprecated class 
hierarchy; it is suggested to avoid these, looking at the ones below instead):

* `JBoss7Server`
* `MySqlNode`

You might also find the following helpful:

* **[Entity Design Tips]({{site.url}}/dev/tips/index.html#EntityDesign)**
* The **[User Guide]({{site.url}}/use/guide/index.html)**
* The **[Mailing List](https://mail-archives.apache.org/mod_mbox/incubator-brooklyn-dev/)**
