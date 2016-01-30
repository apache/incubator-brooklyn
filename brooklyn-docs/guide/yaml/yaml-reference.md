---
title: Broooklyn YAML Blueprint Reference
layout: website-normal
---

## Root Elements

* `name`: human readable names
* `services`: a list of `ServiceSpecification` elements
* `location` (or `locations` taking a list): a `LocationSpecification` element as a string or a map


## Service Specification Elements

Within the `services` block, a list of maps should be supplied, with each map
defining a `ServiceSpecification`.  Each `ServiceSpecification` should declare the
service `type` (synonyms `serviceType` and `service_type`), indicating what type of 
service is being specified there.  The following formats are supported for
defining types:

* `com.acme.brooklyn.package.JavaEntityClass`
* `java:com.acme.brooklyn.package.JavaEntityClass`
* `java-entity-class` (where this has been added to the [catalog](../ops/catalog/))

A reference of some of the common service `type` instances used is included in a section below.

Within the `ServiceSpecification`, other key-value pairs can be supplied to customize
the entity being defined, with these being the most common:

* `id`: an ID string, used to refer to this service

* `location` (or `locations`): as defined in the root element 
  
* `brooklyn.config`: configuration key-value pairs passed to the service entity being created

* `brooklyn.children`: a list of `ServiceSpecifications` which will be configured as children of this entity

* `brooklyn.policies`: a list of policies, each as a map described with their `type` and their `brooklyn.config` as keys

* `brooklyn.enrichers`: a list of enrichers, each as a map described with their `type` and their `brooklyn.config` as keys;
  see the keys declared on individual enrichers; 
  also see [this enricher example](example_yaml/test-app-with-enrichers-slightly-simpler.yaml) for a detailed and commented illustration
  <!-- TODO assert that this yaml maches the yaml we test against -->

* `brooklyn.initializers`: a list of `EntityInitializer` instances to be constructed and run against the entity, 
  each as a map described with their `type` and their `brooklyn.config` as keys.
  An `EntityInitiailzer` can perform arbitrary customization to an entity whilst it is being constructed,
  such as adding dynamic sensors and effectors. These classes must expose a public constructor taking
  a single `Map` where the `brooklyn.config` is passed in.
  Some common initializers are:
  
  * `org.apache.brooklyn.core.effector.ssh.SshCommandEffector`: takes a `name` and `command`,
    and optionally a map of named `parameters` to their `description` and `defaultValue`,
    to define an effector with the given name implemented by the given SSH command
    (on an entity which as an ssh-able machine)

  * `org.apache.brooklyn.core.sensor.ssh.SshCommandSensor`: takes a `name` and `command`,
    and optionally a `period`, to create a sensor feed which populates the sensor with
    the given name by running the given command (on an entity which as an ssh-able machine)

* `brooklyn.parameters`: documents a list of typed parameters the entity accepts. If none
  are specified the config keys declared in the entity's class are used (including the
  information from the `@CatalogConfig` annotation). The items have the following properties:
  * `name` (required): identifier by which to reference the parameter when setting
    or retrieving its value
  * `label`: a value to present to the user, same as `name` if empty
  * `description`: short text describing the parameter behaviour/usage, presented
    to the user
  * `type`: the type of the parameter, one of `string`, `integer`, `long`, `float`,
    `double`, `timestamp`, `duration`, `port`, or a fully qualified Java type name;
    the default is `string`;
    obvious coercion is supported so 
    `timestamp` accepts most common ISO date formats, `duration` accepts `5m`, and port accepts `8080+`
  * `default`: a default value; this will be coerced to the declared `type`
  * `constraints`: a list of constraints the parameter should meet;
    currently `required` is supported, with the default being not required

  A shorthand notation is also supported where just the name of the parameter is supplied
  as an item in the list, with the other values being unset or the default.
  See `displayName` in the following example for an illustration of this:

~~~ yaml
brooklyn.parameters:
# user.age parameter is required, and fully specified
- name: user.age
  type: integer
  label: Age
  description: the age of the user
  constraints:
  - required
# user.name is optional, and has a default
- name: user.name
  default: You
# shorthand notation: displayName will be an optional config of type string with no default
- displayName
~~~

Entities, policies, and initializers may accept additional key-value pairs,
usually documented in their documentation (e.g. javadoc), or in the case of Java
often as static fields in the underlying Java class.
Often there are config keys or flags (indicated by `@SetFromFlag`) declared on the class; 
these declared flags and config keys may be passed in at the root of the `ServiceSpecification` or in `brooklyn.config`.
(Undeclared config is only accepted in the `brooklyn.config` map.)
Referencing the parameters from within java classes is identical to using config keys. In yaml it's
usually referenced using `$brooklyn:scopeRoot().config("displayName")`. See below for more details on scopes.


## Location Specification Elements

<!-- TODO - expand this, currently it's concise notes -->

In brief, location specs are supplied as follows, either for the entire application (at the root)
or for a specific `ServiceSpecification`:

    location:
      jclouds:aws-ec2:
        region: us-east-1
        identity: AKA_YOUR_ACCESS_KEY_ID
        credential: <access-key-hex-digits>

Or in many cases it can be in-lined:

    location: localhost
    location: named:my_openstack
    location: aws-ec2:us-west-1

