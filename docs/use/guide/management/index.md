---
title: How Management Works
layout: page
toc: ../guide_toc.json
categories: [use, guide]

---

brooklyn uses many of the ideas from autonomic computing to implement management of entities in a structured and reusable fashion (including provisioning, 
healing, and optimising).

Each external system, process or service is represented as an entity within brooklyn, with collections of these being represented and
managed by other entities, and so forth through a hierarchy rooted in entities referred to as "Applications". Each entity has:

- provisioning and tear-down logic for the external system(s) it represents
- sensors which it publishes to report on its state and activity
- effectors which can be invoked to change it
- policies which perform analysis, enrichment (sensors), and execution (effectors). It is the policies in brooklyn which
  perform the self-management (in autonomic terms) by monitoring sensors and invoking effectors.


The following recommendations should be considered when designing policies:

**policies should be small and composable**

e.g. one policy which takes a sensor and emits a different, enriched sensor, and a second policy which responds to the enriched sensor of the first 	(e.g. a policy detects a process is maxed out and emits a TOO_HOT sensor; a second policy responds to this by scaling up the VM where it is running, requesting more CPU)
	
**management should take place as "low" as possible in the hierarchy**

ideally management should take run as a policy on the relevant entity

**where a policy cannot resolve a situation at an entity, the issue should be escalated to a manager with a compatible policy**

Typically escalation will go to the entity owner, and then cascade up.
e.g. if the earlier VM CPU cannot be increased, the TOO_HOT event may go to the owner, a cluster entity, which attempts to balance. If the cluster cannot balance, then to another policy which attempts to scale out the cluster, and should the cluster be unable to scale, to a third policy which emits TOO_HOT for the cluster.
	
**management escalation should be carefully designed so that policies are not incompatible**

Best practices for this include:
    - place management responsibility in policies at the entity, as much as possible
    - place escalated management responsibility at the owner entity. Where this is impractical, perhaps because two aspects of an entity are best handled in two different places, ensure that the separation of responsibilities is documented and there is a group membership relationship between secondary/aspect managers.
    - order policies carefully, and mark sensors as "handled" (or potentially "swallow" them locally), so that subsequent policies and owner entities do not take superfluous (or contradictory) corrective action
      
For this milestone release, some of the mechanisms for implementing the above practices are still being developed.

<a name="distributed-management" />
Distributed Management
----------------------

<!---
TODO Describe how and when objects become "live", pushed out to other nodes.
-->

*This section is not available in this milestone release.*

<a name="resilience" />
Resilience
----------
<!---
TODO
-->
*This section is not available in this milestone release.*

<a name="key-apis" />
Key APIs
--------
<!---
TODO - brief overview of
-->
*This section is not complete in this milestone release.*

- ``ManagementContext`` (Java management API)
- ``EntityLocal`` (used by policies)

<a name="observation" />
Observing What is Happening
---------------------------

### Management Web Console

brooklyn comes with a web based management console that can be started using BrooklynLaucher:
{% highlight java %}
public static void main(String\[\] argv) {
	application app = new MyApplicationExample(displayName:"myapp")
	brooklyn.launcher.BrooklynLauncher.manage(app)
	// ...
}
{% endhighlight %}

This will start an embedded brooklyn management node, including the web console.
The URL for the web console defaults to http://localhost:8081.

The mechanism for launching brooklyn management will change in a future release. For this milestone release, the brooklyn management node is embedded.

The brooklyn Management Console serves as a way to track and manage brooklyn
entities. It contains two main views: Dashboard and Details.

**Dashboard**

The dashboard is a high level overview of the state of the application:

[![Screenshot of the Webconsole Dashboard](webconsole-dashboard-w400.png "Screenshot of the Webconsole Dashboard")](webconsole-dashboard.png)


**Details**

The details view gives an in depth view of the application and its entities. Child/parent relationships between the entities are navigable using the entity tree. Selecting a specific entity, allows you to access detailed information about that entity.

[![Screenshot of the Webconsole Detail](webconsole-detail-w400.png "Screenshot of the Webconsole Detail")](webconsole-detail.png)

**Summary:** Description of the selected entity.

**Sensors:** Lists the attribute sensors that the entity has and their values.

**Effectors:** Lists the effectors that can be invoked on the selected entity.

**Activity:** Current and historic activity of the entity, currently running effectors, finished effectors.

**Location:** The geographical location of the selected entity.

**Policies:** Lists the policies associated with the current entity. Policies can be suspended, resumed and removed through the UI.

### Security


In this milestone release only two Spring Security users are created: user and admin.

In future releases it will be possible to add and configure users. 

*admin access* (username:admin, password:password).

*user access* (username:user, password:password).

Only the **admin** user has access to the Management Console.

<a name="observation-other" />
Other Ways to Observe Activity
------------------------------

### Java API

``ManagementContext`` provides a Java programmatic API. 

More information can be found in the javadoc for ``ManagementContext``.

### Command-line Console

*Not available yet.*

### Management REST API

*Not available yet.*

### Logging

*This section is in development at the time of this milestone release.*

Logging uses slf4j. Add the appropriate maven slf4j implementation dependency and logging config file.

Examples for testing can be found in some of the poms.

