---
layout: page
title: Whirr Hadoop Cluster
toc: /toc.json
---

{% readj before-begin.include.md %}

The project ``examples/hadoop-and-whirr`` includes deployment descriptors 
showing how to provision Whirr-based clusters from Brooklyn,
including setting up a Hadoop recipe.

## Whirr Hadoop

The class ``WhirrHadoopExample`` shows how a Hadoop cluster can be started
with an arbitrary size, with one line using the ``WhirrHadoopCluster`` entity.

{% highlight java %}
    WhirrCluster cluster = new WhirrHadoopCluster(size: 2, memory: 1024, name: "brooklyn-hadoop", this)
{% endhighlight %}

You can run this by running ``./demo-hadoop.sh``.
Once it is running, navigate to the Brooklyn web console to see the ``NAME_NODE_URL`` sensor.
(Then, using [``attributeWhenReady``]({{ site.url }}/use/guide/defining-applications/advanced-concepts.html#dependent), 
you can easily embed this in your application to roll out its own Hadoop cluster.)

## Custom Whirr Recipe

The class ``WhirrExample`` shows how an arbitrary [Whirr](http://whirr.apache.org) recipe
can be run from within Brooklyn.  This allows integration with Chef and Puppet,
as well as rolling out systems ranging from Cassandra to Voldemort.

<!-- TODO include a code snippet, including a recipe -->
