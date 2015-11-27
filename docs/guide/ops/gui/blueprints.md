---
title: Deploying Blueprints
layout: website-normal
menu_parent: index.md
children:
- { section: Launching from a Blueprint, title: Launching from a Blueprint } 
- { section: Launching from the Catalog, title: Launching from the Catalog } 
---

{% include fields.md %}


## Launching from a Blueprint

When you first access the web console on [http://127.0.0.1:8081](http://127.0.0.1:8081) you will be requested to create your first application.

We'll start by deploying an application via a YAML blueprint consisting of the following layers.

- MySQL DB
- Dynamic web application cluster
  - Nginx load balancer
  - Tomcat app server cluster

[![Brooklyn web console, showing the YAML tab of the Add Application dialog.](images/add-application-modal-yaml.png)](images/add-application-modal-yaml-large.png)

Switch to the YAML tab and copy the blueprint below into the large text box. 

But *before* you submit it, modify the YAML to specify the location where the application will be deployed.

{% highlight yaml %}
{% readj _my-web-cluster.yaml %}
{% endhighlight %}

Replace the `location:` element with values for your chosen target environment, for example to use SoftLayer rather than AWS (updating with your own credentials): 

{% highlight yaml %}
location:
  jclouds:softlayer:
    identity: ABCDEFGHIJKLMNOPQRST
    credential: s3cr3tsq1rr3ls3cr3tsq1rr3ls3cr3tsq1rr3l
{% endhighlight %}

**NOTE**: See __[Locations](../locations)__ in the Operations section of the User Guide for instructions on setting up alternate cloud providers, bring-your-own-nodes, or localhost targets, and storing credentials/locations in a file on disk rather than in the blueprint.

With the modified YAML in the dialog, click "Finish". The dialog will close and Brooklyn will begin deploying your
application. Your application will be shown as "Starting" on the web console's front page.

[![Brooklyn web console, showing the application starting.](images/home-app-starting.png)](images/home-app-starting-large.png)

Depending on your choice of location it may take some time for the application nodes to start, the next page describes how you can monitor the progress of the application deployment and verify its successful deployment.

## Launching from the Catalog

Instead of pasting the YAML blueprint each time, it can be added to the Brooklyn Catalog where it will be accessible from the Catalog tab of the Create Application dialog.

[![Viewing Catalog entries in Add Application dialog.](images/add-application-catalog-web-cluster-with-db.png)](images/add-application-catalog-web-cluster-with-db-large.png)

<!-- TODO: more detail for adding to catalog? but wait for persistence to be the default, 
     rather than extensively document default.catalog.bom.
     also need to include instructions on stopping (currently in help, including stopping apps) -->

See __[Catalog](../catalog/)__ in the Operations section of the User Guide for instructions on creating a new Catalog entry from your Blueprint YAML.


## Next 

So far we have touched on Brooklyn's ability to *deploy* an application blueprint to a cloud provider.  
The next section will show how to **[Monitor and Manage Applications](managing.html)**.
