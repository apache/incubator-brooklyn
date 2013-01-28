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
		<version>0.5.0-M1</version>  <!-- BROOKLYN_VERSION -->
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
{% readj example_files/tomcat_simple.groovy %}
{% endhighlight %}

While this is written in Groovy, the code can be written in pure Java if preferred, 
using the long-hand syntax of ``tomcat.setConfig(TomcatServer.HTTP_PORT, 80)``
in lieu of the flags (named arguments in ``flagKey: value`` notation).

The ``wars`` flag is also supported (with config keys ``ROOT_WAR`` and ``NAMED_WARS`` the long-hand syntax);
they accept EARs and other common archives, and can be described as files or as URLs (as Strings), 
with URLs supporting an optional ``classpath://org/acme/resources/xxx.war`` syntax.


### Starting a Tomcat Cluster in Amazon EC2

The code below starts a tomcat cluster in Amazon EC2:

*In this release, the following snippet should be considered pseudo code.*

{% highlight java %}
{% readj example_files/tomcat_EC2.groovy %}
{% endhighlight %}

The ``newEntity`` flag in the cluster constructor indicates how new entities should be created. The WAR configuration set on the cluster is inherited by each of the TomcatServer contained by (i.e. "children of") the cluster.

The ``DynamicWebAppCluster`` is dynamic in that it supports resizing the cluster, adding and removing servers, as managed either manually or by policies embedded in the entity.

The main method creates a ``JcloudsLocationFactory`` with appropriate credentials for the AWS account, along with the
RSA key to used for subsequently logging into the VM. It also specifies the relevant security group which should enable
the 8080 port configured above. Finally, a JcloudsLocation allows to select the Amazon region the cluster will run in.


### Starting a Tomcat Cluster with Nginx

The code below starts a Tomcat cluster along with an Nginx instance, where each Tomcat server in the cluster is registered with the Nginx instance.

{% highlight java %}
{% readj example_files/tomcat_nginx.groovy %}
{% endhighlight %}

This creates a cluster that of Tomcat servers, along with an Nginx instance. The ``NginxController`` instance
is notified whenever a member of the cluster joins or leaves; the entity is configured to look at the ``HTTP_PORT``
attribute of that instance so that the Nginx configuration can be updated with the ip:port of the cluster member.

The beauty of OO programming is that classes can be re-used.  The compound entity created above is
available off-the-shelf as the ``LoadBalancedWebCluster`` entity, as used in the following example. 


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

{% highlight java %}
{% readj example_files/tomcat_multi-location.groovy %}
{% endhighlight %}

This creates a web-fabric. When started, this creates a web-cluster in each location supplied.

Examples Source
---------------

The source code for these examples is available for download from GitHub. To retrieve the source, execute the following command:

    git clone git@github.com:brooklyncentral/brooklyn-examples.git

You can also [browse the code](https://github.com/brooklyncentral/brooklyn-examples) on the web.
