---
title: Examples
layout: page
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

** TODO: this examples page is deprecated;
code is out-of-date, and better examples are described on the web site.
need to figure out if this page should be kept at all
(indeed if the "guide" is even still relevant)**


### Integrating with a Maven project

If you have a Maven-based project, integrate this XML fragment with your pom.xml:

<!-- TODO this should import from the downloads page -->

{% highlight xml %}
<dependencies>
	<dependency>
		<groupId>io.brooklyn</groupId>
		<artifactId>brooklyn-all</artifactId>
		<version>0.5.0-SNAPSHOT</version>  <!-- BROOKLYN_VERSION -->
	</dependency>
</dependencies>
 
<repository>
    <id>cloudsoft-releases</id>
    <url>http://developers.cloudsoftcorp.com/download/maven2/</url>
</repository>
<repository>
    <id>libs-snapshot-local</id>
    <url>http://ccweb.cloudsoftcorp.com/maven/libs-snapshot-local/</url>
    <snapshots>
        <enabled>true</enabled>
        <updatePolicy>never</updatePolicy>
        <checksumPolicy>fail</checksumPolicy>
    </snapshots>
</repository>
{% endhighlight %}


### Starting a Tomcat Server

The code below starts a Tomcat server on the local machine.

The ``main`` method defines the application, and passes it to the ``BrooklynLauncher`` to be managed. 
It is then started in a localhost location (other locations are shown in the next section).

The Tomcat's configuration indicates that the given WAR should be deployed to the Tomcat server when it is started.

{% highlight java %}
{% readj example_files/tomcat_simple.java %}
{% endhighlight %}

The ``wars`` config is also supported (with config keys ``ROOT_WAR`` and ``NAMED_WARS`` the long-hand syntax);
they accept EARs and other common archives, and can be described as files or as URLs (as Strings), 
with URLs supporting an optional ``classpath://org/acme/resources/xxx.war`` syntax.


### Starting Tomcat in Amazon EC2

To start a tomcat node or cluster in Amazon EC2, the application is identical to that for localhost. 
The only difference is the location supplied.

The Brooklyn CLI can be used to launch the application in your choice of location, such as:

{% highlight bash %}
brooklyn launch --app TomcatServerApp --location localhost
brooklyn launch --app TomcatServerApp --location aws-ec2:eu-west-1
{% endhighlight %}

 
### Starting a Tomcat Cluster with Nginx

The code below starts a Tomcat cluster along with an Nginx instance, where each Tomcat server in the cluster is registered with the Nginx instance.

{% highlight java %}
{% readj example_files/tomcat_nginx.java %}
{% endhighlight %}

This creates a cluster that of Tomcat servers, along with an Nginx instance. The ``NginxController`` instance
is notified whenever a member of the cluster joins or leaves; the entity is configured to look at the ``HTTP_PORT``
attribute of that instance so that the Nginx configuration can be updated with the ip:port of the cluster member.

<!---
TODO things may need tidying (paragraphs, and/or eliminating any extra setConfig calls, though looks like these have gone)
-->


Starting a Multi-location Tomcat Fabric
---------------------------------------

<!---
TODO this example should use several cloud providers, including Openshift, and use GeoDNS, 
and maybe a data store and/or messaging service; it is the last "most advanced" example
-->

<!---
FIXME Discuss above comment with Aled/Alex as it is contentious
-->

The ``ControlledDynamicWebAppCluster`` entity used above can also be used with a DynamicFabric to start
a web-cluster in each location.

{% highlight java %}
{% readj example_files/tomcat_multi-location.java %}
{% endhighlight %}


Examples Source
---------------

Source code for (more up-to-date!) examples is available for download from GitHub. To retrieve the source, execute the following command:

    git clone git@github.com:brooklyncentral/brooklyn-examples.git

You can also [browse the code](https://github.com/brooklyncentral/brooklyn-examples) on the web.
