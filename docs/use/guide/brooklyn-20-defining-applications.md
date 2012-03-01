---
title: Introduction
layout: page
toc: guide_toc.json
---

This introduces brooklyn and describes how it simplifies the deployment and management of big applications. It is
intended for people who are using brooklyn-supported application components (such as web/app servers, data stores)
to be able to use brooklyn to easily start their application in multiple locations with off-the-shelf management
policies.

The Basic Concepts - Entities and How to Use Them
-------------------------------------------------

At the heart of brooklyn is the concept of an **entity**. Some entities correspond to actual machines or processes such
as a ``JBossNode`` or a ``RabbitMqNode``, others correspond to a group of other entities, such as a ``TomcatCluster`` or
``MultiLocationMySqlTier``. Entities can be grouped or composed to form other entities, and are code so they
can be extended, overridden, and modified. Their main responsibilities are:

- Provisioning the entity in the given location of locations
- Holding configuration and state (attributes) for the entity
- Reporting monitoring data (sensors) about the status of the entity
- Exposing operations (effectors) that can be performed on the entity
- Hosting management policies and tasks related to the entity

Entities, Application, Ownership and Membership
-----------------------------------------------

All entities have an owner entity, which creates and manages it, with two exceptions: applications and templates. Top-level
``Application`` entities are created and managed externally, perhaps by a script. **Templates** are the other exception, and are covered in more detail later.

A **Group** is an entity to which other entities are memembers. Membership can be used for whatever purposes the application definer wishes. A typical use-case is to manage a collection of
entities together for one purpose (e.g. wide-area load-balancing between locations) even though they may have been
created by different **owners** (e.g. a multi-tier stack within a location).

Lifecycle and ManagementContext
-------------------------------

An ``Application Entity`` defines the ``ManagementContext`` instance and is responsible for starting the deployment of the entire entity tree under its ownership. Only ``Application Entity`` can define the ``ManagementContext``.

The management context entity forms part of the management plane. The management plane is responsible for the distribution of the ``Entity`` instances across multiple machines and multiple locations, tracking the transfer of events (subscriptions) between ``Entity`` instances, and the execution of tasks (often initiated by management policies).

An ``Application Entity`` provides a ``start()`` method which begins provisioning the management plane and distributing the management of entities owned by the application (and their entities, recursively). In a multi-location deployment, management operates in all regions, with brooklyn entity instances 
being mastered in the relevant region.

Provisioning of entities typically happens in parallel automatically,
although this can be customized. This is implemented as ``Tasks`` which are tracked by the management plane and is visible in the management console.

Customizing provisioning can be useful where two starting entities depend on each other. For example, it is often necessary to delay start of one entity until another entity reaches a certain state, and to supply run-time information about the latter to the former.

For new entities joining an existing network, the entity is deployed to the management plane when it is wired in to an application i.e. by giving it an owner. Templates for new entities are also deployed to the management plane in the same manner.

Configuration, Sensors and Effectors
------------------------------------

All entities contain a map of config information. This can contain arbitrary values, typically keyed under static ``ConfigKey`` fields on the ``Entity`` sub-class. These values are inherited, so setting a configuration value at the
application will make it available in all entities underneath unless it is overridden.

Configuration is propagated when an application "goes live" (i.e. its ``deploy()`` or ``start()`` method is invoked), so config values must be set before this occurs. 

Configuration values can be specified in a configuration file (``~/.brooklyn/brooklyn.properties``)
to apply universally, and programmatically to a specific entity and its descendants using the ``entity.setConfig(KEY, VALUE)``
method. 
Many common configuration parameters are available as "flags" which can be supplied in the entity's constructor of the form ``new MyEntity(owner, config1: "value1", config2: "value2")``. 
Documentation of the flags available for individual constructors can be found in the javadocs, or by inspecting ``@SetFromFlag`` annotations on the ``ConfigKey`` definitions. 

**Sensors** (activity information and notifications) and **Effectors** (operations that can be invoked on the entity), are defined by entities as static fields on the ``Entity`` subclass. Sensors can be updated by the entity or associated tasks, and sensors from an entity can be subscribed to by its owner or other entities to track changes in an entity's activity. Effectors, can be invoked by an entity's owner remotely, and the invoker is able to track the execution of that effector. Effectors can be invoked by other entities, but use this functionality with care to prevent too many managers!

Entities are Java classes and data can also be stored in internal fields.
This data will not be inherited and will not be externally visible (and resilience is more limited), but the data will be moved when an entity's master location is changed. (See discussion of management below.)


Dependent Configuration
-----------------------

Under the covers brooklyn has a sophisticated sensor event and subscription model, but conveniences around this model make it very simple to express  cross-entity dependencies. Consider the example where Tomcat instances need to know a set of URLs to connect to a Monterey processing fabric (or a database tier or other entities)
::

    tomcat.webCluster.template.setConfig(JavaEntity.JVM_PROPERTY("monterey.urls"),
               attributeWhenReady(monterey, Monterey.MGMT_PLANE_URLS) )


