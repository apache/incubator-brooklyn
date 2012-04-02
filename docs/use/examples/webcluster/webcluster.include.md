
{% readj ../before-begin.include.md %}

The project ``examples/simple-web-cluster`` includes several deployment descriptors 
for rolling out a web application, under ``src/main/java``.

## Simple Web Server

The simplest of these, ``SingleWebServerExample``, starts JBoss on a single machine with a "Hello World" war deployed,
with a single line:

{% highlight java %}
public class SingleWebServerExample extends AbstractApplication {

    JBoss7Server web = new JBoss7Server(this, 
        war: "classpath://hello-world-webapp.war", 
        httpPort: 8080)
        
    // (other housekeeping removed, including public static void main)
     
}
{% endhighlight %}

You can run this (on *nix or Mac) as follows:

{% highlight bash %}
% cd /path/to/brooklyn-examples/simple-web-cluster
% ./demo-web-cluster.sh
{% endhighlight %}

Then visit the webapp on port 8080, or the Brooklyn console on 8081.  (Default credentials are admin/password.)

With appropriate setup (as described [here]({{ site.url }}/use/guide/management/index.html#startup-config)) 
this can also be deployed to your favourite cloud, let's pretend it's Amazon Ireland, as follows: 

{% highlight bash %}
% cd /path/to/brooklyn-examples/simple-web-cluster
% ./demo-web-server.sh aws-ecs:eu-west-1
{% endhighlight %}


## Elastic Three-Tier

Ready for something more interesting?  Try this:

{% highlight bash %}
simple-web-cluster% ./demo-web-and-data.sh
{% endhighlight %}

This launches the class ``WebClusterDatabaseExample`` (also described in the [walkthrough]({{ site.url }}/use/walkthrough.html))
which launches a pool of web-servers -- of size 1 initially,
but manually configurable (if you stop the policy first, in the GUI, then use the ``resize`` effector) --
with an Nginx load-balancer set up in front of them, and backed by a MySQL database.

The essential code fragment looks like this:

{% highlight java %}
public class WebClusterDatabaseExample extends AbstractApplication {
    
    ControlledDynamicWebAppCluster web = new ControlledDynamicWebAppCluster(this, war: WAR_PATH);
    MySqlNode mysql = new MySqlNode(this, creationScriptUrl: DB_SETUP_SQL_URL);

    {
        web.factory.configure(
            httpPort: "8080+", 
            (JBoss7Server.JAVA_OPTIONS):
                // -Dbrooklyn.example.db.url="jdbc:mysql://192.168.1.2:3306/visitors?user=brooklyn\\&password=br00k11n"
                ["brooklyn.example.db.url": valueWhenAttributeReady(mysql, MySqlNode.MYSQL_URL,
                    { "jdbc:"+it+"visitors?user=${DB_USERNAME}\\&password=${DB_PASSWORD}" }) ]);

        web.cluster.addPolicy(new
            ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND).
                setSizeRange(1, 5).
                setMetricRange(10, 100));
    }
}
{% endhighlight %}

You can, of course, try this with your favourite cloud, 
tweak the database start script, or drop in your favourite WAR.


## A Few Other Things

This project includes an intermediate nginx-web-cluster only example, 
in ``WebClusterExample``, and a few demonstrations of other syntaxes (classes ending ``Alt``).
Tweak the scripts to use these, or run in your IDE (which may take [a bit of setup]({{site.url}}/dev/build/ide.html)).

The webapp that is used is included under ``examples/hello-world-webapp``.

If you encounter any difficulties, please [tell us]({{ site.url }}/meta/contact.html) and we'll do our best to help.
