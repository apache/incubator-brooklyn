---
title: Catalog
layout: website-normal
children:
- { section: General YAML Schema }
- { section: Catalog Metadata }
- { section: Catalog YAML Examples }
- { section: Templates and the Add-Application Wizard, title: Templates }
- { section: Adding to the Catalog, title: Adding and Deleting } 
- { section: Versioning } 
- { section: CLI Options }
 
---

Brooklyn provides a **catalog**, which is a persisted collection of versioned blueprints and other resources. 
Blueprints in the catalog can be deployed directly, via the Brooklyn REST API or the web console,
or referenced in other blueprints using their `id`.

 
### Catalog Items YAML Syntax

An item or items to be added to the catalog is defined by a YAML file,
specifying the catalog metadata for the items and the actual blueprint or resource definition.


#### General YAML Schema
 
A single catalog item can be defined following this general structure:

~~~ yaml
brooklyn.catalog:
  <catalog-metadata>
  item:
    <blueprint-or-resource-definition>
~~~ 


To define multiple catalog items in a single YAML,
where they may share some metadata,
use the following structure:

~~~ yaml
brooklyn.catalog:
  <catalog-metadata>
  items:
  - <additional-catalog-metadata>
    item:
      <blueprint-or-resource-definition>
  - <additional-catalog-metadata>
    item:
      <blueprint-or-resource-definition>
~~~ 


#### Catalog Metadata

Catalog metadata fields supply the additional information required In order to register an item in the catalog. 
These fields can be supplied as `key: value` entries 
where either the `<catalog-metadata>` or `<additional-catalog-metadata>` placeholders are,
with the latter overriding the former unless otherwise specfied below.

The following metadata is *required* for all items:

- `id`: a human-friendly unique identifier for how this catalog item will be referenced from blueprints
- `version`: multiple versions of a blueprint can be installed and used simultaneously;
  this field disambiguates between blueprints of the same `id`.
  Note that this is typically *not* the version of the software being installed,
  but rather the version of the blueprint. For more information on versioning, see below.
  (Also note YAML treats numbers differently to Strings. Explicit quotes may sometimes be required.)

To reference a catalog item in another blueprint, simply reference its ID and optionally its version number.
For instance, if we've added an item with metadata `{ id: datastore, version: "1.0" }` (such as the example below),
we could refer to it in another blueprint with: 

~~~ yaml
services:
- type: datastore:1.0
~~~ 

In addition to the above fields, exactly **one** of the following is also required:

- `item`: the YAML for a service or policy or location specification 
  (a map containing `type` and optional `brooklyn.config`)
  or a full application blueprint (in the usual YAML format) for a template; **or*
- `items`: a list of catalog items, where each entry in the map follows the same schema as
  the `brooklyn.catalog` value, and the keys in these map override any metadata specified as
  a sibling of this `items` key (or, in the case of `brooklyn.libraries` they add to the list);
  if there are references between items, then order is important, 
  `items` are processed in order, depth-first, and forward references are not supported.

The following optional catalog metadata is supported:
  
- `itemType`: the type of the item being defined.
  When adding a template (see below) this must be set.
  In most other cases this can be omitted and type type will be inferred.
  The supported item types are:
  - `entity`
  - `template`
  - `policy`
  - `location`
- `name`: a nicely formatted display name for the item, used when presenting it in a GUI
- `description`: supplies an extended textual description for the item
- `iconUrl`: points to an icon for the item, used when presenting it in a GUI.
  The URL prefix `classpath` is supported but these URLs may *not* refer items in any OSGi bundle in the `brooklyn.libraries` section 
  (to prevent requiring all OSGi bundles to be loaded at launch).
  Icons are instead typically installed either at the server from which the OSGi bundles or catalog items are supplied 
  or in the `conf` folder of the Brooklyn distro.
- `scanJavaAnnotations` [experimental]: if provided (as `true`), this will scan any locally provided
  library URLs for types annotated `@Catalog` and extract metadata to include them as catalog items.
  If no libraries are specified this will scan the default classpath.
  This feature is experimental and may change or be removed.
  Also note that external OSGi dependencies are not supported 
  and other metadata (such as versions, etc) may not be applied.
