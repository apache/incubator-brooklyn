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

You can grab the distribution artifact, containing Brooklyn, its dependencies and launch scripts, here:

<!-- BROOKLYN_VERSION_BELOW -->
* [0.4.0-M2.tar.gz](http://developers.cloudsoftcorp.com/download/maven2/io/brooklyn/brooklyn-dist/0.4.0-M2/brooklyn-dist-0.4.0-M2-dist.tar.gz)
* [0.4.0-M2.zip](http://developers.cloudsoftcorp.com/download/maven2/io/brooklyn/brooklyn-dist/0.4.0-M2/brooklyn-dist-0.4.0-M2-dist.zip)
* [0.4.0-M1.tar.gz](http://developers.cloudsoftcorp.com/download/maven2/io/brooklyn/brooklyn-dist/0.4.0-M1/brooklyn-dist-0.4.0-M1-dist.tar.gz)
* [0.4.0-M1.zip](http://developers.cloudsoftcorp.com/download/maven2/io/brooklyn/brooklyn-dist/0.4.0-M1/brooklyn-dist-0.4.0-M1-dist.zip)

Just download your preferred flavour and unpack.

<a name="alljar"></a>
## The All Jar

You can grab a single JAR containing all of Brooklyn and its dependencies here:

<!-- BROOKLYN_VERSION_BELOW -->
* [0.4.0-M2](http://developers.cloudsoftcorp.com/download/maven2/io/brooklyn/brooklyn-all/0.4.0-M2/)
* [0.4.0-M1](http://developers.cloudsoftcorp.com/download/maven2/io/brooklyn/brooklyn-all/0.4.0-M1/)
* [SNAPSHOT](http://ccweb.cloudsoftcorp.com/maven/libs-snapshot-local/io/brooklyn/brooklyn-all/0.4.0-SNAPSHOT/)

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

If you are looking for a specific version (versions of the examples are aligned with Brooklyn non-snapshot releases) try the following command:

{% highlight bash %}
% curl -L https://github.com/brooklyncentral/brooklyn-examples/tarball/0.4.0-M2 -o brooklyn-0.4.0-M2.tgz
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

