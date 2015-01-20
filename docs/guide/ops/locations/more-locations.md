---
title: More Locations
layout: website-normal
children:
- { section: Single Host }
- { section: The Multi Location }
- { section: The Server Pool }
---

Some additional location types are supported for specialized situations:

### Single Host

The spec `host`, taking a string argument (the address) or a map (`host`, `user`, `password`, etc.),
provides a convenient syntax when specifying a single host.
For example:

{% highlight yaml %}
services:
- type: brooklyn.entity.webapp.jboss.JBoss7Server 
  location:
    host: 192.168.0.1
{% endhighlight %}

Or, in `brooklyn.properties`, set `brooklyn.location.named.host1=host:(192.168.0.1)`.


### The Multi Location

The spec `multi` allows multiple locations, specified as `targets`,
to be combined and treated as one location.
When the first target is full, the next is tried, and so on:

{% highlight yaml %}
location:
  multi:
    targets:
    - byon:(hosts=192.168.0.1)
    - jclouds:aws-ec2:
      identity: acct1
    - jclouds:aws-ec2:
      identity: acct2      
{% endhighlight %}

The example above provisions the first node to `192.168.0.1`,
then it provisions into `acct1` at Amazon if possible,
and then to `acct2`.



### The Server Pool

The {% include java_link.html class_name="ServerPool" package_path="brooklyn/entity/pool" project_subpath="software/base" %}
entity type allows defining an entity which becomes available as a location.

