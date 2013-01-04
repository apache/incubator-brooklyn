---
layout: page
title: Downloads
toc: ../toc.json
---

## Contents

* [The Distro](#distro)
* [A Single Jar](#alljar)
* [Examples](#examples)
* [Maven](#maven)  
* [Source Code](#source)

<a name="distro"></a>
## The Distro

{% capture maven_this_version_base_url %}{% if site.brooklyn-version contains 'SNAPSHOT' %}http://ccweb.cloudsoftcorp.com/maven/libs-snapshot-local/{% else %}http://developers.cloudsoftcorp.com/download/maven2/{% endif %}{% endcapture %}

You can grab the distribution artifact, containing Brooklyn, its dependencies and launch scripts, 
here{% if site.brooklyn-version contains 'SNAPSHOT' %} (but please **check the date** on snapshot artifacts){% endif %}:

* [{{ site.brooklyn-version }}]({{ maven_this_version_base_url }}io/brooklyn/brooklyn-dist/{{ site.brooklyn-version }}/)
* [all stable versions](http://developers.cloudsoftcorp.com/download/maven2/io/brooklyn/brooklyn-dist/)
* [all snapshot versions](http://ccweb.cloudsoftcorp.com/maven/libs-snapshot-local/io/brooklyn/brooklyn-dist/)


Just download your preferred flavour and unpack.

<a name="alljar"></a>
## The All Jar

You can grab a single JAR containing all of Brooklyn and its dependencies 
here{% if site.brooklyn-version contains 'SNAPSHOT' %} (again please check the date on snapshot artifacts){% endif %}:

* [{{ site.brooklyn-version }} (jar)]({{ maven_this_version_base_url }}io/brooklyn/brooklyn-all/{{ site.brooklyn-version }}/)
* [all stable versions](http://developers.cloudsoftcorp.com/download/maven2/io/brooklyn/brooklyn-all/)
* [all snapshot versions](http://ccweb.cloudsoftcorp.com/maven/libs-snapshot-local/io/brooklyn/brooklyn-all/)

Just download your preferred flavour and add it to your classpath.

<a name="examples"></a>
## Examples

You can clone the most recent stable examples from the [brooklyn-examples git repository](http://github.com/brooklyncentral/brooklyn-examples):

{% highlight bash %}
% git clone https://github.com/brooklyncentral/brooklyn-examples.git
{% endhighlight %}

You can also download them from [here](https://github.com/brooklyncentral/brooklyn-examples/tarball/master).

If you prefer to do this from the command-line, use:

{% highlight bash %}
% curl -L https://github.com/brooklyncentral/brooklyn-examples/tarball/master -o brooklyn-latest.tgz
{% endhighlight %}

If you are looking for a specific version (e.g. to run examples compiled for a specific Brooklyn version) try the following command:

{% highlight bash %}
% export BV=0.4.0-M2
% curl -L https://github.com/brooklyncentral/brooklyn-examples/tarball/${BV} -o brooklyn-${BV}.tgz
{% endhighlight %}

Once you have the examples you can build them with [maven (v3)]({{site.url}}/dev/build/).

Note however, that you still need to have the Brooklyn installed in order to run them.

A good entry point is the [Elastic Web Cluster]({{site.url}}/use/examples/webcluster.html).

<a name="maven"></a>
## Maven

If you use maven, you can add Brooklyn with the following entries in your pom:

{% highlight xml %}
    <repositories>
        <repository>
            <id>cloudsoft-releases</id>
            <url>http://developers.cloudsoftcorp.com/download/maven2/</url>
        </repository>
        <repository>
            <id>libs-snapshot-local</id>
            <url>http://ccweb.cloudsoftcorp.com/maven/libs-snapshot-local/</url>
            <snapshots>
                <enabled>true</enabled>
                <updatePolicy>never</updatePolicy>
                <checksumPolicy>fail</checksumPolicy>
            </snapshots>
        </repository>
    </repositories>
    
    <dependencies>
        <dependency>
            <groupId>io.brooklyn</groupId>
            <artifactId>brooklyn-all</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
{% endhighlight %}

Brooklyn-All (used above) brings in all dependencies, including jclouds and Apache Whirr.
If you prefer a smaller repo you might want just ``brooklyn-core``,  ``brooklyn-policies``, 
and some of: ``brooklyn-software-webapp``,  ``brooklyn-software-database``, ``brooklyn-software-messaging``, ``brooklyn-systems-hadoop``.
(Browse the full list [here](http://ccweb.cloudsoftcorp.com/maven/libs-snapshot-local/io/brooklyn/).)

**TODO: we are moving to mavencentral so the repositories section will shortly be unnecessary**

<a name="source"></a>
## Source Code

Full source is at [github.com/brooklyncentral/brooklyn](http://github.com/brooklyncentral/brooklyn).
Information on working with the source is [here]({{ site.url }}/dev/code).

Alternatively you can download archives of the source directly:

<a href="https://github.com/brooklyncentral/brooklyn/tarball/master"><img border="0" width="90" src="https://github.com/images/modules/download/tar.png"></a>
<a href="https://github.com/brooklyncentral/brooklyn/zipball/master"><img border="0" width="90" src="https://github.com/images/modules/download/zip.png"></a>

