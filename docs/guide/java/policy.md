---
layout: website-normal
title: Writing a Policy
---

### Your First Policy

Policies perform the active management enabled by Brooklyn.  
Each policy instance is associated with an entity,
and at runtime it will typically subscribe to sensors on that entity or children,
performing some computation and optionally actions when a subscribed sensor event occurs.
This action might be invoking an effector or emitting a new sensor,
depending the desired behavior is.

Writing a policy is straightforward.
Simply extend ``AbstractPolicy``,
overriding the ``setEntity`` method to supply any subscriptions desired:

{% highlight java %}
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity)
        subscribe(entity, TARGET_SENSOR, this)
    }
{% endhighlight %}

and supply the computation and/or activity desired whenever that event occurs:

{% highlight java %}
    @Override
    public void onEvent(SensorEvent<Integer> event) {
        int val = event.getValue()
        if (val % 2 == 1)
            entity.sayYoureOdd();
    }
{% endhighlight %}


You'll want to do more complicated things, no doubt,
like access other entities, perform multiple subscriptions,
and emit other sensors -- and you can.
See the best practices below and source code for some commonly used policies and enrichers,
such as ``AutoScalerPolicy`` and ``RollingMeanEnricher``. 

One rule of thumb, to close on:
try to keep policies simple, and compose them together at runtime;
for instance, if a complex computation triggers an action,
define one **enricher** policy to aggregate other sensors and emit a new sensor,
then write a second policy to perform that action.


### Best Practice

The following recommendations should be considered when designing policies:
    
#### Management should take place as "low" as possible in the hierarchy
*   place management responsibility in policies at the entity, as much as possible ideally management should take run as a policy on the relevant entity

*   place escalated management responsibility at the parent entity. Where this is impractical, perhaps because two aspects of an entity are best handled in two different places, ensure that the separation of responsibilities is documented and there is a group membership relationship between secondary/aspect managers.


#### Policies should be small and composable

e.g. one policy which takes a sensor and emits a different, enriched sensor, and a second policy which responds to the enriched sensor of the first     (e.g. a policy detects a process is maxed out and emits a TOO_HOT sensor; a second policy responds to this by scaling up the VM where it is running, requesting more CPU)

#### Where a policy cannot resolve a situation at an entity, the issue should be escalated to a manager with a compatible policy.

Typically escalation will go to the entity parent, and then cascade up.
e.g. if the earlier VM CPU cannot be increased, the TOO_HOT event may go to the parent, a cluster entity, which attempts to balance. If the cluster cannot balance, then to another policy which attempts to scale out the cluster, and should the cluster be unable to scale, to a third policy which emits TOO_HOT for the cluster.
    
#### Management escalation should be carefully designed so that policies are not incompatible

Order policies carefully, and mark sensors as "handled" (or potentially "swallow" them locally), so that subsequent policies and parent entities do not take superfluous (or contradictory) corrective action.
      
### Implementation Classes

- extend ``AbstractPolicy``, or override an existing policy
