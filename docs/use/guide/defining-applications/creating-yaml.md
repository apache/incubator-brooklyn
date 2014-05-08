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

You can also specify pre-existing servers to use, as a global pool or specific to a service.
Both are shown here (with the definition at the service being used in this case):

{% highlight yaml %}
{% readj example_yaml/simple-appserver-with-location-byon.yaml %}
{% endhighlight %}

You'll also note in this example that we've used JSON-style notation in the second `location` block.
YAML supports this, and sometimes that makes more readable plans.
(Although in this case a simple `location: localhost` is equivalent and even more succinct, but this is a tutorial.)

For more information see the Locations section of the [YAML reference](yaml-reference.html) 
and in the [User's Guide]({{ site.url }}/use/guide/locations/),
and the [template brooklyn.properties](/use/guide/quickstart/brooklyn.properties). 

### VM Configuration

TODO


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
