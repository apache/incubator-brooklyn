---
layout: website-normal
title: Production Installation
---

{% include fields.md %}

To install Apache Brooklyn on a production server:

1. [Set up the prerequisites](#prerequisites)
1. [Download Apache Brooklyn](#download)
1. [Configuring brooklyn.properties](#configuring-properties)
1. [Configuring default.catalog.bom](#configuring-catalog)
1. [Test the installation](#confirm)

This guide covers the basics. You may also wish to configure:

* [Logging](logging.md)
* [Persistence](persistence/)
* [High availability](high-availability.md)


### <a id="prerequisites"></a>Set up the Prerequisites

Check that the server meets the [requirements](requirements.md).
Then configure the server as follows:

* install Java JRE or JDK (version 7 or later)
* install an [SSH key]({{ site.path.guide }}/ops/locations/ssh-keys.html), if not available
* enable [passwordless ssh login]({{ site.path.guide }}/ops/locations/ssh-keys.html)
* create a `~/.brooklyn` directory on the host with `$ mkdir ~/.brooklyn`
* check your `iptables` or other firewall service, making sure that incoming connections on port 8443 is not blocked
* check that the [linux kernel entropy]({{ site.path.website }}/documentation/increase-entropy.html) is sufficient


### <a id="download"></a>Download Apache Brooklyn

Download Brooklyn and obtain a binary build as described on [the download page]({{site.path.website}}/download/).

{% if brooklyn_version contains 'SNAPSHOT' %}
Expand the `tar.gz` archive (note: as this is a -SNAPSHOT version, your filename will be slightly different):
{% else %}
Expand the `tar.gz` archive:
{% endif %}

{% if brooklyn_version contains 'SNAPSHOT' %}
{% highlight bash %}
% tar -zxf apache-brooklyn-dist-{{ site.brooklyn-stable-version }}-timestamp-dist.tar.gz
{% endhighlight %}
{% else %}
{% highlight bash %}
% tar -zxf apache-brooklyn-{{ site.brooklyn-stable-version }}-dist.tar.gz
{% endhighlight %}
{% endif %}

This will create a `apache-brooklyn-{{ site.brooklyn-stable-version }}` folder.

Let's setup some paths for easy commands.

{% highlight bash %}
% cd apache-brooklyn-{{ site.brooklyn-stable-version }}
% BROOKLYN_DIR="$(pwd)"
% export PATH=$PATH:$BROOKLYN_DIR/bin/
{% endhighlight %}


### <a id="configuring-properties"></a>Configuring brooklyn.properties

Set up `brooklyn.properties` as described [here](brooklyn_properties.md):

* Configure the users who should have access
* Turn on HTTPS
* Supply credentials for any pre-defined clouds

It may be useful to use the following script to install an initial `brooklyn.properties`:

{% highlight bash %}
% mkdir -p ~/.brooklyn
% wget -O ~/.brooklyn/brooklyn.properties {{brooklyn_properties_url_live}}
% chmod 600 ~/.brooklyn/brooklyn.properties
{% endhighlight %}


### <a id="configuring-catalog"></a>Configuring the Catalog

By default Brooklyn loads the catalog of available application components and services from 
`default.catalog.bom` on the classpath. The initial catalog is in `conf/brooklyn/` in the dist.
If you have a preferred catalog, simply replace that file.

[More information on the catalog is available here.](catalog/)


### <a id="confirm"></a>Confirm Installation

Launch Brooklyn in a disconnected session so it will remain running after you have logged out:

{% highlight bash %}
% nohup bin/brooklyn launch > /dev/null 2&>1 &
{% endhighlight %}

Apache Brooklyn should now be running on port 8081 (or other port if so specified).


