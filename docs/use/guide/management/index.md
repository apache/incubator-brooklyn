---
title: Runtime Management
layout: page
toc: ../guide_toc.json
categories: [use, guide]

---
<a name="introduction"></a>
brooklyn uses many of the ideas from autonomic computing to implement management of entities in a structured and reusable fashion (including provisioning, 
healing, and optimising).

Each external system, process or service is represented as an entity within brooklyn, with collections of these being represented and
managed by other entities, and so forth through a hierarchy rooted in entities referred to as "Applications". Each entity has:

- provisioning and tear-down logic for the external system(s) it represents
- sensors which it publishes to report on its state and activity
- effectors which can be invoked to change it
- policies which perform analysis, enrichment (sensors), and execution (effectors). It is the policies in brooklyn which
  perform the self-management (in autonomic terms) by monitoring sensors and invoking effectors.

There are several ways to launch applications with brooklyn, and useful configuration options,
as well as a debug-view web console.
This chapter describes these high-level runtime concepts, then proceeds to present more
detailed information on the underlying implementation of management within brooklyn.


<a name="startup-config"></a>
Startup Configuration
---------------------

brooklyn can read configuration from a variety of places:

* the file ``~/.brooklyn/brooklyn.properties``
* ``-D`` defines on the brooklyn (java) command-line
* shell environment variables

Default properties are described in the Javadoc and code of the class ``BrooklynProperties``,
but some of the most common ones are:
 
{% highlight properties %}
brooklyn.jclouds.aws-ec2.identity=AKA50M30N3S1DFR0MAW55
brooklyn.jclouds.aws-ec2.credential=aT0Ps3cr3tC0D3wh1chAW5w1llG1V3y0uTOus333
brooklyn.jclouds.aws-ec2.privateKeyFile=~/path/to/id_rsa       # use specified key (default is ~/.ssh/id_rsa)
brooklyn.jclouds.aws-ec2.publicKeyFile=~/path/to/id_rsa.pub    # (optional, inferred from previous if omitted)
{% endhighlight %} 

These can be specified as a shell environment variable or as a Java system property,
although in those contexts the conventional format ``BROOKLYN_JCLOUDS_AWS_EC2_IDENTITY`` 
is supported and recommended. 



<a name="cli"></a>
Command Line Interface
----------------------

Brooklyn comes with a Command Line Interface (cli) that makes it easier to launch an application.

In order to have easy access to the cli it is useful to configure the PATH environment variable to also point to the cli's bin directory:

{% highlight bash %}
BROOKLYN_HOME=/path/to/brooklyn/
export PATH=$PATH:$BROOKLYN_HOME/usage/dist/target/brooklyn-dist/bin/
{% endhighlight %}

If you have set this up correctly you should be able to invoke the ```brooklyn``` command:

{% highlight bash %}
brooklyn
{% endhighlight %}

To get information about all the supported cli options just run:

{% highlight bash %}
brooklyn help
usage: brooklyn [(-q | --quiet)] [(-v | --verbose)] <command> [<args>]

The most commonly used brooklyn commands are:
    help     Display help information about brooklyn
    launch   Starts a brooklyn application. Note that a BROOKLYN_CLASSPATH environment variable needs to be set up beforehand to point to the user application classpath.

See 'brooklyn help <command>' for more information on a specific command.
{% endhighlight %}

Here is an example of the commands you might run to get the Brooklyn code, compile it and launch an application:

{% highlight bash %}
git clone https://github.com/brooklyncentral/brooklyn.git
cd brooklyn
mvn clean install -DskipTests
BROOKLYN_HOME=$(pwd)
export PATH=${PATH}:${BROOKLYN_HOME}/usage/dist/target/brooklyn-dist/bin/
export BROOKLYN_CLASSPATH=${BROOKLYN_HOME}/examples/simple-web-cluster/target/classes
brooklyn launch --app brooklyn.demo.SingleWebServerExample --location localhost
{% endhighlight %}



