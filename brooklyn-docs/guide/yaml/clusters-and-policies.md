---
title: Clusters and Policies
layout: website-normal
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

Now let's bring the concept of the "cluster" back in.
We could wrap our appserver in the same `DynamicCluster` we used earlier,
although then we'd need to define and configure the load balancer.
But another blueprint, the `ControlledDynamicWebAppCluster`, does this for us.
It takes the same `memberSpec`, so we can build a fully functional elastic 3-tier
deployment of our `hello-world-sql` application as follows:

{% highlight yaml %}
{% readj example_yaml/appserver-clustered-w-db.yaml %}
{% endhighlight %}

This sets up Nginx as the controller by default, but that can be configured
using the `controllerSpec` key. In fact, JBoss is the default appserver,
and because configuration in Brooklyn is inherited by default,
the same blueprint can be expressed more concisely as:

{% highlight yaml %}
{% readj example_yaml/appserver-clustered-w-db-concise.yaml %}
{% endhighlight %}
 
The other nicety supplied by the `ControlledDynamicWebAppCluster` blueprint is that
it aggregates sensors from the appserver, so we have access to things like
`webapp.reqs.perSec.windowed.perNode`.
These are convenient for plugging in to policies!
We can set up our blueprint to do autoscaling based on requests per second
(keeping it in the range 10..100, with a maximum of 5 appserver nodes)
as follows: 

{% highlight yaml %}
{% readj example_yaml/appserver-w-policy.yaml %}
{% endhighlight %}

Use your favorite load-generation tool (`jmeter` is one good example) to send a huge
volume of requests against the server and see the policies kick in to resize it.

