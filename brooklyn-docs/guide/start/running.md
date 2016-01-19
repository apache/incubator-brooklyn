---
title: Running Apache Brooklyn
title_in_menu: Running Apache Brooklyn
layout: website-normal
menu_parent: index.md
---

This guide will walk you through deploying an example 3-tier web application to a public cloud, and demonstrate the autoscaling capabilities of the Brooklyn platform.

An overview of core [Brooklyn concepts](./concept-quickstart.html){:target="_blank"} is available for reference.

Two methods of deployment are detailed in this tutorial, using virtualisation with Vagrant and a local install. Both assume that you are using Linux or Mac OS X.

## Install Apache Brooklyn

{::options parse_block_html="true" /}

<ul class="nav nav-tabs">
    <li class="active impl-1-tab"><a data-target="#impl-1, .impl-1-tab" data-toggle="tab" href="#">Vagrant</a></li>
    <li class="impl-2-tab"><a data-target="#impl-2, .impl-2-tab" data-toggle="tab" href="#">Local Install</a></li>
</ul>

<div class="tab-content">
<div id="impl-1" class="tab-pane fade in active">

[Vagrant](https://www.vagrantup.com/){:target="_blank"} is a software package which automates the process of setting up virtual environments such as [Oracle VirtualBox](https://www.virtualbox.org){:target="_blank"}. We recommend it as
the easiest way of getting started with Apache Brooklyn.

Firstly, download and install:

 * [Vagrant](http://www.vagrantup.com/downloads){:target="_blank"}
 * [Oracle VirtualBox](https://www.virtualbox.org/wiki/Downloads){:target="_blank"}
 
Then download the default Apache Brooklyn vagrant configuration from [here](https://github.com/johnmccabe/vagrant-brooklyn-getting-started/archive/master.tar.gz){:target="_blank"}. This configuration contains everything you need to get started using Apache Brooklyn.

Extract the `tar.gz` archive and navigate into the expanded `vagrant-brooklyn-getting-started-master` folder

{% highlight bash %}
$ tar xvf master.tar.gz
$ cd vagrant-brooklyn-getting-started-master
{% endhighlight %}


</div>
<div id="impl-2" class="tab-pane fade">

Download the Apache Brooklyn binary distribution as described on [the download page]({{site.path.website}}/download/){:target="_blank"}.

{% if brooklyn_version contains 'SNAPSHOT' %}
Extract the `tar.gz` archive (note: as this is a -SNAPSHOT version, your filename will be slightly different):
{% else %}
Extract the `tar.gz` archive and navigate into the expanded `apache-brooklyn-{{ site.brooklyn-version }}` folder.
{% endif %}

{% if brooklyn_version contains 'SNAPSHOT' %}
{% highlight bash %}
$ tar -zxf apache-brooklyn-dist-{{ site.brooklyn-version }}-timestamp-dist.tar.gz
$ cd apache-brooklyn-{{ site.brooklyn.version }}
{% endhighlight %}
{% else %}
{% highlight bash %}
$ tar -zxf apache-brooklyn-{{ site.brooklyn-version }}-dist.tar.gz
$ cd apache-brooklyn-{{ site.brooklyn.version }}
{% endhighlight %}
{% endif %}

**Note**: You'll need a [Java Runtime Environment (JRE)](https://www.java.com){:target="_blank"} installed (version 7 or later), as Brooklyn is Java under the covers.

It is not necessary at this time, but depending on what you are going to do, 
you may wish to set up other configuration options first:
 
* [Security](../ops/brooklyn_properties.html)
* [Persistence](../ops/persistence/)
* [Cloud credentials](../ops/locations/)

</div>
</div>

---

## Launch Apache Brooklyn

<ul class="nav nav-tabs">
    <li class="active impl-1-tab"><a data-target="#impl-1, .impl-1-tab" data-toggle="tab" href="#">Vagrant</a></li>
    <li class="impl-2-tab"><a data-target="#impl-2, .impl-2-tab" data-toggle="tab" href="#">Local Install</a></li>
</ul>

<div class="tab-content">
<div id="impl-1" class="tab-pane fade in active">

Now start Apache Brooklyn with the following command:

{% highlight bash %}
$ vagrant up brooklyn
{% endhighlight %}

You can see if Apache Brooklyn launched OK by viewing the log files with the command

{% highlight bash %}
$ vagrant ssh brooklyn --command 'sudo journalctl -n15 -f -u brooklyn'
{% endhighlight %}

</div>
<div id="impl-2" class="tab-pane fade">

Now start Apache Brooklyn with the following command:

{% highlight bash %}
$ bin/brooklyn launch
{% endhighlight %}

The application should then output it's log into the console

</div>
</div>

---

## Control Apache Brooklyn

Apache Brooklyn has a web console which can be used to control the application. The Brooklyn log will contain the address of the management interface:

<pre>
INFO  Started Brooklyn console at http://127.0.0.1:8081/, running classpath://brooklyn.war
</pre>

By default it can be accessed by opening [127.0.0.1:8081](http://127.0.0.1:8081){:target="_blank"} in your web browser. 

The rest of this getting started guide uses the Apache Brooklyn command line interface (CLI). To use this, download and install the tool as described on the [CLI GitHub page](https://github.com/brooklyncentral/brooklyn-cli){:target="_blank"}.

The CLI provides the command `br`, it's full usage is described in the user manual which can be found [here](../ops/cli/){:target="_blank"}

## Next

The first thing we want to do with Brooklyn is **[deploy a blueprint](blueprints.html)**.