<a name="console"></a>
Management Web Console
----------------------

The web-based management console that comes with brooklyn serves as a way to observe and manage brooklyn entities.
It provides low-level details of activity (including stack-traces), sensor values, and policies,
and some visual widgets for observing what is happening.
This console is not designed as a management dashboard or portal -- 
many good options exist in that space --
but what could be useful is to embed widgets from the brooklyn portal for selected high-level views.

<!-- FIXME Update to use new construction pattern, rather than calling app's constructor -->
To start a management console from your own code, use ``BrooklynLaucher.launch``:
{% highlight java %}
public static void main(String[] argv) {
	Application app = new MyApplicationExample(displayName:"myapp")
    BrooklynServerDetails server = BrooklynLauncher.newLauncher()
            .managing(app)
            .launch();
	// ...
}
{% endhighlight %}

This will start an embedded brooklyn management node, including the web console.
The URL for the web console defaults to http://localhost:8081,
with credentials admin/password.

The mechanism for launching brooklyn management will change in a future release. For this release, the brooklyn management node is embedded.

The console contains two main views: Dashboard and Detail. These update in real-time.

**Dashboard**

The dashboard is a high level overview of the state of the application:

[![Screenshot of the Webconsole Dashboard](webconsole-dashboard-w400.png "Screenshot of the Webconsole Dashboard")](webconsole-dashboard.png)


**Detail**

The Detail view gives an in-depth view of the application and its entities. 
Child-parent relationships between entities are navigable using the entity tree;
each entity is shown with its children (or, in the case of childless group entities, the members). 
Selecting a specific entity allows you to access detailed information about that entity.

[![Screenshot of the Webconsole Detail](webconsole-detail-w400.png "Screenshot of the Webconsole Detail")](webconsole-detail.png)

The Detail view contains a breadcrumb trail, showing the current entitiy's position in the heirarchy, and six tabs:

**Summary:** Description of the selected entity.

**Sensors:** Lists the attribute sensors that the entity has and their values.

**Effectors:** Lists the effectors that can be invoked on the selected entity.

**Activity:** Current and historic activity of the entity, currently running effectors, finished effectors.

**Location:** The geographical location of the selected entity.

**Policies:** Lists the policies associated with the current entity. Policies can be suspended, resumed and removed through the UI.

### Security

Security providers can be configured by specifying `brooklyn.webconsole.security.provider` equal 
to the name of a class implementing `SecurityProvider`.
An implementation of this could point to Spring, LDAP, OpenID or another identity management system.

The default implementation, `ExplicitUsersSecurityProvider`, reads from a list of users and passwords
which should be specified as configuration parameters e.g. in `brooklyn.properties`.
This configuration could look like:

{% highlight properties %}
brooklyn.webconsole.security.explicit.users=admin
brooklyn.webconsole.security.explicit.user.admin=password
{% endhighlight %}

The `users` line should contain a comma-separated list. The special value `*` is accepted to permit all users.
If no values are specified at all the above setting (`admin`/`password`) is used by default.

If not using the web console, you should specify
`brooklyn.webconsole.security.provider=brooklyn.rest.security.provider.BlackholeSecurityProvider` to prevent inadvertant logins.
During dev/test you can specify `brooklyn.webconsole.security.provider=brooklyn.rest.security.provider.AnyoneSecurityProvider`
to allow logins with no credentials. 

 

<a name="observation-other"></a>
Other Ways to Observe Activity
------------------------------

### Java API

``ManagementContext`` provides a Java programmatic API. 

More information can be found in the javadoc for ``ManagementContext``.

### Command-line Console

*Not available yet.*

### Management REST API

Brooklyn does not yet expose a REST API. This was to allow the APi to be designed to align with emerging standards.

Selected management operations are possible remotely using links exposed by the GUI (after authenticating with the GUI).

### Logging

