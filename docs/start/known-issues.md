---
layout: page
title: Known Issues
toc: ../toc.json
---

## Unable to Provision certain types of Debian VMs

*Symptom*: Brooklyn fails to provision Debian VMs (e.g. in aws-ec2).

*Cause*: `sudo` is not available on path, causing Brooklyn to fail to confirm that the VM is ssh'able.

*Fix*: [Pull #600](https://github.com/brooklyncentral/brooklyn/pull/600) fixes this issue and is available in 0.5.0-SNAPSHOT.

*Versions Affected*: 0.5.0-M2
