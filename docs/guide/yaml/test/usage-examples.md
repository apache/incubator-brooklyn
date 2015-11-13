---
title: Example Blueprint Tests
title_in_menu: Example Tests
layout: website-normal
---

{% include fields.md %}

## Introduction
This section provides example tests carried out on the following `simple-tomcat-app` web application catalog item:

{% highlight yaml %}
{% readj example_yaml/simple-tomcat-app.yaml %}
{% endhighlight %}

### Sensor Test Example

This test catalog carries out the following test actions:

- deploys the `sample-tomcat-app`
- tests that the `service.isUp` sensor is `true` within 10 minutes of the `sample-tomcat-app` deployment completing.

{% highlight yaml %}
{% read example_yaml/simple-tomcat-app-sensor-test.yaml %}
{% endhighlight %}

### HTTP Call Test Example

The following yaml snippet extends the sensor test above with a selection of HTTP Call tests:

- application url returns a HTTP status code 200 within 60 seconds.
- response body matches the regex `(?s).*Br[o]{2}klyn Deployed.*`. Note the presence of the `(?s)` dotall flag to test a multiline response.

{% highlight yaml %}
{% readj example_yaml/simple-tomcat-app-http-test-snippet.yaml %}
{% endhighlight %}

### Effector Test Example

Extending the preceeding examples yet again, the following yaml snippet invokes the Tomcat entities `deploy` effector to deploy a new war file whose endpoint is subsequently tested:

- `deploy` effector invoked to deploy war to a `newcontext`
- `/newcontext` url returns a HTTP status code 200 within 5 minutes.

{% highlight yaml %}
{% readj example_yaml/simple-tomcat-app-effector-test-snippet.yaml %}
{% endhighlight %}

## Parallel Test example

The preceeding examples had a single application entity which lends itself to running sequentially within a `TestCase` entity, if a Blueprint consists of multiple services then it is necessary to structure the test with multiple services starting under the `ParallelTestCase` entity.

The following example tests the example multi-service application from the [Getting Started]({{ site.path.guide }}/start/blueprints.html#entitlements) section.

{% highlight yaml %}
{% readj example_yaml/paralleltestcase-example.yaml %}
{% endhighlight %}