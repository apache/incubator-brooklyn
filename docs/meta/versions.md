---
layout: page
title: Versions
toc: /toc.json
---

<!--- display which version we are using, based on where it is written -->

### Brooklyn v{{ site.brooklyn-version }}


{% if site.server %} 
> **Server (debug) mode detected.**

> *Links to other versions on this page and others will likely not work when running in server/debug mode.
Files must be copied to the brooklyncentral.github.com repo for these links to resolve correctly.*

> *Debug page generated {{ site.time }}*
{% endif %}


{% if site.brooklyn-version contains 'SNAPSHOT' %}
<!--- snapshot version -->

  {% if site.url == '' %}

<!--- current version (served off root of site) is snapshot (unusual) -->

This is the documentation for the current snapshot version of Brooklyn,
generated {{ site.time | date_to_string }}.

  {% else %}

<!--- archive docs -->

This is the documentation for a snapshot version of Brooklyn,
generated {{ site.time | date_to_string }}.

[View current documentation here.](/meta/versions.html)


  {% endif %}

NB: "Snapshot" means it is the code at a point in time,
and that a reference to this version {{ site.brooklyn-version }}
may resolve to different code at a different point in time.
Where possible it is preferable to develop against a GA version
rather than a shapshot.  

{% else %}
<!--- not snapshot -->

  {% if site.url == '' %}
   
<!--- current version (served off root of site) -->

This is the documentation for the latest stable version of Brooklyn,
generated {{ site.time | date_to_string }}.
Other versions with documentation available are listed below.

  {% else %}

<!--- archive version -->

This is the archived documentation for Brooklyn v{{ site.brooklyn-version }}
(generated {{ site.time }}, archived under {{ site.url }}).

[View current documentation here.](/meta/versions.html)

  {% endif %}  
{% endif %}


### Version History

* **[MASTER](/v/0.5.0-SNAPSHOT)**: includes new JS GUI and REST API, rebind/persistence support, cleaner naming conventions, more entities

* **[v0.4.0](/v/0.4.0/)**: current RC of first GA release, supporting wide range of entities and examples

