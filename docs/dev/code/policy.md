---
layout: page
title: Writing a Policy
toc: /toc.json
---

Policies perform the active management enabled by Brooklyn.  
Each policy instance is associated with an entity,
and at runtime it will typically subscribe to sensors on that entity or children,
performing some computation and optionally actions when a subscribed sensor event occurs.
This action might be invoking an effector or emitting a new sensor,
depending the desired behvaiour is.

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
See the source code, and see some commonly used policies
in ``ResizerPolicy`` and ``RollingMeanEnricher``. 

One rule of thumb, to close on:
try to keep policies simple, and compose them together at runtime;
for instance, if a complex computation triggers an action,
define one **enricher** policy to aggregate other sensors and emit a new sensor,
then write a second policy to perform that action.