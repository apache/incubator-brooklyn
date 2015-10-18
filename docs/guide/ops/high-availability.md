---
title: High Availability
layout: website-normal
---

Brooklyn will automatically run in HA mode if multiple Brooklyn instances are started
pointing at the same persistence store.  One Brooklyn node (e.g. the first one started)
is elected as HA master:  all *write operations* against Brooklyn entities, such as creating
an application or invoking an effector, should be directed to the master.

Once one node is running as `MASTER`, other nodes start in either `STANDBY` or `HOT_STANDBY` mode:

* In `STANDBY` mode, a Brooklyn instance will monitor the master and will be a candidate
  to become `MASTER` should the master fail. Standby nodes do *not* attempt to rebind
  until they are elected master, so the state of existing entities is not available at
  the standby node.  However a standby server consumes very little resource until it is
  promoted.
  
* In `HOT_STANDBY` mode, a Brooklyn instance will read and make available the live state of
  entities.  Thus a hot-standby node is available as a read-only copy.
  As with the standby node, if a hot-standby node detects that the master fails,
  it will be a candidate for promotion to master.

* In `HOT_BACKUP` mode, a Brooklyn instance will read and make available the live state of
  entities, as a read-only copy. However this node is not able to become master,
  so it can safely be used to test compatibility across different versions.

To explicitly specify what HA mode a node should be in, the following CLI options are available
for the parameter `--highAvailability`:

* `disabled`: management node works in isolation; it will not cooperate with any other standby/master nodes in management plane
* `auto`: will look for other management nodes, and will allocate itself as standby or master based on other nodes' states
* `master`: will startup as master; if there is already a master then fails immediately
* `standby`: will start up as lukewarm standby; if there is not already a master then fails immediately
* `hot_standby`: will start up as hot standby; if there is not already a master then fails immediately
* `hot_backup`: will start up as hot backup; this can be done even if there is not already a master; this node will not be a master 

The REST API offers live detection and control of the HA mode,
including setting priority to control which nodes will be promoted on master failure:

* `/server/ha/state`: Returns the HA state of a management node (GET),
  or changes the state (POST)
* `/server/ha/states`: Returns the HA states and detail for all nodes in a management plane
* `/server/ha/priority`: Returns the HA node priority for MASTER failover (GET),
  or sets that priority (POST)

Note that when POSTing to a non-master server it is necessary to pass a `Brooklyn-Allow-Non-Master-Access: true` header.
For example, the following cURL command could be used to change the state of a `STANDBY` node on `localhost:8082` to `HOT_STANDBY`:

    curl -v -X POST -d mode=HOT_STANDBY -H "Brooklyn-Allow-Non-Master-Access: true" http://localhost:8082/v1/server/ha/state

