---
title: Blueprint Test Entities
title_in_menu: Test Entities
layout: website-normal
---

{% include fields.md %}

## Structural Test Entities

### TestCase
The `TestCase` entity acts as a container for a list child entities which are started *sequentially*.
{% highlight yaml %}
{% readj example_yaml/testcase-entity.yaml %}
{% endhighlight %}
The ability to sequentially run entities is used to allow applications to fully deploy before attempting to start any test entities.

If your application blueprint consists of multiple services then you will also require the `ParallelTestCase` entity.

### ParallelTestCase
The `ParallelTestCase` entity acts as a container for a list of child entities which are started in *parallel*.
{% highlight yaml %}
{% readj example_yaml/paralleltestcase-entity.yaml %}
{% endhighlight %}
This entity is used when your application blueprint consists of multiple services that run in parallel.


## Validation Test Entities

### TestSensor
The `TestSensor` entity performs an assertion on a specified sensor.
{% highlight yaml %}
{% readj example_yaml/testsensor-entity.yaml %}
{% endhighlight %}

#### Parameters
- `target` - entity whose sensor will be tested, specified via DSL. For example, `$brooklyn:component("tomcat")`. See also the `targetId` parameter.
- `targetId` - alternative to the `target` parameter which wraps the DSL component lookup. For example, `tomcat`.
- `sensor` - sensor to evaluate. For example `service.isUp`.
- `timeout` - time to wait for assertion to return a result.
- `assert` - assertion to perform on the specified sensor, options include
  - `equals` - tests that the sensor equals the supplied value. For example `true`.
  - `regex` - tests that the sensor value matches the supplied [regex pattern](http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html?is-external=true), for example `".*hello.*"`.
  - `isNull` - tests that the sensor value is `null`.

### TestEffector
The `TestEffector` entity invokes the specified effector on a target entity.
{% highlight yaml %}
{% readj example_yaml/testeffector-entity.yaml %}
{% endhighlight %}

#### Parameters
- `target` - entity whose effector will be invoked, specified via DSL. For example, `$brooklyn:component("tomcat")`. See also the `targetId` parameter.
- `targetId` - alternative to the `target` parameter which wraps the DSL component lookup. For example, `tomcat`.
- `timeout` - time to wait for the effector task to complete.
- `effector` - effector to invoke, for example `deploy`.
- `params` - parameters to pass to the effector, these will depend on the entity and effector being tested. The example above shows the `url` and `targetName` parameters being passed to Tomcats `deploy` effector.

### TestHttpCall
The `TestHttpCall` entity performs a HTTP GET on the specified URL and performs an assertion on the response.
{% highlight yaml %}
{% readj example_yaml/testhttpcall-entity.yaml %}
{% endhighlight %}

#### Parameters
- `url` - URL to perform GET request on, for example `$brooklyn:component("tomcat").attributeWhenReady("webapp.url")``.
- `timeout` - time to wait for a HTTP response. For example `10s`, `10m`, etc
- `assertions` - perform one of the following assertions.
  - `status` - response must match the specified status code. The example above shows an assertions on a `404` response.
  - `bodyContains` - response body must contain the supplied string, for example `"hello world"`.
  - `regex` - response body must match the supplied [regex pattern](http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html?is-external=true), for example `".*hello.*"`

