---
title: Policies
layout: page
toc: ../guide_toc.json
categories: [use, guide]

---
<a name="introduction"></a>

Policies perform the active management enabled by Brooklyn.  They can subscribe to entity sensors and be triggered by them or they can run periodically.

<!---
TODO, clarify below, memebers of what?
-->
Policies can add subscriptions to sensors on any entity. Normally a policy will subscribe to its related entity, entities that it owns, and/or those entities which are members)

When a policy runs it can:

*	perform calculations,
*	look up other values,
*	invoke efectors  (management policies) or,
*	cause the entity associated with the policy to emit sensor values (enricher policies). 


Entities can have zero or more ``Policy`` instances attached to them.

<a name="writing-policies"></a>
Writing Policies
----------------

### Sample Policy

<!---
TODO
-->

*This section is not complete in this milestone release.*

### Best Practice

The following recommendations should be considered when designing policies:


*	place escalated management responsibility at the owner entity. Where this is impractical, perhaps because two aspects of an entity are best handled in two different places, ensure that the separation of responsibilities is documented and there is a group membership relationship between secondary/aspect managers.


#### policies should be small and composable

e.g. one policy which takes a sensor and emits a different, enriched sensor, and a second policy which responds to the enriched sensor of the first 	(e.g. a policy detects a process is maxed out and emits a TOO_HOT sensor; a second policy responds to this by scaling up the VM where it is running, requesting more CPU)
	
#### management should take place as "low" as possible in the hierarchy
*	place management responsibility in policies at the entity, as much as possible
ideally management should take run as a policy on the relevant entity

#### where a policy cannot resolve a situation at an entity, the issue should be escalated to a manager with a compatible polic

Typically escalation will go to the entity owner, and then cascade up.
e.g. if the earlier VM CPU cannot be increased, the TOO_HOT event may go to the owner, a cluster entity, which attempts to balance. If the cluster cannot balance, then to another policy which attempts to scale out the cluster, and should the cluster be unable to scale, to a third policy which emits TOO_HOT for the cluster.
	
#### management escalation should be carefully designed so that policies are not incompatible

order policies carefully, and mark sensors as "handled" (or potentially "swallow" them locally), so that subsequent policies and owner entities do not take superfluous (or contradictory) corrective action.
      

For this milestone release, some of the mechanisms for implementing the above practices are still being developed.


### Implementation Classes

*This section is not complete in this milestone release.*

- extend ``AbstractPolicy``, or override an existing policy


<a name="implementing-policies"></a>
Implementing Policies
---------------------

<!---
TODO
-->

*This section is in development at the time of this milestone release.*

Please see the class* ``brooklyn.policy.Policy`` *and implementations.