The ``attributeWhenReady(Entity, Sensor)`` call causes the configuration value to be set when that given entity's attribue is ready. 
In the example, ``attributeWhenReady()`` causes the JVM system property ``monterey.urls`` to be set to the value of the ``Monterey.MGMT_PLANE_URLS`` sensor from ``monterey`` when that value is ready. As soon as a management plane URL is announced by the Monterey entity, the configuration value will be available to the Tomcat cluster. 

By default "ready" means being *set* (non-null) and, if appropriate, *non-empty* (for collections and strings) or *non-zero* (for numbers). Formally the interpretation of ready is that of "Groovy truth" defined by an ``asBoolean()`` method on the class and in the Groovy language extensions. 

You can customize "readiness" by supplying a ``Predicate`` (Google common) or ``Closure`` (Groovy) in a third parameter. This evaluates candidate values reported by the sensor until one is found to be ``true``. For
example, passing ``it.size()>=3`` as the readiness argument is useful if you require three management plane URLs.

.. TODO Is this a duplicate thought? You can transform the attribute value with a Function (Google) or Closure to set the config to something different.

More information can be found in the javadoc for ``DependentConfiguration``.

Note that ``Entity.getConfig(KEY)`` will block when it is used. Typically
this does the right thing, blocking only when necessary without the developer having to think through explicit start-up phases, but it can take some getting used to.

You should be careful not to request config information until really necessary (or to use internal non-blocking "raw" mechanisms). Be ready in complicated situations to attend to circular dependencies. The management console gives sufficient information to understand what is happening and identify what is blocking.

Location
--------

Entities can be provisioned/started in the location of your choice. brooklyn transparently uses jclouds to support different cloud providers and to support BYON (Bring Your Own Nodes). 

The implementation of an entity (e.g. Tomcat) is agnostic about where it will be installed/started. When writing the application definition, specify the location (or list of possible locations) for hosting the entity.

The idea is that you could specify the location as AWS and also supply an image id. You could configure the Tomcat entity accordingly: specify the path if the image already has Tomcat installed, or specify that Tomcat must be downloaded/installed. Entities typically use _drivers_ (such as SSH-based) to install, start, and interact with their corresponding real-world instance. 

Common Usage
------------

Entity Class Hierarchy
......................

By convention in brooklyn the following words have a particular meaning, both as types (which extend Group, which extends Entity) and when used as words in other entities (such as TomcatFabric):

- Tier - anything which is homogeneous (has a template and type)

    - Cluster - in-location tier
    - Fabric - multi-location tier

- Stack - heterogeneous (mixed types of children)

- Application - user's entry point

.. TODO

- **template** entities are often used by groups to define how to instantiate themselves and scale-out.
  A template is an entity which does not have an owner and which is not an application.

- **traits** (mixins) providing certain capabilities, such as Resizable and Balanceable

- Resizable

- Balanceable / Moveable / MoveableWithCost

Off-the-Shelf Entities
......................

brooklyn includes a selection of entities already available for use in applications,
including appropriate sensors and effectors, and in some cases include Cluster and Fabric variants.
(These are also useful as templates for writing new entities.)
 
These include:

- Web: Tomcat, JBoss; nginx; GeoScaling; cluster and fabric
- Relational databases: MySQL, Derby
- NoSQL: Infinispan, Redis, GemFire
- Messaging: ActiveMQ, Qpid

For a full list see |chapter:systems available|.


Off-the-Shelf Policies
......................

Policies are highly reusable as their inputs, thresholds and targets are customizable.

- Resizer Policy
   
   Increases or decreases the size of a Resizable entity based on an aggregate sensor value, the current size of the entity, and customized high/low watermarks.

   A Resizer policy can take any sensor as a metric, have its watermarks tuned live, and target any resizable entity - be it an application server managing how many instances it handles, or a tier managing global capacity.

   e.g. if the average request per second across a cluster of Tomcat servers goes over the high watermark, it will resize the cluster to bring the average back to within the watermarks.
  
.. TODO - list some

.. TODO - describe how they can be customised (briefly mention sensors)



Off-the-Shelf Enrichers
.......................

- Delta - converts absolute sensor values into a delta

- Time-weighted Delta - converts absolute sensor values into a delta/second

- Rolling Mean - converts the last N sensor values into a mean

- Rolling time-window mean - converts the last N seconds of sensor values into a weighted mean

- Custom Aggregating - aggregates multiple sensor values (usually across a tier, esp. a cluster) and 
  performs a supplied aggregation method to them to return an aggregate figure, e.g. sum, mean, median, etc. 

Off-the-Shelf Locations
.......................

