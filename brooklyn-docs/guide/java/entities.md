---
title: Custom Entity Development
layout: website-normal
---

This section details how to create new custom application components or groups as brooklyn entities.

The Entity Lifecycle
--------------------

- Importance of serialization, ref to How mananagement works
- Parents and Membership (groups)

What to Extend -- Implementation Classes
----------------------------------------

- entity implementation class hierarchy

  - `SoftwareProcess` as the main starting point for base entities (corresponding to software processes),
    and subclasses such as `VanillaJavaApp`
  - `DynamicCluster` (multiple instances of the same entity in a location) and 
    `DynamicFabric` (clusters in multiple location) for automatically creating many instances,
    supplied with an `EntityFactory` (e.g. `BaseEntityFactory`) in the `factory` flag
  - `AbstractGroup` for collecting entities which are parented elsewhere in the hierachy
  - `AbstractEntity` if nothing else fits
  
- traits (mixins, otherwise known as interfaces with statics) to define available config keys, sensors, and effectors;
    and conveniences e.g. ``StartableMethods.{start,stop}`` is useful for entities which implement ``Startable``

- the ``Entities`` class provides some generic convenience methods; worth looking at it for any work you do

A common lifecycle pattern is that the ``start`` effector (see more on effectors below) is invoked, 
often delegating either to a driver (for software processes) or children entities (for clusters etc).


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

- Validation of config values can be applied by supplying a ``Predicate`` to the ``constraint`` of a ConfigKey builder.
  Constraints are tested after an entity is initialised and before an entity managed.
  Useful predicates include:
  - ``StringPredicates.isNonBlank``: require that a String key is neither null nor empty.
  - ``ResourcePredicates.urlExists``: require that a URL that is loadable by Brooklyn. Use this to
    confirm that necessary resources are available to the entity.
  - ``Predicates.in``: require one of a fixed set of values.
  - ``Predicates.containsPattern``: require that a value match a regular expression pattern.

  An important caveat is that only constraints on config keys that are on an entity's type hierarchy can be
  tested automatically. Brooklyn has no knowledge of the true type of other keys until they are retrieved with a
  ``config().get(key)``.


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

Implementing Effectors
----------------------

The ``Entity`` interface defines the sensors and effectors available. The entity class provides 
wiring for the sensors, and the effector implementations. In simple cases it may be straightforward 
to capture the behaviour of the effectors in a simple methods.
For example deploying a WAR to a cluster can be done as follows:

*This section is not complete. Feel free to [fork]({{site.path.guide}}/dev/code) the docs and lend a hand.*

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

Testing
-------

* Unit tests can make use of `SimulatedLocation` and `TestEntity`, and can extend `BrooklynAppUnitTestSupport`.
* Integration tests and use a `LocalhostMachineProvisioningLocation`, and can also extend `BrooklynAppUnitTestSupport`.


<a name="SoftwareProcess-lifecycle"></a>

SoftwareProcess Lifecycle
-------------------------

`SoftwareProcess` is the common super-type of most integration components (when implementing in Java).

See ``JBoss7Server`` and ``MySqlNode`` for exemplars.

The methods called in a `SoftwareProcess` entity's lifecycle are described below. The most important steps are shown in bold (when writing a new entity, these are the methods most often implemented).

* Initial creation (via `EntitySpec` or YAML):
  * **no-arg constructor**
  * **init**
  * add locations
  * apply initializers
  * add enrichers
  * add policies
  * add children
  * manages entity (so is discoverable by other entities)

* Start:
  * provisions new machine, if the location is a `MachineProvisioningLocation`
  * creates new driver
    * **calls `getDriverInterface`**
    * Infers the concrete driver class from the machine-type, 
      e.g. by default it adds "Ssh" before the word "Driver" in "JBoss7Driver".
    * instantiates the driver, **calling the constructor** to pass in the entity itself and the machine location
  * sets attributes from config (e.g. for ports being used)
  * calls `entity.preStart()`
  * calls `driver.start()`, which:
    * runs pre-install command (see config key `pre.install.command`)
    * uploads install resources (see config keys `files.install` and `templates.install`)
    * **calls `driver.install()`**
    * runs post-install command (see config key `post.install.command`)
    * **calls `driver.customize()`**
    * uploads runtime resources (see config keys `files.runtime` and `templates.runtime`)
    * runs pre-launch command (see config key `pre.launch.command`)
    * **calls `driver.launch()`**
    * runs post-launch command (see config key `post.launch.command`)
    * calls `driver.postLaunch()`
  * calls `entity.postDriverStart()`, which:
    * calls `enity.waitForEntityStart()` - **waits for `driver.isRunning()` to report true**
  * **calls `entity.connectSensors()`**
  * calls `entity.waitForServicUp()`
  * calls `entity.postStart()`

* Restart:
  * If restarting machine...
    * calls `entity.stop()`, with `stopMachine` set to true.
    * calls start
    * restarts children (if configured to do so)
  * Else (i.e. not restarting machine)...
    * calls `entity.preRestart()`
    * calls `driver.restart()`
      * **calls `driver.stop()`**
      * **calls `driver.launch()`**
      * calls `driver.postLaunch()`
    * restarts children (if configured to do so)
    * calls `entity.postDriverStart()`, which:
      * calls `enity.waitForEntityStart()` - **polls `driver.isRunning()`**, waiting for true
    * calls `entity.waitForServicUp()`
    * calls `entity.postStart()`

* Stop:
  * calls `entity.preStopConfirmCustom()` - aborts if exception.
  * calls `entity.preStop()`
  * stops the process:
    * stops children (if configured to do so)
    * **calls `driver.stop()`**
  * stops the machine (if configured to do so)
  * calls `entity.postStop()`

* Rebind (i.e. when Brooklyn is restarted):
  * **no-arg constructor**
  * reconstitutes entity (e.g. setting config and attributes)
  * If entity was running...
    * calls `entity.rebind()`; if previously started then:
      * creates the driver (same steps as for start)
      * calls `driver.rebind()`
      * **calls `entity.connectSensors()`**
  * attaches policies, enrichers and persisted feeds
  * manages the entity (so is discoverable by other entities)
