---
title: Advanced Concepts
layout: page
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

Lifecycle and ManagementContext
-------------------------------

Under-the-covers, at heart of the brooklyn management plane is the ``ManagementContext``. 
This is started automatically when using launching an application using the brooklyn CLI. For programmatic use, see 
``BrooklynLauncher.newLauncher().launch()``.

A Brooklyn deployment consists of many entities in a hierarchical tree, with the privileged *application* entity at the top level.

An application entity (``Application`` class) is responsible for starting the deployment of all its child entities (i.e. the entire entity tree under its ownership).

An ``Application``'s ``start()`` method begins provisioning the child entities of the application (and their entities, recursively). 

Provisioning of entities typically happens in parallel automatically,
although this can be customized. This is implemented as ***tasks*** which are tracked by the management plane and is visible in the [web-based management console]({{site.url}}/use/guide/management/index.html#console).

Customized provisioning can be useful where two starting entities depend on each other. For example, it is often necessary to delay start of one entity until another entity reaches a certain state, and to supply run-time information about the latter to the former.

<!-- TODO ambiguous language; need a better description of the "manage" lifecycle -->
When new entities are created, the entity is wired up to an application by giving it a parent. The entity is then explicitly "managed", which allows other entities to discover it.

Typically a Brooklyn deployment has a single management context which records:

*   all entities under management that are reachable by the application(s) via the parent-child relationships,
*	the state associated with each entity,
*	subscribers (listeners) to sensor events arising from the entities,
*	active tasks (jobs) associated with any the entity,
*	which Brooklyn management node is mastering (managing) each entity.

<!-- TODO Distributed brooklyn not yet supported; needs clarification in docs -->

In a multi-location deployment, management operates in all regions, with brooklyn entity instances being mastered in the relevant region.

When management is distributed a Brooklyn deployment may consist of multiple Brooklyn management nodes each with a ``ManagementContext`` instance.

<!-- TODO - Clarify the following statements.
The management context entity forms part of the management plane. 
The management plane is responsible for the distribution of the ``Entity`` instances across multiple machines and multiple locations, 
tracking the transfer of events (subscriptions) between ``Entity`` instances, and the execution of tasks (often initiated by management policies).
-->

<a name="dependent"></a>
Dependent Configuration
-----------------------

Under the covers Brooklyn has a sophisticated sensor event and subscription model, but conveniences around this model make it very simple to express cross-entity dependencies. Consider the example where Tomcat instances need to know the URL of a database (or a set of URLs to connect to a Monterey processing fabric, or other entities)

{% highlight java %}
setConfiguration(UsesJava.JAVA_OPTIONS, ImmutableMap.of("mysql.url", 
	    attributeWhenReady(mysql, MySqlNode.MY_SQL_URL) ))
{% endhighlight %}

The ``attributeWhenReady(Entity, Sensor)`` call (a static method on the class ``DependentConfiguration``)
causes the configuration value to be set when that given entity's attribue is ready. 
In the example, ``attributeWhenReady()`` causes the JVM system property ``mysql.url`` to be set to the value of the ``MySqlNode.MY_SQL_URL`` sensor from ``mysql`` when that value is ready. As soon as the database URL is announced by the MySql entity, the configuration value will be available to the Tomcat cluster. 

By default "ready" means being *set* (non-null) and, if appropriate, *non-empty* (for collections and strings) or *non-zero* (for numbers). Formally the interpretation of ready is that of "Groovy truth" defined by an ``asBoolean()`` method on the class and in the Groovy language extensions. 

You can customize "readiness" by supplying a ``Predicate`` (Google common) or ``Closure`` (Groovy) in a third parameter. 
This evaluates candidate values reported by the sensor until one is found to be ``true``. 
For example, passing ``{ it.size()>=3 }`` as the readiness argument would require at least three management plane URLs.

More information on this can be found in the javadoc for ``DependentConfiguration``,
along with a few other methods such as ``valueWhenAttributeReady`` which allow post-processing of an attribute value.

Note that if the value of ``CONFIG_KEY`` passed to ``Entity.getConfig`` is a Closure or Task (such as returned by ``attributeWhenReady``),
the first access of ``Entity.getConfig(CONFIG_KEY)`` will block until the task completes.
Typically this does the right thing, blocking when necessary to generate the right start-up sequence
without the developer having to think through the order, but it can take some getting used to.
Be careful not to request config information until really necessary (or to use non-blocking "raw" mechanisms),
and in complicated situations be ready to attend to circular dependencies.
The management console gives useful information for understanding what is happening and resolving the cycle.

Location
--------
<!-- TODO, Clarify is how geographical location works.
-->

Entities can be provisioned/started in the location of your choice. Brooklyn transparently uses [jclouds](http://www.jclouds.org) to support different cloud providers and to support BYON (Bring Your Own Nodes). 

The implementation of an entity (e.g. Tomcat) is agnostic about where it will be installed/started. When writing the application definition specify the location or list of possible locations (``Location`` instances) for hosting the entity.

``Location`` instances represent where they run and indicate how that location (resource or service) can be accessed.

For example, a ``JBoss7Server`` will usually be running in an ``SshMachineLocation``, which contains the credentials and address for sshing to the machine. A cluster of such servers may be running in a ``MachineProvisioningLocation``, capable of creating new ``SshMachineLocation`` instances as required.

<!-- TODO, incorporate the following.

The idea is that you could specify the location as AWS and also supply an image id. You could configure the Tomcat entity accordingly: specify the path if the image already has Tomcat installed, or specify that Tomcat must be downloaded/installed. Entities typically use _drivers_ (such as SSH-based) to install, start, and interact with their corresponding real-world instance. 
-->

Policies
--------
Policies perform the active management enabled by Brooklyn. Entities can have zero or more ``Policy`` instances attached to them. 

Policies can subscribe to sensors from entities or run periodically, and
when they run they can perform calculations, look up other values, and if deemed necessary invoke effectors or emit sensor values from the entity with which they are associated.

Execution
---------

All processing, whether an effector invocation or a policy cycle, are tracked as ***tasks***. This allows several important capabilities:

*	active and historic processing can be observed by operators
*	the invocation context is available in the thread, to check entitlement (permissions) and maintain a
hierarchical causal chain even when operations are run in parallel
*	processing can be managed across multiple management nodes

Some executions create new entities, which can then have tasks associated with them, and the system will record, for example, that a start efector on the new entity is a task associated with that entity, with that task
created by a task associated with a different entity.

The execution of a typical overall start-up sequence is shown below:

[![Brooklyn Flow Diagram](brooklyn-flow-websequencediagrams.com-w400.png "Brooklyn Flow Diagram" )](brooklyn-flow-websequencediagrams.com.png)


## Integration

One vital aspect of Brooklyn is its ability to communicate with the systems it starts. This is abstracted using a ***driver*** facility in Brooklyn, where a
driver describes how a process or service can be installed and managed using a particular technology.

For example, a ``TomcatServer`` may implement start and other effectors using a ``TomcatSshDriver`` which inherits from ``JavaSoftwareProcessSshDriver`` (for JVM and JMX start confguration), inheriting from ``AbstractSoftwareProcessSshDriver``
(for SSH scripting support).

Particularly for sensors, some technologies are used so frequently that they are
packaged as ***feeds*** which can discover their configuration (including from drivers). These include JMX and HTTP (see ``JmxFeed`` and ``HttpFeed``).

Brooklyn comes with entity implementations for a growing number of commonly used systems, including various web application servers, databases and NoSQL data stores, and messaging systems. See: [Extras]({{site.url}}/use/guide/extras/index.html).


