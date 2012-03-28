---
layout: page
title: Walkthrough
toc: /toc.json
---

Brooklyn makes it easy to describe how to launch and manage 
sophisticated distributed applications.
Let's start with an example of a three tier application
composed of:

* an Nginx load-balancer
* a cluster of JBoss appservers
* a MySQL database

Here's the essential code which creates these and sets them up
for management:

{% highlight java %}
public class WebClusterDatabaseExample extends AbstractApplication {
    ControlledDynamicWebAppCluster web = new ControlledDynamicWebAppCluster(this);
    MySqlNode mysql = new MySqlNode(this);   
}
{% endhighlight %}

Launch this Brooklyn "Application", specifying a target location,
and our application is deployed.
Amazon has been used here, but lots of targets are supported (using [jclouds](http://jclouds.org))
as well as fixed IP addresses or even everything to localhost (very handy for dev/test,
and with port conflicts resolved automatically).

[![Web Console Showing Our Application](walkthrough-webconsole-map-w400.png "Screenshot of the Web Console")](walkthrough-webconsole-map.png) 

.. TODO image is wrong needs updating!

The management console provides a view on to the entities that launched,
including the hierarchy (appservers grouped into a cluster) and their locations. 
Brooklyn collects information from these entities ("sensors"), 
aggregates these for clusters and other groups (using "enrichers"),
and exposes operations ("effectors") that can be performed on entities.

[![Web Console Showing Nginx](walkthrough-webconsole-details-w400.png "Screenshot of the Web Console Showing Nginx")](walkthrough-webconsole-details.png) 

.. TODO image is wrong needs updating!

Of course in the real world, application deployments are more interesting;
they do things and need configuration.  For instance you might need to:
* specify a WAR file
* initialize the database
* tell the webapp servers where to find the database

Let's show how these are done using Brooklyn.
We assume the WAR file and the database init script are accessible
on the classpath, but a range of URL formats is supported.
The "dependent inter-process configuration" -- giving the database's URL
to the webapps -- we'll do here with a JVM system property,
but you're free to use any mechanism you wish.
Under the covers, {{valueWhenAttributeReady}} is monitoring a sensor from MySQL
and generating a string to pass to the webapp software processes;
due to the use of closures, the Brooklyn webapp entities will automatically
block "at the last moment" when the value is needed
(but after e.g. the VMs have been provisioned, to speed things up).

{% highlight java %}
public class WebClusterDatabaseExample extends AbstractApplication {
    ControlledDynamicWebAppCluster webCluster = new ControlledDynamicWebAppCluster(this,
        war: "classpath://hello-world-webapp.war");

    MySqlNode mysql = new MySqlNode(this, 
        creationScriptUrl: "classpath://visitors-database-setup.sql"); 
    
    {
        web.factory.configure(
            httpPort: "8080+", 
            (JBoss7Server.JAVA_OPTIONS):
                // -Dbrooklyn.example.db.url="jdbc:mysql://192.168.1.2:3306/visitors?user=brooklyn\\&password=br00k11n"
                ["brooklyn.example.db.url": valueWhenAttributeReady(mysql, MySqlNode.MYSQL_URL,
                    { "jdbc:"+it+"visitors?user=${DB_USERNAME}\\&password=${DB_PASSWORD}" }) ]);
    }
}
{% endhighlight %}

We now see our app at the Nginx URL:

[![Our Web App](walkthrough-webapp-w400.png "Screenshot of our Web App")](walkthrough-webapp.png) 

Finally, we'll bring in some active management: we're going to monitor requests per second,
and scale out if this exceeds 100 up to a maximum of 5 servers.
This is a naively simple policy, but it shows Brooklyn's real metier,
running management policies for applications whose topology it knows. 

{% highlight java %}
    {
        web.cluster.addPolicy(
            new ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND).
                setSizeRange(1, 5).
                setMetricRange(10, 100);
    }
{% endhighlight %}
        
*Policies* in Brooklyn typically subscribe to sensors, 
perform some computation, and if necessary invoke effectors
on entities.  This is where the ability to group entities
becomes very useful -- policies can be attached to group entities,
and groups themselves can be hierarchical.
It's also handy that often Brooklyn creates the entities,
so it knows what the hierarchy is.

Under the covers, this {{ResizerPolicy}} attaches to any {{Resizable}} entity
(exposing a {{resize}} effector), and monitors a specified sensor (or function)
attempting to keep it within healthy limits.
A separate policy operates at the {{Controlled}} cluster to ensure the
load-balancer is updated as the pool of web servers expands and contracts.

Let us fire up a JMeter session and blast the Nginx address.
The resizer policy scales up our cluster:

[![Web Cluster Scaling with the Resizer Policy](walkthrough-webconsole-scaling-w400.png "Screenshot of Web Cluster Scaling with the Resizer Policy")](walkthrough-webconsole-scaling.png) 

.. TODO image is wrong needs updating!

For your applications, you might want to mix in other data stores, messaging systems, or on-line services including PaaS.
Brooklyn supports some of these out-of-the-box, including a wide-range of tools which it can use Whirr to provision, such as Hadoop.
But if you have something you don't see, [let us know] -- we'd love to work with you to [write a new entity] and [contribute it].
 
All the code for this walkthrough (and even the JMeter script) is included in the {{examples/simple-web-cluster}}
project which comes with Brooklyn.
