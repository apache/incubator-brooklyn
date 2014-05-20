---
layout: page
title: Release Notes
toc: ../toc.json
---

## Brooklyn Version 0.7.0-M1

* Introduction
* New Features
* Backwards Compatibility
* Expectations for future releases
* Community Activity

### Introduction

This milestone release includes many big features, and brings us much closer to 0.7.0.

It incorporates a lot of improvements and feedback from our community. Thank you!

Thanks also go to Brooklyn's commercial users. Already Brooklyn has been adopted into some very exciting projects including 

For more information, please checkout [brooklyncentral.github.io](http://brooklyncentral.github.io), and our
[mailing list at Apache](https://mail-archives.apache.org/mod_mbox/incubator-brooklyn-dev/).

### New Features

The major changes between 0.6.0 and 0.7.0-M1 are:

1. Blueprints in YAML. This is a *major* step forward, making writing using and modifying 
   blueprints accessible to a wider audience. By writing a YAML text file, without touching
   a line of Java code, one can wire together the components and runtime policies of an
   application. The format follows the OASIS CAMP specification with some extensions:
   https://www.oasis-open.org/committees/camp/
   
2. Persistence and rebind. Brooklyn persists its state, so that on restart it will rebind 
   to the existing entities under management. Previously, this required custom coding.
   Now it just requires starting with `brooklyn launch --persist auto --noShutdownOnExit`.
   Most likely, `--persist auto` will become the default in the 0.7.0 GA release.
   Note that policies are not yet persisted and will not be present after a stop-and-rebind;
   this will be fixed in another release soon!

3. Many usability and performance improvements to the web-console.

4. Reloading of brooklyn configuration. When triggered (through the web-console, REST api, or
   via code), the brooklyn.properties will be reloaded.

5. Various performance improvements, including reuse of ssh sessions when executing 
   multiple (frequent) ssh commands on a given machine.

6. The maven archetype, for generating a new downstream project, has an improved main class.
   This extends the default brooklyn main (used by `brooklyn launch`) so shares all the 
   same features.

7. Increased Windows support - Brooklyn can now be started on Windows using the new 
   `brooklyn.ps1` script, which behaves as the Linux/UNIX `brooklyn` script.

8. Improvements to REST api, including:
    * Deploying YAML blueprints
    * Retrieving location details
    * Fetching entity info for all (or filtered) descendants; see /{application}/descendants

9. Many new conveniences and utilities, including:
    * brooklyn.entity.software.OsTasks for finding the OS and architecture details of a machine.
    * brooklyn.util.os.Os, including for improved use of tmp directories.
    * brooklyn.enricher.Enrichers.builder(), for easier construction of enrichers using a 
      fluent API.
    * brooklyn.util.http.HttpTool, including a fluent API for building 
      org.apache.http.client.HttpClient and for doing simple GET/POST/PUT/DELETE requests.
    * brooklyn.util.javalang.Threads

10. Massive improvements to several clouds, including SoftLayer and Google Compute Engine
   (being contributed back to jclouds).

11. New entities include:
    * VanillaSoftwareProcess for a script-based entity, requiring no Java code to be written.
    * MongoDB sharded database.
    * Solr
    * Zookeeper ensemble (i.e. a cluster of Zeekeeper servers)


### Backwards Compatibility

1. OpenShift integration has moved from core brooklyn to the downstream project 
   https://github.com/cloudsoft/brooklyn-openshift

2. Code deprecated in 0.6.0 has been deleted.

3. Location configuration getter/setter methods changed to match those of Entities.
   This is in preparation for having all Locations *be* Entities, expected in 0.7.0 GA.

4. Some additional code has been deprecated - users are strongly encouraged to update these:
    1. Various enricher classes, and base abstract classes, have been deprecated in favour 
       of the new Enrichers.builder().
    1. BasicGroup.CHILDREN_AS_MEMBERS, preferring the new Group.addMemberChild()
    1. BasicStartable.LocationFilter has been generalised to Locations.LocationsFilter
    1. Config keys SUGGESTED_INSTALL_DIR and SUGGESTED_RUN_DIR are renamed to INSTALL_DIR
       and RUN_DIR respectively.
    1. brooklyn.event.feed.http.HttpPolls is deprecated, preferring brooklyn.util.http.HttpTool
    1. brooklyn.util.task.ExecutionUtils is deprecated, preferring more strongly typed 
       approaches.
    1. Methods of brooklyn.util.ResourceUtils are deprecated, preferring methods in 
       other less generic utility classes.
    1.  Changes to REST api:
        * ApplicationSpec is deprecated, preferring the YAML approach.
        * /v1/server/version deprecated, preferring /v1/server/version
    1. brooklyn.rest.util.URLParamEncoder deprecated, moved to brooklyn.util.net.URLParamEncoder
    1. Config keys DB_URL and MARIADB_URL deprecated, preferring DATASTORE_URL.
        Note this may break backwards compatibility where the string name "database.url" was used;
        these need to be changed to "datastore.url".
    1. CassandraCluster renamed to CassandraDatacenter, to match Cassandra's naming convention.
    1. brooklyn.util.ssh.IptablesCommands.Protocol deprecated, preferring the new more generic 
       brooklyn.util.net.Protocol
    1. ShellUtils deprecated as it loses important information. Prefer utilities such as ShellFeed.


### Expectations for future releases

Big things we expect in 0.7.0 GA, or very soon after, include:

1. Location extends Entity - i.e. all locations will be entities and can be explored 
   better via the API and managed autonomically.

2. Brooklyn persistence on by default, with improved robustness, error reporting, and 
   manual recovery. 


### Community Activity

We're really exciting about the open source downstream projects using Brooklyn, 
especially for Cassandra, MongoDB and OpenGamma to name but a few.

We're also looking forward to imminent support for Docker, being led by @andreaturli
