---
title: Lifecycle and ManagementContext
layout: website-normal
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

Under-the-covers, at heart of the brooklyn management plane is the ``ManagementContext``. 
This is started automatically when using launching an application using the brooklyn CLI. For programmatic use, see 
``BrooklynLauncher.newLauncher().launch()``.

A Brooklyn deployment consists of many entities in a hierarchical tree, with the privileged *application* entity at the top level.

An application entity (``Application`` class) is responsible for starting the deployment of all its child entities (i.e. the entire entity tree under its ownership).

An ``Application``'s ``start()`` method begins provisioning the child entities of the application (and their entities, recursively). 

Provisioning of entities typically happens in parallel automatically,
although this can be customized. This is implemented as ***tasks*** which are tracked by the management plane and is accessible in the web-based management console and REST API.

Customized provisioning can be useful where two starting entities depend on each other. For example, it is often necessary to delay start of one entity until another entity reaches a certain state, and to supply run-time information about the latter to the former.

<!-- TODO ambiguous language; need a better description of the "manage" lifecycle -->
When new entities are created, the entity is wired up to an application by giving it a parent. The entity is then explicitly "managed", which allows other entities to discover it.

Typically a Brooklyn deployment has a single management context which records:

*   all entities under management that are reachable by the application(s) via the parent-child relationships,
*	the state associated with each entity,
*	subscribers (listeners) to sensor events arising from the entities,
*	active tasks (jobs) associated with any the entity,
*	which Brooklyn management node is mastering (managing) each entity.

<!-- TODO Distributed brooklyn not yet supported; needs clarification in docs -->

In a multi-location deployment, management operates in all regions, with brooklyn entity instances being mastered in the relevant region.

When management is distributed a Brooklyn deployment may consist of multiple Brooklyn management nodes each with a ``ManagementContext`` instance.

<!-- TODO - Clarify the following statements.
The management context entity forms part of the management plane. 
The management plane is responsible for the distribution of the ``Entity`` instances across multiple machines and multiple locations, 
tracking the transfer of events (subscriptions) between ``Entity`` instances, and the execution of tasks (often initiated by management policies).
-->
