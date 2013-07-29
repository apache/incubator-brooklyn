      
{% readj ../before-begin.include.md %}

## Simple Web Server

Go to this particular example's directory:

{% highlight bash %}
% cd simple-web-cluster
{% endhighlight %}

The CLI needs to know where to find your compiled examples. You can set this up by exporting
the ``BROOKLYN_CLASSPATH`` environment variable in the following way:

{% highlight bash %}
% export BROOKLYN_CLASSPATH=$(pwd)/target/classes
{% endhighlight %}

The project ``simple-web-cluster`` includes several deployment descriptors
for rolling out a web application, under ``src/main/java``.



The simplest of these, ``SingleWebServerExample``, starts JBoss on a single machine with a "Hello World" war deployed,
with a single line:

{% highlight java %}
public class SingleWebServerExample extends AbstractApplication {
    private static final String WAR_PATH = "classpath://hello-world-webapp.war";

    @Override
    public void init() {
        addChild(EntitySpecs.spec(JBoss7Server.class)
                .configure("war", WAR_PATH)
                .configure("httpPort", 8080));
    }
}
{% endhighlight %}

You can run this as follows (on *nix or Mac, assuming `ssh localhost` requires no password or passphrase):

{% highlight bash %}
% ${BROOKLYN_HOME}/bin/brooklyn launch --app brooklyn.demo.SingleWebServerExample \
  --location localhost
{% endhighlight %}


Then visit the webapp on port 8080, or the Brooklyn console on localhost:8081.
Note that the installation may take some time, because the default deployment downloads the software from
the official repos.  You can monitor start-up activity for each entity in the ``Activity`` pane in the management console,
and see more detail by tailing the log file (``tail -f brooklyn.log``).

With appropriate setup (as described [here]({{ site.url }}/use/guide/management/index.html#startup-config)) 
this can also be deployed to your favourite cloud, let's pretend it's Amazon Ireland, as follows: 

{% highlight bash %}
% ${BROOKLYN_HOME}/bin/brooklyn launch --app brooklyn.demo.SingleWebServerExample \
  --location aws-ec2:eu-west-1
{% endhighlight %}


## Elastic Three-Tier

Ready for something more interesting?  Try this:

{% highlight bash %}
% ${BROOKLYN_HOME}/bin/brooklyn launch --app brooklyn.demo.WebClusterDatabaseExample \
  --location localhost
{% endhighlight %}

This launches the class ``WebClusterDatabaseExample`` (also described in the [walkthrough]({{ site.url }}/start/walkthrough/index.html))
which launches a pool of web-servers -- of size 1 initially,
but manually configurable (if you stop the policy first, in the GUI, then use the ``resize`` effector) --
with an Nginx load-balancer set up in front of them, and backed by a MySQL database.

The essential code fragment looks like this:

{% highlight java %}
public class WebClusterDatabaseExample extends AbstractApplication {
    public static final String WAR_PATH = "classpath://hello-world-sql-webapp.war";
    
    public static final String DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql";
    
    public static final String DB_TABLE = "visitors";
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";

    @Override
    public void init() {
        MySqlNode mysql = addChild(EntitySpecs.spec(MySqlNode.class)
                .configure("creationScriptUrl", DB_SETUP_SQL_URL));
        
        ControlledDynamicWebAppCluster web = addChild(EntitySpecs.spec(ControlledDynamicWebAppCluster.class)
                .configure("memberSpec", EntitySpecs.spec(JBoss7Server.class)
                        .configure("httpPort", "8080+")
                        .configure("war", WAR_PATH)
                        .configure(javaSysProp("brooklyn.example.db.url"), 
                                formatString("jdbc:%s%s?user=%s\\&password=%s", 
                                        attributeWhenReady(mysql, MySqlNode.MYSQL_URL), DB_TABLE, DB_USERNAME, DB_PASSWORD))));
        
        web.getCluster().addPolicy(AutoScalerPolicy.builder().
                        metric(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND).
                        sizeRange(1, 5).
                        metricRange(10, 100).
                        build());
    }
}
{% endhighlight %}

You can, of course, try this with your favourite cloud, 
tweak the database start script, or drop in your favourite WAR.


## A Few Other Things

The project includes variants of the examples shown here, 
including alternative syntax (the `*Alt*` files), 
and a web-only cluster (no database) in `WebClusterExample``.

The webapp that is used is included under ``examples/hello-world-webapp``.

You may wish to check out the [Global Web Fabric example]({{ site.url }}/use/examples/global-web-fabric/) next.

If you encounter any difficulties, please [tell us]({{ site.url }}/meta/contact.html) and we'll do our best to help.
