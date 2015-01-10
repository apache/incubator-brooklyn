---
title: Setting Locations
layout: guide-normal
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

Brooklyn supports a very wide range of target locations -- localhost is mainly a convenience for testing.
With deep integration to [Apache jclouds](http://jclouds.org), most well-known clouds and cloud platforms are supported.
The following example is for Amazon EC2:

{% highlight yaml %}
{% readj example_yaml/simple-appserver-with-location.yaml %}
{% endhighlight %}

(You'll need to replace the `identity` and `credential` with the 
"Access Key ID" and "Secret Access Key" for your account,
as configured in the [AWS Console](https://console.aws.amazon.com/iam/home?#security_credential).)

Other popular public clouds include `softlayer`, `google-compute-engine`, and `rackspace-cloudservers-us`.
Private cloud systems including `openstack-nova` and `cloudstack` are also supported,
although for these you'll supply an `endpoint: https://9.9.9.9:9999/v2.0/` 
(or `client/api/` in the case of CloudStack) instead of the `region`.

You can also specify pre-existing servers to use -- "bring-your-own-nodes".
These can be a global pool or specific to a service.
Both styles are shown here (though normally only one will be selected,
<!-- TODO see #1377, currently it is *parent* location which is preferred typically --> 
depending on the blueprint):

{% highlight yaml %}
{% readj example_yaml/simple-appserver-with-location-byon.yaml %}
{% endhighlight %}

You'll also note in this example that we've used JSON-style notation in the second `location` block.
YAML supports this, and sometimes that makes more readable plans.
(Although in this case a simple `location: localhost` is equivalent and even more succinct, but this is a tutorial.)

For more information see the Locations section of the [YAML reference](yaml-reference.html) 
and in the [Operations]({{ site.path.guide }}/ops/locations) section of the User Guide.
Another good reference is the [template brooklyn.properties]({{ brooklyn_properties_url_path }}),
which if you install in `~/.brooklyn/brooklyn.properties` and edit with your credentials,  
allows you to refer to clouds simply as `location: jclouds:aws-ec2:eu-west-1` or
set up "named locations" you can use as `location: named:my_cloudstack`.
