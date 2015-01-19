---
title: Deploying YAML Blueprints
layout: page
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

Once you've [written a YAML blueprint](creating-yaml.md), there are several ways to deploy it.
These insructions assume you have [installed]({{ site.url }}/use/guide/quickstart/) Brooklyn.
You can then:

- Supply the YAML blueprint file on the CLI when launching the server:

{% highlight bash %}
$ brooklyn launch --app ./blueprint.yaml
{% endhighlight %}


Or, assuming you've launched a server already 
(usually on [http://127.0.0.1/](http://127.0.0.1/) unless you've 
configured security in [`brooklyn.properties`](/use/guide/quickstart/brooklyn.properties)),
you can:

- Curl it to the Brooklyn REST API:

{% highlight bash %}
$ curl -T ./blueprint.yaml -X POST http://localhost:8081/v1/applications
{% endhighlight %}


- In the web-console, select the "YAML" tab in the "Add Application" wizard:

[![Web Console](web-console-yaml-700.png "YAML via Web Console")](web-console-yaml.png)


- The web-console also has an interactive "REST API" page,
  where you can paste the YAML for uploading into the `POST` to `/v1/applications`.
