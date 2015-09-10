---
title: Getting Started - Policies
title_in_menu: Policies
layout: website-normal
---

### Exploring and Testing Policies

To see an example of policy based management, please deploy the following blueprint:

{% highlight yaml %}
{% readj _my-web-cluster2.yaml %}
{% endhighlight %}

The app server cluster has an `AutoScalerPolicy`, and the loadbalancer has a `targets` policy.

Use the Applications tab in the web console to drill down into the Policies section of the ControlledDynamicWebAppCluster. You will see that the `AutoScalerPolicy` is running.


This policy automatically scales the cluster up or down to be the right size for the cluster's current load. One server is the minimum size allowed by the policy.

The loadbalancer's `targets` policy ensures that the loadbalancer is updated as the cluster size changes.

Sitting idle, this cluster will only contain one server, but you can use a tool like [jmeter](http://jmeter.apache.org/) pointed at the nginx endpoint to create load on the cluster. Download a jmeter test plan [here](https://github.com/apache/incubator-brooklyn/blob/master/examples/simple-web-cluster/resources/jmeter-test-plan.jmx).

As load is added, Apache Brooklyn requests a new cloud machine, creates a new app server, and adds it to the cluster. As load is removed, servers are removed from the cluster, and the infrastructure is handed back to the cloud.


### Under the Covers

The `AutoScalerPolicy` here is configured to respond to the sensor
reporting requests per second per node, invoking the default `resize` effector.
By clicking on the policy, you can configure it to respond to a much lower threshhold
or set long stabilization delays (the period before it scales out or back).

An even simpler test is to manually suspend the policy, by clicking "Suspend" in the policies list.
You can then switch to the "Effectors" tab and manually trigger a `resize`.
On resize, new nodes are created and configured, 
and in this case a policy on the nginx node reconfigures nginx whenever the set of active
targets changes.


### Next

This guide has given a quick overview to writing blueprints for applications, deploying applications, and
managing them. Next, learn more about any of:

* [Writing Blueprints with YAML](../yaml/) 
* [Writing Blueprints with Java](../java/) 
* [Operating Brooklyn](../ops/) 

