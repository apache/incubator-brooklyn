---
layout: page
title: Downloads
toc: ../toc.json
---

## Contents

* [A Single Jar](#alljar)
* [Examples](#examples)
* [Maven](#maven)  
* [Source Code](#source)

<a name="alljar"></a>
## The All Jar

You can grab a single JAR containing all of Brooklyn and its dependencies here:

<!-- BROOKLYN_VERSION_BELOW -->
* [SNAPSHOT](http://ccweb.cloudsoftcorp.com/maven/libs-snapshot-local/io/brooklyn/brooklyn-all/0.4.0-M2/)

Just download your preferred flavour and add it to your classpath.

<a name="examples"></a>
## Examples

You can checkout 
[examples]({{site.url}}/use/examples) 
from [github.com/brooklyncentral/brooklyn-examples](http://github.com/brooklyncentral/brooklyn-examples),
build them with [maven (v3)]({{site.url}}/dev/build/), 
and then run them with the ``demo*.sh`` scripts in the examples.
This will take care of downloading Brooklyn and the dependencies.

A good entry point is the [Simple Web Cluster]({{site.url}}/use/examples/webcluster.html).

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
and some of ``brooklyn-{software-{webapp,database,messaging},systems-hadoop}``.
(Browse the full list [here](http://ccweb.cloudsoftcorp.com/maven/libs-snapshot-local/brooklyn/).)

**TODO: we are moving to mavencentral so the repositories section will shortly be unnecessary**

<a name="source"></a>
## Source Code

Full source is at [github.com/brooklyncentral/brooklyn](http://github.com/brooklyncentral/brooklyn).
Information on working with the source is [here]({{ site.url }}/dev/code).

Alternatively you can download archives of the source directly:

<a href="https://github.com/brooklyncentral/brooklyn/tarball/master"><img border="0" width="90" src="https://github.com/images/modules/download/tar.png"></a>
<a href="https://github.com/brooklyncentral/brooklyn/zipball/master"><img border="0" width="90" src="https://github.com/images/modules/download/zip.png"></a>

