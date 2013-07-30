---
layout: page
title: Downloads
toc: ../toc.json
---
{% include fields.md %}

First time user? The [getting started]({{ site.url }}/use/guide/quickstart/index.html) guide will walk you through downloading Brooklyn, setup of your `brooklyn.properties` and `catalog.xml` files, and then trying the [examples](#examples). 

## Download Version {{ site.brooklyn-version }}

<table>
<tr>
	<th style='text-align:left'>Download</th>
	<th style='text-align:left'>File/Format</th>
	<th>checksums <small><a href="/meta/verify.html" title='Instructions on verifying the integrity of your downloads.'>(?)</a></small></th>
</tr>
<tr>
	<td style='text-align:left;vertical-align:top' rowspan='2'>Distro</td>
	<td style='text-align:left'><a href='{{ this_dist_url_zip }}' title='Download ZIP archive'>brooklyn-dist-{{ site.brooklyn-version }}-dist.zip</a></td>
	<td><small><a href='{{ this_dist_url_zip }}.asc'>PGP</a>, <a href='{{ this_dist_url_zip }}.sha1'>SHA1</a></small></td>
</tr>
<tr>
	<td style='text-align:left'><a href='{{ this_dist_url_tgz }}' title='Download TGZ archive'>brooklyn-dist-{{ site.brooklyn-version }}-dist.tar.gz</a></td>
	<td ><small><a href='{{ this_dist_url_tgz }}.asc'>PGP</a>, <a href='{{ this_dist_url_tgz }}.sha1'>SHA1</a></small></td>
</tr>
<tr>
	<td style='text-align:left'>All Jar</td>
	<td style='text-align:left'><a href='{{ this_alljar_url_jar }}' title='Download the ALL JAR'>brooklyn-all-{{ site.brooklyn-version }}-with-dependencies.jar</a></td>
	<td ><small><a href='{{ this_alljar_url_jar }}.asc'>PGP</a>, <a href='{{ this_alljar_url_jar }}.sha1'>SHA1<a/></small></td>
</tr>
<tr>
	<td style='text-align:left'>Release notes</td>
	<td style='text-align:left'><a href='{{ site.url }}/start/release-notes.html'>{{ site.brooklyn-version }}</a></td>
	<td> - </td>
</tr>
</table>
{% if SNAPSHOT %}
<span style='float:right'><small>Source: <a href='{{ this_anything_url_search }}'>Sonatype</a></small></span>
{% else %}
<span style='float:right'><small>Source: <a href='{{ this_anything_url_search }}'>Maven Central</a></small></span>
{% endif %}

<a name="distro"></a>
## The Distro

The distribution archive contains Brooklyn as a standalone executable package.

* previous stable versions: [Maven Central]({{ mavencentral_repo_groupid_url }}brooklyn-dist/)
* previous snapshot versions: [Sonatype]({{ sonatype_repo_groupid_url }}brooklyn-dist/)

{% if site.brooklyn-version contains 'SNAPSHOT' %} 
**Please note**: You are reading the documentation for a snapshot version of Brooklyn.
You should always confirm that the source and date for snapshot artifacts.
{% endif %}


<a name="alljar"></a>
## The All Jar

This is a single JAR containing all of Brooklyn and its dependencies, for developing Brooklyn into your own applications. Just download your preferred flavour and add it to your classpath.

{% if SNAPSHOT %}{% else %}
* [Version {{ site.brooklyn-version }}](http://search.maven.org/#artifactdetails|io.brooklyn|brooklyn-all|{{ site.brooklyn-version }}|jar) 
{% endif %}
* previous stable versions: [Maven Central]({{ mavencentral_repo_groupid_url }}brooklyn-all/)
* previous snapshot versions: [Sonatype]({{ sonatype_repo_groupid_url }}brooklyn-all/)

{% if site.brooklyn-version contains 'SNAPSHOT' %} 
**Again**, check the source and date for SNAPSHOT JARs.
{% endif %}

## Release Notes

Release notes can be found [here]({{ site.url }}/start/release-notes.html).

<a name="examples"></a>
## Examples

{% if SNAPSHOT %}
As this is a snapshot version of Brooklyn, please find the examples in the main Brooklyn codebase (in the `/examples` directory).

When this version is released, the [brooklyn-examples git repository](http://github.com/brooklyncentral/brooklyn-examples) will be updated and instructions for use will be included here. 

{% else %}

You can checkout the examples from the [brooklyn-examples git repository](http://github.com/brooklyncentral/brooklyn-examples).

Maven (v3) is required to build them, as described [here]({{ site.url }}/dev/build/).
The examples for this version ({{ site.brooklyn-version }}) are in the branch 
`{% if SNAPSHOT %}{{ site.brooklyn-snapshot-git-branch }}{% else %}{{ site.brooklyn-version }}{% endif %}`, so if you have `git` and `mvn` already, you can simply:

{% highlight bash %}
% git clone https://github.com/brooklyncentral/brooklyn-examples.git
% cd brooklyn-examples
{% if brooklyn_examples_branch == 'master' %}{% else %}% git checkout {{ brooklyn_examples_branch }}
{% endif %}% mvn clean install
{% endhighlight %}
 

If you don't use `git`, you can download the projects as a tarball instead
from [this link](https://github.com/brooklyncentral/brooklyn-examples/tarball/{{ brooklyn_examples_branch }}). 
These commands should do the trick:

{% highlight bash %}
% curl -L -o brooklyn-examples-{{ brooklyn_examples_branch }}.tgz \
     https://github.com/brooklyncentral/brooklyn-examples/tarball/{{ brooklyn_examples_branch }}
% tar xvfz brooklyn-examples-{{ brooklyn_examples_branch }}.tgz
% mv brooklyncentral-brooklyn-examples-* brooklyn-examples-{{ brooklyn_examples_branch }} \
     # change the strange name which github assigns in the tarball
% mvn clean install
{% endhighlight %}


A good example to start with is the [Elastic Web Cluster]({{site.url}}/use/examples/webcluster.html).

{% endif %} 


<a name="maven"></a>
## Maven

If you use Maven, you can add Brooklyn with the following in your pom:

{% highlight xml %}
    <dependencies>
        <dependency>
            <groupId>io.brooklyn</groupId>
            <artifactId>brooklyn-all</artifactId>
            <version>{{ site.brooklyn-version }}</version>
        </dependency>
    </dependencies>
{% endhighlight %}

`brooklyn-all` (used above) brings in all dependencies, including jclouds and Apache Whirr.
If you prefer a smaller repo you might want just ``brooklyn-core``,  ``brooklyn-policies``, 
and some of ``brooklyn-software-webapp``,  ``brooklyn-software-database``, ``brooklyn-software-messaging``, or others
(browse the full list [here]({{ this_anything_url_search }})).

If you wish to use the Sonatype and/or Cloudsoft repositories (particularly for snapshots),
you can add some of the following sections:

{% highlight xml %}
    <repositories>
        <repository>
            <id>cloudsoft-cloudfront-releases-repo</id>
            <url>http://developers.cloudsoftcorp.com/maven/releases/</url>
        </repository>
        <!-- optional for snapshot versions -->
        <repository>
            <id>sonatype-nexus-snapshots</id>
            <name>Sonatype Nexus Snapshots</name>
            <url>https://oss.sonatype.org/content/repositories/snapshots</url>
            <releases> <enabled>false</enabled> </releases>
            <snapshots> <enabled>true</enabled> </snapshots>
        </repository>
        <repository>
            <id>cloudsoft-cloudfront-snapshots-repo</id>
            <url>http://developers.cloudsoftcorp.com/maven/snapshots/</url>
            <snapshots>
                <enabled>true</enabled>
                <updatePolicy>never</updatePolicy>
                <checksumPolicy>fail</checksumPolicy>
           </snapshots>
         </repository>
    </repositories>
{% endhighlight %}

{% if SNAPSHOT %}
**Please note**: to use a snapshot version of Brooklyn, you must either have Brooklyn built locally
or one of the additional snapshot repositories above.
{% endif %}


<a name="source"></a>
## Source Code

Full source is at [github.com/brooklyncentral/brooklyn](http://github.com/brooklyncentral/brooklyn).
Information on working with the source is [here]({{ site.url }}/dev/code).

Alternatively you can download archives of the source directly:

<a href="https://github.com/brooklyncentral/brooklyn/tarball/master"><img border="0" width="90" src="https://github.com/images/modules/download/tar.png"></a>
<a href="https://github.com/brooklyncentral/brooklyn/zipball/master"><img border="0" width="90" src="https://github.com/images/modules/download/zip.png"></a>