- SSH
- Compute: Amazon, GoGrid, vCloud, and many more (using jclouds)

::

    # use a special key when connecting to public clouds
    brooklyn.jclouds.private-key-file=~/.ssh/public_clouds/id_rsa
    brooklyn.jclouds.localhost.private-key-file=~/.ssh/id_rsa   # need this one for localhost
    brooklyn.jclouds.aws-ec2.identity=ABCDEFGHIJKLMNOPQRST      # AWS credentials
    brooklyn.jclouds.aws-ec2.credential=s3cr3tsq1rr3ls3cr3tsq1rr3ls3cr3tsq1rr3l
    brooklyn.geoscaling.username=cloudsoft                      # credentials for 'geoscaling' service
    brooklyn.geoscaling.password=xxx

These can also be set as environment variables (in the shell) or system properties (java command line).
(There are also ``BROOKLYN_JCLOUDS_PRIVATE_KEY_FILE`` variants accepted.)

For any provider you will typically need to set ``identity`` and ``credential``
in the ``brooklyn.jclouds.provider`` namespace.
Other fields may be available (from brooklyn or jclouds).

``brooklyn.jclouds.public-key-file`` can also be specied but can usually be omitted 
(it will be inferred by adding the suffix ``.pub`` to the private key).
There should be no passphrases on the key files.


Examples
--------

Integrating with a Maven project
................................

If you have a Maven-based project, integrate this XML fragment with your pom.xml:

::

    <dependencies>
        <dependency>
            <groupId>brooklyn</groupId>
            <artifactId>brooklyn-launcher</artifactId>
            <version>0.3.0-SNAPSHOT</version>
            <classifier>with-dependencies</classifier>
        </dependency>
    </dependencies>
     
    <repositories>
        <repository>
            <id>cloudsoft-maven-repository</id>
            <url>http://developer.cloudsoftcorp.com/download/maven2/</url>
        </repository>
    </repositories>

Starting a Tomcat Server
........................

The code below starts a Tomcat server on the local machine.

The ``main`` method defines the application, and passes it to the ``BrooklynLauncher`` to be managed. Here It is then started in a localhost location, but any location could be used including EC2 or GoGrid.

The ``init`` method declares the entities that comprise the app. In this case, it is a single ``tomcat`` instance. 

.. FIXME what init method?

The Tomcat's configuration indicates that the given WAR should be deployed to the Tomcat server when it is started.

.. TODO httpPort: => http: in Alex's docs

::

    class TomcatServerApp extends AbstractApplication {
        def tomcat = new TomcatServer(owner: this, httpPort: 8080, war: "/path/to/booking-mvc.war")
        
        public static void main(String... args) {
            TomcatServerApp demo = new TomcatServerApp(displayName : "tomcat server example")
            BrooklynLauncher.manage(demo)
            demo.start([new LocalhostMachineProvisioningLocation(count: 1)])
        }
    }


The code can be written in pure Java if preferred, using the long-hand syntax of ``tomcat.setConfig(TomcatServer.HTTP_PORT, 80)``
in lieu of the flags. The ``wars`` flag is also supported (with config keys ``ROOT_WAR`` and ``NAMED_WARS`` the long-hand syntax);
they accept EARs and other common archives, and can be described as files or URLs, including a ``classpath://org/acme/resources/xxx.war``
syntax.


Starting a Tomcat Cluster in Amazon EC2
.......................................

The code below starts a tomcat cluster in Amazon EC2:

.. TODO httpPort: => http: in Alex's docs

*In this milestone release, the following snippet should be considered pseudo code as it has not been tested.*

::

    class TomcatClusterApp extends AbstractApplication {
        DynamicWebAppCluster cluster = new DynamicWebAppCluster(
            owner : this,
            initialSize: 2,
            newEntity: { properties -> new TomcatServer(properties) },
            httpPort: 8080, 
            war: "/path/to/booking-mvc.war")
    
        public static void main(String[] argv) {
            TomcatClusterApp demo = new TomcatClusterApp(displayName : "tomcat cluster example")
            BrooklynLauncher.manage(demo)
    
            JcloudsLocationFactory locFactory = new JcloudsLocationFactory([
                        provider : "aws-ec2",
                        identity : "xxxxxxxxxxxxxxxxxxxxxxxxxxx",
                        credential : "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                        sshPrivateKey : new File("/home/bob/.ssh/id_rsa.private"),
                        sshPublicKey : new File("/home/bob/.ssh/id_rsa.pub"),
                        securityGroups:["my-security-group"]
                    ])
    
            JcloudsLocation loc = locFactory.newLocation("us-west-1")
            demo.start([loc])
        }
    }

The ``newEntity`` flag in the cluster constructor indicates how new entities should be created. The WAR configuration set on the cluster is inherited by each of the TomcatServer contained (i.e. "owned") by the cluster.

