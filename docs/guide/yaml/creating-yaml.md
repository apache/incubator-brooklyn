---
title: The Basic Structure
layout: guide-normal
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

## A First Blueprint

The easiest way to write a blueprint is as a YAML file.
This follows the  <a href="https://www.oasis-open.org/committees/camp/">OASIS CAMP</a> plan specification, 
with some extensions described below.
(A [YAML reference](yaml-reference.html) has more information,
and if the YAML doesn't yet do what you want,
it's easy to add new extensions using your favorite JVM language.)

### The Basic Structure

Here's a very simple YAML blueprint plan, to explain the structure:

{% highlight yaml %}
{% readj example_yaml/simple-appserver.yaml %}
{% endhighlight %}

* The `name` is just for the benefit of us humans.

* The `location` specifies where this should be deployed.
  If you've [set up passwordless localhost SSH access]({{ site.path.guide }}/use/guide/locations/) 
  you can use `localhost` as above, but if not, just wait ten seconds for the next example.
  
* The `services` block takes a list of the typed services we want to deploy.
  This is the meat of the blueprint plan, as you'll see below.

Finally, that clipboard in the corner lets you easily copy-and-paste into the web-console:
simply [download and launch]({{ site.path.guide }}/use/guide/quickstart/) Brooklyn,
then in the "Add Application" dialog at the web console
(usually [http://127.0.0.1:8081/](http://127.0.0.1:8081/). 
There are several other ways to deploy, including `curl` and via the command-line,
and you can configure https and security, and much more, as described [here](deploying-yaml.html).

[![Web Console](web-console-yaml-700.png "YAML via Web Console")](web-console-yaml.png)



<!--
TODO building up children entities

-->

<!--

### Using Chef Recipes

TODO

-->


### More Information

Plenty of examples of blueprints exist in the Brooklyn codebase,
so a good starting point is to [`git clone`]({{ site.path.guide }}/dev/code/index.html) it
and search for `*.yaml` files therein.

Brooklyn lived as a Java framework for many years before we felt confident
to make a declarative front-end, so you can do pretty much anything you want to
by dropping to the JVM. Information on that is available:
* in the [user guide]({{site.path.guide}}/use/guide/entities/index.html),
* through a [Maven archetype]({{site.path.guide}}/use/guide/defining-applications/archetype.html),
* in the [codebase](https://github.com/apache/incubator-brooklyn),
* and in plenty of [examples]({{site.path.guide}}/use/examples/index.html).

You can also come talk to us, on IRC (#brooklyncentral on Freenode) or
any of the usual [hailing frequencies]({{site.path.guide}}/meta/contact.html),
as these documents are a work in progress.