---
title: Getting Started
layout: page
toc: ../guide_toc.json
categories: [use, guide]
---

{% include fields.md %}

This guide will walk you through deploying an application to a public cloud.

We will be deploying an example 3-tier web application, described using this blueprint: 

{% highlight yaml %}
{% readj my-web-cluster.yaml %}
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

Note: You'll need a Java JRE or SDK installed (version 6 or later), as Brooklyn is Java under the covers.

## Launch Brooklyn

Let's setup some paths for easy commands.

(Click the clipboard on these code snippets for easier c&p.)

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

    INFO  Starting brooklyn web-console on loopback interface because no security config is set
    INFO  Started Brooklyn console at http://127.0.0.1:8081/, running classpath://brooklyn.war and []

But before we really use Brooklyn, we need to configure security and locations.
In particular, if you are running on a remote server, you'll notice that Brooklyn has
bound only to the loopback device, not a public network interface, and you won't be able
to access the server. This is for security reasons; you can override by passing `-b 0.0.0.0`,
or once you have configured security it will automatically bind to all interfaces by default.
 
Stop Brooklyn with ctrl-c, or in the web-browser (under "Help"), or with `kill`.
(The PID is written to a file in the directory where you launch.) 


<a name="properties"></a>
## Configuring Brooklyn with brooklyn.properties

Brooklyn reads startup configuration from a file `~/.brooklyn/brooklyn.properties`, by default.
You can create this from a template [brooklyn.properties](brooklyn.properties) file which you edit;
this file can be installed with:

{% highlight bash %}
$ mkdir ~/.brooklyn
$ cd ~/.brooklyn
$ wget {{site.url}}/use/guide/quickstart/brooklyn.properties
{% endhighlight %}

Open this file in a text editor and follow the instructions there. The main settings are summarized below:

<a name="security"></a>
## Configuring Security

To configure Brooklyn to run on a public IP address, security should be enabled.
The simplest way is to define a user and password in `~/.brooklyn/brooklyn.properties`
(described above): 

    brooklyn.webconsole.security.users=admin
    brooklyn.webconsole.security.user.admin.password=s3cr3t

Other modes, including LDAP, are described in this file.

The other common setting is to run under https (on port 8443 by default):

    brooklyn.webconsole.security.https.required=true

These settings require a restart to Brooklyn.


<a name="location"></a>
## Configuring a Location

Brooklyn deploys applications to ``locations''. These locations
can be clouds, machines with fixed IPs or localhost (for testing).
Their configuration can be specified in `~/.brooklyn/brooklyn.properties` (described above),
and then these locations can be easily selected within Brooklyn.
Alternatively this information can be specified in the YAML when applications are deployed,
without needing to set it in `brooklyn.properties`.

Some sample settings for this are:

    brooklyn.location.jclouds.aws-ec2.identity = AKA_YOUR_ACCESS_KEY_ID
    brooklyn.location.jclouds.aws-ec2.credential = <access-key-hex-digits>
    brooklyn.location.named.aws-california = jclouds:aws-ec2:us-west-1
    brooklyn.location.named.aws-california.displayName = AWS US West 1 (CA)

    brooklyn.location.jclouds.softlayer.identity = username
    brooklyn.location.jclouds.softlayer.credential = <private-key-hex-digits>
    brooklyn.location.named.softlayer-dal05 = jclouds:softlayer:dal05
    brooklyn.location.named.softlayer-dal05.displayName = Softlayer Dallas

If you want to test Brooklyn on localhost, follow [these instructions]({{site.url}}/use/guide/locations/) 
to ensure that your Brooklyn can access your machine.

Once updated, restart Brooklyn (or reload the properties within the web GUI).


## Launching an Application in Brooklyn

There are several ways to deploy a YAML blueprint (including specifying the blueprint on the command line or submitting it via the REST API).

For now, we will simply copy-and-paste the raw YAML blueprint into the web console.

Open the web console ([127.0.0.1:8081](http://127.0.0.1:8081)). As Brooklyn is not currently managing any applications the 'Create Application' dialog opens automatically. Select the YAML tab.

![Brooklyn web console, showing the YAML tab of the Add Application dialog.](images/add-application-modal-yaml.png)


### Chose your Cloud / Location

Edit the 'location' parameter in the YAML template (repeated below) to use the location you configured.

For example, replace:
{% highlight yaml %}
location: location
{% endhighlight %}

with (one of):
{% highlight yaml %}
location: aws-ec2:us-east-1
location: rackspace-cloudservers-us:ORD
location: google-compute-engine:europe-west1-a
location: localhost
{% endhighlight %}

**My Web Cluster Blueprint**

{% highlight yaml %}
{% readj my-web-cluster.yaml %}
{% endhighlight %}

Paste the modified YAML into the dialog and click 'Finish'.
The dialog will close and Brooklyn will begin deploying your application.

Your application will be shown as 'Starting' on the web console's front page.

![My Web Cluster is STARTING.](images/my-web-cluster-starting.png)


## Monitoring and Managing Applications

Click on the application name, or open the Applications tab.

We can explore the management hierarchy of the application, which will show us the entities it is composed of.

 * My Web Cluster (A `BasicApplication`)
     * My DB (A `MySqlNode`)
     * My Web (A `ControlledDynamicWebAppCluster`)
        * Cluster of JBoss7 Servers (A `DynamicWebAppCluster`)
        * NginxController (An `NginxController`)



Clicking on the 'My Web' entity will show the Summary tab. Here we can see if the cluster is ready to serve and, when ready, grab the web address for the front of the loadbalancer.

![Exploring My Web.](images/my-web.png)


The Activity tab allows us to drill down into what activities each entity is currently doing or has recently done. It is possible to drill down to all child tasks, and view the commands issued, and any errors or warnings that occured.

Drill into the 'My DB' start operation. Working down through  'Start (processes)', then 'launch', we can discover the ssh command used including the stdin, stdout and stderr.

[![My DB Activities.](images/my-db-activities.png)](images/my-db-activities-large.png)


## Stopping the Application

To stop an application, select the application in the tree view (the top/root entity), click on the Effectors tab, and invoke the 'Stop' effector. This will cleanly shutdown all components in the application and return any cloud machines that were being used.

[![My DB Activities.](images/my-web-cluster-stop-confirm.png)](images/my-web-cluster-stop-confirm-large.png)


### Next 

So far we have touched on Brooklyn's ability to *deploy* an application blueprint to a cloud provider, but this a very small part of Brooklyn's capabilities!

Brooklyn's real power is in using Policies to automatically *manage* applications. There is also the (very useful) ability to store a catalog of application blueprints, ready to go.

[Getting Started - Policies and Catalogs](policies-and-catalogs.html)
