---
title: Multiple Services and Dependency Injection
layout: website-normal
---

We've seen the configuration of machines and how to build up clusters.
Now let's return to our app-server example and explore how more interesting
services can be configured, composed, and combined.


### Service Configuration

We'll begin by using more key-value pairs to configure the JBoss server to run a real app:

{% highlight yaml %}
{% readj example_yaml/appserver-configured.yaml %}
{% endhighlight %}

(As before, you'll need to add the `location` info; `localhost` will work for these and subsequent examples.)

When this is deployed, you can see management information in the Brooklyn Web Console,
including a link to the deployed application (downloaded to the target machine from the `hello-world` URL),
running on port 8080.

**Tip**:  If port 8080 might be in use, you can specify `8080+` to take the first available port >= 8080;
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


### Multiple Services

If you explored the `hello-world-sql` application we just deployed, 
you'll have noticed it tries to access a database.
And it fails, because we have not set one up.  Let's do that now:

{% highlight yaml %}
{% readj example_yaml/appserver-w-db.yaml %}
{% endhighlight %}

Here there are a few things going on:

* We've added a second service, which will be the database;
  you'll note the database has been configured to run a custom setup script
* We've injected the URL of the second service into the appserver as a Java system property
  (so our app knows where to find the database) 

**Caution: Be careful if you write your YAML in an editor which attempts to put "smart-quotes" in.
All quote characters must be plain ASCII, not fancy left-double-quotes and right-double-quotes!**

There are as many ways to do dependency injection as there are developers,
it sometimes seems; our aim in Brooklyn is not to say this has to be done one way,
but to support the various mechanisms people might need, for whatever reasons.
(We each have our opinions about what works well, of course;
the one thing we do want to call out is that being able to dynamically update
the injection is useful in a modern agile application -- so we are definitively **not**
recommending this Java system property approach ... but it is an easy one to demo!)

The way the dependency injection works is again by using the `$brooklyn:` DSL,
this time referring to the `component("db")` (looked up by the `id` field on our DB component),
and then to a sensor emitted by that component.
All the database entities emit a `database.url` sensor when they are up and running;
the `attributeWhenReady` DSL method will store a pointer to that sensor (a Java Future under the covers)
in the Java system properties map which the JBoss entity reads at launch time, blocking if needed.

This means that the deployment occurs in parallel, and if the database comes up first,
there is no blocking; but if the JBoss entity completes its installation and 
downloading the WAR, it will wait for the database before it launches.
At that point the URL is injected, first passing it through `formatString`
to include the credentials for the database (which are defined in the database creation script).


### An Aside: Substitutability

Don't like JBoss?  Is there something about Maria?
One of the modular principles we follow in Brooklyn is substitutability:
in many cases, the config keys, sensors, and effectors are defined
in superclasses and are portable across multiple implementations.

Here's an example deploying the same application but with different flavors of the components:

{% highlight yaml %}
{% readj example_yaml/appserver-w-db-other-flavor.yaml %}
{% endhighlight %}

We've also brought in the `provisioning.properties` from the VM example earlier
so our database has 8GB RAM.
Any of those properties, including `imageId` and `user`, can be defined on a per-entity basis.
