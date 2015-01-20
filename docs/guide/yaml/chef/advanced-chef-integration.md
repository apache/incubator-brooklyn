---
title: Advanced Chef Integration
title_in_menu: Advanced Chef Integration
layout: website-normal
---

### Adding Sensors and Effectors

Custom sensors and effectors can be added using an `entity.initializer` section in the YAML blueprint.

One common pattern is to have sensors which extract information from Ohai.
Another common pattern is to install a monitoring agent as part of the run list,
configured to talk to a monitoring store, and then to add a sensor feed which reads data from that store.

On the effector side, you can add SSH-based effectors in the usual way.
You can also describe additional chef converge targets following the pattern set down in
`ChefLifecycleEffectorTasks`, making use of conveniences in `ChefSoloTasks` and `ChefServerTasks`,
or provide effectors which invoke network API's of the systems under management
(for example to supply the common `executeScript` effector as on the standard `MySqlNode`). 
   

### Next Steps: Simpifying sensors and effectors, transferring files, and configuring ports

The Brooklyn-Chef integration is work in progress, with a few open issues we'd still like to add.
Much of the thinking for this is set forth in the [Google document](https://docs.google.com/a/cloudsoftcorp.com/document/d/18ZwzmncbJgJeQjnSvMapTWg6N526cvGMz5jaqdkxMf8)
indicated earlier.  If you'd like to work with us to implement these, please let us know.


## Reference

A general schema for the supported YAML is below: 

{% highlight yaml %}
- type: chef:cookbook_name
  cookbook_urls:
    cookbook_name: url://for/cookbook.tgz
    dependency1: url://for/dependency1.tgz
  launch_run_list: [ "cookbook_name::start" ]
  launch_attributes: # map of arguments to set in the chef node
  service_name: cookbook_service
  pid_file: /var/run/cookbook.pid
{% endhighlight %}

If you are interested in exploring the Java code for creating blueprints,
start with the `TypedToyMySqlEntiyChef` class, which essentially does what this tutorial has shown;
and then move on to the `DynamicToyMySqlEntiyChef` which starts to look at more sophisticated constructs.
(Familiarity with BASH and basic Java blueprints may be useful at that stage.)

