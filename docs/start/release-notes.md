---
layout: page
title: Release Notes
toc: ../toc.json
---

## Brooklyn Version 0.6.0 Milestone Two (0.6.0-M2)

* Introduction
* New Features
* Backwards Compatibility

### Introduction

This milestone release includes many big features, and brings us much closer to a 0.6.0 release.

It incorporates a lot of improvements and feedback from our community. Thank you!

Thanks also go to Brooklyn's commercial users. Already Brooklyn has been adopted into some very exciting projects including controlling custom PaaS offerings, big data clusters, and three-tier web-apps.

Work on this release can be tracked using Brooklyn's GitHub issue tracker:
 
* [Brooklyn GitHub Issues](https://github.com/brooklyncentral/brooklyn/issues)
* [Issues > 0.6.0 ](https://github.com/brooklyncentral/brooklyn/issues?milestone=6)

And via the mailing lists:
 
* [brooklyn-dev@googlegroups.com](http://groups.google.com/group/brooklyn-dev)
* [brooklyn-users@googlegroups.com](http://groups.google.com/group/brooklyn-users)
 
### New Features

The major changes between 0.6.0-M1 and 0.6.0-M2 are:

1. Improved Support for Chef, including for Windows machines. 

1. GUI Improvements: Better ability to drill down on tasks, auto-updating entity-tree with status indicators, faster status updates, and better performance over slow links.

1. Hazelcast datagrid implementation added. (Note this is still considered experimental in M2.)

1. Brooklyn now tracks location and application usage, and records it in the datagrid. More general:
	* Create/destroy of VMs (through location manage/unmanage) is tracked
	* Application start/stop/destroy is tracked
	* Current and historic usage information can be retrieved through the REST API.

1. Improved entity restart logic, to handle failed components even better.

1. Creation of a Maven Archetype for a sample application, which can be used as a template for new applications.

1. Support added for location extension points. Locations that support more advanced features can expose these through the extension mechanism, allowing client-code to call `location.hasExtension(extensionClass)` and `location.getExtension(extensionClass)`.

1. New PostgreSQL entity (via Chef)

1. Improvements to Google compute engine support.

1. Updated Getting Started brooklyn.properties to get HP Cloud and Softlayer users onboard faster

1. Now using org.apache.jclouds 1.6.1-incubating (was: org.jclouds 1.6.0)


### Backwards Compatibility

1. In brooklyn.properties Locations must now be specified as 'brooklyn.location.*'  e.g. `brooklyn.location.jclouds.*` rather than `brooklyn.jclouds.*` Using the old naming scheme will give deprecation warnings in the logs.

1. Logging has been updated. Please see [logging.md]({{site.url}}/dev/tips/logging.html).  Existing custom logback.xml configuration may no longer work (e.g. brooklyn logback-defaults.xml no longer exists).

1. `EntitySpecs.spec( ... )` is now deprecated; use `EntitySpec.create( ... )` instead.

1. JcloudsLocationCustomizer: method signatures have changed. For those extending BasicJcloudsLocationCustomizer as recommended, existing code will work but should be changed to use the new signatures.

1. Deprecated `new BasicConfigKey(key, defaultValue)`. Instead use `ConfigKeys.newConfigKeyWithDefault(key, defaultValue)`.

1. There have been some other minor changes, where code is now deprecated - all such changes are clearly marked in the javadoc, indicating the recommended approach.



## Previous Release Notes

* [0.6.0-M1 - Previous Milestone](http://brooklyncentral.github.io/v/0.6.0-M1/start/release-notes.html) 
* [0.5.0 - Previous GA ](http://brooklyncentral.github.io/v/0.5.0/start/release-notes.html) 