---
layout: page
title: Code Structure
toc: /toc.json
---

## The Basics

Brooklyn is available on [GitHub](http://github.com/brooklyncentral/brooklyn).  Check it out using:

{% highlight bash %}
git clone git@github.com:brooklyncentral/brooklyn.git
{% endhighlight %}

You'll find versions in branches, and examples in the **examples** sub-dir.
These examples are pushed to the [brooklyn-examples](http://github.com/brooklyncentral/brooklyn-examples) GitHub project when a version is released.


## Project Structure

Brooklyn is split into the following projects and subprojects:

* **``api``**: the pure-Java interfaces for interacting with the system
* **``core``**: the base class implementations for entities and applications, entity traits, locations, policies, sensor and effector support, tasks, and more 
* **``policies``**: collection of useful policies for automating entity activity  
* **``software``**: entities which are launched by launched software processes on machines
    * TODO webapp, database, messaging, etc  
* **``systems``**: TODO
    * TODO whirr, openshift
* **``usage``**: TODO
    * TODO
* **``examples``**: TODO
    * TODO

 
## Next Steps

If you're interested in building and editting the code, check out:

* Maven setup
* IDE setup
* Tests

If you want to start writing your own policies and entities, have a look at:

* [Writing a Brooklyn Policy](policy.html)
* [Writing a Brooklyn Entity](entity.html)
* Or see the [User Guide]({{ site.url }}/use/guide/index.html) 
  on [policies]({{ site.url }}/use/guide/policies/index.html)
  and [entities]({{ site.url }}/use/guide/entities/index.html)

Where things aren't documented **please ask us** at 
[brooklyn-dev@googlegroups.com](http://groups.google.com/group/brooklyn-dev)
so we can remedy this!