- `brooklyn.libraries`: a list of pointers to OSGi bundles required for the catalog item.
  This can be omitted if blueprints are pure YAML and everything required is included in the classpath and catalog.
  Where custom Java code or bundled resources is needed, however, OSGi JARs supply
  a convenient packaging format and a very powerful versioning format.
  Libraries should be supplied in the form 
  `brooklyn.libraries: [ "http://...", "http://..." ]`, 
  or as
  `brooklyn.libraries: [ { name: symbolic-name, version: 1.0, url: http://... }, ... ]` if `symbolic-name:1.0` 
  might already be installed from a different URL and you want to skip the download.
  Note that these URLs should point at immutable OSGi bundles;
  if the contents at any of these URLs changes, the behaviour of the blueprint may change 
  whenever a bundle is reloaded in a Brooklyn server,
  and if entities have been deployed against that version, their behavior may change in subtle or potentially incompatible ways.
  To avoid this situation, it is highly recommended to use OSGi version stamps as part of the URL.


#### Catalog YAML Examples

##### A Simple Example

The following example installs the `RiakNode` entity, making it also available as an application template,
with a nice display name, description, and icon.
It can be referred in other blueprints to as `datastore:1.0`,
and its implementation will be the Java class `org.apache.brooklyn.entity.nosql.riak.RiakNode` included with Brooklyn.

~~~ yaml
brooklyn.catalog:
  id: datastore
  version: 1.0
  itemType: template
  iconUrl: classpath://org/apache/brooklyn/entity/nosql/riak/riak.png
  name: Datastore (Riak)
  description: Riak is an open-source NoSQL key-value data store.
  item:
    type: org.apache.brooklyn.entity.nosql.riak.RiakNode
    name: Riak Node
~~~ 


##### Multiple Items

This YAML will install three items:

~~~ yaml
brooklyn.catalog:
  version: 1.1
  iconUrl: classpath://org/apache/brooklyn/entity/nosql/riak/riak.png
  description: Riak is an open-source NoSQL key-value data store.
  items:
    - id: riak-node
      item:
        type: org.apache.brooklyn.entity.nosql.riak.RiakNode
        name: Riak Node
    - id: riak-cluster
      item:
        type: org.apache.brooklyn.entity.nosql.riak.RiakCluster
        name: Riak Cluster
    - id: datastore
      name: Datastore (Riak Cluster)
      itemType: template
      item:
        services:
        - type: riak-cluster
          location: 
            jclouds:softlayer:
              region: sjc01
              # identity and credential must be set unless they are specified in your brooklyn.properties
              # identity: XXX
              # credential: XXX
          brooklyn.config:
            # the default size is 3 but this can be changed to suit your requirements
            initial.size: 3
            provisioning.properties:
              # you can also define machine specs
              minRam: 8gb
~~~ 

The items this will install are:

- `riak-node`, as before, but with a different name
- `riak-cluster` as a convenience short name for the `org.apache.brooklyn.entity.nosql.riak.RiakCluster` class
- `datastore`, now pointing at the `riak-cluster` blueprint, in SoftLayer and with the given size and machine spec, 
  as the default implementation for anyone
  requesting a `datastore` (and if installed atop the previous example, new references to `datastore` 
  will access this version because it is a higher number);
  because it is a template, users will have the opportunity to edit the YAML (see below).
  (This must be supplied after `riak-cluster`, because it refers to `riak-cluster`.)


#### Legacy Syntax

The following legacy and experimental syntax is also supported:

~~~ yaml
<blueprint-definition>
brooklyn.catalog:
  <catalog-metadata>
~~~ 

In this format, the `brooklyn.catalog` block is optional;
and an `id` in the `<blueprint-definition>` will be used to determine the catalog ID. 
This is primarily supplied for OASIS CAMP 1.1 compatibility,
where the same YAML blueprint can be POSTed to the catalog endpoint to add to a catalog
or POSTed to the applications endpoint to deploy an instance.
(This syntax is discouraged as the latter usage, 
POSTing to the applications endpoint,
will ignored the `brooklyn.catalog` information;
this means references to any `item` blocks in the `<catalog-metadata>` will not be resolved,
and any OSGi `brooklyn.libraries` defined there will not be loaded.)



