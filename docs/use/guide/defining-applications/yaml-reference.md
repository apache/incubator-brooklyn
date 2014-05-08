---
title: Broooklyn YAML Blueprint Reference
layout: page
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

## Root Elements

* `name`: human readable names
* `services`: a list of `ServiceSpecification` elements
* `location` (or `locations` taking a list): a `LocationSpecification` element as a string or a map


## `ServiceSpecification` Elements

Within the `services` block, a list of maps should be supplied, with each map
defining a `ServiceSpecification`.  Each `ServiceSpecification` should declare the
service `type` (synonyms `serviceType` and `service_type`), indicating what type of 
service is being specified there.  The following formats are supported for
defining types:

* `io.brooklyn.package.JavaEntityClass`
* `java:io.brooklyn.package.JavaEntityClass`
* *OSGi and YAML references are TODO*

Within the `ServiceSpecification`, other key-value pairs can be supplied to customize
the entity being defined, with these being the most common:

* `id`: an ID string, used to refer to this service

* `location` (or `locations`): as defined in the root element 
  
* `brooklyn.config`: configuration key-value pairs passed to the service entity being created

* `brooklyn.children`: TODO

* `brooklyn.policies`: TODO

Each entity can define additional key-value pairs, with other keys being passed as flags,
so consult its documentation or source for more information. 


## `LocationSpecification` Elements

TODO - as a string or as a map

in brief it is like this:

    location:
      jclouds:aws-ec2:
        region: us-east-1
        identity: AKA_YOUR_ACCESS_KEY_ID
        credential: <access-key-hex-digits>

or in many cases it can be in-lined:

    location: localhost
    location: named:my_openstack
    location: aws-ec2:us-west-1
    
for the first, you'll need password-less ssh access to localhost.
for the second, you'll need to define a named location in `brooklyn.properties`,
using `brooklyn.location.named.my_openstack....` properties.
for the third, you'll need to have the identity and credentials defined in
`brooklyn.properties`, using `brooklyn.location.jclouds.aws-ec2....` properties.
for more information see TODO.

if specifying multiple locations

    locations:
    - localhost
    - named:my_openstack
    - aws-ec2:us-east-2   # if credentials defined in `brooklyn.properties
    - jclouds:aws-ec2:
        region: us-east-1
        identity: AKA_YOUR_ACCESS_KEY_ID
        credential: <access-key-hex-digits>

finally, if you have pre-existing nodes, you can use the `byon` provider:

    location:
      byon:
        user: root
        privateKeyFile: ~/.ssh/couchbase.pem
        hosts:
        - 159.253.144.139
        - 81.95.144.59
        - 159.253.144.140
        - 81.95.144.58

TODO with byon, in yaml are ranges supported e.g. `127.0.0.[1-127]`?

TODO with byon, how do you specify per-host user?


## DSL Commands

Dependency injection other powerful references and types can be built up within the YAML using the
concise DSL defined here:
 
* `$brooklyn:component("ID")` refers to a Brooklyn component with the given ID; you can then access the following subfields:
  * `.attributeWhenReady("sensor")` will store a future which will be blocked when it is accessed,
    until the given `sensor` from the component `ID` has a "truthy" (i.e. non-trivial, non-empty, non-zero) value
  * TODO 
* `$brooklyn:component("scope", "ID")` is also supported, to limit scope to any of
  * `global`: looks for the `ID` anywhere in the plan
  * `child`: looks for the `ID` anywhere in the child only
  * `descendant`: looks for the `ID` anywhere in children or their descendants
  * `sibling`: looks for the `ID` anywhere among children of the parent entity
  * `parent`: returns the parent entity (ignores the `ID`)
  * `this`: returns this entity (ignores the `ID`)
* `$brooklyn:formatString("pattern e.g. %s %s", "field 1", "field 2")` returns a future which creates the formatted string
  with the given parameters, where parameters may be strings *or* other tasks such as `attributeWhenReady`
* `$brooklyn:literal("string")` returns the given string as a literal (suppressing any `$brooklyn:` expansion)
* `$brooklyn:sensor("io.brooklyn.ContainingEntityClass", "sensor.name")` returns the strongly typed sensor defined in the given class
* `$brooklyn:entitySpec(Map)` returns a new `ServiceSpecification` as defined by the given `Map`,
  but as an `EntitySpec` suitable for setting as the value of `ConfigKey<EntitySpec>` config items
  (such as `memberSpec` in `DynamicCluster`)

These can be supplied either as strings or as lists and maps in YAML. 


## Some Powerful YAML Entities

TODO - All entities support configuration via YAML, but these entities in particular 
have been designed for general purpose use from YAML.  Consult the Javadoc for these
elements for more information:


* Vanilla Software
* Chef
* Group and Cluster
* Fabric

