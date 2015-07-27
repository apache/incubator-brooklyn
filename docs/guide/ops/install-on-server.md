---
layout: website-normal
title: Installing Brooklyn
---

{% include fields.md %}

Here we present two *alternatives* to install Brooklyn:

- [Running the *installation script*](#script)
- [Manual installation](#manual)


## <a id="script"></a> Running the Installation Script

There is a simple bash script available to help with the installation process. 

#### Script prerequisites
The script assumes that the server is a recent *RHEL/CentOS 6.x Linux* or *Ubuntu 12.04* installation, but other Linux variants have been tested successfully.

The script will install Java 7 and other required packages if they are not present. 
You must have root access over [passwordless SSH]({{ site.path.guide }}/ops/locations/ssh-keys.html) 
to install brooklyn, but the service runs as an ordinary user once installed. 

To manage the brooklyn service you must also be able to connect to port 8081 remotely.

Once the above prerequisites are satisfied, you should be able to run successfully:
{% highlight bash %}
$ curl -o brooklyn-install.sh -L https://github.com/apache/incubator-brooklyn/raw/master/brooklyn-install.sh
$ chmod +x ./brooklyn-install.sh
$ ./brooklyn-install.sh -s -r <your-server-ip>
{% endhighlight %}


## <a id="manual"></a> Manual Installation

1. [Set up the prerequisites](#prerequisites)
1. [Download Brooklyn](#download)
1. [Configuring brooklyn.properties](#configuring-properties)
1. [Configuring default.catalog.bom](#configuring-catalog)
1. [Test the installation](#confirm)


### <a id="prerequisites"></a>Set up the Prerequisites

Before installing Apache Brooklyn, it is recommented to configure the host as follows. 

* install Java JRE or SDK (version 6 or later)
* install an [SSH key]({{ site.path.guide }}/ops/locations/ssh-keys.html), if not available
* enable [passwordless ssh login]({{ site.path.guide }}/ops/locations/ssh-keys.html)
* create a `~/.brooklyn` directory on the host with `$ mkdir ~/.brooklyn`
* check your `iptables` or other firewall service, making sure that incoming connections on port 8443 is not blocked
* check that the [linux kernel entropy]({{ site.path.website }}/documentation/increase-entropy.html) is sufficient


### <a id="download"></a>Download Brooklyn

Download Brooklyn and obtain a binary build as described on [the download page]({{site.path.website}}/download/).

{% if brooklyn_version contains 'SNAPSHOT' %}
Expand the `tar.gz` archive (note: as this is a -SNAPSHOT version, your filename will be slightly different):
{% else %}
Expand the `tar.gz` archive:
{% endif %}

{% if brooklyn_version contains 'SNAPSHOT' %}
{% highlight bash %}
$ tar -zxf brooklyn-dist-{{ site.brooklyn-stable-version }}-timestamp-dist.tar.gz
{% endhighlight %}
{% else %}
{% highlight bash %}
$ tar -zxf brooklyn-{{ site.brooklyn-stable-version }}-dist.tar.gz
{% endhighlight %}
{% endif %}

This will create a `brooklyn-{{ site.brooklyn-stable-version }}` folder.

Let's setup some paths for easy commands.

{% highlight bash %}
$ cd brooklyn-{{ site.brooklyn-stable-version }}
$ BROOKLYN_DIR="$(pwd)"
$ export PATH=$PATH:$BROOKLYN_DIR/bin/
{% endhighlight %}


### <a id="configuring-properties"></a>Configuring brooklyn.properties

Brooklyn deploys applications to Locations. *Locations* can be clouds, machines with fixed IPs or localhost (for testing).

By default Brooklyn loads configuration parameters (including credentials for any cloud accounts) from 

`~/.brooklyn/brooklyn.properties` 

The `brooklyn.properties` is the main configuration file for deployment locations. Contains the connection details and credentials for all public or on-premises cloud providers, as well as controlling some application startup and security options.

Create a `.brooklyn` folder in your home directory and download the template [brooklyn.properties]({{brooklyn_properties_url_path}}) to that folder.

{% highlight bash %}
$ mkdir -p ~/.brooklyn
$ wget -O ~/.brooklyn/brooklyn.properties {{brooklyn_properties_url_live}}
$ chmod 600 ~/.brooklyn/brooklyn.properties
{% endhighlight %}

You may need to edit `~/.brooklyn/brooklyn.properties` to ensure that brooklyn can access cloud locations for application deployment.


### <a id="configuring-catalog"></a>Configuring the Catalog

By default Brooklyn loads the catalog of available application components and services from 
`default.catalog.bom` on the classpath. The initial catalog is in `conf/brooklyn/` in the dist.
If you have a preferred catalog, simply replace that file.


### <a id="confirm"></a>Confirm Installation

We can do a quick test drive by launching Brooklyn:

{% highlight bash %}
$ brooklyn launch
{% endhighlight %}

Brooklyn will output the address of the management interface:

{% highlight bash %}
INFO  Starting brooklyn web-console on loopback interface because no security config is set

INFO  Started Brooklyn console at http://127.0.0.1:8081/, running classpath://brooklyn.war and []
{% endhighlight %}

Stop Brooklyn with ctrl-c.
