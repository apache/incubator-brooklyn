---
title: Policies
layout: website-normal
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

Policies perform the active management enabled by Brooklyn. Entities can have zero or more ``Policy`` instances attached to them. 

Policies can subscribe to sensors from entities or run periodically, and
when they run they can perform calculations, look up other values, and if deemed necessary invoke effectors or emit sensor values from the entity with which they are associated.
