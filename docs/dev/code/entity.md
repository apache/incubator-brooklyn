---
layout: page
title: Writing an Entity
toc: /toc.json
---



## Things To Know

All entities inherit from `AbstractEntity`, 
usually through one of the following:

* **`SoftwareProcessEntity`**:  if it's a software process
* **`VanillaJavaApp`**:  if it's a plain-old-java app
* **`JavaWebAppSoftwareProcess`**:  if it's a JVM-based web-app
* **`WhirrEntity`**:  if it's a service launched using Whirr
* **`DynamicGroup`**:  if it's a collection of other entities

Software-based processes tend to use *drivers* to install and
launch the remote processes onto *locations* which support that driver type.
For example, `StartStopSshDriver` is a common driver superclass,
targetting `SshMachineLocation` (a machine to which Brooklyn can ssh).
The various `SoftwareProcess` entities above (and some the exemplars 
listed at the end of this page) have their own dedicated drivers.

Finally, there are a collection of *traits* providing common
sensors and effectors on entities, supplied as interfaces.
Choose one (or more) as appropriate.

<!---
TODO: XXX
-->

## Key Steps

So to get started:

1. Create your entity class, extending the appropriate selection from above
2. Create the driver class, again extending as appropriate
3. Wire the `entity.newDriver(Location)` method to the driver 
4. Provide the implementation of missing lifecycle methods in your driver class (details below)
5. Connect the sensors from your entity


## Helpful References

A few handy guides will help make it easy to build your own entities.
Check out some of the exemplar existing entities
(note, some of the other entities use a deprecated class hierarchy;
it is suggested to avoid them!):

* JBoss7Server
* MySqlNode
* OpenShift

You might also find the following helpful:

* The **[User Guide]({{site.url}}/use/guide/index.html)**
* The **[Mailing List](http://groups.google.com/group/brooklyn-dev)** (brooklyn-dev@googlegroups.com)
