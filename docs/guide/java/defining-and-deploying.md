---
title: Defining and Deploying
layout: website-normal
---

## Intro

This walkthrough will set up a sample application which you can use as foundation for creating your own applications.

The sample application is a three tier web service, composed of:

* an Nginx load-balancer
* a cluster of JBoss appservers
* a MySQL database

## Define your Application Blueprint

An application blueprint is defined as a Java class, as follows:

{% highlight java %}
public class ClusterWebServerDatabaseSample extends AbstractApplication {
    @Override
    public void init() {
        MySqlNode mysql = addChild(EntitySpec.create(MySqlNode.class));
        ControlledDynamicWebAppCluster web = addChild(EntitySpec.create(ControlledDynamicWebAppCluster.class));
    }
}
{% endhighlight %}

The file `ClusterWebServerDatabaseSample.java` in `src/main/java/com/acme/sample/brooklyn/sample/app/` 
provides a template to follow.


## Deploying the Application

If you have not already done so, follow the instructions 
[here]({{site.path.guide}}/ops/locations/) to create a `brooklyn.properties` 
file containing credentials for your preferred cloud provider. 

To launch this application, build the project and run the `start.sh` script in the resulting assembly:

{% highlight bash %}
$ mvn clean assembly:assembly

$ cd target/brooklyn-sample-0.1.0-SNAPSHOT-dist/brooklyn-sample-0.1.0-SNAPSHOT/

$ ./start.sh launch \
    --app com.acme.sample.brooklyn.sample.app.ClusterWebServerDatabaseSample \
    --location jclouds:aws-ec2:eu-west-1
{% endhighlight %}

(Amazon is used in this walkthrough, but lots of targets are supported,
including `--location localhost`, fixed IP addresses, and 
everything supported by [jclouds](http://jclouds.org), from OpenStack to Google Compute.)

Your console will inform you that it has started a Brooklyn console at [http://localhost:8081](http://localhost:8081)

[![Web Console]({{ page.url_basedir }}wt-starting-700.png "Web Console")](wt-starting.png) 

The management console provides a view on to the entities that launched,
including the hierarchy (appservers grouped into a cluster) and their locations. 

Brooklyn collects information from these entities ("sensors"), 
aggregates these for clusters and other groups (using "enrichers"),
and exposes operations ("effectors") that can be performed on entities.

[![Web Console Details]({{ page.url_basedir }}wt-tree-jboss-sensors-700.png "Web Console Details")](wt-tree-jboss-sensors.png) 

## What Next?
 
In addition to the sample project created by the archetype, with its README and
`assembly` build, you can find additional code related to this example included with Brooklyn as the ``simple-web-cluster`` example.
{% comment %}
described [in detail here]({{site.path.guide}}/use/examples/webcluster).
{% endcomment %}

For your applications, you might want to mix in other data stores, messaging systems, or on-line services including PaaS.
Brooklyn supports some of these out-of-the-box, including a wide-range of tools which it can use Whirr to provision, such as Hadoop.
But if you have something you don't see, 
[let us know]({{site.path.website}}/community/) -- 
we want to work with you to 
[write a new entity]({{site.path.guide}}/java/entity.html) or
[policy]({{site.path.guide}}/java/policy.html) 
and [contribute it]({{site.path.website}}/developers/how-to-contribute.html).


<!--

Alternatively you can just add a ``main`` method to the application class as follows:

{% highlight java %}
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynServerDetails server = BrooklynLauncher.newLauncher()
                .webconsolePort(port)
                .launch();

        Location loc = server.getManagementContext().getLocationRegistry().resolve(location);

        StartableApplication app = new WebClusterDatabaseExample()
                .appDisplayName("Brooklyn WebApp Cluster with Database example")
                .manage(server.getManagementContext());
        
        app.start(ImmutableList.of(loc));
        
        Entities.dumpInfo(app);
    }
{% endhighlight %}

Compile and run this with the [``brooklyn-all`` jar]({{site.path.guide}}/start/download.html) on the classpath,
pointing at your favourite WAR on your filesystem. 
(If the ``import`` packages aren't picked up correctly,
you can cheat by looking at [the file in Github](https://github.com/apache/incubator-brooklyn/blob/master/examples/simple-web-cluster/src/main/java/brooklyn/demo/WebClusterDatabaseExample.java);
and you'll find a sample WAR which uses the database as configured above 
[here](https://http://ccweb.cloudsoftcorp.com/maven/libs-snapshot-local/io/brooklyn/).)
 TODO example webapp url 
 
If you want to adventure beyond ``localhost`` (the default),
simply supply the your favourite cloud (e.g. ``aws-ec2:eu-west-1``)
with credentials set up as described [here]({{ site.path.guide }}/use/guide/management/index.html#startup-config).

-->
