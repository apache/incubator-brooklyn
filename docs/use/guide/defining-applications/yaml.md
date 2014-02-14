---
title: Common Usage
layout: page
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

### YAML Blueprints

Application blueprints can be written as a YAML file, following the <a href="https://www.oasis-open.org/committees/camp/">OASIS CAMP</a> specification with some extensions.
<!-- TODO document the extensions! -->
Alternatively, they can be written as code in Java, scripts in Groovy, or using your favorite JVM language.

There are several ways to deploy:

- Supply this file at startup:

{% highlight bash %}
$ brooklyn launch --app ./blueprint.yaml
{% endhighlight %}


- Curl it to the Brooklyn REST API to a running server:

{% highlight bash %}
$ curl -T ./blueprint.yaml -X POST http://localhost:8081/v1/applications
{% endhighlight %}


- In the web-console: Select the "YAML" tab in the "Add Application" wizard:

[![Web Console](web-console-yaml-700.png "YAML via Web Console")](web-console-yaml.png)
