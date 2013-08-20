---
title: Common Usage
layout: page
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

### Entity Class Hierarchy

By convention in Brooklyn the following words have a particular meaning, both as types (which extend ``Group``, which extends ``Entity``) and when used as words in other entities (such as ``TomcatFabric``):

- *Cluster* - a homogeneous collection of entities
- *Fabric* - a multi-location collection of entities, with one per location; often used with a cluster per location
- *Stack* - heterogeneous (mixed types of children)
- *Application* - user's entry point

<!---
TODO
-->

- *entity spec* defines an entity, so that one or more such entities can be created; often used by clusters/groups to define how to instantiate new children.
- *entity factories* are often used by clusters/groups to define how to instantiate new children.
- *traits* (mixins) providing certain capabilities, such as Resizable and Balanceable
- *Resizable* entities can re-sized dynamically, to increase/decrease the number of child entities.
- *Movable* entities can be migrated between *balanceable containers*.
- *Balanceable containers* can contain *movable* entities, where each contained entity is normally associated with
    a piece of work within that container.

### Off-the-Shelf Entities

brooklyn includes a selection of entities already available for use in applications,
including appropriate sensors and effectors, and in some cases include Cluster and Fabric variants.
(These are also useful as templates for writing new entities.)
 
These include:

- **Web**: Tomcat, JBoss, Jetty (external), Play (external); nginx; GeoScaling
- **Data**: MySQL, Redis, MongoDB, Infinispan, GemFire (external)
- **Containers**: Karaf
- **Messaging**: ActiveMQ, Qpid, Rabbit MQ
- **PaaS**: Cloud Foundry, Stackato; OpenShift


### Off-the-Shelf Locations

Brooklyn supports deploying to any machine which admits SSH access, as well as to
a huge variety of external and on-premise clouds.  You can also connect to services,
or use whatever technique for deployment suits you best (such as Xebia Overthere, in development!).

Configuration is typically set in `~/.brooklyn/brooklyn.properties` using keys such as the following:

{% highlight java %}
    # use this key for localhost (this is the default, although if you have a passphrase you must set it)
    brooklyn.location.localhost.privateKeyFile=~/.ssh/id_rsa
    
    brooklyn.location.localhost.privateKeyPassphrase=s3cr3tPASSPHRASE
       
    # use a special key when connecting to public clouds, and a particularly special one for AWS
    brooklyn.location.jclouds.privateKeyFile=~/.ssh/public_clouds/id_rsa
    brooklyn.location.jclouds.aws-ec2.privateKeyFile=~/.ssh/public_clouds/aws_id_rsa
        
    # AWS credentials (when deploying to location jclouds:aws-ec2)
    brooklyn.location.jclouds.aws-ec2.identity=ABCDEFGHIJKLMNOPQRST      
    brooklyn.location.jclouds.aws-ec2.credential=s3cr3tsq1rr3ls3cr3tsq1rr3ls3cr3tsq1rr3l
    
    # credentials for 'geoscaling' service
    brooklyn.geoscaling.username=cloudsoft                      
    brooklyn.geoscaling.password=xxx
{% endhighlight %}

These can also be set as environment variables (in the shell) or system properties (java command line).
(There are also ``BROOKLYN_JCLOUDS_PRIVATE_KEY_FILE`` variants accepted.)

For any jclouds provider you will typically need to set ``identity`` and ``credential``
in the ``brooklyn.location.jclouds.provider`` namespace.

To deploy to sets of machines with known IP's, assuming you have the credentials,
use the syntax ``byon:(hosts="user@10.9.1.1,user@10.9.1.2,user@10.9.1.3")``
(this requires your default private key to have access; 
see the ``prod1`` example below for specifying other credentials). 

A wide range of other fields is available, because in the real world sometimes things do get complicated.
The following is supported from the configuration file (with whatever customization you might want available in code): 

- If there is a passphrase on the key file being used, you must supply it to Brooklyn for it to work, of course!
  ``privateKeyPassphrase`` does the trick (as in ``brooklyn.location.jclouds.privateKeyPassphrase``, or other places
  where ``privateKeyFile`` is valid).  If you don't like keys, you can just use a plain old ``password``.

- Hardware requirements such as ``minRam`` and ``minCores`` can be supplied, or a ``hardwareId``  (jclouds only)

- Specific Secury Groups can be specified using `securityGroups`, if you want to reuse set of existing ones (jclouds only)

- Specific KeyPair can be specified using `keyPair`, if you want to reuse an existing keypair (jclouds only).

- Specific VM images can be specified using ``imageId`` or ``imageNameRegex`` (jclouds only)

- User metadata can be attached, using the syntax ``userMetadata=key=value,key2="value 2"`` (jclouds only)

- A ``user`` can be specified, with the property that -- in a jclouds world -- the user will be *created* on the machine,
  with admin rights, authorizing the relevant public key (corresponding to the private key, or as described below). 
  Login for the root account will be disabled, as will login by password if a public key is supplied. 
  (This is skipped if ``user`` is the ``root`` or other initial login user.)
  
- You can specify the user account to use to login to jclouds initially with the ``loginUser`` property.
  Typically this is auto-detected by jclouds
  (often ``root``, or ``ubuntu`` or ``ec2-user`` for known Ubuntu or Amazon Linux images), 
  but the strategy isn't foolproof, particularly in some private cloud setups. (jclouds only). In some cases, you may need to specify a `loginUser.privateKeyFile` if the image you are using doesn't allow ssh password login.

- Public keys can be specified using ``publicKeyFile``, 
  although these can usually be omitted if they follow the common pattern of being
  the private key file with the suffix ``.pub`` appended.
  (It is useful in the case of ``loginUser.publicKeyFile``, where you shouldn't need,
  or might not even have, the private key of the ``root`` user in order to log in.)

You can also define named locations for commonly used groups of properties, 
with the syntax ``brooklyn.location.named.your-group-name.``
followed by the relevant properties.
These can be accessed at runtime using the syntax ``named:your-group-name`` as the deployment location.

Some more advanced examples showing the syntax and properties above are as follows:

{% highlight java %}
    # Production pool of machines for my application (deploy to named:prod1)
    brooklyn.location.named.prod1=byon:(hosts="10.9.1.1,10.9.1.2,produser2@10.9.2.{10,11,20-29}")
    brooklyn.location.named.prod1.user=produser1
    brooklyn.location.named.prod1.privateKeyFile=~/.ssh/produser_id_rsa
    brooklyn.location.named.prod1.privateKeyPassphrase=s3cr3tCOMPANYpassphrase
    
    # AWS using my company's credentials and image standard, then labelling images so others know they're mine
    brooklyn.location.named.company-jungle=jclouds:aws-ec2:us-west-1
    brooklyn.location.named.company-jungle.identity=BCDEFGHIJKLMNOPQRSTU      
    brooklyn.location.named.company-jungle.privateKeyFile=~/.ssh/public_clouds/company_aws_id_rsa
    brooklyn.location.named.company-jungle.imageId=ami-12345
    brooklyn.location.named.company-jungle.minRam=2048
    brooklyn.location.named.company-jungle.userMetadata=application=my-jungle-app,owner="Bob Johnson"
{% endhighlight %}

