# Entities

## TestCase
A logical grouping for tests, e.g. Restart tests.

```
type: org.apache.brooklyn.test.framework.TestCase
name: Stop Test
brooklyn.children:
  - ***
  - ***
```

## ParallelTestCase
A logical grouping for tests where each child is started in parallel instead of being run sequentially.

```
type: org.apache.brooklyn.test.framework.ParallelTestCase
name: Start Test
brooklyn.children:
  - ***
  - ***
```

## TestSensor
Entity that tests a sensor value on another entity, e.g. service.isUp == TRUE.

#### Configuration
| Key | Description | Required |
| --- | ----------- | -------- |
| target | The target entity to test | yes (no if *targetId* is supplied) |
| targetId | The id of the target entity to test | yes (no if *target* is supplied) |
| assert | Assertions to be evaluated | yes |
| timeout | The duration to wait on a result | no |

##### Assertions
| Key | Description |
| --- | ----------- |
| equal | Sensor value equals  |
| regex | Sensor value matches regex |
| isNull | Sensor value has not been set |

```
type: org.apache.brooklyn.test.framework.TestSensor
target: $brooklyn:component("nginx1")
sensor: service.isUp
equals: true
timeout: 5m
```

## TestEffector
Entity that invokes an effector on another entity, e.g. restart.

#### Configuration
| Key | Description | Required |
| --- | ----------- | -------- |
| target | The target entity to effect | yes (no if *targetId* is supplied) |
| targetId | The id of the target entity to effect | yes (no if *target* is supplied) |
| effector | The name of the effector to invoke | yes |
| params | Parameters to pass to the effector | no |
| timeout | The duration to wait on a response from an effector | no |

#### Sensors
| Key | Description |
| --- | ----------- |
| result | The result of invoking the effector (null if no result) |

```
type: org.apache.brooklyn.test.framework.TestEffector
name: Deploy WAR
target: $brooklyn:component("tomcat")
effector: deploy
params:
  url: https://tomcat.apache.org/tomcat-6.0-doc/appdev/sample/sample.war
  targetName: sample1
```

## TestHtmlCall
Entity that makes a HTTP Request and tests the response.

#### Configuration
| Key | Description | Required |
| --- | ----------- | -------- |
| url | The URL to test | yes |
| assert | Assertions to be evaluated | yes |
| timeout | The duration to wait for assertion result | no |

##### Assertions
| Key | Description |
| --- | ----------- |
| string | HTTP body contains text |
| regex | HTTP body matches regex |
| status | HTTP status code equals |

```
  - type: org.apache.brooklyn.test.framework.TestHttpCall
    name: Status Code 200
    url: $brooklyn:component("tomcat").attributeWhenReady("main.uri")
    timeout: 1m
    assert:
      status: 200
  - type: org.apache.brooklyn.test.framework.TestHttpCall
    name: String match
    url: $brooklyn:component("tomcat").attributeWhenReady("main.uri")
    timeout: 1m
    assert:
      bodyContains: Sample Brooklyn Deployed
  - type: org.apache.brooklyn.test.framework.TestHttpCall
    name: Status Code 404
    url: $brooklyn:formatString("%s/invalidpath/", component("tomcat").attributeWhenReady("webapp.url"))
    assert:
      status: 404
  - type: org.apache.brooklyn.test.framework.TestHttpCall
    name: Regex match
    url: $brooklyn:component("tomcat").attributeWhenReady("webapp.url")
    # the regex assert uses java.lang.String under the hood so if the url is expected to returns
    # a multi-line response you should use the embedded dotall flag expression `(?s)` in your regex.
    # See: http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html
    assert:
      regex: "(?s).*illustrate(\\s)*how(\\s)*web(\\s)*applications.*"
```



----
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.