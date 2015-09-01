---
layout: website-normal
title: Troubleshooting Deployment
toc: /guide/toc.json
---

This guide describes common problems encountered when deploying applications.


## YAML deployment errors

The error `Invalid YAML: Plan not in acceptable format: Cannot convert ...` means that the text is not 
valid YAML. Common reasons include that the indentation is incorrect, or that there are non-matching
brackets.

The error `Unrecognized application blueprint format: no services defined` means that the `services:`
section is missing.

An error like `Deployment plan item Service[name=<null>,description=<null>,serviceType=com.acme.Foo,characteristics=[],customAttributes={}] cannot be matched` means that the given entity type (in this case com.acme.Foo) is not in the catalog or on the classpath.

An error like `Illegal parameter for 'location' (aws-ec3); not resolvable: java.util.NoSuchElementException: Unknown location 'aws-ec3': either this location is not recognised or there is a problem with location resolver configuration` means that the given location (in this case aws-ec3) 
was unknown. This means it does not match any of the named locations in brooklyn.properties, nor any of the
clouds enabled in the jclouds support, nor any of the locations added dynamically through the catalog API.


## VM Provisioning Failures

There are many stages at which VM provisioning can fail! An error `Failure running task provisioning` 
means there was some problem obtaining or connecting to the machine.

An error like `... Not authorized to access cloud ...` usually means the wrong identity/credential was used.

An error like `Unable to match required VM template constraints` means that a matching image (e.g. AMI in AWS terminology) could not be found. This 
could be because an incorrect explicit image id was supplied, or because the match-criteria could not
be satisfied using the given images available in the given cloud. The first time this error is 
encountered, a listing of all images in that cloud/region will be written to the debug log.

Failure to form an ssh connection to the newly provisioned VM can be reported in several different ways, 
depending on the nature of the error. This breaks down into failures at different points:

* Failure to reach the ssh port (e.g. `... could not connect to any ip address port 22 on node ...`).
* Failure to do the very initial ssh login (e.g. `... Exhausted available authentication methods ...`).
* Failure to ssh using the newly created user.

There are many possible reasons for this ssh failure, which include:

* The VM was "dead on arrival" (DOA) - sometimes a cloud will return an unusable VM. One can work around
  this using the `machineCreateAttempts` configuration option, to automatically retry with a new VM.
* Local network restrictions. On some guest wifis, external access to port 22 is forbidden.
  Check by manually trying to reach port 22 on a different machine that you have access it.
* NAT rules not set up correctly. On some clouds that have only private IPs, Brooklyn can automatically
  create NAT rules to provide access to port 22. If this NAT rule creation fails for some reason,
  then Brooklyn will not be able to reach the VM. If NAT rules are being created for your cloud, then
  check the logs for warnings or errors about the NAT rule creation.
* ssh credentials incorrectly configured. The Brooklyn configuration is very flexible in how ssh
  credentials can be configured. However, if a more advanced configuration is used incorrectly (e.g. 
  the wrong login user, or invalid ssh keys) then this will fail.
* Wrong login user. The initial login user to use when first logging into the new VM is inferred from 
  the metadata provided by the cloud provider about that image. This can sometimes be incomplete, so
  the wrong user may be used. This can be explicitly set using the `loginUser` configuration option.
  An example of this is with some Ubuntu VMs, where the "ubuntu" user should be used. However, on some clouds
  it defaults to trying to ssh as "root".
* Bad choice of user. By default, Brooklyn will create a user with the same name as the user running the
  Brooklyn process; the choice of user name is configurable. If this user already exists on the machine, 
  then the user setup will not behave as expected. Subsequent attempts to ssh using this user could then fail.
* Custom credentials on the VM. Most clouds will automatically set the ssh login details (e.g. in AWS using  
  the key-pair, or in CloudStack by auto-generating a password). However, with some custom images the VM
  will have hard-coded credentials that must be used. If Brooklyn's configuration does not match that,
  then it will fail.
* Guest customisation by the cloud. On some clouds (e.g. vCloud Air), the VM can be configured to do
  guest customisation immediately after the VM starts. This can include changing the root password.
  If Brooklyn is not configured with the expected changed password, then the VM provisioning may fail
  (depending if Brooklyn connects before or after the password is changed!).
 
A very useful debug configuration is to set `destroyOnFailure` to false. This will allow ssh failures to
be more easily investigated.


## Timeout Waiting For Service-Up

A common generic error message is that there was a timeout waiting for service-up.

This just means that the entity did not get to service-up in the pre-defined time period (the default is 
two minutes, and can be configured using the `start.timeout` config key; the timer begins after the 
start tasks are completed).

See the [overview](overview.html) for where to find additional information, especially the section on
"Entity's Error Status".
