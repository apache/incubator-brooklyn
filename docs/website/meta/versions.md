---
layout: website-normal
title: Versions
---

## Current Version: {{ site.brooklyn-stable-version }}

The current stable version of Brooklyn is {{ site.brooklyn-stable-version }}:

* [Download]({{ site.path.website }}/download/)
* [User Guide]({{ site.path.guide }}/)
* [Release Notes]({{ site.path.guide }}/misc/release-notes.html)

This documentation was generated {{ site.time | date_to_string }}.


## Version History

Apache Brooklyn has made the following releases:

* **0.7.0-incubating**: New policies, more clouds, improved Windows support and many other improvements. Apache-endorsed binary release! (July 2015)
  * [User guide](/v/0.7.0-incubating/), download via mirrors
    &laquo;<a href="https://www.apache.org/dyn/closer.cgi/incubator/brooklyn/apache-brooklyn-0.7.0-incubating/apache-brooklyn-0.7.0-incubating-bin.tar.gz">bin.tar.gz</a>&raquo;
    &laquo;<a href="https://www.apache.org/dyn/closer.cgi/incubator/brooklyn/apache-brooklyn-0.7.0-incubating/apache-brooklyn-0.7.0-incubating-bin.zip">bin.zip</a>&raquo;
    &laquo;<a href="https://www.apache.org/dyn/closer.cgi/incubator/brooklyn/apache-brooklyn-0.7.0-incubating/apache-brooklyn-0.7.0-incubating-src.tar.gz">src.tar.gz</a>&raquo;
    &laquo;<a href="https://www.apache.org/dyn/closer.cgi/incubator/brooklyn/apache-brooklyn-0.7.0-incubating/apache-brooklyn-0.7.0-incubating-src.zip">src.zip</a>&raquo;

* **0.7.0-M2-incubating**: YAML, persistence, Chef, Windows, Docker. The first Apache release! (December 2014)
  * [User guide](/v/0.7.0-M2-incubating/), download via mirrors
    &laquo;<a href="https://www.apache.org/dyn/closer.cgi/incubator/brooklyn/0.7.0-M2-incubating/apache-brooklyn-0.7.0-M2-incubating.tar.gz">src.tar.gz</a>&raquo;

Note: To prevent accidentally referring to out-of-date information,
a banner is displayed when accessing content from specific versions in the archive.
You may
<a href="javascript:void(0);" onclick="set_user_versions_all();">disable all warnings</a> or
<a href="javascript:void(0);" onclick="clear_user_versions();">re-enable all warnings</a>.


## Ancient Versions

The versions below were made prior to joining The Apache Software Foundation, therefore **they are not endorsed by
Apache** and are not hosted by Apache or their mirrors. You can obtain the source code by
[inspecting the branches of the pre-Apache GitHub repository](https://github.com/brooklyncentral/brooklyn/branches/stale)
and binary releases by
[querying Maven Central for io.brooklyn:brooklyn.dist](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22io.brooklyn%22%20AND%20a%3A%22brooklyn-dist%22).

* **[0.7.0-M1](/v/0.7.0-M1/)**: YAML, Chef, catalog, persistence (Apr 2014)

* **[0.6.0](/v/0.6.0/)**: use of spec objects, chef and windows support, more clouds (Nov 2013)

* **[0.5.0](/v/0.5.0/)**: includes new JS GUI and REST API, rebind/persistence support, cleaner model and naming conventions, more entities (May 2013)

* **[0.4.0](/v/0.4.0/)**: initial public GA release of Brooklyn to Maven Central, supporting wide range of entities and examples (Jan 2013)


### Versioning

Brooklyn follows [semantic versioning](http://semver.org/) with a leading `0.` qualifier:

    0.<major>.<minor>[.<patch>]-<qualifier>

Breaking backward compatibility increments the `<major>` version.
New additions without breaking backward compatibility ups the `<minor>` version.
Bug fixes and misc changes bumps the `<patch>` version.
New major and minor releases zero the less significant counters.

From time to time, the Brooklyn project will make snapshots, milestone releases, and other qualified versions available,
using the following conventions:

* Milestone versions (`-M<n>`) have been voted on and have been through some QA,
  but have not had the extensive testing as is done on a release.

* Snapshot (`-SNAPSHOT`) is the bleeding edge.
  With good test coverage these builds are usually healthy, 
  but they have not been through QA or voted on.

* Nightly builds (`-N<date>`) represent a snapshot which has
  been given a permanent version number, typically for use by other projects.
  The same caveats as for snapshot releases apply (no QA or Apache vote). 

* Release Candidate builds (`-RC<n>`) are made in the run-up to a release;
  these should not normally be used except for deciding whether to cut a release.

