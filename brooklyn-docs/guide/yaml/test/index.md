---
title: Testing YAML Blueprints
layout: website-normal
children:
- test-entities.md
- usage-examples.md
---

Brooklyn provides a selection of basic test entities which can be used to validate Blueprints via YAML. These are divided into two groups; structural, which effect the order in which child entities are started; and validation, which are used to confirm the application is deployed as intended.

Structural test entities include:

- `TestCase`  - starts child entities sequentially.
- `ParallelTestCase` - starts child entities in parallel.

Validation test entities include:

- `TestSensor` - perform assertion on a specified sensor.
- `TestEffector` - invoke effector on specified target entity.
- `TestHttpCall` - perform assertion on response to specified HTTP GET Request.
- `SimpleShellCommandTest` - test assertions on the result of a shell command on the same node as the target entity.

The following sections provide details on each test entity along with examples of their use.

{% include list-children.html %}
