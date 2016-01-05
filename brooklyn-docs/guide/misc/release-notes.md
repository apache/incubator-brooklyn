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

For changes in prior versions, please refer to the release notes for 
[0.8.0](/v/0.8.0-incubating/misc/release-notes.html).

