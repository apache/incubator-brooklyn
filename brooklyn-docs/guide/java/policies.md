---
title: Policies
layout: website-normal

---

Policies perform the active management enabled by Brooklyn.  
They can subscribe to entity sensors and be triggered by them or they can run periodically.

<!---
TODO, clarify below, memebers of what?
-->
Policies can add subscriptions to sensors on any entity. Normally a policy will subscribe to its related entity, to the child entities, and/or those entities which are members.

When a policy runs it can:

*	perform calculations,
*	look up other values,
*	invoke effectors  (management policies) or,
*	cause the entity associated with the policy to emit sensor values (enricher policies). 

Entities can have zero or more ``Policy`` instances attached to them.


Off-the-Shelf Policies
----------------------

Policies are highly reusable as their inputs, thresholds and targets are customizable.

### Management Policies

- AutoScaler Policy
   
   Increases or decreases the size of a Resizable entity based on an aggregate sensor value, the current size of the entity, and customized high/low watermarks.

   An AutoScaler policy can take any sensor as a metric, have its watermarks tuned live, and target any resizable entity - be it an application server managing how many instances it handles, or a tier managing global capacity.

   e.g. if the average request per second across a cluster of Tomcat servers goes over the high watermark, it will resize the cluster to bring the average back to within the watermarks.
  
<!---
TODO - list some
TODO - describe how they can be customised (briefly mention sensors)
-->


###  Enrichers

*	Delta

	Converts absolute sensor values into a delta.
	

*	Time-weighted Delta

	Converts absolute sensor values into a delta/second.
	
*	Rolling Mean

	Converts the last *N* sensor values into a mean.
	
*	Rolling Time-window Mean

	Converts the last *N* seconds of sensor values into a weighted mean.

*	Custom Aggregating

	Aggregates multiple sensor values (usually across a tier, esp. a cluster) and performs a supplied aggregation method to them to return an aggregate figure, e.g. sum, mean, median, etc. 


Next: Writing a Policy
---------------------------

To write a policy, see the section on [Writing a Policy](policy.html).
