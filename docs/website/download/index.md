---
layout: website-normal
title: Download
children:
- { path: verify.md }
---

## Latest Brooklyn Source Code Release

[Download Apache Brooklyn {{ site.data.brooklyn.version }} from our mirror sites](https://www.apache.org/dyn/closer.cgi/incubator/brooklyn/{{ site.data.brooklyn.version }}/apache-brooklyn-{{ site.data.brooklyn.version }}.tar.gz)

You can also verify that you build has not been tampered with by [verifying the hashes and signatures](verify.html).


## Build the Binary Package

We do not yet have an official binary package for Apache Brooklyn. We plan to address this in our next release. However,
it is relatively easy to create the binary package from source code, if you have a working JDK of at least version 6,
and Maven 3.

Unpack `apache-brooklyn-{{ site.data.brooklyn.version }}.tar.gz` and then execute this command in the `apache-brooklyn-{{ site.data.brooklyn.version }}` folder:

{% highlight bash %}
mvn clean install -DskipTests
{% endhighlight %}

You can then find the binary distribution in the folder `usage/dist/target/brooklyn-dist`, or archived as `usage/dist/target/brooklyn-{{ site.data.brooklyn.version }}-dist.tar.gz`.


## Get Started!

Take a look at the [Get Started]({{ site.path.guide }}/start/running.html) page!


## Previous Versions

Versions of Brooklyn prior to 0.7.0-M2 were all made prior to joining the Apache Incubator, therefore **they are not
endorsed by Apache** and are not hosted by Apache or their mirrors. You can obtain the source code by [inspecting the
branches of the pre-Apache GitHub repository](https://github.com/brooklyncentral/brooklyn/branches/stale) and binary
releases by [querying Maven Central for io.brooklyn:brooklyn.dist](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22io.brooklyn%22%20AND%20a%3A%22brooklyn-dist%22).
