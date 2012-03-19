---
title: Common Usage
layout: page
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

### Entity Class Hierarchy

By convention in Brooklyn the following words have a particular meaning, both as types (which extend ``Group``, which extends ``Entity``) and when used as words in other entities (such as ``TomcatFabric``):

- *Tier* - anything which is homogeneous (has a template and type)
    - *Cluster* - an in-location tier
    - *Fabric* - a multi-location tier
- *Stack* - heterogeneous (mixed types of children)
- *Application* - user's entry point

<!---
TODO
-->

-	*template* entities are often used by groups to define how to instantiate themselves and scale-out.
  A template is an entity which does not have an owner and which is not an application.
-	*traits* (mixins) providing certain capabilities, such as Resizable and Balanceable
-	*Resizable*
-	*Balanceable*
-	*Moveable*
-	*MoveableWithCost*

### Off-the-Shelf Entities

brooklyn includes a selection of entities already available for use in applications,
including appropriate sensors and effectors, and in some cases include Cluster and Fabric variants.
(These are also useful as templates for writing new entities.)
 
These include:

- **Web**: Tomcat, JBoss; nginx; GeoScaling; cluster and fabric
- **Relational Databases**: MySQL, Derby
- **NoSQL**: Infinispan, Redis, GemFire
- **Messaging**: ActiveMQ, Qpid

See [Extras](../extras/) for a full list of systems available out of the box.


### Off-the-Shelf Locations

- SSH
- Compute: Amazon, GoGrid, vCloud, and many more (using jclouds)
{% highlight java %}
    # use a special key when connecting to public clouds
    brooklyn.jclouds.private-key-file=~/.ssh/public_clouds/id_rsa
    
    # need this one for localhost
    brooklyn.jclouds.localhost.private-key-file=~/.ssh/id_rsa   
    
    # AWS credentials
    brooklyn.jclouds.aws-ec2.identity=ABCDEFGHIJKLMNOPQRST      
    brooklyn.jclouds.aws-ec2.credential=s3cr3tsq1rr3ls3cr3tsq1rr3ls3cr3tsq1rr3l
    
    # credentials for 'geoscaling' service
    brooklyn.geoscaling.username=cloudsoft                      
    brooklyn.geoscaling.password=xxx
{% endhighlight %}

These can also be set as environment variables (in the shell) or system properties (java command line).
(There are also ``BROOKLYN_JCLOUDS_PRIVATE_KEY_FILE`` variants accepted.)

For any provider you will typically need to set ``identity`` and ``credential``
in the ``brooklyn.jclouds.provider`` namespace.
Other fields may be available (from brooklyn or jclouds).

``brooklyn.jclouds.public-key-file`` can also be specied but can usually be omitted 
(it will be inferred by adding the suffix ``.pub`` to the private key).
There should be no passphrases on the key files.

