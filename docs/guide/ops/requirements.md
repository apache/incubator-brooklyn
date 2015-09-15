---
title: Requirements
layout: website-normal
---

## Server Specification

The size of server required by Brooklyn depends on the amount of activity. This includes:

* the number of entities/VMs being managed
* the number of VMs being deployed concurrently
* the amount of management and monitoring required per entity

For dev/test or when there are only a handful of VMs being managed, a small VM is sufficient.
For example, an AWS m3.medium with one vCPU, 3.75GiB RAM and 4GB disk.

For larger production uses, a more appropriate machine spec would be two or more cores,
at least 8GB RAM and 100GB disk. The disk is just for logs, a small amount of persisted state, and
any binaries for custom blueprints/integrations.


## Supported Operating Systems

The recommended operating system is CentOS 6.x or RedHat 6.x.

Brooklyn has also been tested on Ubuntu 12.04 and OS X.


## Software Requirements

Brooklyn requires Java (JRE or JDK) minimum version 1.7. 
OpenJDK is recommended. Brooklyn has also been tested on IBM J9 and Oracle's JVM.


## Configuration Requirements

### Ports

The ports used by Brooklyn are:

* 8443 for https, to expose the web-console and REST api.
* 8081 for http, to expose the web-console and REST api.

Whether to use https rather than http is configurable using the CLI option `--https`; 
the port to use is configurable using the CLI option `--port <port>`.

To enable remote Brooklyn access, ensure these ports are open in the firewall.
For example, to open port 8443 in iptables, ues the command:

    /sbin/iptables -I INPUT -p TCP --dport 8443 -j ACCEPT


### Locale

Brooklyn expects a sensible set of locale information and time zones to be available;
without this, some time-and-date handling may be surprising.

Brooklyn parses and reports times according to the time zone set at the server.
If Brooklyn is targetting geographically distributed users, 
it is normally recommended that the server's time zone be set to UTC.


### User Setup

It is normally recommended that Brooklyn run as a non-root user with keys installed to `~/.ssh/id_rsa{,.pub}`. 


### Linux Kernel Entropy

Check that the [linux kernel entropy]({{ site.path.website }}/documentation/increase-entropy.html) is sufficient.
