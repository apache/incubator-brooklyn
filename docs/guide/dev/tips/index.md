---
layout: website-normal
title: Miscellaneous Tips and Tricks
---

## General Good Ways of Working

* If working on something which could be contributed to Brooklyn,
  do it in a project under the ``sandbox`` directory.
  This means we can accept pulls more easily (as sandbox items aren't built as part of the main build)
  and speed up collaboration.
  
* When debugging an entity, make sure the  [brooklyn.SSH logger](logging.html) is set to DEBUG and accessible.
 
* Use tests heavily!  These are pretty good to run in the IDE (once you've completed [IDE setup]({{site.path.guide}}/dev/env/ide/)),
  and far quicker to spot problems than runtime, plus we get early-warning of problems introduced in the future.
  (In particular, Groovy's laxity with compilation means it is easy to introduce silly errors which good test coverage will find much faster.)
  
* If you hit inexplicable problems at runtime, try clearing your Maven caches,
  or the brooklyn-relevant parts, under ``~/.m2/repository``.
  Also note your IDE might be recompiling at the same time as a Maven command-line build,
  so consider turning off auto-build.
  
* When a class or method becomes deprecated, always include ``@deprecated`` in the Javadoc 
  e.g. "``@deprecated since 0.7.0; instead use {@link ...}``"
  * Include when it was deprecated
  * Suggest what to use instead -- e.g. link to alternative method, and/or code snippet, etc.
  * Consider logging a warning message when a deprecated method or config option is used, 
    saying who is using it (e.g. useful if deprecated config keys are used in yaml) -- 
    if it's a method which might be called a lot, some convenience for "warn once per entity" would be helpful)
  * See the [Java deprecation documentation](https://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/deprecation/deprecation.html)


<a name="EntityDesign"></a>

## Entity Design Tips

* Look at related entities and understand what they've done, in particular which
  sensors and config keys can be re-used.
  (Many are inherited from interfaces, where they are declared as constants,
  e.g. ``Attributes`` and ``UsesJmx``.)
  
* Understand the location hierarchy:  software process entities typically get an ``SshMachineLocation``,
  and use a ``*SshDriver`` to do what they need.  This will usually have a ``MachineProvisioningLocation`` parent, e.g. a
  ``JcloudsLocation`` (e.g. AWS eu-west-1 with credentials) or possibly a ``LocalhostMachineProvisioningLocation``.
  Clusters will take such a ``MachineProvisioningLocation`` (or a singleton list); fabircs take a list of locations.
  Some PaaS systems have their own location model, such as ``OpenShiftLocation``.

Finally, don't be shy about [talking with others]({{site.path.website}}/community/), 
that's far better than spinning your wheels (or worse, having a bad experience),
plus it means we can hopefully improve things for other people!


## Project Maintenance

* Adding a new project may need updates to ``/pom.xml`` ``modules`` section and ``usage/all`` dependencies
 
* Adding a new example project may need updates to ``/pom.xml`` and ``/examples/pom.xml`` (and the documentation too!)

