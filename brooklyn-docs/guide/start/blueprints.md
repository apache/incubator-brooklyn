---
title: Deploying Blueprints
layout: website-normal
menu_parent: index.md
children:
- { section: Launching from a Blueprint, title: Blueprint } 
---

Blueprints are the descriptors or patterns which describe how Apache Brooklyn should deploy applications.

## Launching from a Blueprint

We'll start by deploying an application with a simple YAML blueprint containing a Tomcat server.

Copy the blueprint below into a text file, "myapp.yaml", in your workspace (Note, to copy the file you can
hover your mouse over the right side of the text box below to get a Javascript "copy" button.)

{% highlight yaml %}
name: Tomcat
location:
services:
- serviceType: brooklyn.entity.webapp.tomcat.TomcatServer
{% endhighlight %}


## Locations

Before you create an application with the configuration you need to modify the YAML to specify a location. Locations in Apache Brooklyn are server resources Brooklyn can use to deploy applications. These locations may be servers or cloud providers which provide access to servers. 

In order to configure the location Apache Brooklyn launches an application, replace the ```location:``` element with values for your chosen target environment, here are some examples for the variouss location types.

{::options parse_block_html="true" /}

<ul class="nav nav-tabs">
    <li class="active impl-1-tab"><a data-target="#impl-1, .impl-1-tab" data-toggle="tab" href="#">Vagrant</a></li>
    <li class="impl-2-tab"><a data-target="#impl-2, .impl-2-tab" data-toggle="tab" href="#">Clouds</a></li>
    <li class="impl-3-tab"><a data-target="#impl-3, .impl-3-tab" data-toggle="tab" href="#">BYON</a></li>
    <li class="impl-4-tab"><a data-target="#impl-4, .impl-4-tab" data-toggle="tab" href="#">Localhost</a></li>
</ul>

<div class="tab-content">
<div id="impl-1" class="tab-pane fade in active">

The Vagrant configuration described in [Running Apache Brooklyn](./running.html), on the previous page is the **recommended** way of running this tutorial. This configuration comes with four blank vagrant configurations called byon1 to byon4

These can be launched by entering the following line into the terminal in the vagrant configuration directory.

{% highlight bash %}
 $ vagrant up byon1 byon2 byon3 byon4
{% endhighlight %}

The location in "myapp.yaml" can then be replaced by the following YAML to launch to these

{% highlight yaml %}
location:
  byon:
    user: vagrant
    password: vagrant
    hosts:
      - 10.10.10.101
      - 10.10.10.102
      - 10.10.10.103
      - 10.10.10.104
{% endhighlight %}

</div>
<div id="impl-2" class="tab-pane fade">

Apache Brooklyn uses [Apcahe jclouds](http://jclouds.apache.org/) to support a range of cloud locations. More information on the range of providers and configurations is available [here](../ops/locations/#clouds).

As an example here is a configuration for [Amazon Web Services (AWS)](http://www.aws.amazon.com). Swap the identity and credential with your AWS account details then replace the location in your "myapp.yaml" with this.

{% highlight yaml %}
location:
  jclouds:aws-ec2:
    identity: ABCDEFGHIJKLMNOPQRST
    credential: s3cr3tsq1rr3ls3cr3tsq1rr3ls3cr3tsq1rr3l
{% endhighlight %}

</div>
<div id="impl-3" class="tab-pane fade">

The Bring Your Own Nodes (BYON) configuration allows Apache Brooklyn to make use of already available servers. These can be specified by a list of IP addresses with a user and password as shown below. More information including the full range of configuration options is available [here](../ops/locations/#byon). 

{% highlight yaml %}
location:
  byon:
    user: myuser
    password: mypassword
    # or...
    #privateKeyFile: ~/.ssh/my.pem
    hosts:
    - 192.168.0.18
    - 192.168.0.19
{% endhighlight %}

</div>
<div id="impl-4" class="tab-pane fade">

</div>
</div>

---

**Note**: For instructions on setting up a variety of locations or storing credentials/locations in a file on disk rather than in the blueprint see __[Locations](../ops/locations)__ in the Operations section of the User Guide.

## Deploying the Application

First, log in to brooklyn with the command line tool (CLI) by typing:
{% highlight bash %}
$ br login http://localhost:8081/
{% endhighlight %}

To secure the Apache Brooklyn instance you can add a username and password to Brooklyn's properties file, as described in the User Guide [here](../ops/brooklyn_properties.html). 
Then the login command will require the additional parameters of the userid and password.

Now you can create the application with the command below:

{% highlight bash %}
$ br deploy myapp.yaml
Id:       hTPAF19s   
Name:     Tomcat   
Status:   In progress   
{% endhighlight %}

Depending on your choice of location it may take some time for the application to start, the next page describes how 
you can monitor the progress of the application deployment and verify its successful deployment.

## Next

Having deployed an application, the next step is **[monitoring and managing](managing.html)** it.