---
title: Application, Parent and Membership
layout: website-normal
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

All entities have a ***parent*** entity, which creates and manages it, with one important exception: *applications*.
Application entities are the top-level entities created and managed externally, manually or programmatically.

Applications are typically defined in Brooklyn as an ***application descriptor***. 
This is a Java class specifying the entities which make up the application,
by extending the class ``AbstractApplication``, and specifying how these entities should be configured and managed.

All entities, including applications, can be the parent of other entities. 
This means that the "child" is typically started, configured, and managed by the parent.
For example, an application may be the parent of a web cluster; that cluster in turn is the parent of web server processes.
In the management console, this is represented hierarchically in a tree view.

A parallel concept is that of ***membership***: in addition to one fixed parent,
and entity may be a ***member*** of any number of special entities called ***groups***.
Membership of a group can be used for whatever purpose is required; 
for example, it can be used to manage a collection of entities together for one purpose 
(e.g. wide-area load-balancing between locations) even though they may have been
created by different parents (e.g. a multi-tier stack within a location).
