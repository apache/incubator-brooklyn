---
title: Getting Started - Policies
title_in_menu: Policies
layout: website-normal
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
name: java-cluster-db-policy-example
services:
- serviceType: brooklyn.entity.webapp.ControlledDynamicWebAppCluster
  name: My Web with Policy
  location: localhost
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
  name: My DB
  location: localhost
  brooklyn.config:
    # this also uses the flag rather than the config key
    creationScriptUrl: https://bit.ly/brooklyn-visitors-creation-script
{% endhighlight %}

Explore this app using the 'bk list application' and other commands from the previous section.

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

Use the Applications tab in the web console to drill down into the Policies section of the ControlledDynamicWebAppCluster. 
You will see that the `AutoScalerPolicy` is running.

The loadbalancer's `Controller targets tracker` policy ensures that the loadbalancer is updated as the cluster size changes.

This policy automatically scales the cluster up or down to be the right size for the cluster's current load. One server 
is the minimum size allowed by the policy.

Sitting idle, this cluster will only contain one server, but you can use a tool like [jmeter](http://jmeter.apache.org/) 
pointed at the nginx endpoint to create load on the cluster. Download a jmeter test 
plan [here](https://github.com/apache/incubator-brooklyn/blob/master/examples/simple-web-cluster/resources/jmeter-test-plan.jmx).

As load is added, Apache Brooklyn requests a new cloud machine, creates a new app server, and adds it to the cluster. 
As load is removed, servers are removed from the cluster, and the infrastructure is handed back to the cloud.

{% highlight bash %}
$ br list application
Id         Name                             Status    Location   
AQT22sAj   java-cluster-db-policy-example   RUNNING   koWi1cvr   
$ br list entities AQT22sAj
Id         Name                 Type   
JT5eTIc5   My Web with Policy   org.apache.brooklyn.entity.webapp.ControlledDynamicWebAppCluster   
cyXBexRx   My DB                org.apache.brooklyn.entity.database.mysql.MySqlNode   
$ br show entity AQT22sAj JT5eTIc5
Id         Name                                                                                                                          Type   
dAaReeKJ   Cluster of TomcatServer (FixedListMachineProvisioningLocation{id=koWi1cvr, name=FixedListMachineProvisioningLocation:koWi})   org.apache.brooklyn.entity.webapp.DynamicWebAppCluster   
z6oxo0pC   NginxController:z6ox                                                                                                          org.apache.brooklyn.entity.proxy.nginx.NginxController   
$ br list policies AQT22sAj z6oxo0pC
Name                         State   
Controller targets tracker   RUNNING   
{% endhighlight %}

## Under the Covers

The `AutoScalerPolicy` here is configured to respond to the sensor
reporting requests per second per node, invoking the default `resize` effector.
By updating on the policy, you can configure it to respond to a much lower threshhold
or set long stabilization delays (the period before it scales out or back).
{% highlight bash %}
TODO: example of command to update a policy
{% endhighlight %}

An even simpler test is to manually suspend the policy, by invoking "Suspend" on it.

{% highlight bash %}
TODO design this command
{% endhighlight %}

You can then invoke a `resize` using the appropriate effector:
{% highlight bash %}
TODO
{% endhighlight %}

On resize, new nodes are created and configured, 
and in this case a policy on the nginx node reconfigures nginx whenever the set of active
targets changes.


## Next

This guide has given a quick overview to writing blueprints for applications, deploying applications, and
managing them. Next, learn more about any of:

* [Writing Blueprints with YAML](../yaml/) 
* [Writing Blueprints with Java](../java/) 
* [Operating Brooklyn](../ops/) 