---
title: Persistence
layout: website-normal
children:
- { section: Command Line Options }
- { section: File-based Persistence }
- { section: Object Store Persistence }
- { section: Rebinding to State }
- { section: Writing Persistable Code }
- { section: Persisted State Backup }
---

Brooklyn can be configured to persist its state so that the Brooklyn server can be restarted, 
or so that a high availability standby server can take over.

Brooklyn can persist its state to one of two places: the file system, or to an Object Store
of your choice.

Command Line Options
--------------------

To configure brooklyn, the relevant command line options for the `launch` commands are:

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

The persistence directory and location can instead be specified from `brooklyn.properties` using
the following config keys:

* `brooklyn.persistence.dir`
* `brooklyn.persistence.location.spec`


File-based Persistence
----------------------

To persist to the file system, start brooklyn with:

{% highlight bash %}
brooklyn launch --persist auto --persistenceDir /path/to/myPersistenceDir
{% endhighlight %}

If there is already data at `/path/to/myPersistenceDir`, then a backup of the directory will 
be made. This will have a name like `/path/to/myPersistenceDir.20140701-142101345.bak`.

The state is written to the given path. The file structure under that path is:

* `./entities/`
* `./locations/`
* `./policies/`
* `./enrichers/`

In each of those directories, an XML file will be created per item - for example a file per
entity in `./entities/`. This file will capture all of the state - for example, an
entity's: id; display name; type; config; attributes; tags; relationships to locations, child 
entities, group membership, policies and enrichers; and dynamically added effectors and sensors.

If using the default persistence dir (i.e. no `--persistenceDir` was specified), then Brooklyn will
write its state to `~/.brooklyn/brooklyn-persisted-state/data`. Copies of this directory
will be automatically created in `~/.brooklyn/brooklyn-persisted-state/backups/` each time Brooklyn 
is restarted (or if a standby Brooklyn instances takes over as master).

A custom directory for Brooklyn state can also be configured in `brooklyn.properties` using:
    
    # For all Brooklyn files
    brooklyn.base.dir=/path/to/base/dir
    
    # Sub-directory of base.dir for writing persisted state (if relative). If directory
    # starts with "/" (or "~/", or something like "c:\") then assumed to be absolute. 
    brooklyn.persistence.dir=data

    # Sub-directory of base.dir for creating backup directories (if relative). If directory
    # starts with "/" (or "~/", or something like "c:\") then assumed to be absolute. 
    brooklyn.persistence.backups.dir=backups

This `base.dir` will also include temporary files such as the OSGi cache.

If `persistence.dir` is not specified then it will use the sub-directory
`brooklyn-persisted-state/data` of the `base.dir`. If the `backups.dir` is not specified
the backup directories will be created in the sub-directory `backups` of the persistence dir.


Object Store Persistence
------------------------

Brooklyn can persist its state to any Object Store API that jclouds supports including 
S3, Swift and Azure. This gives access to any compatible Object Store product or cloud provider
including AWS-S3, SoftLayer, Rackspace, HP and Microsoft Azure. For a complete list of supported
providers, see [jclouds](http://jclouds.apache.org/reference/providers/#blobstore).

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
nohup brooklyn launch --persist auto --persistenceDir myContainerName --persistenceLocation named:softlayer-swift-ams01 &
{% endhighlight %}


The following `brooklyn.properties` options can also be used:

    # Location spec string for an object store (e.g. jclouds:swift:URL) where persisted state 
    # should be kept; if blank or not supplied, the file system is used.
    brooklyn.persistence.location.spec=<location>

    # Container name for writing persisted state
    brooklyn.persistence.dir=/path/to/dataContainer

    # Location spec string for an object store (e.g. jclouds:swift:URL) where backups of persisted 
    # state should be kept; defaults to the local file system.
    brooklyn.persistence.backups.location.spec=<location>

    # Container name for writing backups of persisted state;
    # defaults to 'backups' inside the default persistence container.
    brooklyn.persistence.backups.dir=/path/to/backupContainer


Rebinding to State
------------------

When Brooklyn starts up pointing at existing state, it will recreate the entities, locations 
and policies based on that persisted state.

Once all have been created, Brooklyn will "manage" the entities. This will bind to the 
underlying entities under management to update the each entity's sensors (e.g. to poll over 
HTTP or JMX). This new state will be reported in the web-console and can also trigger 
any registered policies.


