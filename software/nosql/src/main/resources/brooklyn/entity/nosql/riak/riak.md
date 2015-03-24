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