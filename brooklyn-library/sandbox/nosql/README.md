# Brooklyn Entity for MongoDB #

This entity allows MongoDB instances to be started and managed by Brooklyn.

## Usage ##

You can add a MongoDB entity with this code: (Groovy)

    mongodb = new MongoDbServer(owner: myApplication);

The entity supports the following configuration keys:

  * MongoDbServer.PORT - the TCP port to listen on. Defaults to 27017+
  * MongoDbServer.VERSION - which version of mongoDB to download and
    install. Defaults to 2.2.2
  * MongoDbServer.CONFIG_URL - a URL of a MongoDB configuration file to install.


Note that several configuration parameters are reserved. If you supply
a CONFIG_URL that attempts to set any of these parameters, they will
be ignored.

  * pidfilepath
  * fork
  * dbpath
  * logpath

## Further development ##

The following areas are suggested for further development:

### Sensors ###

MongoDB has several statistics available through its programmatic API
(visible in a similar way to documents). These could potentially be
used to expose health data as Brooklyn sensors.

See http://docs.mongodb.org/manual/administration/monitoring/#statistics

### Replication ###

MongoDB supports a *replication* system. This allows up to 12 MongoDB
instances to be started and brought together as a replication set. One
node becomes the primary and will service write requests, replicating
to other nodes ("secondaries") in the set. Reads can be serviced by
any node.

Replication provides extra resilience, as the secondaries will monitor
the health of the primary, and in the event of its failure, one of the
secondaries will be promoted to primary.

It also has the potential to act as a performance tool for heavy read
scenarios, as reads can be directed to any node.

It has an analog in multiple web application servers with a
load-balancer node in front - a cluster controller will be required to
expose effectors that control the size of the cluster, and sensors
that aggregate the individual MongoDB sensors. It differs from the web
tier example by not needing a load balancer - MongoDB manages this
itself.

See http://docs.mongodb.org/manual/core/replication/

### Sharding ###

Sharding distributes data between multiple nodes, each node
responsible for a specific subset of the data. The allows MongoDB to
scale up to handle large quantities of data that a single node could
not handle on its own. It also improves performance in workloads
involving many writes, as the write operations will be spread across
multiple nodes (subject to the data being written being fairly evenly
distributed across nodes).

See http://docs.mongodb.org/manual/sharding/

----
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.