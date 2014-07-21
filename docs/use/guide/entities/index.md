---
title: Custom Entity Development
layout: page
toc: ../guide_toc.json
categories: [use, guide, entities]
---

This section details how to create new custom application components or groups as brooklyn entities.

<a name="entity-lifestyle"></a>
The Entity Lifecycle
--------------------

- Importance of serialization, ref to How mananagement works
- Parents and Membership (groups)

<a name="implementation-classes"></a>
What to Extend -- Implementation Classes
----------------------------------------

- entity implementation class hierarchy

  - ``SoftwareProcess`` as the main starting point for base entities (corresponding to software processes),
    and subclasses such as ``VanillaJavaApp``
  - ``DynamicCluster`` (multiple instances of the same entity in a location) and 
    ``DynamicFabric`` (clusters in multiple location) for automatically creating many instances,
    supplied with an ``EntityFactory`` (e.g. ``BaseEntityFactory``) in the ``factory`` flag
  - abstract ``Group`` for collecting entities which are parented elsewhere in the hierachy
  - ``AbstractEntity`` if nothing else fits
  
- traits (mixins, otherwise known as interfaces with statics) to define available config keys, sensors, and effectors;
    and conveniences e.g. ``StartableMethods.{start,stop}`` is useful for entities which implement ``Startable``

- the ``Entities`` class provides some generic convenience methods; worth looking at it for any work you do

A common lifecycle pattern is that the ``start`` effector (see more on effectors below) is invoked, 
often delegating either to a driver (for software processes) or children entities (for clusters etc).

See ``JBoss7Server`` and ``MySqlNode`` for exemplars.


<a name="configuration"></a>
Configuration
-------------
<!---
TODO: why to use config?
-->

- AttributeSensorAndConfigKey fields can be automatically converted for ``SoftwareProcess``. 
  This is done in ``preStart()``. This must be done manually if required for other entities,
  often with ``ConfigToAttributes.apply(this)``.

- Setting ports is a special challenge, and one which the ``AttributeSensorAndConfigKey`` is particularly helpful for,
  cf ``PortAttributeSensorAndConfigKey`` (a subclass),
  causing ports automatically get assigned from a range and compared with the target ``PortSupplied`` location.
  
  Syntax is as described in the PortRange interface. For example, "8080-8099,8800+" will try port 8080, try sequentially through 8099, then try from 8800 until all ports are exhausted.
  
  This is particularly useful on a contended machine (localhost!). Like ordinary configuration, the config is done by the user, and the actual port used is reported back as a sensor on the entity.
 
<a name="implementing-sensors"></a>
Implementing Sensors
--------------------

- e.g. HTTP, JMX

Sensors at base entities are often retrieved by feeds which poll the entity's corresponding instance in the real world.
The ``SoftwareProcess`` provides a good example; by subclassing it and overriding the ``connectSensors()`` method
you could wire some example sensors using the following: 

{% highlight java %}
public void connectSensors() {
	super.connectSensors()
	
    httpFeed = HttpFeed.builder()
            .entity(this)
            .period(200)
            .baseUri(mgmtUrl)
            .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                    .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                    .onError(Functions.constant(false)))
            .poll(new HttpPollConfig<Integer>(REQUEST_COUNT)
                    .onSuccess(HttpValueFunctions.jsonContents("requestCount", Integer.class)))
            .build();
}
    
@Override
protected void disconnectSensors() {
    super.disconnectSensors();
    if (httpFeed != null) httpFeed.stop();
}
{% endhighlight %}

In this example (a simplified version of ``JBoss7Server``), the url returns metrics in JSON. 
We report the entity as up if we get back an http response code of 200, or down if any other response code or exception.
We retrieve the request count from the response body, and convert it to an integer.

Note the first line (``super.connectSensors()``); as one descends into specific convenience subclasses (such as for Java web-apps), the work done by the parent class's overridden methods may be relevant, and will want to be invoked or even added to a resulting list.

For some sensors, and often at compound entities, the values are obtained by monitoring values of other sensors on the same (in the case of a rolling average) or different (in the case of the average of children nodes) entities. This is achieved by policies, described below.

<a name="implementing-effectors"></a>
Implementing Effectors
----------------------

The ``Entity`` interface defines the sensors and effectors available. The entity class provides 
wiring for the sensors, and the effector implementations. In simple cases it may be straightforward 
to capture the behaviour of the effectors in a simple methods.
For example deploying a WAR to a cluster can be done as follows:

*This section is not complete. Feel free to [fork]({{site.url}}/dev/code) the docs and lend a hand.*

<!---
TODO show an effector which recurses across children
-->

For some entities, specifically base entities, the implementation of effectors might need other tools (such as SSH), and may vary by location, so having a single implementation is not appropriate.

The problem of multiple inheritance (e.g. SSH functionality and entity inheritance) and multiple implementations (e.g. SSH versus Windows) is handled in brooklyn using delegates called _drivers_. 

In the implementations of ``JavaWebApp`` entities, the behaviour which the entity always does is captured in the entity class (for example, breaking deployment of multiple WARs into atomic actions), whereas implementations which is specific to a particular entity and driver (e.g. using scp to copy the WARs to the right place and install them, which of course is different among appservers, or using an HTTP or JMX management API, again where details vary between appservers) is captured in a driver class.

Routines which are convenient for specific drivers can then be inherited in the driver class hierarchy. For example, when passing JMX environment variables to Java over SSH, ``JavaSoftwareProcessSshDriver`` extends ``AbstractSoftwareProcessSshDriver`` and parents ``JBoss7SshDriver``.

<!---
TODO more drivers such as jmx, etc are planned
-->

<a name="testing"></a>
Testing
-------

* Run in a mock ``SimulatedLocation``, defining new metaclass methods to be able to start there and assert the correct behaviour when that is invoked
