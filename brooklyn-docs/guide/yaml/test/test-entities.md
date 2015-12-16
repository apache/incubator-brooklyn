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
- `assert` - assertion to perform on the specified sensor value. See section on assertions below.

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
- `applyAssertionTo` - The filed to apply the assertion to. For example `status`, `body`
- `assert` - assertion to perform on the response.  See section on assertions below.

### SimpleShellCommandTest

The SimpleShellCommandTest runs a command on the host of the target entity.
The script is expected not to run indefinitely, but to return a result (process exit code), along with its 
standard out and error streams, which can then be tested using assertions.
If no assertions are explicitly configured, the default is to assert a non-zero exit code.

Either a shell command may be provided in the YAML, or a URL for a script which will be executed.

{% highlight yaml %}
{% readj example_yaml/entities/simpleshellcommandtest-entity.yaml %}
{% endhighlight %}

#### Parameters
- `command` - The shell command to execute. (This and `downloadUrl` are mutually exclusive.)
- `downloadUrl` - URL for a script to download and execute. (This and `command` are mutually exclusive.)
- `scriptDir` - if `downloadUrl` is used.  The directory on the target host where downloaded scripts should be copied to.
- `runDir` - the working directory where the command or script will be executed on the target host.
- `assertStatus` - Assertions on the exit code of the command or script. See section on assertions below.
- `assertOut` - Assertions on the standard output of the command as a String.
- `assertErr` -  Assertions on the standard error of the command as a String.

## Assertions

The following conditions are provided by those test entities above that include assertions

- `isNull` - asserts that the actual value is `null`.
- `notNull` - asserts that the actual value is NOT `null`.
- `isEqualTo` - asserts that the actual value equals an expected value.
- `equalTo` - a synonym for `isEqualTo`
- `equals` - a synonym for `isEqualTo`
- `matches` - asserts that the actual value matches a [regex pattern](http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html?is-external=true), for example `".*hello.*"`.
- `contains` - asserts that the actual value contains the supplied value
- `isEmpty` - asserts that the actual value is an empty string
- `notEmpty` - asserts that the actual value is a non empty string
- `hasTruthValue` - asserts that the actual value has the expected interpretation as a boolean

Assertions may be provided as a simple map:

```yaml
  assert:
       contains: 2 users
       matches: .*[\d]* days.*
```

If there is the need to make multiple assertions with the same key, the assertions can be specified 
as a list of such maps:

```yaml
  assert:
       - contains: 2 users
       - contains: 2 days
```
