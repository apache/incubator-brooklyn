---
title: Service State
layout: page
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

Any entity can use the standard "service-up" and "service-state" 
sensors to inform other entities and the GUI about its status.

In normal operation, entities should publish at least one "service not-up indicator",
using the `ServiceNotUpLogic.updateNotUpIndicator` method.  Each such indicator should have
a unique name or input sensor.  `Attributes.SERVICE_UP` will then be updated automatically
when there are no not-up indicators.

When there are transient problems that can be detected, to trigger `ON_FIRE` status
entity code can similarly set `ServiceProblemsLogic.updateProblemsIndicator` with a unique namespace,
and subsequently clear it when the problem goes away.
These problems are reflected at runtime in the `SERVICE_PROBLEMS` sensor,
allowing multiple problems to be tracked independently.

When an entity is changing the expected state, e.g. starting or stopping,
the expected state can be set using `ServiceStateLogic.setExpectedState`;
this expected lifecycle state is considered together with `SERVICE_UP` and `SERVICE_PROBLEMS`
to compute the actual state.  By default the logic in `ComputeServiceState` is applied.

For common entities, good out-of-the-box logic is applied, as follows:

* For `SoftwareProcess` entities, lifecycle service state is updated by the framework
  and a service not-up indicator is linked to the driver `isRunning()` check.
  
* For common parents, including `AbstractApplication` and `AbstractGroup` subclasses (including clusters, fabrics, etc),
  the default enrichers analyse children and members to set a not-up indicator
  (requiring at least one child or member who is up) and a problem indicator
  (if any children or members are on-fire).
  In some cases other quorum checks are preferable; this can be set e.g. by overriding 
  the `UP_QUORUM_CHECK` or the `RUNNING_QUORUM_CHECK`, as follows:
  
      public static final ConfigKey<QuorumCheck> UP_QUORUM_CHECK = ConfigKeys.newConfigKeyWithDefault(AbstractGroup.UP_QUORUM_CHECK, 
          "Require all children and members to be up for this node to be up",
          QuorumChecks.all());

  Alternatively the `initEnrichers()` method can be overridden to specify a custom-configured
  enricher or set custom config key values (as done e.g. in `DynamicClusterImpl` so that
  zero children is permitted provided when the initial size is configured to be 0).


For sample code to set and more information on these methods' behaviours,
see javadoc in `ServiceStateLogic`,
overrides of `AbstractEntity.initEnrichers()`
and tests in `ServiceStateLogicTests`.

<!-- TODO include more documentation, sample code (ideally extracted on the fly from test cases so we know it works!) -->


## Notes on Advanced Use

The enricher to derive `SERVICE_UP` and `SERVICE_STATE_ACTUAL` from the maps and expected state values discussed above
is added by the `AbstractEntity.initEnrichers()` method.
This method can be overridden -- or excluded altogether by by overriding `init()` --
and can add enrichers created using the `ServiceStateLogic.newEnricherFromChildren()` method
suitably customized using methods on the returned spec object, for instance to look only at members
or specify a quorum function (from `QuorumChecks`). 
If different logic is required for computing `SERVICE_UP` and `SERVICE_STATE_ACTUAL`,
use `ServiceStateLogic.newEnricherFromChildrenState()` and `ServiceStateLogic.newEnricherFromChildrenUp()`,
noting that the first of these will replace the enricher added by the default `initEnrichers()`,
whereas the second one runs with a different namespace (unique tag).
For more information consult the javadoc on those classes.

Entities can set `SERVICE_UP` and `SERVICE_STATE_ACTUAL` directly.
Provided these entities never use the `SERVICE_NOT_UP_INDICATORS` and `SERVICE_PROBLEMS` map,
the default enrichers will not override these values.

