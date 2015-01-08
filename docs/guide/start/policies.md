---
title: Getting Started - Policies
title_in_menu: Policies
layout: guide-normal
---

### Exploring and Testing Policies

The Demo Web Cluster with DB application is pre-configured with two polices.

The app server cluster has an `AutoScalerPolicy`, and the loadbalancer has a `targets` policy.

Use the Applications tab in the web console to drill down into the Policies section of the ControlledDynamicWebAppCluster's Cluster of JBoss7Servers.

You will see that the `AutoScalerPolicy` is running.

[![Inspecting the jboss7 cluster policies.](images/jboss7-cluster-policies.png)](images/jboss7-cluster-policies-large.png)


This policy automatically scales the cluster up or down to be the right size for the cluster's current load. (One server is the minimum size allowed by the policy.)

The loadbalancer's `targets` policy ensures that the loadbalancer is updated as the cluster size changes.

Sitting idle, this cluster will only contain one server, but you can use a tool like [jmeter](http://jmeter.apache.org/) pointed at the nginx endpoint to create load on the cluster. (Download a [jmeter test plan](https://github.com/apache/incubator-brooklyn/blob/master/examples/simple-web-cluster/resources/jmeter-test-plan.jmx).)

As load is added, Brooklyn requests a new cloud machine, creates a new app server, and adds it to the cluster. As load is removed, servers are removed from the cluster, and the infrastructure is handed back to the cloud.

### Next

This guide has given a quick overview to writing blueprints for applications, deploying applications, and
managing them. Next, learn more about any of:

* [Writing blueprints with YAML](../yaml/) 
* [Writing blueprints with Java](../java/) 
* [Operating Brooklyn](../ops/) 

