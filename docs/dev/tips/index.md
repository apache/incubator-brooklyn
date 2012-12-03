---
layout: page
title: Miscellaneous Tips and Tricks
toc: /toc.json
---

## General Good Ways of Working

* If working on something which could be contributed to Brooklyn,
  do it in a project under the ``sandbox`` directory.
  This means we can accept pulls more easily (as sandbox items aren't built as part of the main build)
  and speed up collaboration.
  
* When debugging an entity, make sure the  [brooklyn.SSH logger](logging.html) is set to DEBUG and accessible.
 
* Use tests heavily!  These are pretty good to run in the IDE (once you've completed [IDE setup]({{site.url}}/dev/build/ide.html)),
  and far quicker to spot problems than runtime, plus we get early-warning of problems introduced in the future.
  (In particular, Groovy's laxity with compilation means it is easy to introduce silly errors which good test coverage will find much faster.)
  
* If you hit inexplicable problems at runtime, try clearing your Maven caches,
  or the brooklyn-relevant parts, under ``~/.m2/repository``.
  Also note your IDE might be recompiling at the same time as a Maven command-line build,
  so consider turning off auto-build.


<a name="EntityDesign"></a>
## Entity Design Tips

* Look at related entities and understand what they've done, in particular which
  sensors and config keys can be re-used.
  (Many are inherited from interfaces, where they are declared as constants,
  e.g. ``Attributes`` and ``UsesJmx``.)
  
* Understand the location hierarchy:  software process entities typically get an ``SshMachineLocation``,
  and use a ``*SshDriver`` to do what they need.  This will usually have a ``MachineProvisioningLocation`` parent, e.g. a
  ``JcloudsLocation`` (e.g. AWS eu-west-1 with credentials) or possibly a ``LocalhostMachineProvisioningLocation``.
  Clusters will take such a ``MachineProvisioningLocation`` (or a singleton list), and fabrics a list of locations.
  Some PaaS systems have their own location model, such as ``OpenShiftLocation``.

Finally, don't be shy about [talking with others]({{site.url}}/meta/contact.html), 
that's far better than spinning your wheels (or worse, having a bad experience),
plus it means we can hopefully improve things for other people!


## Project Maintenance

* Adding a new project may need updates to ``/pom.xml`` ``modules`` section and ``usage/all`` dependencies
 
* Adding a new example project may need updates to ``/pom.xml`` and ``/examples/pom.xml`` (and the documentation too!)

