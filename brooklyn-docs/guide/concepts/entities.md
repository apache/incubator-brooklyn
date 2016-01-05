---
title: Entities
layout: website-normal
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

The central concept in a Brooklyn deployment is that of an ***entity***. 
An entity represents a resource under management, either *base* entities (individual machines or software processes) 
or logical collections of these entities.

Fundamental to the processing model is the capability of entities to be the *parent* of other entities (the mechanism by which collections are formed), 
with every entity having a single parent entity, up to the privileged top-level ***application*** entity.

Entities are code, so they can be extended, overridden, and modified. Entities can have events, operations, and processing logic associated with them, and it is through this mechanism that the active management is delivered.

The main responsibilities of an entity are:

- Provisioning the entity in the given location or locations
- Holding configuration and state (attributes) for the entity
- Reporting monitoring data (sensors) about the status of the entity
- Exposing operations (effectors) that can be performed on the entity
- Hosting management policies and tasks related to the entity
