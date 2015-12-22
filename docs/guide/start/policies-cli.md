---
title: Getting Started - Policies
title_in_menu: Policies
layout: website-normal
menu-parent: index-cli.md
---




## A Clustered Example

We'll now look at a more complex example that better shows the capabilities of Brooklyn. 

We'll start by deploying an application via YAML blueprint consisting of the following layers.

- A dynamically scalable Web App Cluster
- A MySQL DB


Copy the blueprint below into a text file, "mycluster.yaml", in your workspace, but *before* you create an application 
with it, again modify the YAML to specify the location where the application will be deployed.  
You will need at least five machines for this example, one for the DB, and four for the tomcats 
(but you can reduce this by changing the "maxPoolSize" below.

{% highlight yaml %}
name: cluster

location:
  jclouds:aws-ec2:
    identity: ABCDEFGHIJKLMNOPQRST
    credential: s3cr3tsq1rr3ls3cr3tsq1rr3ls3cr3tsq1rr3l


services:
- serviceType: brooklyn.entity.webapp.ControlledDynamicWebAppCluster
  name: webcluster
  brooklyn.config:
    wars.root: http://search.maven.org/remotecontent?filepath=io/brooklyn/example/brooklyn-example-hello-world-sql-webapp/0.6.0-M2/brooklyn-example-hello-world-sql-webapp-0.6.0-M2.war
    http.port: 9280+
    proxy.http.port: 9210+
    java.sysprops: 
      brooklyn.example.db.url: $brooklyn:formatString("jdbc:%s%s?user=%s\\&password=%s",
         component("db").attributeWhenReady("datastore.url"), "visitors", "brooklyn", "br00k11n")
  brooklyn.policies:
  - policyType: brooklyn.policy.autoscaling.AutoScalerPolicy
    brooklyn.config:
      metric: $brooklyn:sensor("brooklyn.entity.webapp.DynamicWebAppCluster", "webapp.reqs.perSec.windowed.perNode")
      metricLowerBound: 10
      metricUpperBound: 100
      minPoolSize: 1
      maxPoolSize: 4
      
- serviceType: brooklyn.entity.database.mysql.MySqlNode
  id: db
  name: mysql
  brooklyn.config:
    creationScriptUrl: https://bit.ly/brooklyn-visitors-creation-script
{% endhighlight %}

Explore this app using the 'application' and other commands from the previous section.

## Configuring Dependencies
The App above illustrates how one component in a blueprint can be configured with information relating to one of the other 
components in the blueprint.  In this example the web cluster is configured with a URL for JDBC connections to the database.
{% highlight yaml %}
java.sysprops: 
      brooklyn.example.db.url: $brooklyn:formatString("jdbc:%s%s?user=%s\\&password=%s",
         component("db").attributeWhenReady("datastore.url"), "visitors", "brooklyn", "br00k11n")
{% endhighlight %}

the syntax ```$brooklyn:formatString(...)``` is an example of the Brooklyn DSL (Domain Specific Language) which 
allows expressions referring to Brooklyn's management information to be embedded in blueprints.  The line above also illustrates the use of Brooklyn's ```component(...)``` and ```attributeWhenReady(...)``` to get an identified component from a deployment, and to wait until the component is fully deployed before reading one of its sensors ("datastore.url" in this case). 

## Managing with Policies


The app server cluster has an `AutoScalerPolicy`and the loadbalancer has a `Controller targets tracker` policy.

For example
{% highlight yaml %}
$ br app cluster ent webcluster policy
Id         Name                                                      State   
mMZngBnb   org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy   RUNNING   
{% endhighlight %}

You can investigate the status of the `AutoScalerPolicy` with 

{% highlight yaml %}
$ br app cluster ent webcluster policy org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy
"RUNNING"
{% endhighlight %}

A more detailed description of the parameters of the policy can be obtained with
{% highlight yaml %}
$ br app cluster ent webcluster policy org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy
Name                                      Value                                                                Description   
autoscaler.currentSizeOperator            org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy$4@9393100       
autoscaler.entityWithMetric                                                                                    The Entity with the metric that will be monitored   
autoscaler.maxPoolSize                    4                                                                       
autoscaler.maxReachedNotificationDelay    0ms                                                                  Time that we consistently wanted to go above the maxPoolSize for, after which the maxSizeReachedSensor (if any) will be emitted   
autoscaler.maxSizeReachedSensor                                                                                Sensor for which a notification will be emitted (on the associated entity) when we consistently wanted to resize the pool above the max allowed size, for maxReachedNotificationDelay milliseconds   
autoscaler.metric                         Sensor: webapp.reqs.perSec.windowed.perNode (java.lang.Double)          
autoscaler.metricLowerBound               10                                                                   The lower bound of the monitored metric. Below this the policy will resize down   
autoscaler.metricUpperBound               100                                                                  The upper bound of the monitored metric. Above this the policy will resize up   
autoscaler.minPeriodBetweenExecs          100ms                                                                   
autoscaler.minPoolSize                    1                                                                       
autoscaler.poolColdSensor                 Sensor: resizablepool.cold (java.util.Map)                              
autoscaler.poolHotSensor                  Sensor: resizablepool.hot (java.util.Map)                               
autoscaler.poolOkSensor                   Sensor: resizablepool.cold (java.util.Map)                              
autoscaler.resizeDownIterationIncrement   1                                                                    Batch size for resizing down; the size will be decreased by a multiple of this value   
autoscaler.resizeDownIterationMax         2147483647                                                           Maximum change to the size on a single iteration when scaling down   
autoscaler.resizeDownStabilizationDelay   0ms                                                                     
autoscaler.resizeOperator                 org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy$3@387a7e10      
autoscaler.resizeUpIterationIncrement     1                                                                    Batch size for resizing up; the size will be increased by a multiple of this value   
autoscaler.resizeUpIterationMax           2147483647                                                           Maximum change to the size on a single iteration when scaling up   
autoscaler.resizeUpStabilizationDelay     0ms                                               
{% endhighlight %}


The loadbalancer's `Controller targets tracker` policy ensures that the loadbalancer is updated as the cluster size changes.

This policy automatically scales the cluster up or down to be the right size for the cluster's current load. One server 
is the minimum size allowed by the policy.

Sitting idle, this cluster will only contain one server, but you can use a tool like [jmeter](http://jmeter.apache.org/) 
pointed at the nginx endpoint to create load on the cluster. Download a jmeter test 
plan [here](https://github.com/apache/incubator-brooklyn/blob/master/examples/simple-web-cluster/resources/jmeter-test-plan.jmx).

As load is added, Apache Brooklyn requests a new cloud machine, creates a new app server, and adds it to the cluster. 
As load is removed, servers are removed from the cluster, and the infrastructure is handed back to the cloud.


## Under the Covers

The `AutoScalerPolicy` here is configured to respond to the sensor
reporting requests per second per node, invoking the default `resize` effector.
By updating on the policy, you can configure it to respond to a much lower threshhold
or set long stabilization delays (the period before it scales out or back).

At present the CLI does not support a command to update a policy.

However it is possible to manually suspend the policy, by 

{% highlight bash %}
$ br app cluster ent webcluster stop-policy org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy

{% endhighlight %}

You can then invoke a `resize` using the appropriate effector:
{% highlight bash %}
$ br app cluster ent webcluster effector resize invoke -P desiredSize=3
{% endhighlight %}

On resize, new nodes are created and configured, 
and in this case a policy on the nginx node reconfigures nginx whenever the set of active
targets changes.

The policy can then be re-enabled with start-policy:

{% highlight bash %}
$ br app cluster ent webcluster start-policy org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy

{% endhighlight %}


## Next

This guide has given a quick overview to writing blueprints for applications, deploying applications, and
managing them. Next, learn more about any of:

* [Writing Blueprints with YAML](../yaml/) 
* [Writing Blueprints with Java](../java/) 
* [Operating Brooklyn](../ops/) 