---
layout: website-landing
title: Home
landing: true
children:
- learnmore/
- { path: download/, menu: null }
- { path: /guide/start/index.md, title_in_menu: Get Started, href_path: /guide/start/running.md}
- path: documentation/
  menu:
  - { path: /guide/index.md, title_in_menu: "User Guide", 
      menu_customization: { dropdown_section_header: true } }
  - { path: /guide/yaml/index.md, title_in_menu: YAML Blueprints, href_path: /guide/yaml/creating-yaml.md }
  - { path: /guide/java/index.md, title_in_menu: Java Blueprints }
  - { path: /guide/ops/index.md, title_in_menu: Operations,
      menu_customization: { dropdown_section_header: true } }
  - { path: /guide/dev/index.md, title_in_menu: Developer Guide }
  - { path: meta/versions.md, title_in_menu: Versions,
      menu_customization: { dropdown_new_section: true } }
  - { path: documentation/other-docs.md, title_in_menu: Other Resources }
- community/
- developers/
---

<div class="jumbotron">
<div id="apachebrooklynbanner">&nbsp;</div>

<div class="row">
<div class="col-md-4" markdown="1">

### model

*Blueprints* describe your application, stored as *text files* in *version control*

*Compose* from the [*dozens* of supported components](learnmore/catalog/) or your *own components* using *bash, Java, Chef...*

<div class="text-muted" markdown="1">
#### JBoss &bull; Cassandra &bull; QPid &bull; nginx &bull; [many more](learnmore/catalog/)
</div>

</div>
<div class="col-md-4" markdown="1">

### deploy

Components *configured &amp; integrated* across *multiple machines* automatically

*20+ public clouds*, or your *private cloud* or bare servers - and *Docker* containers

<div class="text-muted" markdown="1">
#### Amazon EC2 &bull; CloudStack &bull; OpenStack &bull; SoftLayer &bull; many more
</div>

</div>
<div class="col-md-4" markdown="1">

### manage

*Monitor* key application *metrics*; *scale* to meet demand; *restart* and *replace* failed components

View and modify using the *web console* or automate using the *REST API*

<div class="text-muted" markdown="1">
#### Metric-based autoscaler &bull; Restarter &amp; replacer &bull; Follow the sun &bull; Load balancing 
</div>

</div>
</div><!-- row -->

<div style="text-align: center" markdown="1">

<a class="btn btn-primary btn-lg" role="button" href="learnmore/">learn more</a>
<a class="btn btn-primary btn-lg" role="button" href="{{ site.path.guide }}/start/running.html">get started</a>

</div>

</div><!-- jumbotron -->