<!---
TODO - describe how to simply configure logging slf4j
-->
<a name="sensors-and-effectors" />
Sensors and Effectors
---------------------

### Sensors

Sensors are typically defined as static named fields on the Entity subclass. These define the channels of events and activity that interested parties can track remotely. For example:
{% highlight java %}
/** a sensor for saying hi (illustrative), carrying a String value 
	which is typically the name of the person to whom we are saying hi */
public static final Sensor<String> HELLO_SENSOR = ...

{% endhighlight %}

If the entity is local (e.g. to a policy) these can be looked up using get(Sensor). If it may be remote, you can subscribe to it through various APIs.

<!---
TODO probably say about events now, quick reference about SubscriptionManager (either here or in next section on management context)
TODO remaining section on Sensors perhaps should be moved to Writing Entities section? as reader won't need to know too much detail of sensor types to understand policies... though perhaps saying some is right. (Talking about JSON is almost certainly overkill...)
-->

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

Sensor values are defined as statics which can be used to programmatically drive the subscription.
<!---
TODO , etc., example
-->

### SubscriptionManager

*This section is is progress at the time of this milestone release.*

*See the* ``SubscriptionManager`` *class.*
<!---
TODO
-->

### Effectors

Like sensors and config info, effectors are also static fields on the Entity class. These describe actions available on the entity, similar to methods. Their implementation includes details of how to invoke them, typically this is done by calling a method on the entity. Effectors are typically defined as follows:

{% highlight java %}
/** an effector which returns no value,
	but which causes the entity to emit a HELLO sensor event */
public static Effector<Void> SAY_HI = ...

{% endhighlight %}

Effectors are invoked by calling ``invoke(SAY_HI, name:"Bob")`` or similar. The method may take an entity if context is not clear, and it takes parameters as named parameters or a Map.

Invocation returns a ``Task`` object (extending ``Future``) allowing the caller to understand progress and errors on the task, as well as ``Task.get()`` the
return value (blocking).

The management framework ensures that execution occurs on the machine where the ``Entity`` is mastered, with progress, result, and/or any errors reported back to the caller. It does this through the ``ExecutionManager`` which, where necessary, creates proxy ``Task`` instances. The ``ExecutionManager`` associates ``Tasks`` with the corresponding ``Entity`` so that these can be tracked externally (and relocated if the Entity is remastered to a different location).

It is worth noting that, where a method corresponds to an effector, direct invocation of that method on an ``Entity`` will implicitly generate the ``Task`` object as though the effector had been invoked. For example, invoking ``Cluster.resize(int)``, where ``resize`` provides an ``Effector RESIZE``, will generate a Task which can be observed remotely.

The execution framework that provides this functionality is independent of brooklyn, although it was developed for brooklyn.

### ExecutionManager

The ``ExecutionManager`` is responsible for tracking simultaneous executing tasks and associating these with given **tags**.
Arbitrary tasks can be run by calling ``Task submit(Runnable)`` (similarly to the standard ``Executor``, although it also supports ``Callable`` arguments including Groovy closures, and can even be passed ``Task`` instances which have not been started). ``submit`` also accepts a few other named parameters, including ``description``, which allow additional metadata to be kept on the ``Task``. The main benefit then is to have rich metadata for executing tasks, which can be inspected through methods on the ``Task`` interface.

By using the ``tag`` or ``tags`` named parameters on ``submit`` (or setting ``tags`` in a ``Task`` that is submitted), execution can be associated with various categories. This allows easy viewing can be examined by calling
``ExecutionManager.getTasksWithTag(...)``.

In this example uses Groovy, with time delays abused for readability. brooklyn's test cases check this using mutexes, which is recommended.
	
	ExecutionManager em = []
	em.submit(tag:"a", description:"1-a", { Thread.sleep(1000) })
	em.submit(tags:["a","b"], description:"2-a+b", { Thread.sleep(1000) })
	assert em.getTasksWithTag("a").size()==2
	assert em.getTasksWithTag("a").every { Task t -> !t.isDone() }
	Thread.sleep(1500)
	assert em.getTasksWithTag("a").size()==2
	assert em.getTasksWithTag("a").every { Task t -> t.isDone() }
	
Note that it is currently necessary to prune dead tasks, either periodically or by the caller. By default they are kept around for reference. It is expected that an enhancement in a future release will allow pruning completed/failed tasks after a specified amount of time.

It is possible to define ParallelTasks and SequentialTasks and to specify inter-task relationships with TaskPreprocessors - e.g. either submitting a SequentialTasks or specifying ``em.setTaskPreprocessorForTag("a", SingleThreadedExecution.class)`` will cause ``2-a+b`` to run after ``1-a``
completes. This allows building quite sophisticated workflows relatively easily. For more information consult the javadoc on these classes and associated tests.

<a name="writing-policies" />
Writing Policies
----------------

*This section is not complete in this milestone release.*

- Policies often run periodically or on sensor events
- Policies can add subscriptions to sensors on any entity (although usually it will be its related entity, those entities it owns, and/or those entities which are members) 
- Policies may invoke effectors (management policies) or simply generate new attributes or events (enricher policies).

### Implementation Classes

*This section is not complete in this milestone release.*

- extend ``AbstractPolicy``, or override an existing policy
