---
title: Writing YAML Blueprints
layout: page
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

## A First Blueprint

The easiest way to write a blueprint is as a YAML file.
This follows the  <a href="https://www.oasis-open.org/committees/camp/">OASIS CAMP</a> plan specification, 
with some extensions described below.
(A [YAML reference](yaml-reference.html) has more information,
and if the YAML doesn't yet do what you want,
it's easy to add new extensions using your favorite JVM language.)

### The Basic Structure

Here's a very simple YAML blueprint plan, to explain the structure:

{% highlight yaml %}
{% readj example_yaml/simple-appserver.yaml %}
{% endhighlight %}

* The `name` is just for the benefit of us humans.

* The `location` specifies where this should be deployed.
  If you've [set up passwordless localhost SSH access]({{ site.url }}/use/guide/locations/) 
  you can use `localhost` as above, but if not, just wait ten seconds for the next example.
  
* The `services` block takes a list of the typed services we want to deploy.
  This is the meat of the blueprint plan, as you'll see below.

Finally, that clipboard in the corner lets you easily copy-and-paste into the web-console:
simply [download and launch]({{ site.url }}/use/guide/quickstart/) Brooklyn,
then in the "Add Application" dialog at the web console
(usually [http://127.0.0.1:8081/](http://127.0.0.1:8081/). 
There are several other ways to deploy, including `curl` and via the command-line,
and you can configure https and security, and much more, as described [here](deploying-yaml.html).

[![Web Console](web-console-yaml-700.png "YAML via Web Console")](web-console-yaml.png)


### Setting Locations

Brooklyn supports a very wide range of target locations -- localhost is mainly a convenience for testing.
With deep integration to [Apache jclouds](http://jclouds.org), most well-known clouds and cloud platforms are supported.
The following example is for Amazon EC2:

{% highlight yaml %}
{% readj example_yaml/simple-appserver-with-location.yaml %}
{% endhighlight %}

(You'll need to replace the `identity` and `credential` with the 
"Access Key ID" and "Secret Access Key" for your account,
as configured in the [AWS Console](https://console.aws.amazon.com/iam/home?#security_credential).)

Other popular public clouds include `softlayer`, `google-compute-engine`, and `rackspace-cloudservers-us`.
Private cloud systems including `openstack-nova` and `cloudstack` are also supported,
although for these you'll supply an `endpoint: https://9.9.9.9:9999/v2.0/` 
(or `client/api/` in the case of CloudStack) instead of the `region`.

You can also specify pre-existing servers to use -- "bring-your-own-nodes".
These can be a global pool or specific to a service.
Both styles are shown here (though normally only one will be selected,
<!-- TODO see #1377, currently it is *parent* location which is preferred typically --> 
depending on the blueprint):

{% highlight yaml %}
{% readj example_yaml/simple-appserver-with-location-byon.yaml %}
{% endhighlight %}

You'll also note in this example that we've used JSON-style notation in the second `location` block.
YAML supports this, and sometimes that makes more readable plans.
(Although in this case a simple `location: localhost` is equivalent and even more succinct, but this is a tutorial.)

For more information see the Locations section of the [YAML reference](yaml-reference.html) 
and in the [User's Guide]({{ site.url }}/use/guide/locations/).
Another good reference is the [template brooklyn.properties](/use/guide/quickstart/brooklyn.properties),
which if you install in `~/.brooklyn/brooklyn.properties` and edit with your credentials,  
allows you to refer to clouds simply as `location: jclouds:aws-ec2:eu-west-1` or
set up "named locations" you can use as `location: named:my_cloudstack`.


## Configuring VMs

Another simple blueprint will just create a VM which you can use, without any software installed upon it:

{% highlight yaml %}
{% readj example_yaml/simple-vm.yaml %}
{% endhighlight %}


*We've omitted the `location` section here and in many of the examples below;
add the appropriate choice when you paste your YAML. Note that the `provisioning.properties` will be
ignored if deploying to `localhost` or `byon` fixed-IP machines.* 

This will create a VM with the specified parameters in your choice of cloud.
In the GUI (and in the REST API), the entity is called "VM",
and the hostname and IP address(es) are reported as [sensors]({{ site.url }}/use/guide/defining-applications/basic-concepts.html).
There are many more `provisioning.properties` supported here,
including:

* a `user` to create (if not specified it creates the same username as `brooklyn` is running under) 
* a `password` for him or a `publicKeyFile` and `privateKeyFile` (defaulting to keys in `~/.ssh/id_rsa{.pub,}` and no password,
  so if you have keys set up you can immediately ssh in!)
* `machineCreateAttempts` (for dodgy clouds, and they nearly all fail occasionally!) 
* and things like `imageId` and `userMetadata` and disk and networking options (e.g. `autoAssignFloatingIp` for private clouds)

For more information, see the javadoc on `JcloudsLocationConfig`.


### Clusters, Specs, and Composition

What if you want multiple machines?

One way is just to repeat the `- type: brooklyn.entity.basic.EmptySoftwareProcess` block,
but there's another way which will keep your powder [DRY](http://en.wikipedia.org/wiki/Don't_repeat_yourself):

{% highlight yaml %}
{% readj example_yaml/cluster-vm.yaml %}
{% endhighlight %}

Here we've composed the previous blueprint introducing some new important concepts, the `DynamicCluster`
the `$brooklyn` DSL, and the "entity-spec".  Let's unpack these. 

The `DynamicCluster` creates a set of homogeneous instances.
At design-time, you specify an initial size and the specification for the entity it should create.
At runtime you can restart and stop these instances as a group (on the `DynamicCluster`) or refer to them
individually. You can resize the cluster, attach enrichers which aggregate sensors across the cluster, 
and attach policies which, for example, replace failed members or resize the cluster dynamically.

The specification is defined in the `memberSpec` key.  As you can see it looks very much like the
previous blueprint, with one extra line.  Entries in the blueprint which start with `$brooklyn:`
refer to the Brooklyn DSL and allow a small amount of logic to be embedded
(if there's a lot of logic, it's recommended to write a blueprint YAML plugin or write the blueprint itself
as a plugin, in Java or a JVM-supported language).  

In this case we want to indicate that the parameter to `memberSpec` is an entity specification
(`EntitySpec` in the underlying type system); the `entitySpec` DSL command will do this for us.
The example above thus gives us 5 VMs identical to the one we created in the previous section.


## A Bigger Blueprint

We've seen the configuration of machines and how to build up clusters.
Now let's return to our app-server example and explore how more interesting
services can be configured, composed, and combined.

Also note there are some good overview materials [here]({{site.url}}//use/guide/defining-applications/basic-concepts.html)
covering clusters, sensors, effectors and more, 
if you're the kind of person who likes to learn more about concepts before seeing them in action.


### Service Configuration

We'll begin by using more key-value pairs to configure the JBoss server to run a real app:

{% highlight yaml %}
{% readj example_yaml/appserver-configured.yaml %}
{% endhighlight %}

(As before, you'll need to add the `location` info; `localhost` will work for these and subsequent examples.)

When this is deployed, you can see management information in the Brooklyn Web Console,
including a link to the deployed application (downloaded to the target machine from the `hello-world` URL),
running on port 8080.

**Top tip**:  If port 8080 might be in use, you can specify `8080+` to take the first available port >= 8080;
the actual port will be reported as a sensor by Brooklyn.

It's also worth indicating an alternate, more formal syntax.
Not all configuration on entities is supported at the top level of the service specification
(only those which are defined as "flags" in the underlying blueprint,
e.g. the `@SetFromFlag("war")` in the `WebAppServiceConstants` parent of `JBoss7Server`).
All configuration has a formal qualified name, and this can be supplied even where flags or config keys are not
explicitly defined, by placing it into a `brooklyn.config` section:

{% highlight yaml %}
{% readj example_yaml/appserver-configured-in-config.yaml %}
{% endhighlight %}



### Multiple Services and Dependency Injection

If you explored the `hello-world-sql` application we just deployed, 
you'll have noticed it tries to access a database.
And it fails, because we have not set one up.  Let's do that now:

{% highlight yaml %}
{% readj example_yaml/appserver-w-db.yaml %}
{% endhighlight %}

Here there are a few things going on:

* We've added a second service, which will be the database;
  you'll note the database has been configured to run a custom setup script
* We've injected the URL of the second service into the appserver as a Java system property
  (so our app knows where to find the database) 

**Caution: Be careful if you write your YAML in an editor which attempts to put "smart-quotes" in.
All quote characters must be plain ASCII, not fancy left-double-quotes and right-double-quotes!**

There are as many ways to do dependency injection as there are developers,
it sometimes seems; our aim in Brooklyn is not to say this has to be done one way,
but to support the various mechanisms people might need to do, for whatever reasons.
(We each have our opinions about what works well, of course;
the one thing we do want to call out is that being able to dynamically update
the injection is useful in a modern agile application -- so we are definitively **not**
recommending this Java system property approach ... but it is an easy one to demo!)

The way the dependency injection works is again by using the `$brooklyn:` DSL,
this time referring to the `component("db")` (looked up by the `id` field on our DB component),
and then to a sensor emitted by that component.
All the database entities emit a `database.url` sensor when they are up and running;
the `attributeWhenReady` DSL method will store a pointer to that sensor (a Java Future under the covers)
in the Java system properties map which the JBoss entity reads at launch time, blocking if needed.

This means that the deployment occurs in parallel, and if the database comes up first,
there is no blocking; but if the JBoss entity completes its installation and 
downloading the WAR, it will wait for the database before it launches.
At that point the URL is injected, first passing it through `formatString`
to include the credentials for the database (which are defined in the database creation script).



### An Aside: Substitutability

Don't like JBoss?  Is there something about Maria?
One of the modular principles we follow in Brooklyn is substitutability:
in many cases, the config keys, sensors, and effectors are defined
in superclasses and are portable across multiple implementations.

Here's an example deploying the same application but with different flavors of the components:

{% highlight yaml %}
{% readj example_yaml/appserver-w-db-other-flavor.yaml %}
{% endhighlight %}

We've also brought in the `provisioning.properties` from the VM example earlier
so our database has 8GB RAM.
Any of those properties, including `imageId` and `user`, can be defined on a per-entity basis.


### Clusters and Policies

Now let's bring the concept of the "cluster" back in.
We could wrap our appserver in the same `DynamicCluster` we used earlier,
although then we'd need to define and configure the load balancer.
But another blueprint, the `ControlledDynamicWebAppCluster`, does this for us.
It takes the same `memberSpec`, so we can build a fully functional elastic 3-tier
deployment of our `hello-world-sql` application as follows:

{% highlight yaml %}
{% readj example_yaml/appserver-clustered-w-db.yaml %}
{% endhighlight %}

This sets up Nginx as the controller by default, but that can be configured
using the `controllerSpec` key. In fact, JBoss is the default appserver,
and because configuration in Brooklyn is inherited by default,
the same blueprint can be expressed more concisely as:

{% highlight yaml %}
{% readj example_yaml/appserver-clustered-w-db-concise.yaml %}
{% endhighlight %}
 
The other nicety supplied by the `ControlledDynamicWebAppCluster` blueprint is that
it aggregates sensors from the appserver, so we have access to things like
`webapp.reqs.perSec.windowed.perNode`.
These are convenient for plugging in to policies!
We can set up our blueprint to do autoscaling based on requests per second
(keeping it in the range 10..100, with a maximum of 5 appserver nodes)
as follows: 

{% highlight yaml %}
{% readj example_yaml/appserver-w-policy.yaml %}
{% endhighlight %}

Use your favorite load-generation tool (`jmeter` is one good example) to send a huge
volume of requests against the server and see the policies kick in to resize it.


## New Custom Entities

So far we've covered how to configure and compose entities.
There's a large library of blueprints available, but
there are also times when you'll want to write your own.

For complex use cases, you can write JVM, but for many common situations,
some of the highly-configurable blueprints make it easy to write in YAML,
including `bash` and Chef.
 

### Vanilla Software using `bash`

The following blueprint shows how a simple script can be embedded in the YAML
(the `|` character is special YAML which makes it easier to insert multi-line text):

{% highlight yaml %}
{% readj example_yaml/vanilla-bash-netcat.yaml %}
{% endhighlight %}

This starts a simple `nc` listener on port 4321 which will respond `hello` to the first
session which connects to it. Test it by running `telnet localhost 4321`.  

This is just a simple script, but it shows how any script can be easily embedded here,
including a script to download and run other artifacts.
Many artifacts are already packaged such that they can be downloaded and launched 
with a simple script, and `VanillaSoftwareProcess` can also be used for them. 
We can specify a `download.url` which downloads artifacts (unpacking TAR, TGZ, and ZIP archives)
before running `launch.command` relative to where that file is installed (or unpacked),
with `./start.sh` being the default `launch.command`.

So if we create a file `/tmp/netcat-server.tgz` containing just `start.sh` in the root
which consists of the two lines in the previous example,
we can instead write our example as: 

{% highlight yaml %}
{% readj example_yaml/vanilla-bash-netcat-file.yaml %}
{% endhighlight %}

The one requirement of the script is that it store the process ID (PID) in the file
pointed to by `$PID_FILE`, hence the second line of the script.
This is because Brooklyn wants to monitor the services under management. 
(There are other options, as documented on the Javadoc of the `VanillaSoftwareProcess` class.)
And indeed, once you've run one `telnet` to the server, you'll see that the 
service has gone "on fire" in Brooklyn -- because the `nc` process has stopped. 

Besides detecting this failure, Brooklyn policies can be added to the YAML to take appropriate 
action. A simple recovery here might just be to restart the process:

{% highlight yaml %}
{% readj example_yaml/vanilla-bash-netcat-restarter.yaml %}
{% endhighlight %}

Autonomic management in Brooklyn often follows the principle that complex behaviours emerge
from composing simple policies.
The blueprint above uses one policy to triggering a failure sensor when the service is down,
and another responds to such failures by restarting the service.
This makes it easy to configure various aspects, such as to delay to see if the service itself recovers
(which here we've set to 15 seconds) or to bail out on multiple failures within a time window (which again we are not doing).
Running with this blueprint, you'll see that the service shows as on fire for 15s after a `telnet`,
before the policy restarts it. 

This of course is a simple example, but it shows many of the building blocks used in real-world blueprints,
and how often they can be easily described and combined in Brooklyn YAML blueprints.

<!--
TODO building up children entities

TODO adding sensors and effectors
-->

<!--

### Using Chef Recipes

TODO

-->


### More Information

Plenty of examples of blueprints exist in the Brooklyn codebase,
so a good starting point is to [`git clone`]({{ site.url }}/dev/code/index.html) it
and search for `*.yaml` files therein.

Brooklyn lived as a Java framework for many years before we felt confident
to make a declarative front-end, so you can do pretty much anything you want to
by dropping to the JVM. Information on that is available:
* in the [user guide]({{site.url}}/use/guide/entities/index.html),
* through a [Maven archetype]({{site.url}}/use/guide/defining-applications/archetype.html),
* in the [codebase](https://github.com/brooklyncentral/brooklyn),
* and in plenty of [examples]({{site.url}}/use/examples/index.html).

You can also come talk to us, on IRC (#brooklyncentral on Freenode) or
any of the usual [hailing frequencies]({{site.url}}/meta/contact.html),
as these documents are a work in progress.
