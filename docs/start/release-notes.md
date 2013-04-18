---
layout: page
title: Release Notes
toc: ../toc.json
---

## Brooklyn Version 0.5.0

This release includes many big features. It incorporates a lot of improvements and feedback from our community. Thank you!

Thanks also go to Brooklyn's commercial users. Already Brooklyn has been adopted into some very exciting projects including controlling custom PaaS offerings, big data clusters, and three-tier web-apps.

New Features
------------

The major changes between 0.4.*x* and 0.5 are:

* "Rebind" suppport: allow Brooklyn to restart and re-binding to the entities in the applications.
  The state is automatically persisted as entities change, so Brooklyn can survive unexpected termination
  and retarts.

* Entities have been separated into an interface and implementation, rather than just a single class.
  Construction of entities is now done using an EntitySpec, rather than directly calling the constructor.
  This improvement is required to simplify remoting in a distributed brooklyn management plane.

* Downloading of entity installers is greatly improved:

  * More configurable, with ability to specify URLs in the brooklyn configuration files or to override in code.

  * Will fallback to a repository maintained by Cloudsoft, so if an artifact is removed from the official public site then it will not break the entity. (*See also:* [downloads.cloudsoftcorp.com/brooklyn/repository](http://downloads.cloudsoftcorp.com/brooklyn/repository/))

* Support for running applications across private subnets.

* Policies can now be re-configured on-the-fly through the REST API and through the web-console.

* New `feed` classes for entities to poll for their sensor values.
  This replaces the now-deprecated `SensorAdapter` classes.

* Some entities now support configuration files being supplied in FreeMarker template format. These include JBoss AS7, ActiveMQ and MySql. More will be converted to use this pattern.

* A CLI-based ssh-tool implementation (Brooklyn can be configured to use that, rather than the default sshj).
  This is useful for things like Tectia integration, which does not work with sshj due to key file formats.

* Several new entities have been added, including:

  * MongoDB
  * Cassandra
  * PostgreSQL
  * RubyRep
  * Kafka
  * DynamicWebAppFabric

Backwards Compatibility
---------------------

For upgrading from 0.4.*x* to 0.5.0:

* Entity classes have been renamed, e.g. `MySqlNode` is now an interface and the implementationis `MySqlNodeImpl`.

  * The minimum change for this to work is to update your references to include the `-Impl` suffixes. However, that will result in deprecation warnings.

  * The recommended approach is to use the EntitySpec when constructing entities. A good place to start is to look at the updated example applications.

* The default username for provisioning with jclouds has changed to use the name of the user executing the Brooklyn process.

  * Java's `System.getProperty("user.name")` is used instead of 'root' or 'ubuntu'

  * Usernames can be overridden in brooklyn.properties or using system properties. For example, by entering `brooklyn.location.named.acmecloud.user=root` in brooklyn.properties or using the command syntax `-Dbrooklyn.location.named.acmecloud.user=root`.

    `brooklyn.jclouds.aws-ec2.user=root` could also be used to apply user=root to all aws-ec2 VMs.

* Some deprecated code has been deleted. All of this code was commented in 0.4.0 with text such as "will be deleted in 0.5". Where code has been deprecated in 0.5.0, there are javadoc comments indicating what should be used instead. 
