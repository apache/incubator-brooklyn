---
layout: website-normal
title: Topology, Dependencies, and Management Policies
title_in_menu: Topology, Dependencies, and Management Policies
--- 
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

Under the covers, ``attributeWhenReady`` is monitoring a sensor from MySQL
and generating a string to pass to the webapp software processes; ``formatString``
is a similar utility that returns a string once all of its parts have been resolved.
Due to the use of futures, the Brooklyn webapp entities will automatically
block "at the last moment" when the value is needed
(but after e.g. the VMs have been provisioned, to speed things up).

{% highlight java %}
public class ClusterWebServerDatabaseSample extends AbstractApplication {
    @Override
    public void init() {
        MySqlNode mysql = addChild(EntitySpec.create(MySqlNode.class)
                .configure(MySqlNode.CREATION_SCRIPT_URL, "classpath://visitors-database-setup.sql"));
        
        ControlledDynamicWebAppCluster web = addChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("memberSpec", EntitySpec.create(JBoss7Server.class)
                        .configure("httpPort", "8080+")
                        .configure("war", WAR_PATH)
                        .configure(JavaEntityMethods.javaSysProp("brooklyn.example.db.url"), 
                                formatString("jdbc:%s%s?user=%s\\&password=%s", 
                                        attributeWhenReady(mysql, MySqlNode.MYSQL_URL), DB_TABLE, DB_USERNAME, DB_PASSWORD))));
    }
}
{% endhighlight %}

We now see our app at the Nginx URL:

[![Our Web App]({{ page.url_basedir }}wt-deployed-application-700.png "Screenshot of our Web App")](wt-deployed-application.png) 

Finally, we'll bring in some active management: we're going to monitor requests per second,
and scale out if this exceeds 100 up to a maximum of 5 servers.
This is a naively simple policy, but it shows Brooklyn's real metier,
running management policies for applications whose topology it knows. 

{% highlight java %}
        web.getCluster().addPolicy(AutoScalerPolicy.builder().
                        metric(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND).
                        sizeRange(1, 5).
                        metricRange(10, 100).
                        build());
{% endhighlight %}
        
*Policies* in Brooklyn typically subscribe to sensors,  perform some computation, and if necessary invoke effectors on entities.  This is where the ability to group entities
becomes very useful -- policies can be attached to group entities, and groups themselves can be hierarchical. It's also handy that often Brooklyn creates the entities,
so it knows what the hierarchy is.

Under the covers, this ``AutoScalerPolicy`` attaches to any ``Resizable`` entity (exposing a ``resize`` effector), and monitors a specified sensor (or function) attempting to keep it within healthy limits. A separate policy operates at the ``Controlled`` cluster to ensure the load-balancer is updated as the pool of web servers expands and contracts.

Fire up a JMeter session (or other load testing tool) and blast the Nginx address. The auto-scaler policy will scale up the cluster.

