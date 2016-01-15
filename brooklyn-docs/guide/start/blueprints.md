---
title: Deploying Blueprints
layout: website-normal
menu_parent: index.md
children:
- { section: Launching from a Blueprint, title: Blueprint } 
---

<div style="width: 100%; display: block; background-color: #CC9966; margin-bottom: 2px;  padding: 50px 30px 50px 80px;" >
  <h3>NOTE</h3>
  <div>
  The structure of Brooklyn's repositories is changing at present (Jan 2016). Until this is complete 
  please obtain the "br" command line tool from <a href="https://github.com/brooklyncentral/brooklyn-cli">Brooklyn Central</a>
  </div>
</div>

## Launching from a Blueprint

We'll start by deploying an application with a simple YAML blueprint containing a Tomcat server.

Copy the blueprint below into a text file, "myapp.yaml", in your workspace, but *before* you create an application with 
it, modify the YAML to specify the location where the application will be deployed.  (Note, to copy the file you can
hover your mouse over the right side of the text box below to get a Javascript "copy" button.)

{% highlight yaml %}
name: Tomcat
location:
  jclouds:aws-ec2:
    identity: ABCDEFGHIJKLMNOPQRST
    credential: s3cr3tsq1rr3ls3cr3tsq1rr3ls3cr3tsq1rr3l
services:
- serviceType: brooklyn.entity.webapp.tomcat.TomcatServer
{% endhighlight %}

Replace the ```location:``` element with values for your chosen target environment, for example to use SoftLayer rather 
than AWS (updating with your own credentials): 

{% highlight yaml %}
location:
  jclouds:softlayer:
    identity: ABCDEFGHIJKLMNOPQRST
    credential: s3cr3tsq1rr3ls3cr3tsq1rr3ls3cr3tsq1rr3l
{% endhighlight %}

Or, if you already have machines provisioned, you can use the "bring your own nodes" (byon) approach. 
Of course, replace the identity and address values below with your own values.
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

**Note**: See __[Locations](../ops/locations)__ in the Operations section of the User Guide for instructions on setting
up alternate cloud providers, bring-your-own-nodes, or localhost targets, and storing credentials/locations in a file 
on disk rather than in the blueprint.
(For the application above, if you are using a "Bring your own Nodes" location, you will need at least three nodes.)

First you will have to log in to brooklyn:
{% highlight bash %}
$ br login http://localhost:8081/
{% endhighlight %}

To secure the server you can add a username and password in Brooklyn's properties file, as described in the User Guide. 
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