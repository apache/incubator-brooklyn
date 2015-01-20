---
title: The Basic Structure
layout: website-normal
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
  If you've [set up passwordless localhost SSH access](../ops/locations/#localhost) 
  you can use `localhost` as above, but if not, just wait ten seconds for the next example.
  
* The `services` block takes a list of the typed services we want to deploy.
  This is the meat of the blueprint plan, as you'll see below.

Finally, that clipboard in the corner lets you easily copy-and-paste into the web-console:
simply [download and launch]({{ site.path.guide }}/start/running.html) Brooklyn,
then in the "Add Application" dialog at the web console
(usually [http://127.0.0.1:8081/](http://127.0.0.1:8081/). 
There are several other ways to deploy, including `curl` and via the command-line,
and you can configure users, https, persistence, and more, 
as described [in the ops guide](../ops/).

[![Web Console](web-console-yaml-700.png "YAML via Web Console")](web-console-yaml.png)



<!--
TODO building up children entities

-->



### More Information

Topics to explore next on the topic of YAML blueprints are:

{% include list-children.html %}

Plenty of examples of blueprints exist in the Brooklyn codebase,
so another starting point is to [`git clone`]({{ site.path.guide }}/dev/code/index.html) it
and search for `*.yaml` files therein.

Brooklyn lived as a Java framework for many years before we felt confident
to make a declarative front-end, so you can do pretty much anything you want to
by dropping to the JVM. For more information on Java:

* start with a [Maven archetype]({{site.path.guide}}/java/archetype.html)
* see all [Brooklyn Java guide]({{site.path.guide}}/java/) topics
* look at test cases in the [codebase](https://github.com/apache/incubator-brooklyn)

<!-- 
TODO
* review some [examples]({{site.path.guide}}/use/examples/index.html)
-->

You can also come talk to us, on IRC (#brooklyncentral on Freenode) or
any of the usual [hailing frequencies]({{site.path.website}}/community/),
as these documents are a work in progress.