### Templates and the Add-Application Wizard

When a `template` is added to the catalog, the blueprint will appear in the 'Create Application' dialog
as shown here:

[![MySQL in Brooklyn Catalog](mysql-in-catalog-w700.png "MySQL in Brooklyn Catalog")](mysql-in-catalog.png) 



### Catalog Management

The Catalog tab in the web console will show all versions of catalog items,
and allow you to add new items.


#### Adding to the Catalog

On the UI the "add" button <img src="images/add-to-catalog.png" width="24" alt="add-to-catalog" /> at the top of the menu panel allows the
addition of new Applications to the catalog, via YAML, and of new Locations.

In addition to the GUI, items can be added to the catalog via the REST API
with a `POST` of the YAML file to `/v1/catalog` endpoint.
To do this using `curl`:

~~~ bash
curl http://127.0.0.1:8081/v1/catalog --data-binary @/path/to/riak.catalog.bom
~~~ 



#### Deleting from the Catalog

On the UI, if an item is selected, a 'Delete' button in the detail panel can be used to delete it from the catalog.

Using the REST API, you can delete a versioned item from the catalog using the corresponding endpoint. 
For example, to delete the item with id `datastore` and version `1.0` with `curl`:

~~~ bash
curl -X DELETE http://127.0.0.1:8081/v1/catalog/applications/datastore/1.0
~~~ 

**Note:** Catalog items should not be deleted if there are running apps which were created using the same item. 
During rebinding the catalog item is used to reconstruct the entity.

If you have running apps which were created using the item you wish to delete, you should instead deprecate the catalog item.
Deprecated catalog items will not appear in the add application wizard, or in the catalog list but will still
be available to Brooklyn for rebinding. The option to display deprecated catalog items in the catalog list will be added
in a future release.

Deprecation applies to a specific version of a catalog item, so the full
id including the version number is passed to the REST API as follows:

~~~ bash
curl -X POST http://127.0.0.1:8081/v1/catalog/entities/MySQL:1.0/deprecated/true
~~~ 


### Versioning

Version numbers follow the OSGi convention. This can have a major, minor, micro and qualifier part.
For example, `1.0`. `1.0.1` or `1.0.1-20150101`.

The combination of `id:version` strings must be unique across the catalog.
It is an error to deploy the same version of an existing item:
to update a blueprint, it is recommended to increase its version number;
alternatively in some cases it is permitted to delete an `id:version` instance
and then re-deploy.
If no version is specified, re-deploying will automatically
increment an internal version number for the catalog item.

When referencing a blueprint, if a version number is not specified 
the latest non-snapshot version will be loaded when an entity is instantiated.


### CLI Options

The `brooklyn` CLI includes several commands for working with the catalog.

* `--catalogAdd <file.bom>` will add the catalog items in the `bom` file
* `--catalogReset` will reset the catalog to the initial state 
  (based on `brooklyn/default.catalog.bom` on the classpath, by default in a dist in the `conf/` directory)
* `--catalogInitial <file.bom>` will set the catalog items to use on first run,
  on a catalog reset, or if persistence is off

If `--catalogInitial` is not specified, the default initial catalog at `brooklyn/default.catalog.bom` will be used.
As `scanJavaAnnotations: true` is set in `default.catalog.bom`, Brooklyn will scan the classpath for catalog items,
which will be added to the catalog.
To launch Brooklyn without initializing the catalog, use `--catalogInitial classpath://brooklyn/empty.catalog.bom`

If [persistence](../persistence/) is enabled, catalog additions will remain between runs. If items that were
previously added based on items in `brooklyn/default.catalog.bom` or `--catalogInitial` are 
deleted, they will not be re-added on subsequent restarts of brooklyn. I.e. `--catalogInitial` is ignored
if persistence is enabled and persistent state has already been created.

For more information on these commands, run `brooklyn help launch`.


<!--
TODO: make test cases from the code snippets here, and when building the docs assert that they match test cases
-->
