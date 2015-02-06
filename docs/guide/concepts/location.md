---
title: Location
layout: website-normal
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

<!-- TODO, Clarify is how geographical location works.
-->

Entities can be provisioned/started in the location of your choice. Brooklyn transparently uses [jclouds](http://www.jclouds.org) to support different cloud providers and to support BYON (Bring Your Own Nodes). 

The implementation of an entity (e.g. Tomcat) is agnostic about where it will be installed/started. When writing the application definition specify the location or list of possible locations (``Location`` instances) for hosting the entity.

``Location`` instances represent where they run and indicate how that location (resource or service) can be accessed.

For example, a ``JBoss7Server`` will usually be running in an ``SshMachineLocation``, which contains the credentials and address for sshing to the machine. A cluster of such servers may be running in a ``MachineProvisioningLocation``, capable of creating new ``SshMachineLocation`` instances as required.

<!-- TODO, incorporate the following.

The idea is that you could specify the location as AWS and also supply an image id. You could configure the Tomcat entity accordingly: specify the path if the image already has Tomcat installed, or specify that Tomcat must be downloaded/installed. Entities typically use _drivers_ (such as SSH-based) to install, start, and interact with their corresponding real-world instance. 
-->
