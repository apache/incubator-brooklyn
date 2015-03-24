# Riak Examples

Here is a selection of examples showing how to deploy Riak.


### A Single-Node Deployment

```
location: YOUR_CLOUD
services:
- type: brooklyn.entity.nosql.riak.RiakNode
```


### A Single-Node Deployment

```
location: YOUR_CLOUD
services:
- type: brooklyn.entity.nosql.riak.RiakNode
```


### A Cluster

```
services:
- type: brooklyn.entity.nosql.riak.RiakCluster
  location: YOUR_CLOUD
  initialSize: 5
```


### A Cluster at a Specific Version with a Web App

```
services:
- type: brooklyn.entity.nosql.riak.RiakCluster
  id: cluster
  brooklyn.config:
    initialSize: 2
    install.version: 2.0.0
- type: brooklyn.entity.webapp.ControlledDynamicWebAppCluster
  brooklyn.config:
    initialSize: 2
    wars.root: https://s3-eu-west-1.amazonaws.com/brooklyn-clocker/brooklyn-example-hello-world-sql-webapp.war
    java.sysprops: 
      brooklyn.example.riak.nodes: $brooklyn:component("cluster").attributeWhenReady("riak.cluster.nodeList")
```

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
