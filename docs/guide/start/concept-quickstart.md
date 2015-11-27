---
title: Brooklyn Concepts Quickstart
layout: website-normal
menu_parent: index.md
---

The following section provides a quick summary of the main Brooklyn concepts you will encounter in Getting Started.  For further discussion of these concepts see [The Theory Behind Brooklyn]({{site.path.website}}/learnmore/theory.html), and the detailed descriptions in [Brooklyn Concepts]({{site.path.guide}}/concepts/).

***Deployment and Management*** Brooklyn is built for agile deployment of applications across cloud and other targets, and real-time autonomic management. "Autonomic computing" is the concept of components looking after themselves where possible (self-healing, self-optimizing, etc).

***Blueprints***  A blueprint defines an application by specifying its components, such as processes, or combinations of processes across multiple machines and services. The blueprint also specifies the inter-relationships between the configurations of the components.

***Entities*** The central concept in a Brooklyn deployment is that of an entity. An entity represents a resource under management (individual machines or software processes) or logical collections of these. Entities are arranged hierarchically. They can have events, operations, and processing logic associated with them, and it is through this mechanism that the active management is delivered.

***Applications*** are the top level entities that are the parents of all other entities.

***Configuration*** Entities can have arbitrary configuration values, which get inherited by their child entities. You can set global (Brooklyn-wide) properties in (``~/.brooklyn/brooklyn.properties``).  Common configuration keys have convenient aliases called "flags".

***Sensors*** are the mechanism for entities to expose information for other entities to see.  Sensors from an entity can be subscribed to by other entities to track changes in the entity’s activity. Sensors can be updated, potentially frequently, by the entity or associated tasks.

***Effectors*** are the mechanism for entities to expose the operations that can be invoked on it by other entities.  The invoker is able to track the execution of that effector with tasks. 


***Lifecycle*** The management context of Brooklyn associates a "lifecycle" with Brooklyn entities.  Common operations are start, stop, and restart (whose meaning differs slightly for applications and processes; the details are in the concepts guide linked above).  Starting an application results in the start() operation being performed recursively (typically in parallel) on the application's children.

***Tasks*** Lifecycle and other operations in Brooklyn are tracked as tasks. This allows current and past processing to be observed by operators, and processing to be managed across multiple management nodes.


***Locations*** can be defined in order to specify where the processes of an application will run.  Brooklyn supports different cloud providers and pre-prepared machines (including localhost), known as "BYON" (Bring Your Own Nodes).

***Policies*** Policies perform the active management enabled by Brooklyn. Entities can have  Policy instances attached to them, which can subscribe to sensors from other entities or run periodically.  When they run they can perform calculations, look up other values, invoke effectors or emit sensor values from the entity with which they are associated.

***Enrichers*** These are mechanisms that subscribe to a sensor, or multiple sensors, and output a new sensor. For example, the enricher which sums a sensor across multiple entities (used to get the total requests-per-second for all the web servers in a cluster), and the enricher which calculates a 60-second rolling average.