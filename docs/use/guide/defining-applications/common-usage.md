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

- SSH: any machine or set of machines to which you can ssh
- Clouds: Amazon, GoGrid, vCloud, and many more (using jclouds)

Configuration is typically set in `~/.brooklyn/brooklyn.properties` using keys such as the following:

{% highlight java %}
    # use this key for localhost (this is the default, although if you have a passphrase you must set it)
    brooklyn.localhost.privateKeyFile=~/.ssh/id_rsa
    brooklyn.localhost.privateKeyPassphrase=s3cr3tPASSPHRASE
       
    # use a special key when connecting to public clouds, and a special one for AWS
    brooklyn.jclouds.privateKeyFile=~/.ssh/public_clouds/id_rsa
    brooklyn.jclouds.aws-ec2.privateKeyFile=~/.ssh/public_clouds/aws_id_rsa
        
    # AWS credentials (when deploying to location jclouds:aws-ec2)
    brooklyn.jclouds.aws-ec2.identity=ABCDEFGHIJKLMNOPQRST      
    brooklyn.jclouds.aws-ec2.credential=s3cr3tsq1rr3ls3cr3tsq1rr3ls3cr3tsq1rr3l

    # define a "named" location which uses a special set of AWS credentials (deploy to named:company-aws)
    brooklyn.location.named.company-aws=jclouds:aws-ec2:us-west-1
    brooklyn.location.named.company-aws.identity=BCDEFGHIJKLMNOPQRSTU      
    brooklyn.location.named.company-aws.privateKeyFile=~/.ssh/public_clouds/company_aws_id_rsa

    # and a "named" location which uses a fixed set of machines (deploy to named:prod1)
    brooklyn.location.named.prod1=byon:(hosts="10.9.0.1,10.9.0.2,10.9.0.3,10.9.0.4")
    brooklyn.location.named.prod1.user=produser      
    brooklyn.location.named.prod1.privateKeyFile=~/.ssh/produser_id_rsa
    brooklyn.location.named.prod1.privateKeyPassphrase=s3cr3tCOMPANYpassphrase
    
    # credentials for 'geoscaling' service
    brooklyn.geoscaling.username=cloudsoft                      
    brooklyn.geoscaling.password=xxx
{% endhighlight %}

These can also be set as environment variables (in the shell) or system properties (java command line).
(There are also ``BROOKLYN_JCLOUDS_PRIVATE_KEY_FILE`` variants accepted.)

For any provider you will typically need to set ``identity`` and ``credential``
in the ``brooklyn.jclouds.provider`` namespace.
Other fields may be available (from brooklyn or jclouds).

Public keys can also be specified, using ``brooklyn.jclouds.publicKeyFile``, 
but these can usually be omitted 
(it will be inferred by adding the suffix ``.pub`` to the private key).
If there is a passphrase on the key file being used, you must supply it to Brooklyn for it to work, of course!

