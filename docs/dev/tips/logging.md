---
layout: page
title: Logging
toc: /toc.json
---

## A Quick Overview

For logging, we use the ``slf4j`` facade, usually hooked up to a ``log4j`` implementation.

Tests have debug logging across the board, included by default from ``usage/test-support``

Launcher (and all) define a log4j.properties which logs at INFO level with a few selected categories at DEBUG
(debug output goes only to a file, ``./brooklyn.log``).

Some categories are quite useful, as you'll see in the ``log4j.properties`` files in the project:

* brooklyn
* brooklyn.SSH
* org.jclouds


## Dependencies

### log4j.properties

If you've not inherited brooklyn-launcher (or brooklyn-all) you'll need a ``log4j.properties`` 
(or ``log4j.xml``, which will dominate).
There are plenty of other valid reasons for wanting to supply your own logging as well.

A good starting point is the configuration file in 
[launcher](https://github.com/brooklyncentral/brooklyn/blob/master/usage/launcher/src/main/resources/log4j.properties).

Once you've tweaked this, place it in your classpath, 
or specify ``-Dlog4j.configuration=/path/to/your/log4j.properties``. 
For more information see [logging.apache.org/log4j](http://logging.apache.org/log4j/1.2/manual.html).

### Maven

If you've not inherited brooklyn-launcher (or brooklyn-all) you may not have an SLF4J implementation project
on your classpath (you'll see some SLF4J complaints at runtime).
Adding a dependency on an implementation, such as ``log4j12``, should resolve the problem:

{% highlight xml %}
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-log4j12</artifactId>
            <version>${slf4j.version}</version>
            <optional>true</optional>
        </dependency>
{% endhighlight %} 

As of this writing we use 1.5.11 for the version,
as per the [root pom](https://github.com/brooklyncentral/brooklyn/blob/master/pom.xml).
The ``optional`` line means the dependency should not be passed to any projects
which depend on your project; you can remove it to force the import your logging implementation choice.
(You can of course use a differing slf4j-compliant logger, such as ``java.util.logging`` or ``ch.qos.logback``.)


## Caveats

* SLF4J **version >= 1.6** is **not compatible** with 1.5.x and breaks certain things (such as the web console written in Grails).

* Logging for **tests** isn't picked up correctly in some environments (some Eclipse flavours).
  The root cause seems to be a rogue log4j.properties included in the groovy-all OSGi bundle bsf.jar which sets a FATAL threshhold.
  To resolve this add an explicit project dependency on test-support, 
  or create your own log4j.properties file.

