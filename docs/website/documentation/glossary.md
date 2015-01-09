---
layout: website-normal
title: Glossary
---

[//]: # (Abusing link groups to write comments that are not rendered in the document..)
[//]: # (The links below reference the id attributes generated for the headers.)
[//]: # (For example, the Autonomic section can be referred to with a link to #autonomic.)
[//]: # (So if you alter any of the headers you should update the relevant link group too.)

[autonomic]: #autonomic
[blueprint]: #blueprint
[effector]: #effector
[entity]: #entity
[policy]: #policy
[sensor]: #sensor
[YAML]: #yaml

[//]: # (Note: Autonomic and blueprint section could link to learnmore page.)


#### Autonomic

Refers to the self-managing characteristics of distributed computing resources,
adapting to unpredictable changes while hiding intrinsic complexity to
operators and users.


#### Blueprint

A description of an application or system, which can be used for its automated
deployment and runtime management. The blueprint describes a model of the
application (i.e. its components, their configuration, and their
relationships), along with policies for runtime management. The blueprint can
be described in [YAML][].

###### See also
* [Documentation]({{site.path.website}}/learnmore/catalog/index.html) for the entity,
  policy and enricher blueprints that Apache Brooklyn supports out-of-the-box.


#### Effector

An operation on an [entity][].


#### Entity

A component of an application or system. This could be a physical component, a
service, a grouping of components, or a logical construct describing part of an
application/system. It is a "managed element" in autonomic computing parlance.


#### Policy

Part of an autonomic management system, performing runtime management. A policy
is associated with an [entity][]; it normally manages the health of that entity
or an associated group of entities (e.g. HA policies or auto-scaling policies).


#### Sensor

An attribute of an [entity][].


#### YAML

A human-readable data format.

###### See also
* [Wikipedia article](http://en.wikipedia.org/wiki/YAML) on YAML


#### Apache Jclouds

An open source Java library that provides a consistent interface to many
clouds. Apache Brooklyn uses Apache Jclouds as its core cloud abstraction.

###### See also
* [Project homepage](https://jclouds.apache.org/)


#### CAMP and TOSCA

OASIS Cloud Application Management for Platforms (CAMP) and OASIS Topology and
Orchestration Specification for Cloud Applications (TOSCA) are specifications
that aim to standardise the portability and management of cloud applications.

###### See also
* [CAMP homepage](https://www.oasis-open.org/committees/tc_home.php?wg_abbrev=camp)
* [TOSCA homepage](https://www.oasis-open.org/committees/tc_home.php?wg_abbrev=tosca)

