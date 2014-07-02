---
title: Persistence
layout: page
toc: ../guide_toc.json
categories: [use, guide]

---
<a name="introduction"></a>
Brooklyn can be configured to persist its state so that the Brooklyn server can be restarted, 
or so that a high availability standby server can take over.

Brooklyn can persist its state to one of two places: the file system, or to an Object Store
of your choice.

<a name="command-line-options"></a>
Command Line Options
--------------------

To configure brooklyn, the relevant command line options are:

* `--persist` <persistence mode>
  The persistence mode.
* `--persistenceDir` <persistence dir>
  The directory to read/write persisted state (or container name if using an object store).
* `--persistenceLocation` <persistence location>
  The location spec for an object store to read/write persisted state.

For the persistence mode, the possible values are:

* `disabled` means that no state will be persisted or read; when Brooklyn stops all state is lost.
* `rebind` means that it will read existing state, and recreate entities, locations and policies 
  from that. If there is no existing state, startup will fail.
* `clean` means that any existing state will be deleted, and Brooklyn will be started afresh.
* `auto` means Brooklyn will rebind if there is any existing state, or will start afresh if 
  there is no state.


<a name="file-based-persistence"></a>
File-based Persistence
----------------------

To persist to the file system, start brooklyn with:

{% highlight bash %}
brooklyn launch --persist auto --persistenceDir /path/to/myPersistenceDir
{% endhighlight %}

an
If there is already data at `/path/to/myPersistenceDir`, then a backup of the directory will 
be made. This will have a name like `/path/to/myPersistenceDir.20140701-142101345.bak`.

The state is written to the given path. The file structure under that path is:

* `./entities/`
* `./locations/`
* `./policies/`
* `./enrichers/`

In each of those directories, an XML file will be created per item - for example a file per
entity in ./entities/. This file will capture all of the state - for example, an
entity's: id; display name; type; config; attributes; tags; relationships to locations, child 
entities, group membership, policies and enrichers; and dynamically added effectors and sensors.


<a name="object-store-persistence"></a>
Object Store Persistence
------------------------

Brooklyn can persist its state to any Object Store API that jclouds supports including 
S3, Swift and Azure. This gives access to any compatible Object Store product or cloud provider
including AWS-S3, SoftLayer, Rackspace, HP and Microsoft Azure.

To configure the Object Store, add the credentials to `~/.brooklyn/brooklyn.properties` such as:

{% highlight properties %}
brooklyn.location.named.aws-s3-eu-west-1:aws-s3:eu-west-1
brooklyn.location.named.aws-s3-eu-west-1.identity=ABCDEFGHIJKLMNOPQRSTU
brooklyn.location.named.aws-s3-eu-west-1.credential=abcdefghijklmnopqrstuvwxyz1234567890ab/c
{% endhighlight %} 

or:

{% highlight properties %}
brooklyn.location.named.softlayer-swift-ams01=jclouds:swift:https://ams01.objectstorage.softlayer.net/auth/v1.0
brooklyn.location.named.softlayer-swift-ams01.identity=ABCDEFGHIJKLM:myname
brooklyn.location.named.softlayer-swift-ams01.credential=abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz12
{% endhighlight %} 

Start Brooklyn pointing at this target object store, e.g.:

{% highlight bash %}
brooklyn launch --persist auto --persistenceDir myContainerName --persistenceLocation named:softlayer-swift-ams01
{% endhighlight %}


<a name="rebind"></a>
Rebind
------

When Brooklyn starts up pointing at existing state, it will recreate the entities, locations 
and policies based on that persisted state.

Once all have been created, Brooklyn will "manage" the entities. This will bind to the 
underlying entities under management to update the each entity's sensors (e.g. to poll over 
HTTP or JMX). This new state will be reported in the web-console and can also trigger 
any registered policies.


<a name="handling-rebind-failures"></a>
Handling Rebind Failures
------------------------
If rebind were to fail for any reason, details of the underlying failures will be reported 
in the brooklyn.debug.log. There are several approaches to resolving problems.

<a name="rebind-failures-determine-underlying-cause"></a>
### Determine Underlying Cause

The problems reported in brooklyn.debug.log will indicate where the problem lies - which 
entities, locations or policies, and in what way it failed.

