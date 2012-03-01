---
title: brooklyn Introduction
layout: page
toc: guide_toc.json
---

**brooklyn** is a provisioning and management framework for big, distributed applications. At a glance, it lets you:

- Describe an application topology once, and use this definition to set it up (provision) and keep it up (management)
- Describe the application topology in code, for re-use, version control, power and readability
- Run multiple tiers and even varying stacks, configured and managed together
- Run in multiple locations with efficient, secure wide-area management

Prerequisites
=============

This guide requires that you have the Java 6 JDK, curl, wget and Maven 3 installed.

If you are using Eclipse, you will require the Groovy and Maven plugins.

- In Eclipse goto: Help -> Install New Software, or obtain the following using the Eclipse Marketplace.

- Groovy Plugin: http://dist.springsource.org/release/GRECLIPSE/e3.7

- Maven Plugin: http://download.eclipse.org/technology/m2e/releases



WorldwideSpringTravel Example
=============================


This is what a relatively complex sample application looks like::

    public class WorldwideSpringTravel extends AbstractApplication {
           // define our multi-location tiers (using off-the-shelf entities)
           def tomcat = new TomcatWithDnsFabric(this,
               name:'SpringTravelWebApp', war:'spring-travel.war',
               domain:'www.travel.mycompany.com', dns:"ns.mycompany.com")
           def gemfire = new GemfireFabric(this, name:'SpringTravelGemfire')
           def monterey = new MontereyFabric(this,
               name:'SpringTravelBooking', osgi:'com.cloudsoft.spring.booking.impl')
           { //wire the tiers together
               tomcat.webCluster.template.setConfig(JavaEntity.JVM_PROPERTY("monterey.urls"),
                   attributeWhenReady(monterey, Monterey.MGMT_PLANE_URLS) )
               monterey.setConfig(JavaEntity.JVM_PROPERTY("monterey.urls"),
                   attributeWhenReady(gemfire, Gemfire.URLS) )
               monterey.policy << new MontereyLatencyOptimisationPolicy()
           } 
    }

This consists of a Tomcat web-app tier (which will automatically configure the DNS records for the given domain), a
Gemfire datacache, and a Monterey processing fabric with a latency optimisation policy. The following command will start
the application running across three locations::

    new WorldwideSpringTravel().withConfig(LOGIN_CREDENTIALS).start(
           location:[ new AmazonEurope(), new GoGridUsWest(),
               new VcloudLocation("http://privatecloud.mycompany.com:8080") ]

A management plane is launched with the application, running policies for load-balancing, scaling, and optimizing
placement. Keeping a handle on the WorldwideSpringTravel instance allows programmatic monitoring, manual management, and
policy change; the management plane can also be accessed through a command-line console, a web console, or a REST web
API. The management web console, shown below, shows the hierarchy of entities active in real-time--down to the level of
each Tomcat process on a VM and every Monterey code segment, if desired.
