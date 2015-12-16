---
title: Configuring VMs
layout: website-normal
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

Another simple blueprint will just create a VM which you can use, without any software installed upon it:

{% highlight yaml %}
{% readj example_yaml/simple-vm.yaml %}
{% endhighlight %}


*We've omitted the `location` section here and in many of the examples below;
add the appropriate choice when you paste your YAML. Note that the `provisioning.properties` will be
ignored if deploying to `localhost` or `byon` fixed-IP machines.* 

This will create a VM with the specified parameters in your choice of cloud.
In the GUI (and in the REST API), the entity is called "VM",
and the hostname and IP address(es) are reported as [sensors]({{ site.path.guide }}/concepts/entities.html).
There are many more `provisioning.properties` supported here,
including:

* a `user` to create (if not specified it creates the same username as `brooklyn` is running under) 
* a `password` for him or a `publicKeyFile` and `privateKeyFile` (defaulting to keys in `~/.ssh/id_rsa{.pub,}` and no password,
  so if you have keys set up you can immediately ssh in!)
* `machineCreateAttempts` (for dodgy clouds, and they nearly all fail occasionally!) 
* and things like `imageId` and `userMetadata` and disk and networking options (e.g. `autoAssignFloatingIp` for private clouds)

For more information, see [Operations: Locations](../ops/locations/).
