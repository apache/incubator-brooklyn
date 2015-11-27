---
title: Locations
layout: website-normal
children:
- { section: Clouds }
- { section: Inheritance and Named Locations, title: Named Locations }
- { section: Localhost }
- { section: BYON }
- cloud-credentials.md
- more-locations.md
- location-customizers.md
- ssh-keys.md
---

Locations are the environments to which Brooklyn deploys applications, including:

Brooklyn supports a wide range of locations:

* <a href="#clouds">Clouds</a>, where it will provision machines
* <a href="#localhost">Localhost</a> (e.g. your laptop), 
  where it will deploy via `ssh` to `localhost` for rapid testing
* <a href="#byon">BYON</a>, where you "bring your own nodes",
  specifying already-existing hosts to use
* And many others, including object stores and online services

Configuration can be set in `~/.brooklyn/brooklyn.properties`
or directly in YAML when specifying a location.
On some entities, config keys determining maching selection and provisioning behavior
can also be set `in `provisioning.properties`.  


### Clouds

For most cloud provisioning tasks, Brooklyn uses
<a href="http://jclouds.org">Apache jclouds</a>.
The identifiers for some of the most commonly used jclouds-supported clouds are
(or [see the full list](http://jclouds.apache.org/reference/providers/)):

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

The above YAML can be embedded directly in blueprints, either at the root or on individual services.
If you prefer to keep the credentials separate, these can be set instead in `brooklyn.properties` 
in the `jclouds.<provider>` namespace:

{% highlight bash %}
brooklyn.location.jclouds.aws-ec2.identity=ABCDEFGHIJKLMNOPQRST  
brooklyn.location.jclouds.aws-ec2.credential=s3cr3tsq1rr3ls3cr3tsq1rr3ls3cr3tsq1rr3l
{% endhighlight %}

And in this case you can reference the location in YAML with `location: jclouds:aws-ec2`.

The Getting Started [template brooklyn.properties]({{ site.path.guide }}/start/brooklyn.properties) contains more examples 
of configuring cloud endpoints, including information on credential types used in different clouds.


#### OS Initial Login and Setup

Once a machine is provisioned, Brooklyn will normally attempt to log in via SSH and configure the machine sensibly.

The credentials for the initial OS log on are typically discovered from the cloud, 
but in some environments this is not possible.
The keys `loginUser` and either `loginUser.password` or `loginUser.privateKeyFile` can be used to force
Brooklyn to use specific credentials for the initial login to a cloud-provisioned machine.

(This custom login is particularly useful when using a custom image templates where the cloud-side account 
management logic is not enabled. For example, a vCloud (vCD) template can have guest customization that will change
the root password. This setting tells AMP to only use the given password, rather than the initial 
randomly generated password that vCD returns. Without this property, there is a race for such templates:
does Brooklyn manage to create the admin user before the guest customization changes the login and reboots,
or is the password reset first (the latter means Brooklyn can never ssh to the VM). With this property, 
Brooklyn will always wait for guest customization to complete before it is able to ssh at all. In such
cases, it is also recommended to use `useJcloudsSshInit=false`.)

Following a successful logon, Brooklyn performs the following steps to configure the machine:

1. creates a new user with the same name as the user `brooklyn` is running as locally
  (this can be overridden with `user`, below).

1. install the local user's `~/.ssh/id_rsa.pub` as an `authorized_keys` on the new machine,
   to make it easy for the operator to `ssh` in
   (override with `privateKeyFile`; or if there is no `id_{r,d}sa{,.pub}` an ad hoc keypair will be generated;
   if there is a passphrase on the key, this must be supplied)  

1. give `sudo` access to the newly created user (override with `grantUserSudo: false`)

1. disable direct `root` login to the machine

These steps can be skipped or customized as described below.



#### jclouds Config Keys

The following is a subset of the most commonly used configuration keys used to customize 
cloud provisioning.
For more keys and more detail on the keys below, see 
{% include java_link.html class_name="JcloudsLocationConfig" package_path="org/apache/brooklyn/location/jclouds" project_subpath="locations/jclouds" %}.

###### VM Creation
    
- Most providers require exactly one of either `region` (e.g. `us-east-1`) or `endpoint` (the URL, usually for private cloud deployments)

- Hardware requirements can be specified, including 
  `minRam`, `minCores`, and `os64Bit`; or as a specific `hardwareId`

- VM image constraints can be set using `osFamily` (e.g. `Ubuntu`, `CentOS`, `Debian`, `RHEL`)
  and `osVersionRegex`, or specific VM images can be specified using `imageId` or `imageNameRegex`

- Specific VM images can be specified using `imageId` or `imageNameRegex`

- Specific Security Groups can be specified using `securityGroups`, as a list of strings (the existing security group names),
  or `inboundPorts` can be set, as a list of numeric ports (selected clouds only)

- A specific existing key pair known at the cloud to use can be specified with `keyPair`
  (selected clouds only)

- A specific VM name (often the hostname) base to be used can be specified by setting `groupId`.
  By default, this name is constructed based on the entity which is creating it,
  including the ID of the app and of the entity.
  (As many cloud portals let you filter views, this can help find a specific entity or all machines for a given application.)
  For more sophisticated control over host naming, you can supply a custom 
  {% include java_link.html class_name="CloudMachineNamer" package_path="org/apache/brooklyn/core/location/cloud/names" project_subpath="core" %},
  for example
  `cloudMachineNamer: CustomMachineNamer`.
  {% include java_link.html class_name="CustomMachineNamer" package_path="org/apache/brooklyn/core/location/cloud/names" project_subpath="core" %}
  will use the entity's name or following a template you supply.
  On many clouds, a random suffix will be appended to help guarantee uniqueness;
  this can be removed by setting `vmNameSaltLength: 0` (selected clouds only).
  <!-- TODO jclouds softlayer includes a 3-char hex suffix -->
  
- A DNS domain name where this host should be placed can be specified with `domainName`
  (in selected clouds only)

- User metadata can be attached using the syntax `userMetadata: { key: value, key2: "value 2" }` 
  (or `userMetadata=key=value,key2="value 2"` in a properties file)

- By default, several pieces of user metadata are set to correlate VMs with Brooklyn entities,
  prefixed with `brooklyn-`.
  This user metadata can be omitted by setting `includeBrooklynUserMetadata: false`.

- You can specify the number of attempts Brooklyn should make to create
  machines with `machineCreateAttempts` (jclouds only). This is useful as an efficient low-level fix
  for those occasions when cloud providers give machines that are dead on arrival.
  You can of course also resolve it at a higher level with a policy such as 
  {% include java_link.html class_name="ServiceRestarter" package_path="org/apache/brooklyn/policy/ha" project_subpath="policy" %}.

- If you want to investigate failures, set `destroyOnFailure: false`
  to keep failed VM's around. (You'll have to manually clean them up.)
  The default is false: if a VM fails to start, or is never ssh'able, then the VM will be terminated.


###### OS Setup

- `user` and `password` can be used to configure the operating user created on cloud-provisioned machines

- The `loginUser` config key (and subkeys) control the initial user to log in as,
  in cases where this cannot be discovered from the cloud provider
 
- Private keys can be specified using `privateKeyFile`; 
  these are not copied to provisioned machines, but are required if using a local public key
  or a pre-defined `authorized_keys` on the server.
  (For more information on SSH keys, see [here](ssh-keys.html).) 

- If there is a passphrase on the key file being used, you must supply it to Brooklyn for it to work, of course!
  `privateKeyPassphrase` does the trick (as in `brooklyn.location.jclouds.privateKeyPassphrase`, or other places
  where `privateKeyFile` is valid).  If you don't like keys, you can just use a plain old `password`.

- Public keys can be specified using `publicKeyFile`, 
  although these can usually be omitted if they follow the common pattern of being
  the private key file with the suffix `.pub` appended.
  (It is useful in the case of `loginUser.publicKeyFile`, where you shouldn't need,
  or might not even have, the private key of the `root` user when you log in.)

- Use `dontCreateUser` to have Brooklyn run as the initial `loginUser` (usually `root`),
  without creating any other user.

- A post-provisioning `setup.script` can be specified (as a URL) to run an additional script,
  before making the `Location` available to entities,
  optionally also using `setup.script.vars` (set as `key1:value1,key2:value2`)

- Use `openIptables: true` to automatically configure `iptables`, to open the TCP ports required by
  the software process. One can alternatively use `stopIptables: true` to entirely stop the
  iptables service.

- Use `installDevUrandom: true` to fall back to using `/dev/urandom` rather than `/dev/random`. This setting
  is useful for cloud VMs where there is not enough random entropy, which can cause `/dev/random` to be
  extremely slow (causing `ssh` to be extremely slow to respond).

- Use `useJcloudsSshInit: false` to disable the use of the native jclouds support for initial commands executed 
  on the VM (e.g. for creating new users, setting root passwords, etc.). Instead, Brooklyn's ssh support will
  be used. Timeouts and retries are more configurable within Brooklyn itself. Therefore this option is particularly 
  recommended when the VM startup is unusual (for example, if guest customizations will cause reboots and/or will 
  change login credentials).

- Use `brooklyn.ssh.config.noDeleteAfterExec: true` to keep scripts on the server after execution.
  The contents of the scripts and the stdout/stderr of their execution are available in the Brooklyn web console,
  but sometimes it can also be useful to have them on the box.
  This setting prevents scripts executed on the VMs from being deleted on completion.
  Note that some scripts run periodically so this can eventually fill a disk; it should only be used for dev/test. 

###### Custom template options

jclouds supports many additional options for configuring how a virtual machine is created and deployed, many of which
are for cloud-specific features and enhancements. Brooklyn supports some of these, but if what you are looking for is
not supported directly by Brooklyn, we instead offer a mechanism to set any parameter that is supported by the jclouds
template options for your cloud.

Part of the process for creating a virtual machine is the creation of a jclouds `TemplateOptions` object. jclouds
providers extends this with extra options for each cloud - so when using the AWS provider, the object will be of
type `AWSEC2TemplateOptions`. By [examining the source code](https://github.com/jclouds/jclouds/blob/jclouds-1.9.0/providers/aws-ec2/src/main/java/org/jclouds/aws/ec2/compute/AWSEC2TemplateOptions.java),
you can see all of the options available to you.

The `templateOptions` config key takes a map. The keys to the map are method names, and Brooklyn will find the method on
the `TemplateOptions` instance; it then invokes the method with arguments taken from the map value. If a method takes a
single parameter, then simply give the argument as the value of the key; if the method takes multiple parameters, the
value of the key should be an array, containing the argument for each parameter.

For example, here is a complete blueprint that sets some AWS EC2 specific options:

    location: AWS_eu-west-1
    services:
    - type: org.apache.brooklyn.entity.software.base.EmptySoftwareProcess
      provisioningProperties:
        templateOptions:
          subnetId: subnet-041c8373
          mapNewVolumeToDeviceName: ["/dev/sda1", 100, true]
          securityGroupIds: ['sg-4db68928']

Here you can see that we set three template options:

- `subnetId` is an example of a single parameter method. Brooklyn will effectively try to run the statement
  `templateOptions.subnetId("subnet-041c88373");`
- `mapNewVolumeToDeviceName` is an example of a multiple parameter method, so the value of the key is an array.
  Brooklyn will effectively true to run the statement `templateOptions.mapNewVolumeToDeviceName("/dev/sda1", 100, true);`
- `securityGroupIds` demonstrates an ambiguity between the two types; Brooklyn will first try to parse the value as
  a multiple parameter method, but there is no method that matches this parameter. In this case, Brooklyn will next try
  to parse the value as a single parameter method which takes a parameter of type `List`; such a method does exist so
  the operation will succeed.

If the method call cannot be matched to the template options available - for example if you are trying to set an AWS EC2
specific option but your location is an OpenStack cloud - then a warning is logged and the option is ignored.



  
See the following resources for more information:

- [AWS VPC issues which may affect users with older AWS accounts](vpc-issues.html)
- [Amazon EC2 and Amazon Virtual Private Cloud](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-vpc.html#vpc-only-instance-types)
- [Your Default VPC and Subnets](http://docs.aws.amazon.com/AmazonVPC/latest/UserGuide/default-vpc.html)
- [Amazon VPC FAQs](http://aws.amazon.com/vpc/faqs/#Default_VPCs)
  

### Inheritance and Named Locations

Named locations can be defined for commonly used groups of properties, 
with the syntax `brooklyn.location.named.your-group-name.`
followed by the relevant properties.
These can be accessed at runtime using the syntax `named:your-group-name` as the deployment location.

Some illustrative examples using named locations and
showing the syntax and properties above are as follows:

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

The precedence for configuration defined at different levels is that the most value
defined in the most specific context will apply.

For example, in the example below the config key is repeatedly overridden. If you deploy
`location: named:my-aws`, Brooklyn will get `VAL5` or `KEY`:
  
{% highlight bash %}
brooklyn.location.KEY=VAL1
brooklyn.location.jclouds.KEY=VAL2
brooklyn.location.jclouds.aws-ec2.KEY=VAL3
brooklyn.location.jclouds.aws-ec2@us-west-1.KEY=VAL4
brooklyn.location.named.my-aws=jclouds:aws-ec2:us-west-1
brooklyn.location.named.my-aws.KEY=VAL5
{% endhighlight %}


### Localhost

If passwordless ssh login to `localhost` and passwordless `sudo` is enabled on your 
machine, you should be able to deploy blueprints with no special configuration,
just by specifying `location: localhost` in YAML.

If you use a passpharse or prefer a different key, these can be configured as follows: 

{% highlight bash %}
brooklyn.location.localhost.privateKeyFile=~/.ssh/brooklyn_key
brooklyn.location.localhost.privateKeyPassphrase=s3cr3tPASSPHRASE
{% endhighlight %}

If you encounter issues or for more information, see [SSH Keys Localhost Setup](ssh-keys.html#localhost-setup). 

If you are normally prompted for a password when executing `sudo` commands, passwordless `sudo` must also be enabled.  To enable passwordless `sudo` for your account, a line must be added to the system `/etc/sudoers` file.  To edit the file, use the `visudo` command:
{% highlight bash %}
sudo visudo
{% endhighlight %}
Add this line at the bottom of the file, replacing `username` with your own user:
{% highlight bash %}
username ALL=(ALL) NOPASSWD: ALL
{% endhighlight %}
If executing the following command does not ask for your password, then `sudo` should be setup correctly:
{% highlight bash %}
sudo ls
{% endhighlight %}


### BYON

"Bring-your-own-nodes" mode is useful in production, where machines have been provisioned by someone else,
and during testing, to cut down provisioning time.

Your nodes must meet the following prerequisites:

- A suitable OS must have been installed on all nodes
- The node must be running sshd (or similar)
- the brooklyn user must be able to ssh to each node as root or as a user with passwordless sudo permission. (For more information on SSH keys, see [here](ssh-keys.html).) 

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

Some of the login properties as described above for jclouds are supported,
but not `loginUser` (as no users are created), and not any of the
VM creation parameters such as `minRam` and `imageId`.
(These clearly do not apply in the same way, and they are *not* 
by default treated as constraints, although an entity can confirm these
where needed.)
As before, if the brooklyn user and its default key are authorized for the hosts,
those fields can be omitted.

Named locations can also be configured in your `brooklyn.properties`,
using the format `byon:(key=value,key2=value2)`.
For convenience, for hosts wildcard globs are supported.

{% highlight bash %}
brooklyn.location.named.On-Prem\ Iron\ Example=byon:(hosts="10.9.1.1,10.9.1.2,produser2@10.9.2.{10,11,20-29}")
brooklyn.location.named.On-Prem\ Iron\ Example.user=produser1
brooklyn.location.named.On-Prem\ Iron\ Example.privateKeyFile=~/.ssh/produser_id_rsa
brooklyn.location.named.On-Prem\ Iron\ Example.privateKeyPassphrase=s3cr3tpassphrase
{% endhighlight %}

For more complex host configuration, one can define custom config values per machine. In the example 
below, there will be two machines. The first will be a machine reachable on
`ssh -i ~/.ssh/brooklyn.pem -p 8022 myuser@50.51.52.53`. The second is a windows machine, reachable 
over WinRM. Each machine has also has a private address (e.g. for within a private network).

{% highlight yaml %}
location:
  byon:
    hosts:
    - ssh: 50.51.52.53:8022
      privateAddresses: [10.0.0.1]
      privateKeyFile: ~/.ssh/brooklyn.pem
      user: myuser
    - winrm: 50.51.52.54:8985
      privateAddresses: [10.0.0.2]
      password: mypassword
      user: myuser
      osFamily: windows
{% endhighlight %}

The BYON location also supports a machine chooser, using the config key `byon.machineChooser`. 
This allows one to plugin logic to choose from the set of available machines in the pool. For
example, additional config could be supplied for each machine. This could be used (during the call
to `location.obtain()`) to find the config that matches the requirements of the entity being
provisioned. See `FixedListMachineProvisioningLocation.MACHINE_CHOOSER`.


### Other Location Topics

* [Cloud Credentials](cloud-credentials.html)
* [More Locations](more-locations.html)
* [Location Customizers](location-customizers.html)
* [SSH Keys](ssh-keys.html)
