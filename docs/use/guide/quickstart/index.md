---
title: Getting Started
layout: page
toc: ../guide_toc.json
categories: [use, guide]
---

{% include fields.md %}

This guide will get you up and running with Brooklyn quickly. You will become familiar with launching Brooklyn from the command line, using the web interface and deploying an application (to a public cloud).

### Before We Start
You are going to need some basic tools (that are normally installed by default). You will need `curl`, `wget`, `tar`, `ssh` and `ssh-keygen`.

### Download Brooklyn

Download the Brooklyn distribution. This contains Brooklyn, ready to run.

Save the [Distro tgz]({{ this_dist_url_tgz }}) file to your home directory `~/`, or a location of your choice. (Other [download options]({{site.url}}/start/download.html) are available.)

Expand the `tar.gz` archive. {% if site.brooklyn-version contains 'SNAPSHOT' %}Each Distro is timestamped, so your filename will be different.{% endif %}

{% if site.brooklyn-version contains 'SNAPSHOT' %}
	$ tar -zxf brooklyn-dist-{{ site.version }}-timestamp-dist.tar.gz
{% else %}
	$ tar -zxf brooklyn-dist-{{ site.version }}-dist.tar.gz
{% endif %}

This will create a `brooklyn-{{ site.version }}` folder.

Let's setup some paths for easy commands.

	$ cd brooklyn-{{ site.version }}
	$ BROOKLYN_HOME=$(pwd)
	$ export PATH=$PATH:$BROOKLYN_HOME/bin/

### A Quick Test Drive

Running Brooklyn now will launch the web interface, but there will be little to do, as we haven't configured any deployment locations or added a service catalog. Check your progress by running:

	$ brooklyn launch

Brooklyn will output the address of the management interface:

