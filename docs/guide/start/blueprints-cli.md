---
title: Deploying Blueprints
layout: website-normal
menu_parent: index-cli.md
children:
- { section: Launching from a Blueprint, title: Blueprint } 
- { section: Launching from the Catalog, title: Catalog } 
---


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

Replace the `location:` element with values for your chosen target environment, for example to use SoftLayer rather 
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


## Launching from the Catalog
Instead of pasting the YAML blueprint each time, it can be added to Brooklyns Catalog where it will be accessible 
for use in any blueprint that you want to deploy.

See __[Catalog](../ops/catalog/)__ in the Operations section of the User Guide for instructions on creating a new 
Catalog entry from your Blueprint YAML.
