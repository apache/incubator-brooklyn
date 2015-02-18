---
layout: website-normal
title: Download
children:
- verify.md
- ../meta/versions.md
---

## Latest Brooklyn Source Code Release

[Download Apache Brooklyn {{ site.brooklyn-stable-version }} from our mirror sites](https://www.apache.org/dyn/closer.cgi/incubator/brooklyn/{{ site.brooklyn-stable-version }}/apache-brooklyn-{{ site.brooklyn-stable-version }}.tar.gz)

You can also verify that you build has not been tampered with by [verifying the hashes and signatures](verify.html).


## Build the Binary Package

We do not yet have an official binary package for Apache Brooklyn. We plan to address this in our next release.

However, it is relatively easy to create the binary package from source code, if you have a working recent JDK and Maven 3.

**1)** Unpack `apache-brooklyn-{{ site.brooklyn-stable-version }}.tar.gz`

{% highlight bash %}
tar xvfz apache-brooklyn-{{ site.brooklyn-stable-version }}.tar.gz
{% endhighlight %}

**2)** Move to the newly created `apache-brooklyn-{{ site.brooklyn-stable-version }}` folder:

{% highlight bash %}
cd apache-brooklyn-{{ site.brooklyn-stable-version }}
{% endhighlight %}

**3)** Run this command in the `apache-brooklyn-{{ site.brooklyn-stable-version }}` folder:

{% highlight bash %}
mvn clean install -DskipTests
{% endhighlight %}

You should then find the binary distribution in the folder `usage/dist/target/brooklyn-dist`, or archived as `usage/dist/target/brooklyn-{{ site.brooklyn-stable-version }}-dist.tar.gz`.

**Problems?** More information on building is [here]({{ site.path.guide }}/dev/env/maven-build.html).


## Get Started!

Take a look at the **[Get Started]({{ site.path.guide }}/start/running.html)** page.
