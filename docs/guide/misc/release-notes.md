---
layout: website-normal
title: Release Notes
---

## Version {{ site.brooklyn-version }}

{% if SNAPSHOT %}
**You are viewing a SNAPSHOT release (master branch), so this list is incomplete.**
{% endif %}

* Introduction
* New Features
* Backwards Compatibility
* Community Activity


### Introduction

Version 0.7.0 is a major step for Apache Brooklyn. It is the first full release
of the project as part of the Apache incubator.

Thanks go to our community for their improvements, feedback and guidance, and
to Brooklyn's commercial users for funding much of this development.


### New Features

This release is of a magnitude that makes it difficult to do justice to all of
the features that have been added to Brooklyn in the last eighteen months. The
selection here is by no means all that is new.

1. _Blueprints in YAML_ In a significant boost to accessibility, authors no
   longer need to know Java to model applications. The format follows the
   [OASIS CAMP specification](https://www.oasis-open.org/committees/camp/)
   with some extensions.

1. _Persistence and rebind_ Brooklyn persists its state and on restart rebinds
   to the existing entities.

1. _High availability_ Brooklyn can be run a highly available mode with a
   master node and one or more standby nodes.

1. _Blueprint versioning_ The blueprint catalogue supports multiple versions
   of blueprints. Version dependencies are managed with OSGi.

1. _Windows support_ Brooklyn can both run on and deploy to Windows instances.

1. _Cloud integrations_ Significant support for several clouds, including
   SoftLayer, Google Compute Engine and Microsoft Azure.

1. _Downstream parent_ A new module makes it significantly simpler for downstream
   projects to depend on Brooklyn.


Other post-0.7.0-M2 highlights include:

1. New policies: `SshConnectionFailure`, which emits an event if it cannot make
   an SSH connection to a machine, and `ConditionalSuspendPolicy`, which suspends
   a target policy if it receives a sensor event.

1. Brooklyn reports server features in responses to `GET /v1/server/version`.

1. It is much easier for downstream projects to customise the behaviour of
   `JcloudsLocationSecurityGroupCustomiser`.

1. Brooklyn is compiled with Java 7 and uses jclouds 1.9.0.

1. Improvements to the existing Nginx, Riak, RabbitMQ and Bind DNS entities and
   support for Tomcat 8.


### Backwards Compatibility

Changes since 0.7.0-M2:

1. Passwords generated with the `generate-password` command line tool must be
   regenerated. The tool now generates exactly `sha256( salt + password )`.

Changes since 0.6.0:

1. Code deprecated in 0.6.0 has been deleted. Many classes and methods are newly deprecated.

1. Persistence has been radically overhauled. In most cases the state files
   from previous versions are compatible but many items have had to change.

1. Location configuration getter and setter methods are changed to match those
   of Entities. This is in preparation for having all Locations be Entities.

1. OpenShift integration has moved from core Brooklyn to the downstream project
   https://github.com/cloudsoft/brooklyn-openshift.

Please refer to the release notes for versions
[0.7.0-M2](https://brooklyn.incubator.apache.org/v/0.7.0-M2-incubating/misc/release-notes.html)
and
[0.7.0-M1](https://brooklyn.incubator.apache.org/v/0.7.0-M1/start/release-notes.html)
for further compatibility notes.


### Community Activity

During development of 0.7.0 Brooklyn moved to the Apache Software Foundation.

Many exciting projects are using Brooklyn. Notably:

* [Clocker](http://clocker.io), which creates and manages Docker cloud
  infrastructures.

* The Brooklyn Cloud Foundry Bridge, which brings blueprints into the Cloud
  Foundry marketplace with the [Brooklyn Service
  Broker](https://github.com/cloudfoundry-incubator/brooklyn-service-broker)
  and manages those services with the Cloud Foundry CLI plugin.

* [SeaClouds](http://www.seaclouds-project.eu/), an ongoing EU project for
  seamless adaptive multi-cloud management of service based applications.

