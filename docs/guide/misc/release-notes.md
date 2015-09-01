---
layout: website-normal
title: Release Notes
---

## Version {{ site.brooklyn-version }}

{% if SNAPSHOT %}
**You are viewing a SNAPSHOT release (master branch), so this list is in progress!**
{% endif %}

* Introduction
* New Features
* Backwards Compatibility


### Introduction

Version 0.8.0 is a rapid, clean-up and hardening release, as we prepare for graduation.
The biggest change is the package refactoring, discussed in the Backwards Compatibility section.
Other new features include more externalized configuration,
machine management (suspend/resume and windows enhandements),
MySQL cluster, entitlements enhancements, and pluggable blueprint languages. 

Thanks go to our community for their improvements, feedback and guidance, and
to Brooklyn's commercial users for funding much of this development.


### New Features

New features include:

* All classes are in the `org.apache.brooklyn` namespace

* Externalized configuration, using `$brooklyn:external` to access data which is
  retrieved from a remote store or injected via an extensible mechanism
   
* Port mappings supported for BYON locations:  fixed-IP machines can now be configured 
  within subnets

* The Entitlements API is extended to be more convenient and work better with LDAP

* The blueprint language is pluggable, so downstream projects can supply their own,
  such as TOSCA to complement the default CAMP dialect used by Brooklyn 

* A MySQL master-slave blueprint is added 

* Misc other new sensors and improvements to Redis, Postgres, and general datastore mixins 

* jclouds version bumped to 1.9.1, and misc improvements for several clouds
  including Softlayer and GCE
 

### Backwards Compatibility

Changes since 0.7.0-incubating:

1. **Major:** Packages have been renamed so that everything is in the `org.apache.brooklyn`
   namespace. This decision has not been taken lightly!
   
   This **[migration guide](migrate-to-0.8.0.html)** will assist converting projects to
   the new package structure.
    
   We recognize that this will be very inconvenient for downstream projects,
   and it breaks our policy to deprecate any incompatibility for at least one version,
   but it was necessary as part of becoming a top-level Apache project.
   Pre-built binaries will not be compatible and must be recompiled against this version.

   We have invested significant effort in ensuring that persisted state will be unaffected.

1. Some of the code deprecated in 0.7.0 has been deleted.
   There are comparatively few newly deprecated items.

For changes in prior versions, please refer to the release notes for 
[0.7.0](/v/0.7.0-incubating/misc/release-notes.html).

