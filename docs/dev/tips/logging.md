---
layout: page
title: Logging
toc: /toc.json
---

## Logging: A Quick Overview

For logging, we use **logback** which implements the slf4j API.
This means you can use any slf4j compliant logging framework,
with a default configuration which just works out of the box
and bindings to the other common libraries (``java.util.logging``, ``log4j``, ...)
if you prefer one of those.

To use:

* **Users**:
If using a brooklyn binary installation, simply edit the ``logback.xml``
or ``logback-custom.xml`` supplied in the archive, sometimes in a ``conf/``
directory.

* **Developers**:
When setting up a new project, if you want logging it is recommended to include 
the ``brooklyn-logback-xml`` project as an *optional* and *provided* maven dependency, 
and then to put custom logging configuration in either ``logback-custom.xml`` or ``logback-main.xml``, 
as described below.


## Customizing Your Logging

The project ``brooklyn-logback-xml`` supplies a ``logback.xml`` configuration,
with a mechanism which allows it to be easily customized, consumed, and overridden.
You may wish to include this as an *optional* dependency so that it is not forced
upon downstream projects.  This ``logback.xml`` file supplied contains just one instruction,
to include ``logback-main.xml``, and that file in turn includes:

* ``logback-custom.xml``
* ``brooklyn/logback-appender-file.xml``
* ``brooklyn/logback-appender-stdout.xml``
* ``brooklyn/logback-logger-excludes.xml``
* ``brooklyn/logback-debug.xml``
   
For the most common customizations, simply create a ``logback-custom.xml`` on your classpath
(ensuring it is loaded *before* brooklyn classes in classpath ordering in the pom)
and supply your customizations there:  

{% highlight xml %}
<included>
    <!-- filename to log to -->           
    <property name="logging.basename" scope="context" value="acme-app" />
    
    <!-- additional loggers -->
    <logger name="com.acme.app" level="DEBUG"/>
</included>
{% endhighlight %}

For other configuration, you can override individual files listed above.
For example:

* To remove debug logging, create a trivial ``brooklyn/logback-debug.xml``, 
  containing simply ``<included/>``.
* To customise stdout logging, perhaps to give it a threshhold WARN instead of INFO,
  create a ``brooklyn/logback-appender-stdout.xml`` which defines an appender STDOUT.
* To discard all brooklyn's default logging, create a ``logback-main.xml`` which 
  contains your configuration. This should look like a standard logback
  configuration file, except it should be wrapped in ``<included>`` XML tags rather
  than ``<configuration>`` XML tags (because it is included from the ``logback.xml``
  which comes with ``brooklyn-logback-xml``.)

You should **not** supply your own ``logback.xml`` if you are using ``brooklyn-logback-xml``.
If you do, logback will detect multiple files with that name and will scream at you.
If you wish to supply your own ``logback.xml``, do **not** include ``brooklyn-logback-xml``.
(Alternatively you can include a ``logback.groovy`` which causes logback to ignore ``logback.xml``.)

You can set a specific logback config file to use with:

{% highlight bash %}
-Dlogback.configurationFile=/path/to/config.xml
{% endhighlight %}


## Assemblies

When building an assembly, it is recommended to create a ``conf/logback.xml`` which 
simply includes ``logback-main.xml`` (which comes from the classpath).  Users of the assembly
can then edit the ``logback.xml`` file in the usual way, or they can plug in to the configuration 
mechanisms described above, by creating files such as ``logback-custom.xml`` under ``conf/``.

Including ``brooklyn-logback-xml`` as an *optional* and *provided* dependency means everything
should work correctly in IDE's but it will not include the extra ``logback.xml`` file in the assembly.
(Alternatively if you include the ``conf/`` dir in your IDE build, you should exclude this dependency.)

With this mechanism, you can include ``logback-custom.xml`` and/or other files underneath 
``src/main/resources/`` of a project, as described above (for instance to include custom
logging categories and define the log file name) and it should get picked up, 
both in the IDE and in the assembly.   
 

## Tests

Brooklyn projects ``test`` scope includes the ``brooklyn-utils-test-support`` project
which supplies a ``logback-test.xml``. logback uses this file in preference to ``logback.xml``
when available (ie when running tests). However the ``logback-test.xml`` Brooklyn uses
includes the same ``logback-main.xml`` call path above, so your configurations should still work.

The only differences of the ``logback-test.xml`` configuration is that:

* Debug logging is included for all Brooklyn packages
* The log file is called ``brooklyn-tests.log`` 


## Caveats

* logback uses SLF4J version 1.6 which is **not compatible** with 1.5.x. 
  If you have dependent projects using 1.5.x (such as older Grails) things may break.

* If you're not getting the logging you expect in the IDE, make sure 
  ``src/main/resources`` is included in the classpath.
  (In eclipse, right-click the project, the Build Path -> Configure,
  then make sure all dirs are included (All) and excluded (None) -- 
  ``mvn clean install`` should do this for you.)

* You may find that your IDE logs to a file ``brooklyn-tests.log`` 
  if it doesn't distinguish between test build classpaths and normal classpaths.

