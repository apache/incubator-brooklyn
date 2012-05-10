## The Basics

Brooklyn is available at [GitHub brooklyncentral/brooklyn](http://github.com/brooklyncentral/brooklyn).  Check it out using:

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
* **``software``**: entities which are mainly launched by launched software processes on machines, and collections thereof
    * **``base``**: software process lifecycle abstract classes and drivers (e.g. SSH) 
    * **``webapp``**: web servers (JBoss, Tomcat), load-balancers (Nginx), and DNS (Geoscaling) 
    * **``database``**: relational databases (SQL) 
    * **``nosql``**: datastores other than RDBMS/SQL (often better in distributed environments) 
    * **``messaging``**: messaging systems, including Qpid, Apache MQ 
    * **...**
* **``systems``**: entities which are mainly created or managed by other systems, where Brooklyn integrates with those (multi-machine) systems and is removed from the processes
    * **``whirr``**:  ``base`` Whirr integration, and entities built on Whirr such as the configurable ``hadoop`` deployment
    * **``openshift``**:  entity for deploying and managing OpenShift webapps 
    * **...**
* **``usage``**: projects which make Brooklyn easier to use, either for end-users or Brooklyn developers
    * **web-console**: Grails web-app for the brooklyn management web console (builds a WAR)
    * **launcher**: CLI support and provides a JAR including the web-console WAR
    * **test-support**: provides support for tests, used by nearly all projects in scope ``test`` 
    * **all**: maven project to supply a shaded JAR (containing all dependencies) for convenience 
    * **scripts**: various scripts useful for building, updating, etc. (see comments in the scripts)
* **``docs``**: the markdown source code for this documentation, as described [here]({{site.url}}/dev/tips/update-docs.html)
* **``examples``**: some canonical examples, as listed [here]({{site.url}}/use/examples)
* **``sandbox``**: various projects, entities, and policies which the Brooklyn Project is incubating

 
## Next Steps

If you're interested in building and editing the code, check out:

* [Maven setup](../build/index.html)
* [IDE setup](../build/ide.html)
* [Tests](../build/tests.html)
* [Tips](../tips/index.html)

If you want to start writing your own policies and entities, have a look at:

* [Writing a Brooklyn Policy](policy.html)
* [Writing a Brooklyn Entity](entity.html)
* Or see the [User Guide]({{ site.url }}/use/guide/index.html) 
  on [policies]({{ site.url }}/use/guide/policies/index.html)
  and [entities]({{ site.url }}/use/guide/entities/index.html)

Where things aren't documented **please ask us** at 
[brooklyn-dev@googlegroups.com](http://groups.google.com/group/brooklyn-dev)
so we can remedy this!
