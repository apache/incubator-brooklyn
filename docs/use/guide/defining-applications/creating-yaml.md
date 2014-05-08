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

Finally, that clipboard in the corner let's you easily copy-and-paste into the web-console:
simply [download and launch]({{ site.url }}/use/guide/quickstart/) Brooklyn,
then in the "Add Application" dialog at the web console
(usually [http://127.0.0.1:8081/](http://127.0.0.1:8081/). 
There are several other ways to deploy, including `curl` and via the command-line,
and you can configure https and security, and much more, as described [here](deploying-yaml.html).

[![Web Console](web-console-yaml-700.png "YAML via Web Console")](web-console-yaml.png)


### Setting Locations

Brooklyn supports a very wide range of target locations -- localhost is mainly a convenience for testing.
With deep integration to [Apache jclouds](http://jclouds.org), a very wide range of clouds are supported.
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
Both styles are shown here (with the location specified at the service taking priority):

{% highlight yaml %}
{% readj example_yaml/simple-appserver-with-location-byon.yaml %}
{% endhighlight %}

You'll also note in this example that we've used JSON-style notation in the second `location` block.
YAML supports this, and sometimes that makes more readable plans.
(Although in this case a simple `location: localhost` is equivalent and even more succinct, but this is a tutorial.)

For more information see the Locations section of the [YAML reference](yaml-reference.html) 
and in the [User's Guide]({{ site.url }}/use/guide/locations/),
and the [template brooklyn.properties](/use/guide/quickstart/brooklyn.properties). 


## Configuring VMs

Another simple blueprint will just create a VM which you can use, without any software installed upon it:

{% highlight yaml %}
{% readj example_yaml/simple-vm.yaml %}
{% endhighlight %}


**We've omitted the `location` section here and in many of the examples below;
add the appropriate choice when you paste your YAML. Note that `provisioning.properties` will be
ignored if deploying to `localhost` or `byon` fixed-IP machines.** 

This will create a VM with the specified parameters in your choice of cloud.
In the GUI (and in the REST API), the IP address is reported as a [sensor]({{ site.url }}/use/guide/defining-applications/basic-concepts.html).
There are many, many more `provisioning.properties` supported here,
including:

* a `user` to create (if not specified it creates the same username as `brooklyn` is running under) 
* a `password` for him or a `publicKeyFile` and `privateKeyFile` (defaulting to keys in `~/.ssh/id_rsa{.pub,}` and no password)
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

In this case we're calling to the `entitySpec` DSL command which will do type-coercion so that the child entry
is treated as an entity spec when the `memberSpec` is set.
The example above thus gives us 5 VMs identical to the one we created in the previous section.


## A Bigger Blueprint

### Service Configuration

TODO


### Multiple Services and Dependency Injection

TODO


### Clusters and Policies

TODO


## New Custom Entities

### Vanilla Software using `bash`

TODO

TODO building up children entities

TODO adding sensors and effectors


### Using Chef Recipes

TODO


### More Information

Plenty of examples exist in the Brooklyn codebase,
so a good starting point is to [`git clone`]({{ site.url }}/dev/code/index.html) it
and search for `*.yaml` files therein.

You can also come talk to us, on IRC (#brooklyncentral on Freenode) or
any of the usual [hailing frequencies]({{site.url}}/meta/contact.html),
as these documents are a work in progress.
