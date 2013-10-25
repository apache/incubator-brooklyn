---
layout: page
title: Release Notes
toc: ../toc.json
---



## Brooklyn Version 0.6.0 

* Introduction
* New Features
* Backwards Compatibility
* Community Activity

### Introduction

This release includes many big features. It incorporates a lot of improvements and feedback from our community. Thank you!

Thanks also go to Brooklyn's commercial users. Brooklyn continues to be used in many exciting projects, including managing distributed database clusters, and cloud-enabling financial service applications (not to mention the custom PaaS offerings, big data clusters, and three-tier web-apps that Brooklyn was being used in at Version 0.5.0).

For more information, please checkout [brooklyncentral.github.io](http://brooklyncentral.github.io), and the mailing lists:
 
* [brooklyn-dev@googlegroups.com](http://groups.google.com/group/brooklyn-dev)
* [brooklyn-users@googlegroups.com](http://groups.google.com/group/brooklyn-users)
 
### New Features

*The changes listed here compare Brooklyn 0.5.0 and 0.6.0, and incorporate changes from the 0.6.0 milestone releases. Items marked **&sup1;** are new since 0.6.0-M2*


1. Locations are now constructed using a `LocationSpec`, rather than calling the constructor directly. This improvement is required to allow the Brooklyn management plane to track locations and persist their state.

2. Location specific and advanced features can be implemented using location extension points. The extension mechanism allows client-code to call `location.hasExtension(extensionClass)` and `location.getExtension(extensionClass)`.

3. Hazelcast datagrid implementation added. (NB: this is still considered experimental.)

4. A datagrid can be used to store the entity/location state within Brooklyn. This feature is currently disabled by default (i.e. storing to an in-memory pseduo-datagrid); work will continue on this in subsequent releases.

5. Brooklyn now tracks location and application usage, and records it in the datagrid. More generally:
	* Create/destroy of VMs (through location manage/unmanage) is tracked
	* Application start/stop/destroy is tracked
	* Current and historic usage information can be retrieved through the REST API.

6. Improved Support for Chef, significantly improving support for using Windows machines. 

7. Several additional clouds are supported, including:
	* Abiquo (see also [this blog post](http://www.cloudsoftcorp.com/news/as-simple-as-abc-the-abiquo-brooklyn-catalog/))
	* Google Compute Engine
	* *See also:* Community Activity, below.

8. Several new entities have been added, including:
	* BindDnsServer
	* Jetty6Server
	* MongoDB replica sets
	* BrooklynNode (for Brooklyn bootstrap deploying Brooklyn)
	* New PostgreSQL entity (via Chef)

9. A new [location-metadata.properties](https://github.com/brooklyncentral/brooklyn/blob/master/locations/jclouds/src/main/resources/brooklyn/location-metadata.properties) file allows metadata values (e.g. ISO-3166, lat/lon coordinates, etc) to be added to and overridden. Metadata in `brooklyn.properties` overrides metadata in `location-metadata.properties`.

10. GUI Improvements: Better ability to drill down on tasks, auto-updating entity-tree with status indicators, faster status updates, and better performance over slow links.

11. Improved entity and machine restart logic&sup1;, to handle failed components and dead-on-arrival&sup1; machines even better.

12. Improved onboarding, with the creation of a Maven Archetype for a sample application, (which can be used as a template for new applications) and updates to the [Getting Started brooklyn.properties](/use/guide/quickstart/brooklyn.properties) to get HP Cloud and SoftLayer users onboard faster.

13. Sandbox: Support for rolling out complex apps described using CAMP YAML. This simplifies writing application blueprints, as YAML config files are more accessible and easier to write than Java code. &sup1;

14. Big improvements to the Cassandra integration, including new tutorials for high-availability and wide-area (inter-cloud-provider) cassandra clusters.&sup1;

15. The policies for an entity can now be defined in in an entity's spec. (Simplifying adding policies to entities, and requiring less code to be written.) &sup1;

16. Added support for a cluster to spread its members across a provider's availability zones (e.g. in [AWS](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-regions-availability-zones.html)), to give high availability for that cluster. &sup1;

17. Improved the Brooklyn load balancer abstraction, so that it can be used with things like the CloudStack load balancer or AWS elastic load balancer. &sup1;


### Backwards Compatibility  
For upgrading from 0.5.0 to 0.6.0, existing entities/applications/policies will still work, provided they did not previously have deprecation warnings.

Some additional code has been deprecated - users are strongly encouraged to update these:

1. Use `LocationSpec` (and `managementContext.getLocationManager().createLocation(LocationSpec.spec(MyLocClazz.class)`), rather than calling location constructors directly.

1. In brooklyn.properties Locations must now be specified as `brooklyn.location.*`  (e.g. `brooklyn.location.jclouds.*` rather than brooklyn.jclouds.*). Property names should use CamelCase (e.g. `brooklyn.location.jclouds.publicKeyFile` instead of brooklyn.jclouds.public-key-file). Using the old conventions will give deprecation warnings in the logs.

1. Use `@Effector` and `@EffectorParam` for annotating effector methods in an entity (rather than `@Description`, `@NamedParameter` and `@DefaultValue`).
`location.getName()` has been renamed to `location.getDisplayName()`, to be consistent with Entity. The `location.getChildLocations()` and `location.getParentLocation()` have also been renamed to `getChildren()` and `getParent()` respectively.

1. If overriding a config key in an entity to change the default value specified in a super-type entity or constant, then use `ConfigKeys.newConfigKeyWithDefault(...)`.

1. Use `ConfigKeys.newStringConfigKey(...)` and similar methods, rather than using `new ConfigKey<String>(String.class, ...)`
1. `ListConfigKey` has been deprecated because it no longer guarantees order; instead use `SetConfigKey`. This is a consequence of efficiently using a datagrid to store the data.

1. Use the new `FeedConfig.onFailure()`, `FeedConfig.onError()` and `FeedConfig.checkSuccess()`. For example, This is now called to determine if a http 404 is a success or failure (default: failure), and if an ssh non-zero exit code is a failure (default: yes).

1. Some package/class changes, such as:
	* `brooklyn.util.NetworkUtils` renamed to `brooklyn.util.net.Networking`  
	* `brooklyn.entity.basic.lifecycle.CommonCommands` renamed to `brooklyn.util.ssh.CommonCommands`  
	* `brooklyn.util.MutableMap` renamed to `brooklyn.util.collections.MutableMap` 

1. Logging has been updated. Please see [logging.md](/docs/dev/tips/logging.html).  Existing custom logback.xml configuration may no longer work (e.g. brooklyn logback-defaults.xml no longer exists).
1. `EntitySpecs.spec(...)` is now deprecated; use `EntitySpec.create(...)` instead.
1. `JcloudsLocationCustomizer`: method signatures have changed. For those extending `BasicJcloudsLocationCustomizer` as recommended, existing code will work but should be changed to use the new signatures.
1. Deprecated `new BasicConfigKey(key, defaultValue)`. Instead use `ConfigKeys.newConfigKeyWithDefault(key, defaultValue)`.

There have been some other minor changes, where code is now deprecated - all such changes are clearly marked in the javadoc, indicating the recommended approach.

### Community Activity

[Cloudsoft](http://www.cloudsoftcorp.com) has created Locations for IBM SmartCloud Enterprise and Ravello (repos: [github:cloudsoft/ibm-smartcloud](https://github.com/cloudsoft/ibm-smartcloud), [github:cloudsoft/brooklyn-ravello](https://github.com/cloudsoft/brooklyn-ravello)). This code is public, subject to the Cloudsoft developer licence.

### 0.6.0-M2 - GA Changes
Features marked **&sup1;** (Features 11, 13-17, above) were added in the last push to 0.6.0 GA.