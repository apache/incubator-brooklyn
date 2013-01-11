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

{% include fields.md %}

Distribution archives containing Brooklyn as a standalone executable package 
are available in the following formats and locations:

* **v{{ site.brooklyn-version }}** at
  {% if SNAPSHOT %}[Sonatype]({{ this_dist_url_search }})
  {% else %}[Maven Central]({{ this_dist_url_search }})
  {% endif %}: 
  **[zip]({{ this_dist_url_zip }})** 
  **[tgz]({{ this_dist_url_tgz }})** 
  **[dir]({{ this_dist_url_dir }})** 
* **all stable versions**: 
  at [Maven Central]({{ mavencentral_repo_groupid_url }}brooklyn-dist/)
  and [Cloudsoft]({{ cloudsoft_releases_base_url}}io/brooklyn/brooklyn-dist/) 
* **all snapshot versions**: 
  at [Sonatype]({{ sonatype_repo_groupid_url }}brooklyn-dist/)
  and [Cloudsoft]({{ cloudsoft_snapshots_base_url }}io/brooklyn/brooklyn-dist/)

Just download your preferred flavour and unpack.

{% if site.brooklyn-version contains 'SNAPSHOT' %} 
**Please note**: You are reading the documentation for a snapshot version of Brooklyn.
You should always confirm that the source and date for snapshot artifacts.
{% endif %}


<a name="alljar"></a>
## The All Jar

You can grab a single JAR containing all of Brooklyn and its dependencies 
here:

* **v{{ site.brooklyn-version }}** at
  {% if SNAPSHOT %}[Sonatype]({{ this_alljar_url_search }})
  {% else %}[Maven Central]({{ this_alljar_url_search }})
  {% endif %}: 
  **[jar]({{ this_alljar_url_jar }})** 
  **[dir]({{ this_alljar_url_dir }})** 
* **all stable versions**: 
  at [Maven Central]({{ mavencentral_repo_groupid_url }}brooklyn-all/)
  and [Cloudsoft]({{ cloudsoft_releases_base_url}}/io/brooklyn/brooklyn-all/) 
* **all snapshot versions**: 
  at [Sonatype]({{ sonatype_repo_groupid_url }}brooklyn-all/)
  and [Cloudsoft]({{ cloudsoft_snapshots_base_url }}io/brooklyn/brooklyn-all/)

Just download your preferred flavour and add it to your classpath{% if site.brooklyn-version contains 'SNAPSHOT' %} 
(but again, check the source and date for snapshot JARs){% endif %}.


<a name="examples"></a>
## Examples

You can checkout the examples from the [brooklyn-examples git repository](http://github.com/brooklyncentral/brooklyn-examples).
Maven (v3) is required to build them, as described [here]({{ site.url }}/dev/build/).
The examples for this version ({{ site.brooklyn-version }}) are in the branch 
`{% if SNAPSHOT %}{{ site.brooklyn-snapshot-git-branch }}{% else %}{{ site.brooklyn-version }}{% endif %}`,
so if you have `git` and `mvn` already, you can simply:

{% highlight bash %}
% git clone https://github.com/brooklyncentral/brooklyn-examples.git
% cd brooklyn-examples
{% if brooklyn_examples_branch == 'master' %}{% else %}% git checkout {{ brooklyn_examples_branch }}
{% endif %}% mvn clean install
{% endhighlight %}

{% if SNAPSHOT %}
**Please note**: for snapshot versions of Brooklyn, 
the examples in this repository may not be automatically synched.
It is recommended that you use the code for the examples included
in the main Brooklyn codebase, or use a released version.
{% endif %}  

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



<a name="maven"></a>
## Maven

If you use maven, you can add Brooklyn with the following in your pom:

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

