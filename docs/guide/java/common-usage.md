---
title: Common Classes and Entities
layout: website-normal
---

<!-- TODO old, needs work (refactoring!) and use of java_link -->

### Entity Class Hierarchy

By convention in Brooklyn the following words have a particular meaning, both as types (which extend ``Group``, which extends ``Entity``) and when used as words in other entities (such as ``TomcatFabric``):

- *Cluster* - a homogeneous collection of entities
- *Fabric* - a multi-location collection of entities, with one per location; often used with a cluster per location
- *Stack* - heterogeneous (mixed types of children)
- *Application* - user's entry point

<!---
TODO
-->

- *entity spec* defines an entity, so that one or more such entities can be created; often used by clusters/groups to define how to instantiate new children.
- *entity factories* are often used by clusters/groups to define how to instantiate new children.
- *traits* (mixins) providing certain capabilities, such as Resizable and Balanceable
- *Resizable* entities can re-sized dynamically, to increase/decrease the number of child entities.
- *Movable* entities can be migrated between *balanceable containers*.
- *Balanceable containers* can contain *movable* entities, where each contained entity is normally associated with
    a piece of work within that container.

### Off-the-Shelf Entities

brooklyn includes a selection of entities already available for use in applications,
including appropriate sensors and effectors, and in some cases include Cluster and Fabric variants.
(These are also useful as templates for writing new entities.)
 
These include:

- **Web**: Tomcat, JBoss, Jetty (external), Play (external); nginx; GeoScaling
- **Data**: MySQL, Redis, MongoDB, Infinispan, GemFire (external)
- **Containers**: Karaf
- **Messaging**: ActiveMQ, Qpid, Rabbit MQ
- **PaaS**: Cloud Foundry, Stackato; OpenShift


### Sensors

Sensors are typically defined as static named fields on the Entity subclass. These define the channels of events and activity that interested parties can track remotely. For example:
{% highlight java %}
/** a sensor for saying hi (illustrative), carrying a String value 
    which is typically the name of the person to whom we are saying hi */
public static final Sensor<String> HELLO_SENSOR = ...

{% endhighlight %}

If the entity is local (e.g. to a policy) these can be looked up using ``get(Sensor)``. If it may be remote, you can subscribe to it through various APIs.

Sensors are used by operators and policies to monitor health and know when to invoke the effectors. The sensor data forms a nested map (i.e. JSON), which can be subscribed to through the ``ManagementContext``.

Often ``Policy`` instances will subscribe to sensor events on their associated entity or its children; these events might be an ``AttributeValueEvent`` – an attribute value being reported on change or periodically – or something transient such as ``LogMessage`` or a custom ``Event`` such as "TOO_HOT".

<!---
TODO check classes above; is this much detail needed here?
-->

Sensor values form a map-of-maps. An example of some simple sensor information is shown below in JSON:
        
    {
      config : {
        url : "jdbc:mysql://ec2-50-17-19-65.compute-1.amazonaws.com:3306/mysql"
        status : "running"
      }
      workrate : {
        msgsPerSec : 432
      }
    }

Sensor values are defined as statics, which can be used to programmatically drive the subscription.

A range of `Feed` instances are available to simplify reading sensor information.


### Effectors

Like sensors and config info, effectors are also static fields on the Entity class. These describe actions available on the entity, similar to methods. Their implementation includes details of how to invoke them, typically this is done by calling a method on the entity. Effectors are typically defined as follows:

{% highlight java %}
/** an effector which returns no value,
    but which causes the entity to emit a HELLO sensor event */
public static Effector<Void> SAY_HI = ...

{% endhighlight %}

Effectors are invoked by calling ``invoke(SAY_HI, name:"Bob")`` or similar. The method may take an entity if context is not clear, and it takes parameters as named parameters or a Map.

Invocation returns a ``Task`` object (extending ``Future``). This allows the caller to understand progress and errors on the task, as well as ``Task.get()`` the return value. Be aware that ``task.get()`` is a blocking function that will wait until a value is available before returning.

The management framework ensures that execution occurs on the machine where the ``Entity`` is mastered, with progress, result, and/or any errors reported back to the caller. It does this through the ``ExecutionManager`` which, where necessary, creates proxy ``Task`` instances. The ``ExecutionManager`` associates ``Tasks`` with the corresponding ``Entity`` so that these can be tracked externally (and relocated if the Entity is remastered to a different location).

It is worth noting that where a method corresponds to an effector, direct invocation of that method on an ``Entity`` will implicitly generate the ``Task`` object as though the effector had been invoked. For example, invoking ``Cluster.resize(int)``, where ``resize`` provides an ``Effector RESIZE``, will generate a ``Task`` which can be observed remotely.


### Tasks and the Execution Manager

The ``ExecutionManager`` is responsible for tracking simultaneous executing tasks and associating these with given **tags**.
Arbitrary tasks can be run by calling ``Task submit(Runnable)`` (similarly to the standard ``Executor``, although it also supports ``Callable`` arguments including Groovy closures, and can even be passed ``Task`` instances which have not been started). ``submit`` also accepts a few other named parameters, including ``description``, which allow additional metadata to be kept on the ``Task``. The main benefit then is to have rich metadata for executing tasks, which can be inspected through methods on the ``Task`` interface.

By using the ``tag`` or ``tags`` named parameters on ``submit`` (or setting ``tags`` in a ``Task`` that is submitted), execution can be associated with various categories. This allows easy viewing can be examined by calling
``ExecutionManager.getTasksWithTag(...)``.

The following example uses Groovy, with time delays abused for readability. brooklyn's test cases check this using mutexes, which is recommended.
    
    ExecutionManager em = []
    em.submit(tag:"a", description:"One Mississippi", { Thread.sleep(1000) })
    em.submit(tags:["a","b"], description:"Two Mississippi", { Thread.sleep(1000) })
    assert em.getTasksWithTag("a").size()==2
    assert em.getTasksWithTag("a").every { Task t -> !t.isDone() }
    Thread.sleep(1500)
    assert em.getTasksWithTag("a").size()==2
    assert em.getTasksWithTag("a").every { Task t -> t.isDone() }

It is possible to define `ParallelTask` and sequential `Task` instancess 
and to specify inter-task relationships with `TaskPreprocessor` instances. 
This allows building quite sophisticated workflows relatively easily.

Continuing the example above, submitting a `SequentialTask` 
or specifying ``em.setTaskPreprocessorForTag("a", SingleThreadedExecution.class)`` 
will cause ``Two Mississippi`` to run after ``One Mississippi`` completes.

It is also possible to register `ScheduledTask` instances which run periodically.

**The `Tasks` factory supplies a number of conveniences including builders to make working with tasks easier
and should be the entry point in most cases.**



### Subscriptions and the Subscription Manager

In addition to scheduled tasks, tasks can triggered by subscriptions on other events including sensors.

To register low-level listeners to events, use the `SubscriptionManager` API.

