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

This is the archived documentation for Brooklyn {{ site.brooklyn-version }}
(generated {{ site.time }}, archived under {{ site.url }}).

[View current documentation here.](/meta/versions.html)

  {% endif %}  
{% endif %}


### Version History

* **[0.7.0-SNAPSHOT (master)](/v/0.7.0-SNAPSHOT)**: since 0.6.0, lots of work on yaml, persistence, policies, and more supported systems

* **[0.7.0](/v/0.7.0-M1/)**: most recent milestone release

* **[0.6.0](/v/0.6.0/)**: use of spec objects, chef and windows support, more clouds (Nov 2013)

* **[0.5.0](/v/0.5.0/)**: includes new JS GUI and REST API, rebind/persistence support, cleaner model and naming conventions, more entities (May 2013)

* **[0.4.0](/v/0.4.0/)**: initial public GA release of Brooklyn to Maven Central, supporting wide range of entities and examples (Jan 2013)

Note: To prevent accidentally referring to out-of-date information,
a banner is displayed when accessing specific versions from the archive.
You may 
<a href="#" onclick="set_user_versions_all();">disable all warnings</a> or
<a href="#" onclick="clear_user_versions();">re-enable all warnings</a>.


### Versioning

Brooklyn uses the [semantic versioning](http://semver.org/) guidelines. Releases will be numbered with the following format:

`Brooklyn <major>.<minor>.<patch>`

Breaking backward compatibility increments the `<major>` version.
New additions without breaking backward compatibility ups the `<minor>` version.
Bug fixes and misc changes bumps the `<patch>` version.
New major and minor releases zero the less significant counters.

Additionally, Brooklyn's release process include Snapshots, Milestones and Release Candidates.

A Snapshot (`-SNAPSHOT`) is the bleeding edge. This will not be stable.

Milestone versions (`-Mn`) are frozen snapshots. Some code features may be stable, but the documentation and examples may not be complete.

A Release Candidate (`-rc.n`) is a just-about-ready version. Release candidates are tested against our acceptance criteria, and qualifying builds are promoted as final.
