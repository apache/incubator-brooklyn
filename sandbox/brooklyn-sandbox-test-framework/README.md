# Entities

## TestCase
A logical grouping for tests eg Restart tests
```
type: org.apache.brooklyn.test.framework.TestCase
  name: Stop Test
  brooklyn.children:
  - ***
  - ***
```

## TestSensor
Entity that tests a sensor value on another entity eg service.isUp == TRUE

#### Configuration
| Key | Description | Required |
| --- | ----------- | -------- |
| target | The target entity to test | yes (no if *targetId* is supplied) |
| targetId | The id of the target entity to test | yes (no if *target* is supplied) |
| assert | Assertions to be evaluated | yes |
| timeout | The duration to wait on a result | no |

```
type: org.apache.brooklyn.test.framework.TestSensor
target: $brooklyn:component("nginx1")
sensor: service.isUp
equals: true
timeout: 5m
```

## TestEffector
Entity that invokes an effector on another entity eg restart

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