---
title: Logging
layout: website-normal
---


Brooklyn uses the SLF4J logging facade, which allows use of many popular frameworks including `logback`, 
`java.util.logging` and `log4j`.

The convention for log levels is as follows:

* `ERROR` and above:  exceptional situations which indicate that something has unexpectedly failed or some other problem has occured which the user is expected to attend to
* `WARN`:  exceptional situations which the user may wish to know about, but do not necessarily indicate failure or require a response
* `INFO`:  a synopsis of activity, which should not generate large volumes of events nor overwhelm a human observer
* `DEBUG` and lower:  detail of activity which is not normally of interest, but merits closer inspection under certain circumstances.

Loggers follow the ``package.ClassName`` naming standard.  


## Standard Configuration

A `logback.xml` file is included in the `conf/` directly of the Brooklyn distro; this is read by `brooklyn` at launch time.  Changes to the logging configuration, such as new appenders or different log levels, can be made directly in this file or in a new file included from this.


## Advanced Configuration

The default `logback.xml` file references a collection of other log configuration files included in the Brooklyn jars. It is necessary to understand the source structure in the [logback-includes]({{ site.brooklyn.url.git }}/usage/logback-includes) project.

For example, to change the debug log inclusions, create a folder `brooklyn` under `conf` and create a file `logback-debug.xml` based on the [brooklyn/logback-debug.xml]({{ site.brooklyn.url.git }}/usage/logback-includes/src/main/resources/brooklyn/logback-debug.xml) from that project.


## For More Information

The following resources may be useful when configuring logging:

* The [logback-includes]({{ site.brooklyn.url.git }}/usage/logback-includes) project
* [Brooklyn Developer Guide]({{ site.path.guide }}/dev/tips/logging.html) logging tips
* The [Logback Project](http://logback.qos.ch/) home page
