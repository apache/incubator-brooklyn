---
title: Catalog
layout: website-normal
---

Brooklyn has a 'catalog', which is a collection of versioned blueprints. 
These can be deployed directly or referenced by other blueprints. 
Blueprints in the catalog can be deployed via the Brooklyn REST API, or from 
the web-console's 'Catalog' section of the 'Create Application' dialog box.


<!--
TODO: Clean up confusion in terminology between Catalog item and Blueprint (and Java blueprint?)?
-->

### Catalog items

An item to be added to the catalog is defined in YAML. This looks like any other 
YAML blueprint, but also has a `brooklyn.catalog` section giving appropriate metadata.

An example is shown below.

{% highlight yaml %}
brooklyn.catalog:
  id: org.example.MySQL
  version: 1.0
  iconUrl: classpath://mysql.png
  description: MySql is an open source relational database management system (RDBMS)
  libraries:
    - url: http://example.com/path/to/my-dependency-1.2.3.jar
    - url: http://example.com/path/to/my-other-dependency-4.5.6.jar

services:
- type: brooklyn.entity.database.mysql.MySqlNode
{% endhighlight %}

To explain the `brooklyn.catalog` section:

- The `id: MySQL` section specifies a unique ID used by Brooklyn to identify the catalog item. 
  Other blueprints can reference the catalog item using this id.
- The `version: 1.0` section provides a unique version for the *blueprint*. NOTE: This is *not* the version of the software
being installed (in this case MySQL).
- The `iconUrl: classpath://...` is an optional, but recommended, section that allows you to provide the URL of a graphic to be 
displayed in the 'Add Application' dialog alongside the blueprint name. If using a URL of the form `classpath://...`, then
the image should be on Brooklyn's classpath (e.g. in the `conf` folder of the Brooklyn distro).
- The `description: ...` section allows you to give a free-format description of the blueprint, which is displayed in the 
'Add Application' dialog.

The `libraries` section references OSGi bundles required for the blueprint. It can be omitted if everything
required by the blueprint is already on the Brooklyn classpath. The URLs should be to stable OSGi bundles -
if the bundle at this URL changes then this could impact applications if the Brooklyn server is restarted
or fails over to a standby node.


To reference a catalog item in another blueprint, simply use its id and optionally its version number.
For example: 

{% highlight yaml %}
services:
- type: org.example.MySQL:1.0
{% endhighlight %}


### Adding to the Catalog

To add a catalog item to the catalog, post the YAML file to Brooklyn's REST API by using the `curl` command as
follows (substituting your own usename:password, URL and file path):

{% highlight bash %}
curl -u admin:password http://127.0.0.1:8081/v1/catalog --data-binary @/path/to/mysql-catalog.yaml
{% endhighlight %}



### Deleting from the Catalog

You can delete a versioned item from the catalog using the REST API. For example, to delete
the item with id `org.example.MySQL` and version `1.0`:

{% highlight bash %}
curl -u admin:password -X DELETE http://127.0.0.1:8081/v1/catalog/entities/MySQL/1.0
{% endhighlight %}

**Note** Catalog items should not be deleted if there are running apps which were created using the same item. During
rebinding the catalog item is used to reconstruct the entity.


### Versioning

Version numbers follow the OSGi convention. This can have a major, minor, micro and qualifier part.
For example, `1.0`. `1.0.1` or `1.0.1-20150101`.

If you attempt to deploy the same version of a catalog item a second time, you will receive an 
error stating `Updating existing catalog entries is forbidden`.
To update the blueprint, you will need to change the version number in your yaml file and then
POST the new YAML file.

An exception to this rule is when no version is specified. Re-deploying will automatically
increment an internal version number for that catalog item.

When referencing a blueprint, if a version number is not specified then the latest version will
be used when the application is deployed.


### Special requirements for 'Create Application' dialog

For a blueprint in the catalog to be accessible via the 'Create Application' dialog, it must be an Application 
(i.e. the entity at the root of the blueprint must implement `brooklyn.entity.Application`).
In contrast, if a YAML blueprint is deployed direct via the REST API, then this is not necessary.

For example, the MySql catalog item defined previously could be re-written to use a
`brooklyn.entity.basic.BasicApplication`, because no application-specific logic is 
required other than to pass-through the start and stop commands.
the `MySqlNode` is added as a child of the `BasicApplication`.

{% highlight yaml %}
brooklyn.catalog:
  id: org.example.MySQL
  version: 1.0
  iconUrl: classpath://mysql.png
  description: MySql is an open source relational database management system (RDBMS)

name: MySQL Database
services:
- type: brooklyn.entity.basic.BasicApplication
  brooklyn.children:
  - type: brooklyn.entity.database.mysql.MySqlNode
{% endhighlight %}

When added to the catalog via the HTTP POST command, the blueprint will appear in the 'Create Application' dialog
as shown here:

[![MySQL in Brooklyn Catalog](mysql-in-catalog-w700.png "MySQL in Brooklyn Catalog")](mysql-in-catalog.png) 

When deploying a new version of a blueprint, the catalog will show both the previous and the new versions 
of the blueprint. You may wish to delete the older version, assuming no applications currently running
are using that old version.

<!--
TODO: Should improve the 'Create Application' dialog, so that the two versions don't appear on the front page.
Currently they are indistinguisable, if they have the same description/icon.
-->


<!--
TODO: Add section that explains how to add plain entities to the catalog and use them either from the App Wizard,
(and entity UI) or embed the catalog id + version in another YAML
-->

<!--
TODO: Add documentation to explain that the brooklyn.catalog section can contain a libraries array, each item pointing to 
an OSGi bundle where the code for the blueprint is hosted. Every type from the blueprint will be searched for in the 
libraries first and then on the standard Brooklyn classpath.*
-->

<!--
TODO: Add documentation about adding policies to the catalog, and explaining how to add items to 
the UI using the plus icon on the catalog tab*

TODO: describe entity addition (this just covers app addition)

TODO: describe how to use the web-console GUI
-->
