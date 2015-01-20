---
layout: website-normal
title: Downloads
---
{% include fields.md %}

**Brooklyn in Apache has not yet made an official clean binary release. 
The downloads on this page have not been voted on and should be used at your own risk.
Additionally some links on this page may not work until those builds have been officially released.
For more information, see the [main download page]({{ site.path.website }}/download/).**


## Download Version {{ site.brooklyn-version }}

<table class="table">
  <tr>
	<th style='text-align:left'>Download</th>
	<th style='text-align:left'>File/Format</th>
	<th>checksums <small><a href="{{ site.path.website }}/download/verify.html" title='Instructions on verifying the integrity of your downloads.'>(?)</a></small></th>
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
	<td><small><a href='{{ this_alljar_url_jar }}.asc'>PGP</a>, <a href='{{ this_alljar_url_jar }}.sha1'>SHA1</a></small></td>
  </tr>
  <tr>
    <td style='text-align:left'>Apache Repo</td>
    <td style='text-align:left'>
      <a href='{{ this_anything_url_search }}' title='Search'><i>GUI</i></a>
      —
      <a href='{{ this_dist_url_list }}' title='List'><i>dir</i></a>
    </td>
    <td> — </td>
  </tr>
  <tr>
	<td style='text-align:left'>Release Notes</td>
	<td style='text-align:left'><a href='{{ site.path.guide }}/misc/release-notes.html'>{{ site.brooklyn-version }}</a></td>
	<td> — </td>
  </tr>
</table>


<a name="distro"></a>

## The Distro

The distribution archive contains Brooklyn as a standalone executable package.

* [This version ZIP]({{ this_dist_url_zip }})
* [This version TGZ]({{ this_dist_url_tgz }})
* [Apache stable versions]({{ apache_releases_repo_groupid_url }}/brooklyn-dist/)
* [Apache snapshot versions]({{ apache_snapshots_repo_groupid_url }}/brooklyn-dist/)

Very old versions are available at [Maven Central]({{ mavencentral_repo_groupid_url }}brooklyn-dist/) for stable releases
and [Sonatype]({{ sonatype_repo_groupid_url }}brooklyn-dist/) for snapshot versions.

{% if site.brooklyn-version contains 'SNAPSHOT' %} 
**Please note**: You are reading the documentation for a snapshot version of Brooklyn.
You should always confirm that the source and date for snapshot artifacts.
{% endif %}


<a name="alljar"></a>

## The All Jar

This is a single JAR containing all of Brooklyn and its dependencies, for developing Brooklyn into your own applications. If not using maven, just add it to your classpath.

* [This version]({{ this_alljar_url_jar }})
* [All Apache stable versions]({{ apache_releases_repo_groupid_url }}/brooklyn-all/)
* [All Apache snapshot versions]({{ apache_snapshots_repo_groupid_url }}/brooklyn-all/)

Very old versions are available at [Maven Central]({{ mavencentral_repo_groupid_url }}brooklyn-all/) for stable releases
and [Sonatype]({{ sonatype_repo_groupid_url }}brooklyn-all/) for snapshot versions.

{% if site.brooklyn-version contains 'SNAPSHOT' %} 
**Again**, check the source and date for SNAPSHOT JARs.
{% endif %}

## Release Notes

Release notes can be found [here]({{ site.path.guide }}/misc/release-notes.html).

{% comment %}
TODO
<a name="examples"></a>

## Examples

Examples can be found in the main Brooklyn codebase, in the `/examples` directory.

A good example to start with is the [Elastic Web Cluster]({{site.path.guide}}/use/examples/webcluster.html).

{% endcomment %}

<a name="maven"></a>

## Maven

If you use Maven, you can add Brooklyn with the following in your pom:

<!-- the comment is included due to a jekyll/highlight bug which
     removes indentation on the first line in a highlight block;
     we want the actual XML indented so you can cut and paste into a pom.xml sensibly -->  
{% highlight xml %}
<!-- include all Brooklyn items in our project -->
    <dependencies>
        <dependency>
            <groupId>org.apache.brooklyn</groupId>
            <artifactId>brooklyn-all</artifactId>
            <version>{{ site.brooklyn-version }}</version>
        </dependency>
    </dependencies>
{% endhighlight %}

`brooklyn-all` (used above) brings in all dependencies, including jclouds.
If you prefer a smaller repo you might want just ``brooklyn-core``,  ``brooklyn-policies``, 
and some of ``brooklyn-software-webapp``,  ``brooklyn-software-database``, ``brooklyn-software-messaging``, or others
(browse the full list [here]({{ this_anything_url_search }})).

If you wish to use the Apache snapshot repo and/or Cloudsoft repositories,
you can add some of the following sections:

{% highlight xml %}
<!-- include repos for snapshot items and other dependencies -->
    <repositories>
        <repository>
            <id>apache-nexus-snapshots</id>
            <name>Apache Nexus Snapshots</name>
            <url>https://repository.apache.org/content/repositories/snapshots</url>
            <releases> <enabled>false</enabled> </releases>
            <snapshots> <enabled>true</enabled> </snapshots>
        </repository>
        <repository>
            <id>cloudsoft-cloudfront-releases-repo</id>
            <url>http://developers.cloudsoftcorp.com/maven/releases/</url>
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

Full source is at [github.com/apache/incubator-brooklyn](http://github.com/apache/incubator-brooklyn).
Information on working with the source is [here]({{ site.path.guide }}/dev/code).

Alternatively you can download archives of the source directly:

<a href="https://github.com/apache/incubator-brooklyn/tarball/master"><img border="0" width="90" src="https://github.com/images/modules/download/tar.png"></a>
<a href="https://github.com/apache/incubator-brooklyn/zipball/master"><img border="0" width="90" src="https://github.com/images/modules/download/zip.png"></a>

