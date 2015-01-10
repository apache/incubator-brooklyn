---
layout: guide-normal
title: Release Notes
---

## Version {{ site.brooklyn-version }}

TODO: breacrumbs {{ page.breadcrumb_paths }}.

{% if SNAPSHOT %}
**You are viewing a SNAPSHOT release (master branch), so this list is incomplete.**
{% endif %}

* Introduction
* New Features
* Backwards Compatibility
* Community Activity

### Introduction

This version includes many big features,
incorporating a lot of improvements and feedback from our community. Thank you!

Thanks also go to Brooklyn's commercial users who have funded this development and
made some major contributions. 

For more information, please visit [brooklyn.io](http://brooklyn.io).


### New Features

* A huge expansion of what can be done in YAML.

* First-class Chef integration

* New clouds:  GCE, Softlayer

* Networking

* Docker support:  see [clocker.io](http://clocker.io)


### Backwards Compatibility

* Persistence has been radically overhauled. In most cases the state files from previous versions are compatible,
  but some items have had to change. For most users this should not be an issue as persistence in the previous version
  was not working well in any case. 


### Community Activity

Brooklyn has moved into the Apache Software Foundation.