<a name="rebind-failures-ignore-errors"></a>
### Ignore Errors

The `~/.brooklyn/brooklyn.properties` has several configuration options:

{% highlight properties %}
rebind.failureMode.danglingRef=continue
rebind.failureMode.loadPolicy=continue
rebind.failureMode.addPolicy=continue
rebind.failureMode.rebind=fail_at_end
{% endhighlight %} 

For each of these configuration options, the possible values are:

* `fail_fast`: stop rebind immediately upon errors; do not try to rebind other entities
* `fail_at_end`: continue rebinding all entities, but then fail so that all errors 
  encountered are reported
* `continue`: log a warning, but ignore the error to continue rebinding. Depending on the 
  type of error, this can cause serious problems later (e.g. if the state of an entity
  was entirely missing, then all its children would be orphaned).

The meaning of the configuration options is:

* `rebind.failureMode.dangingRef`: if there is a reference to an entity, location or policy 
  that is missing... whether to continue (discarding the reference) or fail.
* `rebind.failureMode.loadPolicy`: if there is an error instantiate or reconstituting the 
  state of a policy or enricher... whether to continue (discarding the policy or enricher) 
  or fail.
* `rebind.failureMode.addPolicy`: if there is an error re-adding the policy or enricher to
  its associated entity... whether to continue (discarding the policy or enricher) 
  or fail.
* `rebind.failureMode.rebind`: any errors on rebind not covered by the more specific error cases described above.


<a name="rebind-failures-seek-help"></a>
### Seek Help

Help can be found at `dev@brooklyn.incubator.apache.org`, where folk will be able to investigate 
issues and suggest work-arounds.

By sharing the persisted state (with credentials removed), Brooklyn developers will be able to 
reproduce and debug the problem.


<a name="rebind-failures-determine-fix-up-the-state"></a>
### Fix-up the State

The state of each entity, location, policy and enricher is persisted in XML. 
It is thus human readable and editable.

After first taking a backup of the state, it is possible to modify the state. For example,
an offending entity could be removed, or references to that entity removed, or its XML 
could be fixed to remove the problem.


### Fixing with Groovy Scripts

The final (powerful and dangerous!) tool is to execute Groovy code on the running Brooklyn 
instance. If authorized, the REST api allows arbitrary Groovy scripts to be passed in and 
executed. This allows the state of entities to be modified (and thus fixed) at runtime.

If used, it is strongly recommended that Groovy scripts are run against a disconnected Brooklyn
instance. After fixing the entities, locations and/or policies, the Brooklyn instance's 
new persisted state can be copied and used to fix the production instance.


<a name="writing-persistable-code"></a>
Writing Persistable Code
------------------------
The most common problem on rebind is that custom entity code has not been written in a way
that can be persisted and/or rebound.

The rule of thumb when implementing new entities, locations and policies is that all state
must be persistable. All state must be stored as config or as attributes, and must be
serializable (e.g. avoid the use of anonymous inner classes).

Below is a guide for when implementing an entity in Java (or any other JVM language):

* Don't store state in artibrary fields - the field will not be persisted (this is a design
  decision, because Brooklyn cannot intercept the field being written to, so cannot know
  when to persist).
* Store runtime state as attributes.
* Ensure values can be serialized. This (currently) uses xstream, which means it does not
  need to implement `Serializable`. However, if declaring your own classes that are to be
  persisted as state of the entity then declare them as static (or top-level) classes 
  rather than anonymous inner classes or non-static inner classes.
* By extending `SoftwareProcess`, entities get a lot of the rebind logic for free. For 
  example, the default `rebind()` method will call `connectSensors()`.
* If necessary, implement rebind. The `entity.rebind()` is called automatically by the
  Brooklyn framework on rebind, after configuring the entity's config/attributes but before 
  the entity is managed.
  Note that `init()` will not be called on rebind.
* For special cases, it is possible to call `entity.requestPerist()` which will trigger
  asynchronous persistence of the entity.

For locations, policies and enrichers they (currently) do not have attributes. However,
config is persisted automatically. Normally the state of a policy or enricher is transient - 
on rebind it starts afresh, for example with monitoring the performance or health metrics
rather than relying on the persisted values.
