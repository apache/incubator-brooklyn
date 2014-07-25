---
title: Systems Available Out-of-the-Box
layout: page
toc: ../guide_toc.json
categories: [use, guide]
---

brooklyn comes bundled with support for a large number of systems and entities.

*Some entities are in an early-access state, and documentation is incomplete. Please contact the Brooklyn Project for assistance and clarification.*
<!---
.. TODO fix
.. TODO name entities
.. TODO include the fully qualified name of the entity
-->

<a name="web"></a>
Web
---

### Clusters and Interfaces

The class ``ControlledDynamicWebAppCluster`` creates a load-balanced cluster of web servers.
It defaults to Nginx and JBoss 7, but this is configurable with the ``controller`` or ``controllerSpec``, and 
the ``memberSpec`` configuration options.

Most web app server processes, and some clusters and PaaS implementations,
support the interface ``WebAppService`` which defines many sensors including requests per second.
This allows app server metrics to interoperable across implementations in many cases.


### JBoss Application Server

Brooklyn supports JBoss 7 in the calss ``JBoss7Server``, with a wide range of
monitoring.

JBoss 6 is also supported using the different class ``JBoss6Server``.
(The different implementation is needed due to major differences between 6 and 7,
including switching from JMX to HTTP/JSON as the preferred metrics mechanism.)


### Apache Tomcat

Apache Tomcat is supported in the class ``TomcatServer``.
(Note that this currently uses a legacy Brooklyn class hierarchy,
and could benefit from being ported to the ``JavaSoftwareProcessSshDriver`` implementation.)


### Nginx Load Balancer

Nginx provides clustering support for several web/app servers.

The install process downloads the sources for both the service and the sticky session module, configures them using GNI
autoconf and compiles them. This requires gcc and autoconf to be installed. The install script also uses the yum package manager (if available) to install openssl-devel which is required to build the service. This will only work on RHEL or CentOS Linux systems, but the install process should proceed on a vanilla system with development tools available.

On debian/ubuntu to build nginx you can get the required libraries with: 
``apt-get install zlib1g-dev libdigest-sha-perl libssl-dev``.
(The entity install script will attempt to do this with sudo, 
but that may fail if sudo access is not available.) 


<a name="database"></a>
Database
--------

### MySQL

MySQL is one of the most popular relational databases.
Brooklyn supports setting up individual MySQL nodes with arbitrary configuration,
which may be used to create multiple nodes using back-up and synchronization processes as desired.
(If certain patterns for configuring multiple nodes become popular, these could be
added as Brooklyn entities.)  


### Apache Derby

*This entity is in the sandbox.* 

Brooklyn supports Apache Derby, a pure-Java SQL database. For setting up an instance of a server see ``DerbySetup``.


<a name="nosql"></a>
NoSQL
-----

*The NoSQL entities may not be complete.* 

### Redis

Redis is a distributed key-value store, supporting master/slave replication of a store as a clustered cache. This gives
a series of read-only slaves and a single read-write master, which propagates to the slaves with eventual consistency.


### MongoDB


### Cassandra


### CouchBase


<a name="messaging"></a>
Messaging
---------

### Qpid


Qpid support provides a JMS broker, running over AMQP. This exposes JMS queues and topics as entities as well.
See ``QpidSetup`` for instantiating a broker.

### ActiveMQ


ActiveMQ support provides a JMS broker. This exposes JMS queues and topics as entities as well. See ``ActiveMQSetup`` for
instantiating a broker.

### RabbitMQ


<a name="downstream-projects"></a>
Downstream Projects
-------------------

Downstream projects include those below.

### Apache Whirr

https://github.com/brooklyncentral/brooklyn-whirr

Whirr allows running a variety of services on cloud providers and on localhost. This is done by providing a ``recipe`` which describes what services to launch. You can find an example of how Brooklyn integrates with Whirr [here](/use/examples/whirrhadoop/index.html#custom-whirr-recipe).

### OpenShift

https://github.com/cloudsoft/brooklyn-openshift

### CloudFoundry

https://github.com/cloudsoft/brooklyn-cloudfoundry and https://github.com/cloudsoft/brooklyn-cloudfoundry

### MPI

https://github.com/cloudsoft/brooklyn-openmpi

### Waratek

https://github.com/cloudsoft/brooklyn-waratek

### MapR

https://github.com/cloudsoft/brooklyn-mapr

### Cloudera CDH

https://github.com/cloudsoft/brooklyn-cdh

### Drupal and Wordpress

https://github.com/cloudsoft/brooklyn-social-apps
