---
title: Clusters, Specs, and Composition
layout: website-normal
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

What if you want multiple machines?

One way is just to repeat the `- type: org.apache.brooklyn.entity.software.base.EmptySoftwareProcess` block,
but there's another way which will keep your powder [DRY](http://en.wikipedia.org/wiki/Don't_repeat_yourself):

{% highlight yaml %}
{% readj example_yaml/cluster-vm.yaml %}
{% endhighlight %}

Here we've composed the previous blueprint introducing some new important concepts, the `DynamicCluster`
the `$brooklyn` DSL, and the "entity-spec".  Let's unpack these. 

The `DynamicCluster` creates a set of homogeneous instances.
At design-time, you specify an initial size and the specification for the entity it should create.
At runtime you can restart and stop these instances as a group (on the `DynamicCluster`) or refer to them
individually. You can resize the cluster, attach enrichers which aggregate sensors across the cluster, 
and attach policies which, for example, replace failed members or resize the cluster dynamically.

The specification is defined in the `memberSpec` key.  As you can see it looks very much like the
previous blueprint, with one extra line.  Entries in the blueprint which start with `$brooklyn:`
refer to the Brooklyn DSL and allow a small amount of logic to be embedded
(if there's a lot of logic, it's recommended to write a blueprint YAML plugin or write the blueprint itself
as a plugin, in Java or a JVM-supported language).  

In this case we want to indicate that the parameter to `memberSpec` is an entity specification
(`EntitySpec` in the underlying type system); the `entitySpec` DSL command will do this for us.
The example above thus gives us 5 VMs identical to the one we created in the previous section.
