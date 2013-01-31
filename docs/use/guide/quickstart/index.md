---
title: brooklyn Introduction
layout: page
toc: ../guide_toc.json
categories: [use, guide]
---

**brooklyn** is a library that simplifies application deployment and management.
It allows you to:

- Describe an application topology once, and use this definition to set it up (provision) and keep it up (manage)
- Describe the application topology in code, for re-use, version control, power and readability
- Run multiple tiers and even varying stacks, configured and managed together
- Run in multiple locations with efficient, secure, wide-area management

Prerequisites
-------------

This guide requires that you have the Java 6 JDK, curl, wget and Maven 3 installed.

If you are using Eclipse, you will likely want the Git, Groovy, and Maven plugins.
Via Help -> Install New Software, or from the Eclipse Marketplace,
we recommend:

{% readj eclipse.include.md %}

For more information and other development environments,
visit the [IDE section]({{site.url}}/dev/build/ide.html) of the brooklyn web site.


Web Cluster and Database Example
--------------------------------

Here is an example class which uses the Brooklyn library
to launch a three-tier application in the cloud,
complete with management:

{% highlight java %}
{% read WebClusterAndDatabase.java %}
{% endhighlight %}

This consists of a JBoss web-app tier
behind an nginx load-balancer (built up in the class ``ControlledDynamicWebAppCluster``),
connected to a MySQL database instance.

The ``JBoss7Server`` spec tells the appservers to run on the first available port >= 8080
(which will be 8080 except when running multiple instances on ``localhost``),
and wires the URL where the database is running in to the app servers.
The MySQL URL is exposed as an attribute sensor,
as soon as the MySQL instance is started,
and the ``valueWhenAttributeReady`` call sets up a Java ``Future``
so that the provisioning of the appservers can proceed as much as possible
until the value is actually required.
This "just-in-time" approach to dependent configuration simplifies -- as much as possible --
some of the trickiest issues when setting up sophisticated applications.

A management plane is launched with the application, 
running the policy configured with the ``web.getCluster().addPolicy(...)`` command
(as well as other policies which ensure the load-balancer is updated whenever the ``web.cluster`` members change).
Keeping a handle on the application instance allows programmatic monitoring, manual management, and
policy change; the management plane can also be accessed through a command-line console, a web console, or a REST web
API. The management web console shows the hierarchy of entities active in real-time -- 
from a high-level view all the way down to the level of
each JBoss process on every VM, if desired.

<!-- TODO screenshot could be useful? -->


<!--
TODO: could be useful to include an about- the guide here, with links

About the Guide
---------------

The guide is divided into sections aimed at developers building applications using Brooklyn ...
-->
