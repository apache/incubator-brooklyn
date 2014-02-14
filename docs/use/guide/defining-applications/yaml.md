---
title: Common Usage
layout: page
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

### YAML blueprints

Application blueprints can be written as a YAML file, following the <a href="https://www.oasis-open.org/committees/camp/">Camp specification</a>. Alternatively, they can be written as code in Java (or other JVM languages).

There are several ways to deploy:

- We can supply this file at startup:

  {% highlight bash %}
  brooklyn launch --app /path/to/myblueprint.yaml
  {% endhighlight %}

- We can deploy using the web-console.
- We can deploy using the brooklyn REST api.
