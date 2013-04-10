---
layout: page
title: Release Notes
toc: ../toc.json
---

## Brooklyn Version 0.5.0 RC1 (0.5.0-rc.1)

API Tidy on top of M2, using `init()` as the method to override when building composed Application and Entity classes.

There are known issues with Whirr clusters and the Cloud Foundry example (moving) which will be resolved for RC2.   


## Brooklyn Version 0.5.0 Milestone Two (0.5.0-M2)

### Introduction

This milestone release includes many big features, and brings us much closer to a 0.5.0 release.

It incorporates a lot of improvements and feedback from our community. Thank you!

Thanks also go to Brooklyn's commercial users. Already Brooklyn has been adopted into some very exciting projects including controlling custom PaaS offerings, big data clusters, and three-tier web-apps.

Work on this release can be tracked using Brooklyn's GitHub issue tracker:
 
* [github.com/brooklyncentral/brooklyn/issues?milestone=1](https://github.com/brooklyncentral/brooklyn/issues?milestone=1)

And via the mailing lists:
 
* [brooklyn-dev@googlegroups.com](http://groups.google.com/group/brooklyn-dev)
* [brooklyn-users@googlegroups.com](http://groups.google.com/group/brooklyn-users)
 
### New Features

The major changes between M1 and M2 are:

1. Entities have been separated into an interface and implementation, rather than just a single class. Construction of entities is now done using an EntitySpec, rather than directly calling the constructor. This improvement is required to simplify remoting in a distributed brooklyn management plane.

2. Downloading of entity installers is greatly improved:
	* More configurable, with ability to specify URLs in the brooklyn configuration files or to override in code.
	* Will fallback to a repository maintained by Cloudsoft, so if an artifact is removed from the official public site then it will not break the entity. 
	
		See [downloads.cloudsoftcorp.com/brooklyn/repository/](http://downloads.cloudsoftcorp.com/brooklyn/repository/)

3. Support for running applications across private subnets.

4. Policies can now be re-configured on-the-fly through the REST api and through the web-console.

5. Some entities now support configuration files being supplied in FreeMarker template format. These include JBoss AS7, ActiveMQ and MySql. More will be converted to use this pattern.

6. Several new entities have been added, including:
	* MongoDB
	* Cassandra
	* RubyRep
	* DynamicWebAppFabric


### Backwards Compatibility

For upgrading from 0.5.0-M1 to M2:

1. Entity classes have been renamed, e.g. MySqlNode is now an interface and the implementationis MySqlNodeImpl.
	* The minimum change for this to work is to update your references to include the Impl suffixes. However, that will result in deprecation warnings.
	* The recommended approach is to use the EntitySpec when constructing entities. A good place to start is to look at the updated example applications.

2. The default username for provisioning with jclouds has changed to use the name of the user executing the Brooklyn process. 
	* Java's `System.getProperty("user.name")` is used instead of 'root' or 'ubuntu'
	* Usernames can be overridden in `brooklyn.properties` or using system properties.
		For example, by entering '`brooklyn.location.named.acmecloud.user=root`' in `brooklyn.properties` or using the command syntax `-Dbrooklyn.location.named.acmecloud.user=root`.
		
		'`brooklyn.jclouds.aws-ec2.user=root`' could also be used to apply `user=root` to all aws-ec2 VMs. 

3. Some deprecated code has been deleted. All of this code was commented in 0.4.0 with text such as "will be deleted in 0.5".