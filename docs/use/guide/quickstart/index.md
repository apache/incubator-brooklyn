---
title: Getting Started
layout: page
toc: ../guide_toc.json
categories: [use, guide]
---

{% include fields.md %}

This guide will walk you through deploying an application to a public cloud, and managing that application.

We will be deploying an example 3-tier web application, described using this blueprint: 

{% highlight yaml %}
name: My Web Cluster
location: localhost

services:

- serviceType: brooklyn.entity.webapp.ControlledDynamicWebAppCluster
  name: My Web
  location: localhost
  brooklyn.config:
    wars.root: http://search.maven.org/remotecontent?filepath=io/brooklyn/example/brooklyn-example-hello-world-sql-webapp/0.6.0-M2/brooklyn-example-hello-world-sql-webapp-0.6.0-M2.war
    java.sysprops: 
      brooklyn.example.db.url: $brooklyn:formatString("jdbc:%s%s?user=%s\\&password=%s",
         component("db").attributeWhenReady("database.url"), "visitors", "brooklyn", "br00k11n")

- serviceType: brooklyn.entity.database.mysql.MySqlNode
  id: db
  name: My DB
  brooklyn.config:
    creationScriptUrl: classpath://visitors-creation-script.sql
{% endhighlight %}

(This is written in YAML, following the [camp specification](https://www.oasis-open.org/committees/camp/). )


## Install Brooklyn

Download the [Brooklyn distribution]({{ this_dist_url_tgz }}) and expand it to your home directory ( `~/` ), or in a location of your choice. (Other [download options]({{site.url}}/start/download.html) are available.)

{% if site.brooklyn-version contains 'SNAPSHOT' %}
Expand the `tar.gz` archive (note: as this is a -SNAPSHOT version, your filename will be slightly different):
{% else %}
Expand the `tar.gz` archive:
{% endif %}

{% if site.brooklyn-version contains 'SNAPSHOT' %}
{% highlight bash %}
$ tar -zxf brooklyn-dist-{{ site.brooklyn-version }}-timestamp-dist.tar.gz
{% endhighlight %}
{% else %}
{% highlight bash %}
$ tar -zxf brooklyn-dist-{{ site.brooklyn-version }}-dist.tar.gz
{% endhighlight %}
{% endif %}

This will create a `brooklyn-{{ site.brooklyn-version }}` folder.

Note: you'll also need Java JRE or SDK installed (version 6 or later).

## Launch Brooklyn

Let's setup some paths for easy commands.

{% highlight bash %}
$ cd brooklyn-{{ site.brooklyn-version }}
$ BROOKLYN_DIR="$(pwd)"
$ export PATH=$PATH:$BROOKLYN_DIR/bin/
{% endhighlight %}

We can do a quick test drive by launching Brooklyn:

{% highlight bash %}
$ brooklyn launch
{% endhighlight %}

Brooklyn will output the address of the management interface:

`... Started Brooklyn console at http://127.0.0.1:8081/` ([link](http://127.0.0.1:8081/))

But before we really use Brooklyn, we need to setup some Locations.
 
Stop Brooklyn with ctrl-c.

## Configuring a Location

Brooklyn deploys applications to Locations. Locations can be clouds, machines with fixed IPs or localhost (for testing).

Brooklyn loads Location configuration  from `~/.brooklyn/brooklyn.properties`. 

Create a `.brooklyn` folder in your home directory:

{% highlight bash %}
$ mkdir ~/.brooklyn
{% endhighlight %}

Download the template [brooklyn.properties](brooklyn.properties)  and place this in `~/.brooklyn`.  

Open the file in a text editor and add your cloud credentials. If you would rather test Brooklyn on localhost, follow [these instructions]({{site.url}}/use/guide/locations/) to ensure that your Brooklyn can access your machine.

Restart Brooklyn:

{% highlight bash %}
$ brooklyn launch
{% endhighlight %}

## Launching an Application

There are several ways to deploy a YAML blueprint:

1. We can supply a blueprint file at startup: `brooklyn launch --app /path/to/myblueprint.yaml`
1. We can deploy using the web-console.
1. We can deploy using the brooklyn REST api.

We will use the second option to deploy a 3-tier web-app, using the YAML file at the top of this page.

On the home page of the Brooklyn web-console, click the "add application" button (if no applications are currently running, this will be opened automatically). Select the YAML tab and paste your YAML code.

### Chose your cloud / location

Edit the yaml to use the location you configured, e.g. replace:
{% highlight yaml %}
location: localhost
{% endhighlight %}

with:
{% highlight yaml %}
location: google-compute-engine:europe-west1-a
{% endhighlight %}

Click "finish". You should see your application listed, with status "STARTING".

## Monitoring and managing applications

In the Brooklyn web-console clicking on an application listed on the home page, or the Applications tab, will show all the applications currently running.

We can explore the management hierarchy of an application, which will show us the entities it is composed of. If you have deployed the above YAML, then you'll see a standard 3-tier web-app. Clicking on the ControlledDynamicWebAppCluster will show if the cluster is ready to serve and, when ready, will provide a web address for the front of the loadbalancer.

If the service.isUp, you can view the demo web application in your browser at the webapp.url.

Through the Activities tab, you can drill into the activities each entity is doing or has recently done. Click on the task to see its details, and to drill into its "Children Tasks". For example, if you drill into MySqlNode's start operation, you can see the "Start (processes)", then "launch", and then the ssh command used including the stdin, stdout and stderr.


## Stopping the application

To stop an application, select the application in the tree view, click on the Effectors tab, and invoke the "Stop" effector. This will cleanly shutdown all components in the application.

### Testing the Policies

Brooklyn at its heart is a policy driven management plane which can implement business and technical policies.

The Web Cluster with DB demo comes pre-configured with an `AutoScalerPolicy`, attached to
the cluster of JBoss7 servers and a `targets` policy attached to the loadbalancer. You can
 observe policies this in the management console using the Policy tab of the relevant
 entity (e.g. `DynamicWebAppCluster` shows the `AutoScalerPolicy`.

The cluster autoscaler policy will automatically scale the cluster up or down to be the
right size for the current load. ('One server' is the minimum size allowed by the policy.)
The loadbalancer will automatically be updated by the targets policy as the cluster size
changes.

Sitting idle, your cluster will only contain one server, but you can check that the policy
works  using a tool like [jmeter](http://jmeter.apache.org/) pointed at the nginx endpoint
to create load on the cluster.

### Next 

The [Elastic Web Cluster Example]({{site.url}}/use/examples/webcluster/index.html) page 
details how to build the demo application from scratch. It shows how Brooklyn can 
complement your application with policy driven management, and how an application can be 
run without using the service catalog.
