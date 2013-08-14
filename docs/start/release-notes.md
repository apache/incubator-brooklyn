---
layout: page
title: Release Notes
toc: ../toc.json
---

## Brooklyn Version 0.6.0 (Milestone One - 0.6.0-M1)

* Introduction
* New Features
* Backwards Compatibility

### Introduction

This milestone release includes many additions and fixes, and brings us much closer to a 0.6.0 release.

It incorporates a lot of improvements and feedback from our community. Thank you!

Thanks also go to Brooklyn's commercial users. Already Brooklyn has been adopted into some very exciting projects including controlling custom PaaS offerings, big data clusters, and three-tier web-apps.

For more information, please checkout [http://brooklyncentral.github.io](http://http://brooklyncentral.github.io), and subscribe to the mailing lists:

* [brooklyn-dev@googlegroups.com](http://groups.google.com/group/brooklyn-dev)
* [brooklyn-users@googlegroups.com](http://groups.google.com/group/brooklyn-users)

 
### New Features

The major changes between 0.5 and 0.6.0 (including any and all previous 0.6.x releases) are:

1. Locations are now constructed using a LocationSpec, rather than calling the constructor directly. This improvement is required to allow the Brooklyn management plane to track locations and persist their state.

2. A datagrid can be used to store the entity/location state within Brooklyn. This feature is currently disabled by default (i.e. storing to an in-memory pseduo-datagrid); work will continue on this in subsequent releases.

3. A new location-metadata.properties file allows metadata values (e.g. ISO-3166, lat/lon coordinates, etc) to be added to and overridden.

4. Several additional clouds are supported, including:
	* Abiquo
	* Google Compute Engine 
	* IBM SmartCloud (see [https://github.com/cloudsoft/ibm-smartcloud](github.com/cloudsoft/ibm-smartcloud) 
)

5. Several new entities have been added, including:
	* BindDnsServer
	* Jetty6Server
	* MongoDB replica sets
	* BrooklynNode (for Brooklyn bootstrap deploying Brooklyn)


### Backwards Compatibility

For upgrading from 0.5.0 to 0.6.0, existing entities/applications/policies will still work, provided they did not previously have deprecation warnings.

Some additional code has been deprecated - users are strongly encouraged to update these:

1. Some previously deprecated code has been deleted.

2. Use `LocationSpec` (and `managementContext.getLocationManager().createLocation(LocationSpec.spec(MyLocClazz.class)`), rather than calling location constructors directly.

3. Use camelCase for property names in brooklyn.properties (e.g. brooklyn.jclouds.publicKeyFile instead of brooklyn.jclouds.public-key-file).

4. Use `@Effector` and `@EffectorParam` for annotating effector methods in an entity (rather than `@Description`, `@NamedParameter` and `@DefaultValue`).

5. `location.getName()` has been renamed to `location.getDisplayName()`, to be consistent with Entity. The `location.getChildLocations()` and `location.getParentLocation()` have also been renamed to `getChildren()` and `getParent()` respectively.

6. If overriding a config key in an entity to change the default value specified in a super-type entity or constant, then use `ConfigKeys.newConfigKeyWithDefault(...)`.

7. Use `ConfigKeys.newStringConfigKey(...)` and similar methods, rather than using `new ConfigKey<String>(String.class, ...)`

8. `ListConfigKey` has been deprecated because it no longer guarantees order; instead use `SetConfigKey`. This is a consequence of efficiently using a datagrid to store the data.

9. Use the new `FeedConfig.onFailure()`, `FeedConfig.onError()` and `FeedConfig.checkSuccess()`. This is now called for example to determine if a http 404 is a success or failure (default failure), and if an ssh non-zero exit code is a failure (default yes).

10. Some package/class changes, such as:
	* `brooklyn.util.NetworkUtils` renamed to `brooklyn.util.net.Networking`  
	* `brooklyn.entity.basic.lifecycle.CommonCommands` renamed to `brooklyn.util.ssh.CommonCommands`  
	* `brooklyn.util.MutableMap` renamed to `brooklyn.util.collections.MutableMap`  


## Previous Release Notes

[0.5.0](http://brooklyncentral.github.io/v/0.5.0/start/release-notes.html) 