## CLI Commands for Copying State

Brooklyn includes a command to copy persistence state easily between two locations.
The `copy-state` CLI command takes the following arguments:

* `--persistenceDir` <source persistence dir>
  The directory to read persisted state (or container name if using an object store).
* `--persistenceLocation` <source persistence location>
  The location spec for an object store to read persisted state.
* `--destinationDir` <target persistence dir>
  The directory to copy persistence data to (or container name if using an object store).
* `--destinationLocation` <target persistence location>
  The location spec for an object store to copy data to.
* `--transformations` <transformations>
  The local transformations file to be applied to the copy of the data before uploading it.


## Handling Rebind Failures

If rebind fails fail for any reason, details of the underlying failures will be reported 
in the `brooklyn.debug.log`. There are several approaches to resolving problems.


### Determine Underlying Cause

The problems reported in brooklyn.debug.log will indicate where the problem lies - which 
entities, locations or policies, and in what way it failed.

### Ignore Errors

The `~/.brooklyn/brooklyn.properties` has several configuration options:

{% highlight properties %}
rebind.failureMode.danglingRef=continue
rebind.failureMode.loadPolicy=continue
rebind.failureMode.addPolicy=continue
rebind.failureMode.rebind=fail_at_end
rebind.failureMode.addConfig=fail_at_end
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
* `rebind.failureMode.addConfig`: if there is invalid config value, or some other error occurs when adding a config.
* `rebind.failureMode.rebind`: any errors on rebind not covered by the more specific error cases described above.


### Seek Help

Help can be found at `dev@brooklyn.incubator.apache.org`, where folk will be able to investigate 
issues and suggest work-arounds.

By sharing the persisted state (with credentials removed), Brooklyn developers will be able to 
reproduce and debug the problem.


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


Writing Persistable Code
------------------------
The most common problem on rebind is that custom entity code has not been written in a way
that can be persisted and/or rebound.

The rule of thumb when implementing new entities, locations, policies and enrichers is that 
all state must be persistable. All state must be stored as config or as attributes, and must be
serializable. For making backwards compatibility simpler, the persisted state should be clean.

Below are tips and best practices for when implementing an entity in Java (or any other 
JVM language).

How to store entity state:

* Config keys and values are persisted.
* Store an entity's runtime state as attributes.
* Don't store state in arbitrary fields - the field will not be persisted (this is a design
  decision, because Brooklyn cannot intercept the field being written to, so cannot know
  when to persist).
* Don't just modify the retrieved attribute value (e.g. `getAttribute(MY_LIST).add("a")` is bad).
  The value may not be persisted unless setAttribute() is called.
* For special cases, it is possible to call `entity.requestPerist()` which will trigger
  asynchronous persistence of the entity.
* Overriding (and customizing) of `getRebindSupport()` is discouraged - this will change
  in a future version.


How to store policy/enricher/location state:

* Store values as config keys where applicable.
* Unfortunately these (currently) do not have attributes. Normally the state of a policy 
  or enricher is transient - on rebind it starts afresh, for example with monitoring the 
  performance or health metrics rather than relying on the persisted values.
* For special cases, you can annotate a field with `@SetFromFlag` for it be persisted. 
  When you call `requestPersist()` then values of these fields will be scheduled to be 
  persisted. *Warning: the `@SetFromFlag` functionality may change in future versions.*

Persistable state:

* Ensure values can be serialized. This (currently) uses xstream, which means it does not
  need to implement `Serializable`.
* Always use static (or top-level) classes. Otherwise it will try to also persist the outer 
  instance!
