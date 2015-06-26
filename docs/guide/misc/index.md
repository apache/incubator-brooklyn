---
# BROOKLYN_VERSION_BELOW
title: Other 0.8.0-SNAPSHOT Resources
layout: website-normal
children:
- { title: Javadoc, path: javadoc/ }
- download.md
- release-notes.md
- known-issues.md
- { path: ../dev/, title_in_menu: "Developer Guide" }
- { path: /website/documentation/, title_in_menu: "All Documentation", menu_customization: { force_inactive: true } }
---

Further documentation specific to this version of Brooklyn includes:

{% for item in page.menu reversed offset:1 reversed %}
* [{{ item.title_in_menu }}]({{ item.url }})
{% endfor %}

Also see the [other versions]({{ site.path.website }}/meta/versions.html) or [general documentation]({{ site.path.website }}/documentation/).
