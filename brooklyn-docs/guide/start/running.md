---
title: Running Apache Brooklyn
title_in_menu: Running Apache Brooklyn
layout: website-normal
menu_parent: index.md
---

This guide will walk you through deploying an example 3-tier web application to a public cloud, and demonstrate the autoscaling capabilities of the Brooklyn platform.

An overview of core [Brooklyn concepts](./concept-quickstart.html) is available for reference.

This tutorial assumes that you are using Linux or Mac OS X.

## Install Apache Brooklyn

Download the Apache Brooklyn binary distribution as described on [the download page]({{site.path.website}}/download/).

{% if brooklyn_version contains 'SNAPSHOT' %}
Expand the `tar.gz` archive (note: as this is a -SNAPSHOT version, your filename will be slightly different):
{% else %}
Expand the `tar.gz` archive:
{% endif %}

{% if brooklyn_version contains 'SNAPSHOT' %}
{% highlight bash %}
$ tar -zxf apache-brooklyn-dist-{{ site.brooklyn-version }}-timestamp-dist.tar.gz
{% endhighlight %}
{% else %}
{% highlight bash %}
$ tar -zxf apache-brooklyn-{{ site.brooklyn-version }}-dist.tar.gz
{% endhighlight %}
{% endif %}

This will create a `apache-brooklyn-{{ site.brooklyn-version }}` folder.

**Note**: You'll need a Java JRE or SDK installed (version 7 or later), as Brooklyn is Java under the covers.

It is not necessary at this time, but depending on what you are going to do, 
you may wish to set up other configuration options first:
 
* [Security](../ops/brooklyn_properties.html)
* [Persistence](../ops/persistence/)
* [Cloud credentials](../ops/locations/)

## Launch Apache Brooklyn

Now start Brooklyn with the following command:

{% highlight bash %}
$ cd apache-brooklyn-{{ site.brooklyn.version }}
$ bin/brooklyn launch
{% endhighlight %}

Brooklyn will output the address of the management interface:

<pre>
INFO  Starting brooklyn web-console on loopback interface because no security config is set
INFO  Started Brooklyn console at http://127.0.0.1:8081/, running classpath://brooklyn.war and []
</pre>

### Next

Next, open the web console on [127.0.0.1:8081](http://127.0.0.1:8081). 
No applications have been deployed yet, so the "Create Application" dialog opens automatically:
let's remedy this by **[deploying a blueprint](blueprints.html)**.