For the first immediately, you'll need password-less ssh access to localhost.
For the second, you'll need to define a named location in `brooklyn.properties`,
using `brooklyn.location.named.my_openstack....` properties.
For the third, you'll need to have the identity and credentials defined in
`brooklyn.properties`, using `brooklyn.location.jclouds.aws-ec2....` properties.

If specifying multiple locations, e.g. for a fabric:

    locations:
    - localhost
    - named:my_openstack
    - aws-ec2:us-east-2   # if credentials defined in `brooklyn.properties
    - jclouds:aws-ec2:
        region: us-east-1
        identity: AKA_YOUR_ACCESS_KEY_ID
        credential: <access-key-hex-digits>

If you have pre-existing nodes, you can use the `byon` provider, either in this format:

    location:
      byon:
        user: root
        privateKeyFile: ~/.ssh/key.pem
        hosts:
        - 81.95.144.58
        - 81.95.144.59
        - brooklyn@159.253.144.139
        - brooklyn@159.253.144.140

or:

    location:
      byon:
        user: root
        privateKeyFile: ~/.ssh/key.pem
        hosts: "{81.95.144.{58,59},brooklyn@159.253.144.{139-140}"

You cannot use glob expansions with the list notation, nor can you specify per-host
information apart from user within a single `byon` declaration.
However you can combine locations using `multi`:

    location:
      multi:
        targets:
        - byon:
            user: root
            privateKeyFile: ~/.ssh/key.pem
            hosts:
            - 81.95.144.58
            - 81.95.144.59
        - byon:
            privateKeyFile: ~/.ssh/brooklyn_key.pem
            hosts: brooklyn@159.253.144{139-140}


## DSL Commands

Dependency injection other powerful references and types can be built up within the YAML using the
concise DSL defined here:
 
* `$brooklyn:attributeWhenReady("sensor")` will store a future which will be blocked when it is accessed,
  until the given `sensor` from this entity "truthy" (i.e. non-trivial, non-empty, non-zero) value
  (see below on `component` for looking up values on other sensors) 
* `$brooklyn:config("key")` will insert the value set against the given key at this entity (or nearest ancestor);
  can be used to supply config at the root which is used in multiple places in the plan
* `$brooklyn:sensor("sensor.name")` returns the given sensor on the current entity if found, or an untyped (Object) sensor;
  `$brooklyn:sensor("com.acme.brooklyn.ContainingEntityClass", "sensor.name")` returns the strongly typed sensor defined in the given class
* `$brooklyn:component("ID")` refers to a Brooklyn component with the given ID; you can then access the following subfields,
  using the same syntax as defined above but with a different reference entity,
  e.g. `$brooklyn:component("ID").attributeWhenReady("sensor")`:
  * `.attributeWhenReady("sensor")`
  * `.config("key")`
  * `.sensor("sensor.name")`
* `$brooklyn:component("scope", "ID")` is also supported, to limit scope to any of
  * `global`: looks for the `ID` anywhere in the plan
  * `child`: looks for the `ID` anywhere in the child only
  * `descendant`: looks for the `ID` anywhere in children or their descendants
  * `sibling`: looks for the `ID` anywhere among children of the parent entity
  * `parent`: returns the parent entity (ignores the `ID`)
  * `this`: returns this entity (ignores the `ID`)
* `$brooklyn:root()` will return the topmost entity (the application)
* `$broopklyn:scopeRoot()` will return the root entity in the current plan scope.
  For catalog items it's the topmost entity in the plan, for application plans it is the same as
  `$brooklyn:root()`.
* `$brooklyn:formatString("pattern e.g. %s %s", "field 1", "field 2")` returns a future which creates the formatted string
  with the given parameters, where parameters may be strings *or* other tasks such as `attributeWhenReady`
* `$brooklyn:literal("string")` returns the given string as a literal (suppressing any `$brooklyn:` expansion)
* `$brooklyn:object(Map)` creates an object, using keys `type` to define the java type,
  and either `object.fields` or `brooklyn.config` to supply bean/constructor/flags to create an instance
* `$brooklyn:entitySpec(Map)` returns a new `ServiceSpecification` as defined by the given `Map`,
  but as an `EntitySpec` suitable for setting as the value of `ConfigKey<EntitySpec>` config items
  (such as `memberSpec` in `DynamicCluster`)

<!-- TODO examples for object and entitySpec -->

Parameters above can be supplied either as strings or as lists and maps in YAML, 
and the `$brooklyn:` syntax can be used within those parameters.  


## Some Powerful YAML Entities

All entities support configuration via YAML, but these entities in particular 
have been designed for general purpose use from YAML.  Consult the Javadoc for these
elements for more information:

* **Vanilla Software** in `VanillaSoftwareProcess`: makes it very easy to build entities
  which use `bash` commands to install and the PID to stop and restart
* **Chef** in `ChefSoftwareProcess`: makes it easy to use Chef cookbooks to build entities,
  either with recipes following conventions or with configuration in the `ServiceSpecification`
  to use artibitrary recipes 
* `DynamicCluster`: provides resizable clusters given a `memberSpec` set with `$brooklyn.entitySpec(Map)` as described above 
* `DynamicFabric`: provides a set of homogeneous instances started in different locations,
  with an effector to `addLocation`, i.e. add a new instance in a given location, at runtime
