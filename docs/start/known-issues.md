---
layout: page
title: Known Issues
toc: ../toc.json
---

## Unable to Provision certain types of Debian VMs

*Symptom*: Brooklyn fails to provision Debian VMs (e.g. in aws-ec2).

*Cause*: `sudo` is not available on path, causing Brooklyn to fail to confirm that the VM is ssh'able.

*Workaround*: Choose an image that does have sudo (see [wiki.debian.org/Cloud/AmazonEC2Image](http://wiki.debian.org/Cloud/AmazonEC2Image)).

*Fix*: is [Pull #600](https://github.com/brooklyncentral/brooklyn/pull/600); you may also want to run with `brooklyn.jclouds.aws-ec2.user=root` if subsequent commands give permission errors.

*Versions Affected*: 0.5.0-M2