The ``DynamicWebAppCluster`` is dynamic in that it supports resizing the cluster, adding and removing servers, as managed either manually or by policies embedded in the entity.

The main method creates a ``JcloudsLocationFactory`` with appropriate credentials for the AWS account, along with the
RSA key to used for subsequently logging into the VM. It also specifies the relevant security group which should enable
the 8080 port configured above. Finally, a JcloudsLocation allows to select the Amazon region the cluster will run in.


Starting a Tomcat Cluster with Nginx
....................................

The code below starts a Tomcat cluster along with an Nginx instance, where each Tomcat server in the cluster is registered with the Nginx instance.

.. TODO httpPort: => http: in Alex's docs

::

    class TomcatClusterWithNginxApp extends AbstractApplication {
        NginxController nginxController = new NginxController(
            domain : "brooklyn.geopaas.org",
            port : 8000,
            portNumberSensor : Attributes.HTTP_PORT)
    
        ControlledDynamicWebAppCluster cluster = new ControlledDynamicWebAppCluster(
            owner : this,
            controller : nginxController,
            webServerFactory : { properties -> new TomcatServer(properties) },
            initialSize: 2,
            httpPort: 8080, war: "/path/to/booking-mvc.war")
    
        public static void main(String[] argv) {
            TomcatClusterWithNginxApp demo = new TomcatClusterWithNginxApp(displayName : "tomcat cluster with nginx example")
            BrooklynLauncher.manage(demo)
            
            JcloudsLocationFactory locFactory = new JcloudsLocationFactory([
                        provider : "aws-ec2",
                        identity : "xxxxxxxxxxxxxxxxxxxxxxxxxxx",
                        credential : "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                        sshPrivateKey : new File("/home/bob/.ssh/id_rsa.private"),
                        sshPublicKey : new File("/home/bob/.ssh/id_rsa.pub"),
                        securityGroups:["my-security-group"]
                    ])
    
            JcloudsLocation loc = locFactory.newLocation("us-west-1")
    
            demo.start([loc])
        }
    }

This creates a cluster that of Tomcat servers, along with an Nginx instance. The ``NginxController`` instance
is notified whenever a member of the cluster joins or leaves; the entity is configured to look at the ``HTTP_PORT``
attribute of that instance so that the Nginx configuration can be updated with the ip:port of the cluster member.

The beauty of OO programming, of course, is that classes can be re-used.  The compound entity we've created above is
available off-the-shelf as the ``LoadBalancedWebCluster`` entity, as used in the following example. 


.. TODO things may need tidying (paragraphs, and/or eliminating any extra setConfig calls, though looks like these have gone)


Starting a Multi-location Tomcat Fabric
---------------------------------------

.. TODO this example should use several cloud providers, including Openshift, and use GeoDNS, and maybe a data store and/or messaging service; it is the last "most advanced" example

.. FIXME Discuss above comment with Aled/Alex as it is contentious

.. TODO httpPort: => http: in Alex's docs

::

    class TomcatFabricApp extends AbstractApplication {
        Closure webClusterFactory = { Map flags, Entity owner ->
            Map clusterFlags = flags + [newEntity: { properties -> new TomcatServer(properties) }]
            return new DynamicWebAppCluster(clusterFlags, owner)
        }
    
        DynamicFabric fabric = new DynamicFabric(
                owner : this,
                displayName : "WebFabric",
                displayNamePrefix : "",
                displayNameSuffix : " web cluster",
                initialSize : 2,
                newEntity : webClusterFactory,
                httpPort : 8080, 
                war: "/path/to/booking-mvc.war")
        
        public static void main(String[] argv) {
            TomcatFabricApp demo = new TomcatFabricApp(displayName : "tomcat example")
            BrooklynLauncher.manage(demo)
            
            JcloudsLocationFactory locFactory = new JcloudsLocationFactory([
                        provider : "aws-ec2",
                        identity : "xxxxxxxxxxxxxxxxxxxxxxxxxxx",
                        credential : "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                        sshPrivateKey : new File("/home/bob/.ssh/id_rsa.private"),
                        sshPublicKey : new File("/home/bob/.ssh/id_rsa.pub"),
                        securityGroups:["my-security-group"]
                    ])
    
            JcloudsLocation loc = locFactory.newLocation("us-west-1")
            JcloudsLocation loc2 = locFactory.newLocation("eu-west-1")
            demo.start([loc, loc2])
        }
    }

This creates a web-fabric. When started, this creates a web-cluster in each location supplied.

Examples Source
---------------

The source code for these examples is available for download from GitHub. To retrieve the source, execute the following command::

    git clone git@github.com:cloudsoft/brooklyn-examples.git

You can also `browse the code <https://github.com/cloudsoft/brooklyn-examples>`_ on the web.


