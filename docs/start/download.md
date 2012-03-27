---
layout: page
title: Downloads
toc: ../toc.json
---


## Maven

If you use maven, you can get add Brooklyn to a project by adding the following dependency
and repositories to your pom:

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
            <groupId>brooklyn</groupId>
            <artifactId>brooklyn-all</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
{% endhighlight %}

Brooklyn-All (used above) brings in all dependencies, including jclouds and Apache Whirr.
If you prefer a smaller repo you might want just ``brooklyn-core``,  ``brooklyn-policies``, 
and some of ``brooklyn-{software-{webapp,database,messaging},systems-hadoop}``.
(Browse the full list [here](http://ccweb.cloudsoftcorp.com/maven/libs-snapshot-local/brooklyn/).)


## The All Jar

If you prefer to grab a JAR containing all of Brooklyn and its dependencies, you'll find that here:

<!-- BROOKLYN_VERSION_BELOW -->
* [SNAPSHOT](http://ccweb.cloudsoftcorp.com/maven/libs-snapshot-local/brooklyn/brooklyn-all/0.4.0-SNAPSHOT/)


## Sources

Full source is at [github.com/cloudsoft/brooklyn](github.com/cloudsoft/brooklyn).
Information on working with the source is [here]({{ site.url }}/dev/code).

Alternatively you can download archives of the source directly:

<a href="https://github.com/cloudsoft/brooklyn/tarball/master"><img border="0" width="90" src="https://github.com/images/modules/download/tar.png"></a>
<a href="https://github.com/cloudsoft/brooklyn/zipball/master"><img border="0" width="90" src="https://github.com/images/modules/download/zip.png"></a>


