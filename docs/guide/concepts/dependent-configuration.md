---
title: Dependent Configuration
layout: website-normal
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

Under the covers Brooklyn has a sophisticated sensor event and subscription model, but conveniences around this model make it very simple to express cross-entity dependencies. Consider the example where Tomcat instances need to know the URL of a database (or a set of URLs to connect to a Monterey processing fabric, or other entities)

{% highlight java %}
setConfiguration(UsesJava.JAVA_OPTIONS, ImmutableMap.of("mysql.url", 
	    attributeWhenReady(mysql, MySqlNode.MY_SQL_URL) ))
{% endhighlight %}

The ``attributeWhenReady(Entity, Sensor)`` call (a static method on the class ``DependentConfiguration``)
causes the configuration value to be set when that given entity's attribue is ready. 
In the example, ``attributeWhenReady()`` causes the JVM system property ``mysql.url`` to be set to the value of the ``MySqlNode.MY_SQL_URL`` sensor from ``mysql`` when that value is ready. As soon as the database URL is announced by the MySql entity, the configuration value will be available to the Tomcat cluster. 

By default "ready" means being *set* (non-null) and, if appropriate, *non-empty* (for collections and strings) or *non-zero* (for numbers). Formally the interpretation of ready is that of "Groovy truth" defined by an ``asBoolean()`` method on the class and in the Groovy language extensions. 

You can customize "readiness" by supplying a ``Predicate`` (Google common) or ``Closure`` (Groovy) in a third parameter. 
This evaluates candidate values reported by the sensor until one is found to be ``true``. 
For example, passing ``{ it.size()>=3 }`` as the readiness argument would require at least three management plane URLs.

More information on this can be found in the javadoc for ``DependentConfiguration``,
along with a few other methods such as ``valueWhenAttributeReady`` which allow post-processing of an attribute value.

Note that if the value of ``CONFIG_KEY`` passed to ``Entity.getConfig`` is a Closure or Task (such as returned by ``attributeWhenReady``),
the first access of ``Entity.getConfig(CONFIG_KEY)`` will block until the task completes.
Typically this does the right thing, blocking when necessary to generate the right start-up sequence
without the developer having to think through the order, but it can take some getting used to.
Be careful not to request config information until really necessary (or to use non-blocking "raw" mechanisms),
and in complicated situations be ready to attend to circular dependencies.
The management console gives useful information for understanding what is happening and resolving the cycle.
