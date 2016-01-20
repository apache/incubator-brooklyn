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

Version 0.8.0 is [TODO add description] 

Thanks go to our community for their improvements, feedback and guidance, and
to Brooklyn's commercial users for funding much of this development.


### New Features

[TODO]
 

### Backwards Compatibility

Changes since 0.8.0-incubating:

1. **Major:** The classes HttpTool and HttpToolResponse in brooklyn-core (package org.apache.brooklyn.util.core.http)
have been moved to brooklyn-utils-common, in package org.apache.brooklyn.util.
Classes such as HttpFeed that previously returned org.apache.brooklyn.util.core.http.HttpToolResponse in some methods now 
return org.apache.brooklyn.util.HttpToolResponse.

2. **Major:** Locations set in YAML or on a spec are no longer passed to `child.start(...)` by `AbstractApplication`;
this has no effect in most cases as `SoftwareProcess.start` looks at local and inherited locations, but in ambiguous cases
it means that locally defined locations are now preferred. Other classes of entities may need to do similar behaviour,
and it means that calls to `Entity.getLocations()` in some cases will not show parent locations,
unless discovered and set locally e.g. `start()`. The new method `Entities.getAllInheritedLocations(Entity)`
can be used to traverse the hierarchy.  It also means that when a type in the registry (catalog) includes a location,
and a caller references it, that location will now take priority over a location defined in a parent.
Additionally, any locations specified in YAML extending the registered type will now *replace* locations on the referenced type;
this means in many cases an explicit `locations: []` when extending a type will cause locations to be taken from the
parent or application root in YAML. Related to this, tags from referencing specs now preceed tags in the referenced types,
and the referencing catalog item ID also takes priority; this has no effect in most cases, but if you have a chain of
referenced types blueprint plan source code and the catalog item ID are now set correctly. 

For changes in prior versions, please refer to the release notes for 
[0.8.0](/v/0.8.0-incubating/misc/release-notes.html).

3. Task cancellation is now propagated to dependent submitted tasks, including backgrounded tasks if they are transient.
Previously when a task was cancelled the API did not guarantee semantics but the behaviour was to cancel sub-tasks only 
in very limited cases. Now the semantics are more precise and controllable, and more sub-tasks are cancelled.
This can prevent some leaked waits on `attributeWhenReady`.
