---
title: Examples
layout: page
toc: ../../toc.json
categories: [use, guide, defining-applications]
---

### Integrating with a Maven project

If you have a Maven-based project, integrate this XML fragment with your pom.xml:

{% highlight xml %}
<dependencies>
	<dependency>
		<groupId>brooklyn</groupId>
		<artifactId>brooklyn-launcher</artifactId>
		<version>0.3.0-SNAPSHOT</version>
		<classifier>with-dependencies</classifier>
	</dependency>
</dependencies>
 
<repositories>
	<repository>
		<id>cloudsoft-maven-repository</id>
		<url>http://developer.cloudsoftcorp.com/download/maven2/</url>
	</repository>
</repositories>
{% endhighlight %}


### Starting a Tomcat Server

The code below starts a Tomcat server on the local machine.

The ``main`` method defines the application, and passes it to the ``BrooklynLauncher`` to be managed. Here It is then started in a localhost location, but any location could be used including EC2 or GoGrid.

The ``init`` method declares the entities that comprise the app. In this case, it is a single ``tomcat`` instance. 

<!---
FIXME what init method?
-->

The Tomcat's configuration indicates that the given WAR should be deployed to the Tomcat server when it is started.

<!---
TODO httpPort: => http: in Alex's docs
-->

{% highlight java %}
{% readj example_files/tomcat_simple.java %}
{% endhighlight %}

While this is written in scala, the code can be written in pure Java if preferred, using the long-hand syntax of ``tomcat.setConfig(TomcatServer.HTTP_PORT, 80)``
in lieu of the flags.

The ``wars`` flag is also supported (with config keys ``ROOT_WAR`` and ``NAMED_WARS`` the long-hand syntax);
they accept EARs and other common archives, and can be described as files or URLs, including a ``classpath://org/acme/resources/xxx.war``
syntax.


### Starting a Tomcat Cluster in Amazon EC2

The code below starts a tomcat cluster in Amazon EC2:

<!---
TODO httpPort: => http: in Alex's docs
-->

*In this milestone release, the following snippet should be considered pseudo code as it has not been tested.*

{% highlight java %}
{% readj example_files/tomcat_EC2.java %}
{% endhighlight %}

The ``newEntity`` flag in the cluster constructor indicates how new entities should be created. The WAR configuration set on the cluster is inherited by each of the TomcatServer contained (i.e. "owned") by the cluster.

The ``DynamicWebAppCluster`` is dynamic in that it supports resizing the cluster, adding and removing servers, as managed either manually or by policies embedded in the entity.

The main method creates a ``JcloudsLocationFactory`` with appropriate credentials for the AWS account, along with the
RSA key to used for subsequently logging into the VM. It also specifies the relevant security group which should enable
the 8080 port configured above. Finally, a JcloudsLocation allows to select the Amazon region the cluster will run in.


### Starting a Tomcat Cluster with Nginx

The code below starts a Tomcat cluster along with an Nginx instance, where each Tomcat server in the cluster is registered with the Nginx instance.

<!---
TODO httpPort: => http: in Alex's docs
-->
{% highlight java %}
{% readj example_files/tomcat_nginx.java %}
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
TODO this example should use several cloud providers, including Openshift, and use GeoDNS, and maybe a data store and/or messaging service; it is the last "most advanced" example
-->

<!---
FIXME Discuss above comment with Aled/Alex as it is contentious
-->

<!---
TODO httpPort: => http: in Alex's docs
-->
{% highlight java %}
{% readj example_files/tomcat_multi-location.java %}
{% endhighlight %}

This creates a web-fabric. When started, this creates a web-cluster in each location supplied.

Examples Source
---------------

The source code for these examples is available for download from GitHub. To retrieve the source, execute the following command:

    git clone git@github.com:cloudsoft/brooklyn-examples.git

You can also [browse the code](https://github.com/cloudsoft/brooklyn-examples) on the web.