`... Started Brooklyn console at http://127.0.0.1:8081/` ([link](http://127.0.0.1:8081/))

Stop Brooklyn with ctrl-c.

### Setting up Locations and Applications

By default Brooklyn loads configuration parameters from `~/.brooklyn/brooklyn.properties` and a service catalog from `~/.brooklyn/catalog.xml`. 

Create a .brooklyn folder:

	$ mkdir ~/.brooklyn

Then download the following default/template files.

### brooklyn.properties
Download the template [brooklyn.properties](brooklyn.properties) (to `~/.brooklyn`).

brooklyn.properties is a standard java .properties file. 

Edit this file (in any text editor) to add credentials for your favorite cloud.

### catalog.xml
Download the template [catalog.xml](catalog.xml) (to `~/.brooklyn`).

catalog.xml is catalog of application or service blueprints. The example file contains two demos which will be automatically downloaded from the web if you run them.

Edit this file (text editor) and check that the links to the demo applications' .jars are valid. At the time of writing these were for version `0.5.0-SNAPSHOT`, but if this has changed you may need to update the links. <!-- BROOKLYN VERSION -->

### SSH Key
If this is a new machine, or you haven't used SSH before, you will need to create keys for Brooklyn to use, and add them your list of authorized_keys (for deployment to localhost). 

	$ ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa
	$ cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys

(Existing SSH keys?: If you are using an existing key SSH that has a password, or is not located at `~/.ssh/id_rsa`, please supply the `brooklyn.location.localhost.privateKeyFile` and `brooklyn.location.localhost.privateKeyPassphrase` in your `brooklyn.properties` file.)

(MacOSx user?: To allow SSH access to localhost please enable 'Remote Login' in System Preferences > Sharing.)

### 3-2-1 Go!

Now when we launch Brooklyn:

	$ brooklyn launch

Brooklyn will use the `brooklyn.properties` and `catalog.xml` files. There will be more locations available in the management interface at [localhost:8081](http://localhost:8081), and the blueprints from the service catalog will be available for deployment.

Click "add application". The Create Application dialog will appear.

Select the "Demo Web Cluster with DB", then "Next".

For the time being we'll use "localhost" as our deployment location. Click "Finish" and the Create Application dialog will close.

You will see Brooklyn create an Application with status "STARTING".

It make take some time for Brooklyn to download everything and configure the application's initial topography, so lets have a look at the web interface while we wait.

### Exploring the Hierarchy of Web Cluster with DB

Clicking on an application listed on the home page, or the Applications tab, will show you the management hierarchy.

Exploring the hierarchy tree, you will see that the Demo Web Cluster with DB is a classic three tier web application, consisting of a `ControlledDynamicWebAppCluster` and a `MySqlNode`. The `ControlledDynamicWebAppCluster` contains an nginx software loadbalancer (`NginxController`) and as many `JBoss7Servers` as required (it autoscales).

Clicking on the `ControlledDynamicWebAppCluster` and then the Sensor tab will show if the cluster is ready to serve and, when ready, will provide a web address for the front of the loadbalancer.

If the `service.isUp`, you can view the demo web application in your browser at the `webapp.url.`

### Testing the Policies

Brooklyn at its heart is a policy driven management plane. After codifying your technical and business policies, Brooklyn can implement them automatically. 

Brooklyn's policies work autonomically: they are like a nervous system. The need for action, and the correct action to take, are  observed, decided and implemented as low down the management hierarchy (as close to the 'problem') as possible.

The Web Cluster with DB demo comes pre-configured with an `AutoScalerPolicy`, attached to the cluster of JBoss7 servers and a `targets` policy attached to the loadbalancer. You can observe policies this in the management console using the Policy tab of the relevant entity (e.g. `DynamicWebAppCluster` shows the `AutoScalerPolicy`. 

The cluster autoscaler policy will automatically scale the cluster up or down to be the right size for the current load. ('One server' is the minimum size allowed by the policy.)
The loadbalancer will automatically be updated by the targets policy as the cluster size changes.

Sitting idle, your cluster will only contain one server, but you can check that the policy works  using a tool like [jmeter](http://jmeter.apache.org/) pointed at the nginx endpoint to create load on the cluster. 

Brooklyn's policies are configurable, customizable, and can be completely bespoke to your needs. As an example, the `AutoScalerPolicy` is tunable, can be applied to any relevant metric (here, average requests per second), and is a sufficiently complex piece of math that it understand hysteresis and prevents thrashing.

### REST API Browser
Click on the Script tab at the top of the web interface and select REST API. Brooklyn supports a REST, JSON and Java API to allow you to integrate it with (pretty much) anything.

The Script tab allows you to explore the API.

Try: Locations > GET:/v1/locations > Try it out!

You will be presented with a json response of the currently configured locations.

### Stopping the Web Cluster with DB
By now the Web Cluster with DB should have started, and you will have been able to view the application in your browser. 

Returning to the "Applications" tab, and selecting the `WebClusterDatabaseExample`'s Effectors, we can Invoke the Stop Effector. This will cleanly shutdown the Web Cluster with DB example.

### Deploying to Cloud
The user experience of using the service catalog to deploy an application to a cloud is exactly the same as deploying to localhost. Brooklyn transparently handles the differences between locations and environments.

Return to the Create Application dialog, reselect the Web Cluster with DB Demo, and select your public cloud of choice.

(If you have added credentials to your brooklyn.properties file,) Brooklyn will request VMs in the public cloud, provision the application components, and wire them together, returning a cloud IP from which the demo application will be available.

Remember to invoke the stop method when you are finished.

### Closing Thoughts
This guide has shown two aspects of Brooklyn in action: policy driven management capability, and the service catalog/web interface. Additionally, we briefly explored Brooklyn's API.

It is worth noting that Brooklyn could be included as a library in your own applications (no command line required), or it could be used just as a management plane (without a service catalog). 

During this guide we have been using Brooklyn's web interface, but Brooklyn's APIs are extensive and powerful. Brooklyn can be used with (controlled by and make data available to) your existing management UI. Brooklyn is intended to complement (not replace) your existing technologies and tooling.

### Next 

The [Elastic Web Cluster Example]({{site.url}}/use/examples/webcluster/index.html) page details how to build the demo application from scratch. It shows how Brooklyn can complement your application with policy driven management, and how an application can be run without using the service catalog.
