---
title: Blueprint Test Entities
title_in_menu: Test Entities
layout: website-normal
---

{% include fields.md %}

## Structural Test Entities

### TestCase
The `TestCase` entity acts as a container for a list of child entities which are started *sequentially*.

{% highlight yaml %}
{% readj example_yaml/entities/testcase-entity.yaml %}
{% endhighlight %}

This can be used to enforce a strict ordering, for example ensuring a sensor has a certain value before attempting to invoke an effector.

Timeouts on child entities should be set relative to the completion of the preceding entity.

The `ParallelTestCase` entity can be added as a child to run a subset of entities in parallel as a single step.

### ParallelTestCase
The `ParallelTestCase` entity acts as a container for a list of child entities which are started in *parallel*.

{% highlight yaml %}
{% readj example_yaml/entities/paralleltestcase-entity.yaml %}
{% endhighlight %}

This can be used to run a subset of entities in parallel as a single step when nested under a `TestCase` entity.

Timeouts on child entities should be set relative to the start of the `ParallelTestCase`.

## Validation Test Entities

### TestSensor
The `TestSensor` entity performs an assertion on a specified sensors value.

{% highlight yaml %}
{% readj example_yaml/entities/testsensor-entity.yaml %}
{% endhighlight %}

#### Parameters
- `target` - entity whose sensor will be tested, specified via DSL. For example, `$brooklyn:component("tomcat")`. See also the `targetId` parameter.
- `targetId` - alternative to the `target` parameter which wraps the DSL component lookup requiring only the `id` be supplied. For example, `tomcat`.
- `sensor` - sensor to evaluate. For example `service.isUp`.
- `timeout` - duration to wait on assertion to return a result. For example `10s`, `10m`, etc
- `assert` - assertion to perform on the specified sensor value, options include:
  - `equals` - tests that the sensor value equals the supplied value. For example `true`.
  - `regex` - tests that the sensor value matches the supplied [regex pattern](http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html?is-external=true), for example `".*hello.*"`.
  - `isNull` - tests that the sensor value is `null`.

### TestEffector
The `TestEffector` entity invokes the specified effector on a target entity.
{% highlight yaml %}
{% readj example_yaml/entities/testeffector-entity.yaml %}
{% endhighlight %}

#### Parameters
- `target` - entity whose effector will be invoked, specified via DSL. For example, `$brooklyn:component("tomcat")`. See also the `targetId` parameter.
- `targetId` - alternative to the `target` parameter which wraps the DSL component lookup requiring only the `id` be supplied. For example, `tomcat`.
- `timeout` - duration to wait on the effector task to complete. For example `10s`, `10m`, etc
- `effector` - effector to invoke, for example `deploy`.
- `params` - parameters to pass to the effector, these will depend on the entity and effector being tested. The example above shows the `url` and `targetName` parameters being passed to Tomcats `deploy` effector.

### TestHttpCall
The `TestHttpCall` entity performs a HTTP GET on the specified URL and performs an assertion on the response.
{% highlight yaml %}
{% readj example_yaml/entities/testhttpcall-entity.yaml %}
{% endhighlight %}

#### Parameters
- `url` - URL to perform GET request on, this can use DSL for example `$brooklyn:component("tomcat").attributeWhenReady("webapp.url")`.
- `timeout` - duration to wait on a HTTP response. For example `10s`, `10m`, etc
- `assert` - assertion to perform on the response, options include:
  - `status` - response must match the specified status code, for example `200`.
  - `bodyContains` - response body must contain the supplied string, for example `"hello world"`.
  - `regex` - response body must match the supplied [regex pattern](http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html?is-external=true), for example `".*hello.*"`

