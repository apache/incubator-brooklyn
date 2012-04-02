---
layout: page
title: Wishlist
toc: /toc.json
---

This page records some of the additions planned and requested
(approximately a roadmap).
If you're interested in helping, scan the [issues list](https://github.com/brooklyncentral/brooklyn/issues)
on Github for anything related,
email the list, and get started!

## Entities

* WebApp PaaS:  CloudFoundry, AWS Elastic Beanstalk, Google AppEngine (OpenShift a good starting point)
* Non-Java webapps:  PHP, Rails, Node.js, perl
* CDN:  AWS Cloudfront, Akamai, others
* Data:  _lots!_, including MySQL, Mongo, Couch, etc (look at building on Whirr support, like Hadoop does!)
* More monitoring and provisioning:  Nagios, Zenoss, Cacti etc; Juju for deployment; possibly direct Puppet or Chef integration (although Whirr integration is quite powerful) 

## Features

* REST API for deploying, viewing, and managing, including application definitions in JSON/XML (and support for dependent configuration)
* Distributed management plane
* At-Rest serialization of state (likely piggy-backing on JSON/XML and datagrid support)  
* Bind to existing entities on restart
* Extract data from entities that are being stopped, to restore on restart (could extend MySQL example to do this)
* Windows:  support running from windows (untested); work on installation _to_ Windows servers
