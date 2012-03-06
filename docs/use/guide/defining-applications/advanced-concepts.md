---
title: Advanced Concepts
layout: page
toc: ../../toc.json
categories: [use, guide, defining-applications]
---

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


Dependent Configuration
-----------------------

Under the covers brooklyn has a sophisticated sensor event and subscription model, but conveniences around this model make it very simple to express  cross-entity dependencies. Consider the example where Tomcat instances need to know a set of URLs to connect to a Monterey processing fabric (or a database tier or other entities)

{% highlight java %}
tomcat.webCluster.template.setConfig(JavaEntity.JVM_PROPERTY("monterey.urls"),
	attributeWhenReady(monterey, Monterey.MGMT_PLANE_URLS)
)
{% endhighlight %}

The ``attributeWhenReady(Entity, Sensor)`` call causes the configuration value to be set when that given entity's attribue is ready. 
In the example, ``attributeWhenReady()`` causes the JVM system property ``monterey.urls`` to be set to the value of the ``Monterey.MGMT_PLANE_URLS`` sensor from ``monterey`` when that value is ready. As soon as a management plane URL is announced by the Monterey entity, the configuration value will be available to the Tomcat cluster. 

By default "ready" means being *set* (non-null) and, if appropriate, *non-empty* (for collections and strings) or *non-zero* (for numbers). Formally the interpretation of ready is that of "Groovy truth" defined by an ``asBoolean()`` method on the class and in the Groovy language extensions. 

You can customize "readiness" by supplying a ``Predicate`` (Google common) or ``Closure`` (Groovy) in a third parameter. This evaluates candidate values reported by the sensor until one is found to be ``true``. For
example, passing ``it.size()>=3`` as the readiness argument is useful if you require three management plane URLs.

<!---
TODO Is this a duplicate thought? You can transform the attribute value with a Function (Google) or Closure to set the config to something different.
-->

More information can be found in the javadoc for ``DependentConfiguration``.

Note that ``Entity.getConfig(KEY)`` will block when it is used. Typically
this does the right thing, blocking only when necessary without the developer having to think through explicit start-up phases, but it can take some getting used to.

You should be careful not to request config information until really necessary (or to use internal non-blocking "raw" mechanisms). Be ready in complicated situations to attend to circular dependencies. The management console gives sufficient information to understand what is happening and identify what is blocking.

Location
--------

Entities can be provisioned/started in the location of your choice. brooklyn transparently uses jclouds to support different cloud providers and to support BYON (Bring Your Own Nodes). 

The implementation of an entity (e.g. Tomcat) is agnostic about where it will be installed/started. When writing the application definition specify the location (or list of possible locations) for hosting the entity.

The idea is that you could specify the location as AWS and also supply an image id. You could configure the Tomcat entity accordingly: specify the path if the image already has Tomcat installed, or specify that Tomcat must be downloaded/installed. Entities typically use _drivers_ (such as SSH-based) to install, start, and interact with their corresponding real-world instance. 
