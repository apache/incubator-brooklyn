---
title: A Bigger Blueprint
layout: guide-normal
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

We've seen the configuration of machines and how to build up clusters.
Now let's return to our app-server example and explore how more interesting
services can be configured, composed, and combined.

Also note there are some good overview materials [here]({{site.path.guide}}//use/guide/defining-applications/basic-concepts.html)
covering clusters, sensors, effectors and more, 
if you're the kind of person who likes to learn more about concepts before seeing them in action.


### Service Configuration

We'll begin by using more key-value pairs to configure the JBoss server to run a real app:

{% highlight yaml %}
{% readj example_yaml/appserver-configured.yaml %}
{% endhighlight %}

(As before, you'll need to add the `location` info; `localhost` will work for these and subsequent examples.)

When this is deployed, you can see management information in the Brooklyn Web Console,
including a link to the deployed application (downloaded to the target machine from the `hello-world` URL),
running on port 8080.

**Top tip**:  If port 8080 might be in use, you can specify `8080+` to take the first available port >= 8080;
the actual port will be reported as a sensor by Brooklyn.

It's also worth indicating an alternate, more formal syntax.
Not all configuration on entities is supported at the top level of the service specification
(only those which are defined as "flags" in the underlying blueprint,
e.g. the `@SetFromFlag("war")` in the `WebAppServiceConstants` parent of `JBoss7Server`).
All configuration has a formal qualified name, and this can be supplied even where flags or config keys are not
explicitly defined, by placing it into a `brooklyn.config` section:

{% highlight yaml %}
{% readj example_yaml/appserver-configured-in-config.yaml %}
{% endhighlight %}
