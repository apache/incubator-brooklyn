---
title: Locations
layout: website-normal
children:
- { section: Clouds }
- { section: Inheritance and Named Locations, title: Named Locations }
- { section: Localhost }
- { section: BYON }
- more-locations.md
- ssh-keys.md
---

Locations are the environments to which Brooklyn deploys applications, including:

* <a href="#clouds">Clouds</a>, where it will provision machines
* <a href="#localhost">Localhost</a> (e.g. your laptop), where it will deploy via `ssh` to `localhost` for rapid testing
* <a href="#byon">BYON</a>, where you "bring your own nodes", specifying already-existing hosts to use
* And many others, including object stores and online services

Configuration can be set in `~/.brooklyn/brooklyn.properties` or directly in YAML when specifying a location. On some entities, config keys determining maching selection and provisioning behavior can also be set `in `provisioning.properties`.  


### Clouds

For most cloud provisioning tasks, Brooklyn uses <a href="http://jclouds.org">Apache jclouds</a>. The identifiers for some of the most commonly used jclouds-supported clouds are (or [see the full list](http://jclouds.apache.org/reference/providers/)):

* `jclouds:aws-ec2:<region>`: Amazon EC2, where `:<region>` might be `us-east-1` or `eu-west-1` (or omitted)
* `jclouds:softlayer:<region>`: IBM Softlayer, where `:<region>` might be `dal05` or `ams01` (or omitted)
* `jclouds:google-compute-engine`: Google Compute Engine
* `jclouds:openstack-nova:<endpoint>`: OpenStack, where `:<endpoint>` is the access URL (required)
* `jclouds:cloudstack:<endpoint>`: Apache CloudStack, where `:<endpoint>` is the access URL (required)

For any of these, of course, Brooklyn needs to be configured with an `identity` and a `credential`:

{% highlight yaml %}
location:
  jclouds:aws-ec2:
    identity: ABCDEFGHIJKLMNOPQRST
    credential: s3cr3tsq1rr3ls3cr3tsq1rr3ls3cr3tsq1rr3l
{% endhighlight %} 

The above YAML can be embedded directly in blueprints, either at the root or on individual services. If you prefer to keep the credentials separate, these can be set instead in `brooklyn.properties` in the `jclouds.<provider>` namespace:

{% highlight bash %}
brooklyn.location.jclouds.aws-ec2.identity=ABCDEFGHIJKLMNOPQRST  
brooklyn.location.jclouds.aws-ec2.credential=s3cr3tsq1rr3ls3cr3tsq1rr3ls3cr3tsq1rr3l
{% endhighlight %}

And in this case you can reference the location in YAML with `location: jclouds:aws-ec2`.

The Getting Started [template brooklyn.properties]({{ site.path.guide }}/start/brooklyn.properties) contains more examples of configuring cloud endpoints, including information on credential types used in different clouds.


#### OS Initial Login and Setup

Once a machine is provisioned, Brooklyn will normally attempt to log in via SSH and configure the machine sensibly.

The credentials for the initial OS log on are typically discovered from the cloud, but in some environments this is not possible. The keys `loginUser` and either `loginUser.password` or `loginUser.privateKeyFile` can be used to force Brooklyn to use specific credentials for the initial login to a cloud-provisioned machine. (This is particularly useful when using a custom image templates where the cloud-side account management logic is not enabled.)

Following a successful logon, Brooklyn performs the following steps to configure the machine:

1. to create a new user with the same name as the user `brooklyn` is running as locally (this can be overridden with `user`, below)

1. to install the local user's `~/.ssh/id_rsa.pub` as an `authorized_keys` on the new machine, to make it easy for the operator to `ssh` in (override with `privateKeyFile`; or if there is no `id_{r,d}sa{,.pub}` an ad hoc keypair will be generated; if there is a passphrase on the key, this must be supplied)  

1. give `sudo` access to the newly created user (override with `grantUserSudo: false`)

1. disable direct `root` login to the machine

These steps can be skipped or customized as described below.


#### jclouds Config Keys

The following is a subset of the most commonly used configuration keys used to customize cloud provisioning. For more keys and more detail on the keys below, see {% include java_link.html class_name="JcloudsLocationConfig" package_path="brooklyn/location/jclouds" project_subpath="locations/jclouds" %}.

###### OS Setup

- `user` and `password` can be used to configure the operating user created on cloud-provisioned machines.

- The `loginUser` config key (and subkeys) control the initial user to log in as, in cases where this cannot be discovered from the cloud provider.
 
- Private keys can be specified using ``privateKeyFile``; these are not copied to provisioned machines, but are required if using a local public key or a pre-defined `authorized_keys` on the server. (For more information on SSH keys, see [here](ssh-keys.html).) 

- If there is a passphrase on the key file being used, you must supply it to Brooklyn for it to work, of course! ``privateKeyPassphrase`` does the trick (as in ``brooklyn.location.jclouds.privateKeyPassphrase``, or other places where ``privateKeyFile`` is valid). If you don't like keys, you can just use a plain old ``password``.

- Public keys can be specified using ``publicKeyFile``, although these can usually be omitted if they follow the common pattern of being the private key file with the suffix ``.pub`` appended. (It is useful in the case of ``loginUser.publicKeyFile``, where you shouldn't need, or might not even have, the private key of the ``root`` user in order to log in).

- Use `dontCreateUser` to have Brooklyn run as the initial `loginUser` (usually `root`), without creating any other user.

- A post-provisioning `setup.script` can be specified (as a URL) to run an additional script, before making the `Location` available to entities, optionally also using `setup.script.vars` (set as `key1:value1,key2:value2`).


###### VM Creation
    
- Most providers require exactly one of either `region` (e.g. `us-east-1`) or `endpoint` (the URL, usually for private cloud deployments).

- Hardware requirements can be specified, including ``minRam``, ``minCores``, and `os64Bit`; or as a specific ``hardwareId``.

- VM image constraints can be set using `osFamily` (e.g. `Ubuntu`, `CentOS`, `Debian`, `RHEL`) and `osVersionRegex`, or specific VM images can be specified using ``imageId`` or ``imageNameRegex``.

- Specific VM images can be specified using ``imageId`` or ``imageNameRegex``.

- Specific Security Groups can be specified using `securityGroups`, as a list of strings (the existing security group names), or `inboundPorts` can be set, as a list of numeric ports (selected clouds only).

- A specific existing key pair for the cloud to set for `loginUser` can be specified using `keyPair` (selected clouds only).

- User metadata can be attached, using the syntax ``userMetadata=key=value,key2="value 2"``.

- You can specify the number of attempts Brooklyn should make to create machines with ``machineCreateAttempts`` (jclouds only). This is useful for working around the rare occasions in which cloud providers give machines that are dead on arrival.

- If you want to investigate failures, set `destroyOnFailure` to `false` to keep failed VM's around. (You'll have to manually clean them up).


### Inheritance and Named Locations

Named locations can be defined for commonly used groups of properties, with the syntax ``brooklyn.location.named.your-group-name.`` followed by the relevant properties. These can be accessed at runtime using the syntax ``named:your-group-name`` as the deployment location.

Some illustrative examples using named locations and showing the syntax and properties above are as follows:

{% highlight bash %}
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
brooklyn.location.named.company-jungle.machineCreateAttempts=2

brooklyn.location.named.AWS\ Virginia\ Large\ Centos = jclouds:aws-ec2
brooklyn.location.named.AWS\ Virginia\ Large\ Centos.region = us-east-1
brooklyn.location.named.AWS\ Virginia\ Large\ Centos.imageId=us-east-1/ami-7d7bfc14
brooklyn.location.named.AWS\ Virginia\ Large\ Centos.user=root
brooklyn.location.named.AWS\ Virginia\ Large\ Centos.minRam=4096
{% endhighlight %}

If you are confused about inheritance order, the following test may be useful:

{% highlight bash %}
brooklyn.location.jclouds.KEY=VAL1
brooklyn.location.jclouds.aws-ec2.KEY=VAL2
brooklyn.location.named.my-aws=jclouds:aws-ec2
brooklyn.location.named.my-aws.KEY=VAL3
{% endhighlight %}

In the above example, if you deploy to `location: named:my-aws`, Brooklyn will get `VAL3` for `KEY`; this overrides `VAL2` which applies by default to all `aws-ec2` locations, which in turn overrides `VAL1`, which applies to all jclouds locations. 


### Localhost

If passwordless ssh login to `localhost` is enabled on your machine, you should be able to deploy blueprints with no special configuration, just by specifying `location: localhost` in YAML.

If you use a passpharse or prefer a different key, these can be configured as follows: 

{% highlight bash %}
brooklyn.location.localhost.privateKeyFile=~/.ssh/brooklyn_key
brooklyn.location.localhost.privateKeyPassphrase=s3cr3tPASSPHRASE
{% endhighlight %}

If you encounter issues or for more information, see [SSH Keys Localhost Setup](ssh-keys.html#localhost-setup). 


### BYON

"Bring-your-own-nodes" mode is useful in production, where machines have been provisioned by someone else, and during testing, to cut down provisioning time.

To deploy to machines with known IP's in a blueprint, use the following syntax:

{% highlight yaml %}
location:
  byon:
    user: brooklyn
    privateKeyFile: ~/.ssh/brooklyn.pem
    hosts:
    - 192.168.0.18
    - 192.168.0.19
{% endhighlight %}

Some of the login properties as described above for jclouds are supported, but not `loginUser` (as no users are created), and not any of the VM creation parameters such as `minRam` and `imageId`. (These clearly do not apply in the same way, and they are *not* by default treated as constraints, although an entity can confirm these where needed.) As before, if the brooklyn user and its default key are authorized for the hosts, those fields can be omitted.

Named locations can also be configured in your `brooklyn.properties`, using the format ``byon:(key=value,key2=value2)``. For convenience, for hosts wildcard globs are supported.

{% highlight bash %}
brooklyn.location.named.On-Prem\ Iron\ Example=byon:(hosts="10.9.1.1,10.9.1.2,produser2@10.9.2.{10,11,20-29}")
brooklyn.location.named.On-Prem\ Iron\ Example.user=produser1
brooklyn.location.named.On-Prem\ Iron\ Example.privateKeyFile=~/.ssh/produser_id_rsa
brooklyn.location.named.On-Prem\ Iron\ Example.privateKeyPassphrase=s3cr3tpassphrase
{% endhighlight %}


### Other Location Topics

* [More Locations](more-locations.html)
* [SSH Keys](ssh-keys.html)
