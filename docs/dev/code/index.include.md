## The Basics

Brooklyn is available at [GitHub brooklyncentral/brooklyn](http://github.com/brooklyncentral/brooklyn).  Check it out using:

{% highlight bash %}
git clone git@github.com:brooklyncentral/brooklyn.git
cd brooklyn
{% endhighlight %}

Build it with:

{% highlight bash %}
mvn clean install
{% endhighlight %}

And launch it with:

{% highlight bash %}
cd usage/dist/target/brooklyn-dist/
bin/brooklyn launch
{% endhighlight %}

Plenty of examples are in the **examples** sub-dir,
described [here]({{site.url}}/use/examples),
and pushed to the [brooklyn-examples](http://github.com/brooklyncentral/brooklyn-examples) 
GitHub project when a version is released.

Information on using Brooklyn -- configuring locations (in `brooklyn.properties`) 
and adding new projects to a catalog -- can be found in the [User's Guide]({{site.url}}/use/guide/quickstart/index.html).
This document is intended to help people become familiar with the codebase.

## Project Structure

Brooklyn is split into the following projects and sub-projects:

* **``camp``**: the components for a server which speaks the CAMP REST API and which understands the CAMP YAML plan language
* **``api``**: the pure-Java interfaces for interacting with the system
* **``core``**: the base class implementations for entities and applications, entity traits, locations, policies, sensor and effector support, tasks, and more 
* **``locations``**: specific location integrations
    * **``jclouds``**: integration with many cloud APIs and providers, via Apache jclouds
* **``policies``**: collection of useful policies for automating entity activity  
* **``software``**: entities which are mainly launched by launched software processes on machines, and collections thereof
    * **``base``**: software process lifecycle abstract classes and drivers (e.g. SSH) 
    * **``webapp``**: web servers (JBoss, Tomcat), load-balancers (Nginx), and DNS (Geoscaling) 
    * **``database``**: relational databases (SQL) 
    * **``nosql``**: datastores other than RDBMS/SQL (often better in distributed environments) 
    * **``messaging``**: messaging systems, including Qpid, Apache MQ, RabbitMQ 
    * **``monitoring``**: monitoring tools, including Monit
    * **``osgi``**: OSGi servers 
    * **...**
* **``utils``**: projects which lower level utilities
    * **common**: Utility classes and methods developed for Brooklyn but not dependendent on Brooklyn
    * **groovy**: Groovy extensions and utility classes and methods developed for Brooklyn but not dependendent on Brooklyn
    * **jmx/jmxmp-ssl-agent**: An agent implementation that can be attached to a Java process, to give expose secure JMXMP
    * **jmx/jmxrmi-agent**: An agent implementation that can be attached to a Java process, to give expose JMX-RMI without requiring all high-number ports to be open
    * **rest-swagger**: Swagger REST API utility classes and methods developed for Brooklyn but not dependendent on Brooklyn
    * **test-support**: Test utility classes and methods developed for Brooklyn but not dependendent on Brooklyn
* **``usage``**: projects which make Brooklyn easier to use, either for end-users or Brooklyn developers
    * **all**: maven project to supply a shaded JAR (containing all dependencies) for convenience
    * **archetypes**: A maven archetype, for easily generating the structure of a new downstream projects 
    * **camp**: Brooklyn bindings for the CAMP REST API
    * **cli**: backing implementation for Brooklyn's command line interface
    * **dist**: builds brooklyn as a downloadable .zip and .tar.gz
    * **jsgui**: Javascript web-app for the brooklyn management web console (builds a WAR)
    * **launcher**: for launching brooklyn, either using a main method or invoked from the cli project
    * **logback-includes**: Various helpful logback XML files that can be included; does not contain logback.xml 
    * **logback-xml**: Contains a logback.xml that references the include files in brooklyn-logback-includes
    * **rest-api**: The API classes for the Brooklyn REST api
    * **rest-client**: A client Java implementation for using the Brooklyn REST API 
    * **rest-server**: The server-side implementation of the Brooklyn REST API
    * **scripts**: various scripts useful for building, updating, etc. (see comments in the scripts)
    * **qa**: longevity and stress tests
    * **test-support**: provides Brooklyn-specific support for tests, used by nearly all projects in scope ``test``
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

* [Writing a Brooklyn Entity](entity.html)
* [Writing a Brooklyn Policy](policy.html)
* Or see the [User Guide]({{ site.url }}/use/guide/index.html) 
  on [policies]({{ site.url }}/use/guide/policies/index.html)
  and [entities]({{ site.url }}/use/guide/entities/index.html)

Where things aren't documented **please ask us** at 
[the brooklyn mailing list](https://mail-archives.apache.org/mod_mbox/incubator-brooklyn-dev/)
so we can remedy this!