* Any reference to an entity or location will be automatically swapped out for marker, and 
  re-injected with the new entity/location instance on rebind. The same applies for policies,
  enrichers, feeds, catalog items and `ManagementContext`.

Behaviour on rebind:

* By extending `SoftwareProcess`, entities get a lot of the rebind logic for free. For 
  example, the default `rebind()` method will call `connectSensors()`.
  See [`SoftwareProcess` Lifecycle]({{site.path.guide}}/java/entities.html#SoftwareProcess-lifecycle)
  for more details.
* If necessary, implement rebind. The `entity.rebind()` is called automatically by the
  Brooklyn framework on rebind, after configuring the entity's config/attributes but before 
  the entity is managed.
  Note that `init()` will not be called on rebind.
* Feeds will be persisted if and only if `entity.addFeed(...)` was called. Otherwise the
  feed needs to be re-registered on rebind. *Warning: this behaviour may change in future version.*
* All functions/predicates used with persisted feeds must themselves be persistable - 
  use of anonymous inner classes is strongly discouraged.
* Subscriptions (e.g. from calls to `subscribe(...)` for sensor events) are not persisted.
  They must be re-registered on rebind.  *Warning: this behaviour may change in future version.*

Below are tips to make backwards-compatibility easier for persisted state: 

* Never use anonymous inner classes - even in static contexts. The auto-generated class names 
  are brittle, making backwards compatibility harder.
* Always use sensible field names (and use `transient` whenever you don't want it persisted).
  The field names are part of the persisted state.
* Consider using Value Objects for persisted values. This can give clearer separation of 
  responsibilities in your code, and clearer control of what fields are being persisted.
* Consider writing transformers to handle backwards-incompatible code changes.
  Brooklyn supports applying transformations to the persisted state, which can be done as 
  part of an upgrade process.


Persisted State Backup
----------------------

### File system backup

When using the file system it is important to ensure it is backed up regularly.

One could use `rsync` to regularly backup the contents to another server.

It is also recommended to periodically create a complete archive of the state.
A simple mechanism is to run a CRON job periodically (e.g. every 30 minutes) that creates an
archive of the persistence directory, and uploads that to a backup 
facility (e.g. to S3).

Optionally, to avoid excessive load on the Brooklyn server, the archive-generation could be done 
on another "data" server. This could get a copy of the data via an `rsync` job.

An example script to be invoked by CRON is shown below:

    DATE=`date "+%Y%m%d.%H%M.%S"`
    BACKUP_FILENAME=/path/to/archives/back-${DATE}.tar.gz
    DATA_DIR=/path/to/base/dir/data
    
    tar --exclude '*/backups/*' -czvf $BACKUP_FILENAME $DATA_DIR
    # For s3cmd installation see http://s3tools.org/repositories
    s3cmd put $BACKUP_FILENAME s3://mybackupbucket
    rm $BACKUP_FILENAME


### Object store backup

Object Stores will normally handle replication. However, many such object stores do not handle 
versioning (i.e. to allow access to an old version, if an object has been incorrectly changed or 
deleted).

The state can be downloaded periodically from the object store, archived and backed up. 

An example script to be invoked by CRON is shown below:

    DATE=`date "+%Y%m%d.%H%M.%S"`
    BACKUP_FILENAME=/path/to/archives/back-${DATE}.tar.gz
    TEMP_DATA_DIR=/path/to/tempdir
    
    amp copy-state \
            --persistenceLocation named:my-persistence-location \
            --persistenceDir /path/to/bucket \
            --destinationDir $TEMP_DATA_DIR

    tar --exclude '*/backups/*' -czvf $BACKUP_FILENAME $TEMP_DATA_DIR
    # For s3cmd installation see http://s3tools.org/repositories
    s3cmd put $BACKUP_FILENAME s3://mybackupbucket
    rm $BACKUP_FILENAME
    rm -r $TEMP_DATA_DIR
