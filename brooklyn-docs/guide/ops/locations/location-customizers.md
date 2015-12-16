---
title: Location customizers
layout: website-normal
---

Apache Brooklyn supports a number of ways to configure and customize locations. These include
the `JcloudsLocationCustomizer`, which is for advanced customization of VM provisioning through jclouds.
There is also a `MachineLocationCustomizer`, which allows customization of machines being obtained 
from any kind of location (including [Bring Your Own Nodes](index.html#byon)).


## Usage Guidelines

Clearly there is an overlap for where things can be done. This section describes the recommended  
separation of responsibilities.

These are guidelines only - users are obviously free to make alternative usage decisions based on 
their particular use-cases.

### Responsibilities of Entity versus Location

From an entity's perspective, it calls `location.obtain(options)` and gets back a usable 
`MachineLocation` that has a standard base operating system that gives remote access
(e.g. for Linux it expects credentials for a user with `sudo` rights, and ssh access).

However, there are special cases - for example the `location.obtain(options)` could return
a Docker container with the software pre-installed, and no remote access (see the 
[Clocker project](http://clocker.io) for more information on use of Docker with Brooklyn).

The entity is then responsible for configuring that machine according to the needs of the software 
to be installed.

For example, the entity may install software packages, upload/update configuration files, launch
processes, etc.

The entity may also configure `iptables`. This is also possible through the `JcloudsLocation` 
configuration. However, it is preferable to do this in the entity because it is part of 
configuring the machine in the way required for the given software component.

The entity may also perform custom OS setup, such as installing security patches. However, whether 
this is appropriate depends on the nature of the security patch: if the security patch is specific 
to the entity type, then it should be done within the entity; but if it is to harden the base OS 
to make it comply with an organisation's standards (e.g. to overcome shortcomings of the base 
image, or to install security patches) then a `MachineLocationCustomizer` is more appropriate.

### Location Configuration Options

This refers to standard location configuration: explicit config keys, and explicit jclouds template 
configuration that can be passed through.

This kind of configuration is simplest to use. It is the favoured mechanism when it comes to VM 
provisioning, and should be used wherever possible.

Note that a jclouds `TemplateBuilder` and cloud-specific `TemplateOptions` are the generic mechanisms 
within jclouds for specifying the details of the compute resource to be provisioned.

### Jclouds Location Customizer 
A `JcloudsLocationCustomizer` has customization hooks to execute code at the various points of building 
up the jclouds template and provisioning the machine. Where jclouds is being used and where the required 
use of jclouds goes beyond simple configuration, this is an appropriate solution.

For example, there is a `org.apache.brooklyn.location.jclouds.networking.JcloudsLocationSecurityGroupCustomizer`
which gives more advanced support for setting up security groups (e.g. in AWS-EC2).

### Machine Customizer

The `MachineLocationCustomizer` allows customization of machines being obtained from any kind of location.
For example, this includes for jclouds and for Bring Your Own Nodes (BYON).

It provides customization hooks for when the machine has been provisioned (before it is returned by the location)
and when the machine is about to be released by the location.

An example use would be to register (and de-register) the machine in a CMDB.


## Jclouds Location Customizers

*Warning: additional methods (i.e. customization hooks) may be added to the `JcloudsLocationCustomizer` 
interface in future releases. Users are therefore strongly encouraged to sub-class 
`BasicJcloudsLocationCustomizer`, rather than implementing JcloudsLocationCustomizer directly.*

The `JcloudsLocationCustomizer` provides customization hooks at various points of the Brooklyn's
use of jclouds. These can be used to adjust the configuration, to do additional setup, to do
custom logging, etc.

* Customize the `org.jclouds.compute.domain.TemplateBuilder`, before it is used to build the template.
  This is used to influence the choice of VM image, hardware profile, etc. This hook is not normally
  required as the location configuration options can be used in instead.

* Customize the `org.jclouds.compute.domain.Template`, to be used when creating the machine. This  
  hook is most often used for performing custom actions - for example to create or modify a security 
  group or volume, and to update the template's options to use that.

* Customize the `org.jclouds.compute.options.TemplateOptions` to be used when creating the machine.
  The `TemplateOptions` could be cast to a cloud-specific sub-type (if this does not have to work
  across different clouds). Where the use-case is to just set simple configuration on the 
  `TemplateOptions`, consider instead using the config key `templateOptions`, which takes a map
  of type String to Object - the strings should match the method names in the `TemplateOptions`.

* Customize the `org.apache.brooklyn.location.jclouds.JcloudsMachineLocation` that has been 
  created. For Linux-based VMs, if the config `waitForSshable` was not false, then this machine
  is guaranteed to be ssh'able. Similarly for WinRM access to Windows machines, if 
  `waitForWinRmAvailable` was not false.

* Pre-release of the machine. If the actions required are specific to jclouds (e.g. using jclouds 
  to make calls to the cloud provider) then this should be used; otherwise one should use the more
  generic `MachineLocationCustomizer`.

* Post-release of the machine (i.e. after asking jclouds to destroying the machine).

To register a `JcloudsLocationCustomizer` in YAML, the config key `customizers` can be used to 
provide a list of instances. Each instance can be defined using `$brooklyn:object` to indicate 
the type and its configuration. For example:

    location:
      jclouds:aws-ec2:us-east-1:
        customizers:
        - $brooklyn:object:
            type: com.acme.brooklyn.MyJcloudsLocationCustomizer

To register `JcloudsLocationCustomizer` instances programmatically, set the config key
`JcloudsLocationConfig.JCLOUDS_LOCATION_CUSTOMIZERS` on the location, or pass this 
config option when calling `location.obtain(options)`.


## Machine Location Customizers

*Warning: additional methods (i.e. customization hooks) may be added to the `MachineLocationCustomizer` 
interface in future releases. Users are therefore strongly encouraged to sub-class 
`BasicMachineLocationCustomizer`, rather than implementing `MachineLocationCustomizer` directly.*

The `MachineLocationCustomizer` provides customization hooks for when a machine is obtained/released 
from a `MachineProvisioningLocation`. The following hooks are supported: 

* After the machine has been provisioned/allocated, but before it has been returned.

* When the machine is about to be released, but prior to actually destroying/unallocating the
  machine.

To register a `MachineLocationCustomizer` in YAML, the config key `machineCustomizers` can be used  
to provide a list of instances. Each instance can be defined using `$brooklyn:object` to indicate 
the type and its configuration. For example:

    location:
      jclouds:aws-ec2:us-east-1:
        machineCustomizers:
        - $brooklyn:object:
            type: com.acme.brooklyn.MyMachineLocationCustomizer

To register `MachineLocationCustomizer` instances programmatically, set the config key
`CloudLocationConfig.MACHINE_LOCATION_CUSTOMIZERS` on the location, or pass this 
config option when calling `location.obtain(options)`.
