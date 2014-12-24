---
layout: website-normal
title: Documentation
children:
- { path: /guide/index.md, title_in_menu: "User Guide {{ site.brooklyn-stable-version }}", menu_customization: { dropdown_section_header: true } }
- { path: /guide/yaml/index.md, title_in_menu: Brooklyn YAML }
- { path: /guide/java/index.md, title_in_menu: Java Blueprints }
- { path: /guide/ops/index.md }
- { path: other-docs.md, title_in_menu: Other Resources, menu_customization: { dropdown_new_section: true } }
---

TODO kill me

## Official User Manual

Our main user manual is organised by release version. Please pick the version that you are using:

- [0.7.0-M1]({{ site.path.v }}/0.7.0-M1) -
  Please note that this release was made prior to entering the Apache Incubator,
  and therefore it is not endorsed by Apache.

## Server install
Follow this [guide](documentation/install-on-server.html) to install Brooklyn on a production server.
