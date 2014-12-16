---
layout: website-normal
title: Installing on a server
---
Here we present two *alternatives* to install Brooklyn:

- [Running the *installation script*](#script)
- [Manual installation](#manual)

## <a id="script"></a> Running the installation script
There is a simple bash script available to help with the installation process. 

#### Script prerequisites
The script assumes that the server is a recent *RHEL/CentOS 6.x Linux* or *Ubuntu 12.04* installation, but other Linux variants have been tested successfully.

The script will install Java 7 and other required packages if they are not present. You must have root access over [passwordless SSH]({{ site.path.website }}/documentation/passwordless-ssh.html) to install brooklyn, but the service runs as an ordinary user once installed. 

To manage the brooklyn service you must also be able to connect to port 8081 remotely.

Once the above prerequisites are satisfied, you should be able to run successfully:
{% highlight bash %}
$ curl -o brooklyn-install.sh -L https://github.com/apache/incubator-brooklyn/raw/master/brooklyn-install.sh
$ chmod +x ./brooklyn-install.sh
$ ./brooklyn-install.sh -s -r <your-server-ip>
{% endhighlight %}

## <a id="manual"></a> Manual installation

1. [Set up the prerequisites](#prerequisites)
1. [Download Brooklyn](#download)
1. [Configuring brooklyn.properties](#configuring-properties)
1. [Configuring catalog.xml](#configuring-catalog)
1. [Test the installation](#confirm)

### <a id="prerequisites"></a>Set up the prerequisites

Before installing Apache Brooklyn, you will need to configure the host as follows. 

* install Java JRE or SDK (version 6 or later)
* install [SSH key]({{ site.path.website }}/documentation//ssh-key.html), if not available.
* enable [passwordless ssh login]({{ site.path.website }}/documentation/passwordless-ssh.html).
* create a `~/.brooklyn` directory on the host with `$ mkdir ~/.brooklyn`
* Check your iptables service, and if enabled, make sure that it accepts all incoming connections to 8443+ ports.
* [optional] Increase [linux kernel entropy]({{ site.path.website }}/documentation//increase-entropy.html) for faster ssh connections.

## <a id="download"></a>Download Brooklyn

Download the [Brooklyn distribution]({{ site.data.brooklyn.url.dist.tgz }}) and expand it to your home directory ( `~/` ), or in a location of your choice. Other [download options]({{site.path.website}}/download.html) are available.

{% if brooklyn_version contains 'SNAPSHOT' %}
Expand the `tar.gz` archive (note: as this is a -SNAPSHOT version, your filename will be slightly different):
{% else %}
Expand the `tar.gz` archive:
{% endif %}

{% if brooklyn_version contains 'SNAPSHOT' %}
{% highlight bash %}
$ tar -zxf brooklyn-dist-{{ site.data.brooklyn.version }}-timestamp-dist.tar.gz
{% endhighlight %}
{% else %}
{% highlight bash %}
$ tar -zxf brooklyn-dist-{{ site.data.brooklyn.version }}-dist.tar.gz
{% endhighlight %}
{% endif %}

This will create a `brooklyn-{{ site.data.brooklyn.version }}` folder.

Let's setup some paths for easy commands.

{% highlight bash %}
$ cd brooklyn-{{ site.data.brooklyn.version }}
$ BROOKLYN_DIR="$(pwd)"
$ export PATH=$PATH:$BROOKLYN_DIR/bin/
{% endhighlight %}

## <a id="configuring-properties"></a>Configuring brooklyn.properties
Brooklyn deploys applications to Locations. *Locations* can be clouds, machines with fixed IPs or localhost (for testing).

By default Brooklyn loads configuration parameters (including credentials for any cloud accounts) from 

`~/.brooklyn/brooklyn.properties` 

The `brooklyn.properties` is the main configuration file for deployment locations. Contains the connection details and credentials for all public or on-premises cloud providers, as well as controlling some application startup and security options.

Create a `.brooklyn` folder in your home directory and download the template [brooklyn.properties](../quickstart/brooklyn.properties) to that folder.

{% highlight bash %}
$ mkdir -p ~/.brooklyn
$ wget -O ~/.brooklyn/brooklyn.properties {{site.url_root}}{{site.path.website}}/quickstart/brooklyn.properties
$ chmod 600 ~/.brooklyn/brooklyn.properties
{% endhighlight %}

You may need to edit `~/.brooklyn/brooklyn.properties` to ensure that brooklyn can access cloud locations for application deployment.

## <a id="configuring-catalog"></a>Configuring catalog.xml
By default Brooklyn loads the catalog of available application components and services from 
`~/.brooklyn/catalog.xml`. 

{% highlight bash %}
$ wget -O ~/.brooklyn/catalog.xml {{site.url_root}}{{site.path.website}}/quickstart/catalog.xml
{% endhighlight %}

The `catalog.xml` is the application blueprint catalog. The above example file contains some blueprints which will be automatically downloaded from the web if you run them.

You may need to edit `~/.brooklyn/catalog.xml` to update links to any resources for download.

## <a id="confirm"></a>Confirm installation
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
