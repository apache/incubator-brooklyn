---
title: YAML BLueprint Advanced Example
layout: website-normal
---

By this point you should be familiar with the fundamental concepts behind both Apache Brooklyn and YAML blueprints. This section of the documentation is intended to show a complete, advanced example of a YAML blueprint.

The intention is that this example is used to learn the more in-depth concepts, but also to serve as a reference point when writing your own blueprints. This page will first explain what the example is and how to run it, then it will spotlight interesting features.


### The Example

The example itself is a deployment of an ELK stack. ELK stands for Elasticsearch, Logstash and Kibana- and this blueprint deploys, installs, runs and manages all three. Briefly, the component parts are:

* Elasticsearch: A clustered search engine
* Logstash: Collects, parses and stores logs. For our application it will store logs in Elasticsearch
* Kibana: A front end to Elasticsearch

For more about the ELK stack, please see the documentation [here](https://www.elastic.co/webinars/introduction-elk-stack).

In our example, we will be creating the ELK stack, and a Tomcat8 server which sends its logs using Logstash to Elasticsearch.

#### The Blueprints
-----------

There are four blueprints that make up this application. Each of them are used to add one or more catalog items to Brooklyn. You can find them below:

* [Elasticsearch](example_yaml/brooklyn-elasticsearch-catalog.bom)
* [Logstash](example_yaml/brooklyn-logstash-catalog.bom)
* [Kibana](example_yaml/brooklyn-kibana-catalog.bom)
* [ELK](example_yaml/brooklyn-elk-catalog.bom)

#### Running the example
First, add all four blueprints to the Brooklyn Catalog. This can be done by clicking the 'Catalog' tab, clinking the '+' symbol and pasting the YAML. Once this is done, click the 'Application' tab, then the '+' button to bring up the add application wizard. A new Catalog item will be available called 'ELK Stack'. Using the add application wizard, you should be able to deploy an ELK stack to a location of your choosing.

#### Exploring the example
After the example has been deployed, you can ensure it is working as expected by checking the following:

* There is a Kibana sensor called "main.uri", the value of which points to the Kibana front end. You can explore this front end, and observe the logs stored in Elasticsearch. Many Brooklyn applications have a "main.uri" set to point you in the right direction.
* You can also use the Elasticsearch REST API to explore further. The ES entity has an "urls.http.list" sensor. Choosing an url from there you will be able to access the REST API. The following URL will give you the state of the cluster "{es-url}/\_cluster/health?pretty=true". As you can see the number_of_nodes is currently 2, indicating that the Elasticsearch nodes are communicating with each other.

### Interesting Feature Spotlight
We will mainly focus on the Elasticsearch blueprint, and will be clear when another blueprint is being discussed. This blueprint is a cluster of Elasticsearch nodes. Clustering is a useful technique that is explained in more depth [here](../clusters.html).

#### Provisioning Properties
Our Elasticsearch blueprint has a few requirements of the location in which it is run. Firstly, it must be run on an Ubuntu machine to simplify installation. Secondly, there are two ports that need to be opened to ensure that the entities can be accessed from the outside world. These are configured on the provisioning.properties as follows:

~~~yaml
provisioning.properties:
  osFamily: ubuntu
  inboundPorts:
    - $brooklyn:config("elasticsearch.http.port")
    - $brooklyn:config("elasticsearch.tcp.port")
~~~

#### VanillaSoftwareProcess
When composing a YAML blueprint, the VanillaSoftwareProcess is a very useful entity to be aware of. A VanillaSoftwareProcess will instruct Brooklyn to provision an instance, and run a series of shell commands to setup, run, monitor and teardown your program. The commands are specified as configuration on the VanillaSoftwareProcess and there are several available. We will spotlight a few now. To simplify this blueprint, we have specified ubuntu only installs so that our commands can be tailored to this system (E.G. use apt-get rather than YUM).

##### Customize Command
The Customize Command is run after the application has been installed but before it is run. It is the perfect place to create and amend config files. Please refer to the following section of the Elasticsearch blueprint:

~~~yaml
customize.command: |
  $brooklyn:formatString("
  sudo rm -fr sudo tee /etc/elasticsearch/elasticsearch.yml;
  echo discovery.zen.ping.multicast.enabled: false | sudo tee -a /etc/elasticsearch/elasticsearch.yml;
  echo discovery.zen.ping.unicast.enabled: true | sudo tee -a /etc/elasticsearch/elasticsearch.yml;
  echo 'discovery.zen.ping.unicast.hosts: %s' | sudo tee -a /etc/elasticsearch/elasticsearch.yml;
  echo http.port: %s | sudo tee -a /etc/elasticsearch/elasticsearch.yml;
  echo transport.tcp.port: %s | sudo tee -a /etc/elasticsearch/elasticsearch.yml;
  ",
  $brooklyn:component("parent", "").attributeWhenReady("urls.tcp.withBrackets"),
  $brooklyn:config("elasticsearch.http.port"),
  $brooklyn:config("elasticsearch.tcp.port")
  )
~~~
The purpose of this section is to create a YAML file with all of the required configuration. We use the YAML literal style "|" indicator to write a multi line command. We then use $brooklyn:formatString notation to build the string from configuration. We start our series of commands by using the "rm" command to remove the previous config file. We then use "echo" and "tee" to create the new config file and insert the config. Part of the configuration is a list of all hosts that is set on the parent entity- this is done by using a combination of the "component" and  "attributeWhenReady" DSL commands. More on how this is generated later.

##### Check running
After an app is installed and run, this command will be run regularly and used to populate the "service.isUp" sensor. If this command is not specified, or returns an exit code of anything other than zero, then Brooklyn will assume that your entity is broken and will display the fire status symbol. Please refer to the following section of the Elasticsearch blueprint:

~~~yaml
checkRunning.command: |
  $brooklyn:formatString("counter=`wget -T 15 -q -O- %s:%s | grep -c \"status. : 200\"`; if [ $counter -eq 0 ]; then exit 1; fi",
  $brooklyn:attributeWhenReady("host.address"),
  $brooklyn:config("elasticsearch.http.port"))
~~~
There are many different ways to implement this command. For this example, we are querying the REST API to get the status. This command creates a variable called counter, and populates it by performing a "WGET" call to the status URL or the Elasticsearch node, grepping for a 200 status OK code. We then check the counter is populated (I.E. that the end point does return status 200) and exit with an error code of one if not.

#### Enrichers

##### Elasticsearch URLS
To ensure that all Elasticsearch nodes can communicate with each other they need to be configured with the TCP URL of all other nodes. Similarly, the Logstash instances need to be configured with all the HTTP URLs of the Elasticsearch nodes. The mechanism for doing this is the same, and involves using Transformers, Aggregators and Joiners, as follows:

~~~yaml
brooklyn.enrichers:
  - type: org.apache.brooklyn.enricher.stock.Transformer
    brooklyn.config:
      enricher.sourceSensor: $brooklyn:sensor("host.address")
      enricher.targetSensor: $brooklyn:sensor("url.tcp")
      enricher.targetValue: $brooklyn:formatString("%s:%s", $brooklyn:attributeWhenReady("host.address"), $brooklyn:config("elasticsearch.tcp.port"))  
~~~

In this example, we take the host.address and append the TCP port, outputting the result as url.tcp. After this has been done, we now need to collect all the URLs into a list in the Cluster entity, as follows:

~~~yaml
brooklyn.enrichers:
  - type: org.apache.brooklyn.enricher.stock.Aggregator
    brooklyn.config:
      enricher.sourceSensor: $brooklyn:sensor("url.tcp")
      enricher.targetSensor: $brooklyn:sensor("urls.tcp.list")
      enricher.aggregating.fromMembers: true

~~~
In the preceding example, we aggregate all of the TCP urls generated in the early example. These are then stored in a sensor called urls.tcp.list. This list is then joined together into one long string:

~~~yaml
- type: org.apache.brooklyn.enricher.stock.Joiner
  brooklyn.config:
    enricher.sourceSensor: $brooklyn:sensor("urls.tcp.list")
    enricher.targetSensor: $brooklyn:sensor("urls.tcp.string")
    uniqueTag: urls.quoted.string
~~~

Finally, the string has brackets added to the start and end:

~~~yaml
- type: org.apache.brooklyn.enricher.stock.Transformer
  brooklyn.config:
    enricher.sourceSensor: $brooklyn:sensor("urls.tcp.string")
    enricher.targetSensor: $brooklyn:sensor("urls.tcp.withBrackets")
    enricher.targetValue: $brooklyn:formatString("[%s]", $brooklyn:attributeWhenReady("urls.tcp.string"))
~~~

The resulting sensor will be called urls.tcp.withBrackets and will be used by all Elasticsearch nodes during setup.

##### Kibana URL
Kibana also needs to be configured such that it can access the Elasticsearch cluster. However, Kibana can only be configured to point at one Elasticsearch instance. To enable this, we use another enricher in the cluster to select the first URL from the list, as follows:

~~~yaml
- type: org.apache.brooklyn.enricher.stock.Aggregator
  brooklyn.config:
    enricher.sourceSensor: $brooklyn:sensor("host.address")
    enricher.targetSensor: $brooklyn:sensor("host.address.first")
    enricher.aggregating.fromMembers: true
    enricher.transformation:
     $brooklyn:object:
       type: "org.apache.brooklyn.util.collections.CollectionFunctionals$FirstElementFunction"
~~~

Similar to the above Aggregator, this Aggregator collects all the URLs from the members of the cluster. However, this Aggregator specifies a transformation. In this instance a transformation is a Java class that implements a Guava Function\<? super Collection\<?\>, ?\>\>, I.E. a function that takes in a collection and returns something. In this case we specify the FirstElementFunction from the CollectionFunctionals to ensure that we only get the first member of the URL list.

#### Latches
In the ELK blueprint, there is a good example of a latch. Latches are used to force an entity to wait until certain conditions are met before continuing. For example:

~~~yaml
- type: kibana-standalone
  ...
  name: kibana
  customize.latch: $brooklyn:component("es").attributeWhenReady("service.isUp")
~~~

This latch is used to stop Kibana customizing until the Elasticsearch cluster is up. We do this to ensure that the URL sensors have been setup, so that they can be passed into Kibana during the customization phase.

#### Child entities
The ELK blueprint also contains a good example of a child entity.

~~~yaml
- type: org.apache.brooklyn.entity.webapp.tomcat.Tomcat8Server
  brooklyn.config:
    children.startable.mode: background_late
  ...
  brooklyn.children:
  - type: logstash-child
~~~

In this example, a logstash-child is started as a child of the parent Tomcat server. The tomcat server needs to be configured with a "children.startable.mode" to inform Brooklyn when to bring up the child. In this case we have selected background so that the child is disassociated from the parent entity, and late to specify that the parent entity should start before we start the child.
