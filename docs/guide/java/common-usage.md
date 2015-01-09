---
title: Common Classes and Entities
layout: guide-normal
---

### Entity Class Hierarchy

By convention in Brooklyn the following words have a particular meaning, both as types (which extend ``Group``, which extends ``Entity``) and when used as words in other entities (such as ``TomcatFabric``):

- *Cluster* - a homogeneous collection of entities
- *Fabric* - a multi-location collection of entities, with one per location; often used with a cluster per location
- *Stack* - heterogeneous (mixed types of children)
- *Application* - user's entry point

<!---
TODO
-->

- *entity spec* defines an entity, so that one or more such entities can be created; often used by clusters/groups to define how to instantiate new children.
- *entity factories* are often used by clusters/groups to define how to instantiate new children.
- *traits* (mixins) providing certain capabilities, such as Resizable and Balanceable
- *Resizable* entities can re-sized dynamically, to increase/decrease the number of child entities.
- *Movable* entities can be migrated between *balanceable containers*.
- *Balanceable containers* can contain *movable* entities, where each contained entity is normally associated with
    a piece of work within that container.

### Off-the-Shelf Entities

brooklyn includes a selection of entities already available for use in applications,
including appropriate sensors and effectors, and in some cases include Cluster and Fabric variants.
(These are also useful as templates for writing new entities.)
 
These include:

- **Web**: Tomcat, JBoss, Jetty (external), Play (external); nginx; GeoScaling
- **Data**: MySQL, Redis, MongoDB, Infinispan, GemFire (external)
- **Containers**: Karaf
- **Messaging**: ActiveMQ, Qpid, Rabbit MQ
- **PaaS**: Cloud Foundry, Stackato; OpenShift


®