Brooklyn uses the SLF4J logging facade,  which allows use of many popular frameworks including java.util.logging, log4j, and logback.

The convention for log levels is as follows:
* ERROR and above:  exceptional situations which indicate that something has unexpectedly failed or
some other problem has occured which the user is expected to attend to
* WARN:  exceptional situations which the user may which to know about but which do not necessarily indicate failure or require a response
* INFO:  a synopsis of activity, but which should not generate large volumes of events nor overwhelm a human observer
* DEBUG and lower:  detail of activity which is not normally of interest, but which might merit closer inspection under certain circumstances.

Loggers follow the ``package.ClassName`` naming standard.  

It is possible for entities to emit logging activity sensors so that an operator can observe what is occurring within their application through the web console or via programmatic means.

Examples for testing can be found in some of the poms.


<!---

<a name="distributed-management"></a>
Distributed Management
----------------------

TODO Describe how and when objects become "live", pushed out to other nodes.
-->

<!---

<a name="resilience"></a>
Resilience
----------
TODO
*This section still needs to be written. Feel free to [fork]({{site.url}}/dev/code) the docs and lend a hand.*
-->


<a name="key-apis"></a>
Key APIs
--------
<!---
TODO - brief overview of key APIs
-->
*This section still needs to be written. Feel free to [fork]({{site.url}}/dev/code) the docs and lend a hand.*

- ``ManagementContext`` (Java management API)
- ``EntityLocal`` (used by policies)


<!---
TODO - describe how to simply configure logging slf4j
-->
<a name="sensors-and-effectors"></a>
Sensors and Effectors
---------------------

### Sensors

Sensors are typically defined as static named fields on the Entity subclass. These define the channels of events and activity that interested parties can track remotely. For example:
{% highlight java %}
/** a sensor for saying hi (illustrative), carrying a String value 
	which is typically the name of the person to whom we are saying hi */
public static final Sensor<String> HELLO_SENSOR = ...

{% endhighlight %}

If the entity is local (e.g. to a policy) these can be looked up using ``get(Sensor)``. If it may be remote, you can subscribe to it through various APIs.

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

Sensor values are defined as statics, which can be used to programmatically drive the subscription.
<!---
TODO , etc., example
-->

### SubscriptionManager

*This section is not complete. Feel free to [fork]({{site.url}}/dev/code) the docs and lend a hand.*

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

Invocation returns a ``Task`` object (extending ``Future``). This allows the caller to understand progress and errors on the task, as well as ``Task.get()`` the return value. Be aware that ``task.get()`` is a blocking function that will wait until a value is available before returning.

The management framework ensures that execution occurs on the machine where the ``Entity`` is mastered, with progress, result, and/or any errors reported back to the caller. It does this through the ``ExecutionManager`` which, where necessary, creates proxy ``Task`` instances. The ``ExecutionManager`` associates ``Tasks`` with the corresponding ``Entity`` so that these can be tracked externally (and relocated if the Entity is remastered to a different location).

It is worth noting that where a method corresponds to an effector, direct invocation of that method on an ``Entity`` will implicitly generate the ``Task`` object as though the effector had been invoked. For example, invoking ``Cluster.resize(int)``, where ``resize`` provides an ``Effector RESIZE``, will generate a ``Task`` which can be observed remotely.

### ExecutionManager

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

It is possible to define ParallelTasks and SequentialTasks and to specify inter-task relationships with TaskPreprocessors. This allows building quite sophisticated workflows relatively easily. 

Continuing the example above, submitting a SequentialTasks or specifying ``em.setTaskPreprocessorForTag("a", SingleThreadedExecution.class)`` will cause ``Two Mississippi`` to run after ``One Mississippi`` completes.

For more information consult the javadoc on these classes and associated tests.

Note that it is currently necessary to prune dead tasks, either periodically or by the caller. By default they are kept around for reference. It is expected that an enhancement in a future release will allow pruning completed and failed tasks after a specified amount of time.
