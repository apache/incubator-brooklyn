---
title: Systems Available Out-of-the-Box
layout: page
toc: ../../toc.json
---

brooklyn comes bundled with support for a large number of systems and entities.

*In this pre-release version not all entities are fully functional and that the documentation is incomplete.*

*Please contact Cloudsoft for assistance and clarification.*

.. TODO fix
.. TODO name entities
.. TODO include the fully qualified name of the entity

<a name="web" />
Web
---

### JavaWebAppServer

Currently Tomcat and JBoss are supported. For instantiating an instance of Tomcat see TomcatServer. For JBoss,
depending on version refer to JBoss7Server or JBoss6Server.

### Nginx

Nginx provides clustering support for several web/app servers.

The install process downloads the sources for both the service and the sticky session module, configures them using GNI
autoconf and compiles them. This requires gcc and autoconf to be installed. The install script also uses the yum package manager (if available) to install openssl-devel which is required to build the service. This will only work on RHEL or CentOS Linux systems, but the install process should proceed on a vanilla system with development tools available.

On debian/ubuntu to build nginx you can get the required libraries with: apt-get install zlib1g-dev libdigest-sha-perl
libssl-dev

<a name="database" />
Database
--------

### Apache Derby

Support for Apache Derby which is a pure-Java SQL database. For setting up an instance of a server see DerbySetup.

### NoSQL


### Redis


Redis is a distributed key-value store. We support master/slave replication of a store as a clustered cache. This gives
a series of read-only slaves and a single read-write master, which propagates to the slaves with eventual consistency.
See RedisSetup.groovy.

### Infinispan


Support for Infinispan server is currently in progress.

### Gemfire


Gemfire support is a current work in progress providing capability to configure and setup Gemfire servers and define
clusters. See GemfireSetup and GemfireCluster.

<a name="messaging" />
Messaging
---------

### Qpid


Qpid support provides a JMS broker, running over AMQP. This exposes JMS queues and topics as entities as well.
See QpidSetup for instantiating a broker.

### ActiveMQ


ActiveMQ support provides a JMS broker. This exposes JMS queues and topics as entities as well. See ActiveMQSetup for
instantiating a broker.
