---
title: Monitoring and Managing Applications
title_in_menu: Monitoring and Managing Applications
layout: website-normal
menu_parent: index.md
---



So far we have touched on Brooklyn's ability to *deploy* an application blueprint to a cloud provider, but this just the beginning, proceed to  **[Monitoring and Managing Applications](managing.html)**.


## Monitoring and Managing Applications

Having created the application we can query its status:
{% highlight yaml %}
$ br list application
Id         Name             Status     Location   
MkGDqcQc   Tomcat           STARTING   cu8846Za 
{% endhighlight %}

or [DISCUSS - note the full name of the application need not be supplied.] The ID, MkGDqcQc, can also be used instead of the name "Tomcat".
{% highlight yaml %}
$ br show application Tomcat
Id         Name             Status     Location   
MkGDqcQc   Tomcat           STARTING   KNTBdBkh  
{% endhighlight %}

We can wait until the status changes by using "waitfor":
{% highlight yaml %}
$ br waitfor application MkGDqcQc
...[a pause until the application starts]...
Name             Id         Status    Location   
MkGDqcQc   Tomcat           RUNNING   KNTBdBkh  
{% endhighlight %}

By default this will time out after five minutes, but this can be changed by supplying a ```--timeout``` parameter (see details in the CLI Guide [provide a link].


We can explore the management hierarchy of the application, which will show us the entities it is composed of.
{% highlight yaml %}
$ br show -r application MkGDqcQc 
|- Tomcat
+- org.apache.brooklyn.entity.stock.BasicApplication
  |- TomcatServer:SjJ6
  +- org.apache.brooklyn.entity.webapp.tomcat.TomcatServer
{% endhighlight %}

Other options for the "show" command include:

{% highlight yaml %}
$ br show --status application MkGDqcQc
Status RUNNING
Service Up true
Type org.apache.brooklyn.entity.stock.BasicApplication
ID MkGDqcQc
Catalog Item null
{% endhighlight %}

You can view the blueprint for the application again:
{% highlight yaml %}
$ br show --blueprint application MkGDqcQc
name: Tomcat

services:
...etc
{% endhighlight %}

You can view the config of the application:
{% highlight yaml %}
$ br show --config application MkGDqcQc
Name                 Value
brooklyn.wrapper_app true
camp.template.id     IUDK7rrs
{% endhighlight %}

## Entities
To explore the entities of the application you can use the "list entity" command. This will show the 
immediate child entities of a given application or one of its child entities.

{% highlight yaml %}
$ br list entity MkGDqcQc
Id         Name                Type   
SjJ6TDsR   TomcatServer:SjJ6   org.apache.brooklyn.entity.webapp.tomcat.TomcatServer   
{% endhighlight %}

For a more complex entity than our Tomcat example it may be informative to list the entities recursively with -r.


You can get summary information for an entity with "show":

{% highlight yaml %}
$ br show entity MkGDqcQc SjJ6TDsR
Status RUNNING
Service Up true
URL http://10.10.10.101:8080/
Type org.apache.brooklyn.entity.webapp.tomcat.TomcatServer
ID SjJ6TDsR
Catalog Item null
{% endhighlight %}

Also you can see the config of the entity by adding the "--config" parameter.

{% highlight yaml %}
$ br show entity --config MkGDqcQc SjJ6TDsR

Name                    Value
brooklyn.wrapper_app    true
camp.template.id        C2JbRPCR
install.unique_label    TomcatServer_7.0.65
jmx.agent.mode          JMXMP_AND_RMI
onbox.base.dir          /home/vagrant/brooklyn-managed-processes
onbox.base.dir.resolved true
{% endhighlight %}

## Sensors

"Sensors" on entities provide a real-time picture of the status and operation of an entity of the application.

To view the sensors on the application itself, use the command "list sensor <APPID> <APPID>":

{% highlight bash %}
$ br list sensor Tomcat Tomcat
Name                       Description                                                                             Value   
service.isUp               Whether the service is active and availability (confirmed and monitored)                true   
service.notUp.indicators   A map of namespaced indicators that the service is not up                               {}   
service.problems           A map of namespaced indicators of problems with a service                               {}   
service.state.expected     Last controlled change to service state, indicating what the expected state should be   "running @ 1448895058652 / Mon Nov 30 14:50:58 GMT 2015"   
service.state              Actual lifecycle state of the service                                                   "RUNNING"   
{% endhighlight %}

To explore all sensors available on an entity use the command "list sensor <APPID> <ENTITYID>".  Note, again the 
name of the application or entity can be used instead of the ID:

{% highlight bash %}
$ br sensors Tomcat TomcatServer:SjJ6
Name                                            Description                                                                                                      Value   
download.addon.urls                             URL patterns for downloading named add-ons (will substitute things like ${version} automatically)                   
download.url                                    URL pattern for downloading the installer (will substitute things like ${version} automatically)                 "http://download.nextag.com/apache/tomcat/tomcat-7/v${version}/bin/apache-tomcat-${version}.tar.gz"   
expandedinstall.dir                             Directory for installed artifacts (e.g. expanded dir after unpacking .tgz)                                       "/home/vagrant/brooklyn-managed-processes/installs/TomcatServer_7.0.65/apache-tomcat-7.0.65"   
host.address                                    Host IP address                                                                                                  "10.10.10.101"   
host.name                                       Host name                                                                                                        "10.10.10.101"   
host.sshAddress                                 user@host:port for ssh'ing (or null if inappropriate)                                                            "vagrant@10.10.10.101:22"   
host.subnet.address                             Host address as known internally in the subnet where it is running (if different to host.name)                   "10.10.10.101"   
host.subnet.hostname                            Host name as known internally in the subnet where it is running (if different to host.name)                      "10.10.10.101"   
http.port                                       HTTP port                                                                                                        8080   
https.port                                      HTTP port (with SSL/TLS)                                                                                         8443   
install.dir                                     Directory for this software to be installed in                                                                   "/home/vagrant/brooklyn-managed-processes/installs/TomcatServer_7.0.65"   
java.metrics.heap.committed                     Commited heap size (bytes)                                                                                       "203 MB"   
java.metrics.heap.init                          Initial heap size (bytes)                                                                                        "210 MB"   
java.metrics.heap.max                           Max heap size (bytes)                                                                                            "811 MB"   
java.metrics.heap.used                          Current heap size (bytes)                                                                                        "10.5 MB"   
java.metrics.nonheap.used                       Current non-heap size (bytes)                                                                                    "16.6 MB"   
java.metrics.physicalmemory.free                The free memory available to the operating system                                                                "75.7 MB"   
java.metrics.physicalmemory.total               The physical memory available to the operating system                                                            "514 MB"   
java.metrics.processCpuTime.fraction.last       Fraction of CPU time used, reported by JVM (percentage, last datapoint)                                          "0.3998%"   
java.metrics.processCpuTime.fraction.windowed   Fraction of CPU time used, reported by JVM (percentage, over time window)                                        "0.3998%"   
java.metrics.processCpuTime.total               Process CPU time (total millis since start)                                                                      "12.1s"   
java.metrics.processors.available               number of processors available to the Java virtual machine                                                       2   
java.metrics.starttime                          Start time of Java process (UTC)                                                                                 "2015-11-30 14:51:57.357"   
java.metrics.systemload.average                 average system load                                                                                              0.0   
java.metrics.threads.current                    Current number of threads                                                                                        33   
java.metrics.threads.max                        Peak number of threads                                                                                           34   
java.metrics.uptime                             Uptime of Java process (millis, elapsed since start)                                                             "33m 59s"   
jmx.agent.local.path                            Path to JMX driver on the local machine                                                                          "/home/vagrant/brooklyn-managed-processes/apps/MkGDqcQc/entities/TomcatServer_SjJ6TDsR/brooklyn-jmxmp-agent-shaded-0.9.0-SNAPSHOT.jar"   
jmx.context                                     JMX context path                                                                                                 "jmxrmi"   
jmx.direct.port                                 JMX direct/private port (e.g. JMX RMI server port, or JMXMP port, but not RMI registry port)                     31001   
jmx.direct.port.legacy.NOT_USED                 Legacy definition JMX direct/private port (e.g. JMX RMI server port, or JMXMP port, but not RMI registry port)      
jmx.password                                    JMX password                                                                                                        
jmx.service.url                                 The URL for connecting to the MBean Server                                                                       "service:jmx:jmxmp://10.10.10.101:31001"   
jmx.user                                        JMX username                                                                                                        
log.location                                    Log file location                                                                                                "/home/vagrant/brooklyn-managed-processes/apps/MkGDqcQc/entities/TomcatServer_SjJ6TDsR/logs/catalina.out"   
main.uri                                        Main URI for contacting the service/endpoint offered by this entity                                              "http://10.10.10.101:8080/"   
rmi.registry.port                               RMI registry port, used for discovering JMX (private) port                                                       1099   
run.dir                                         Directory for this software to be run from                                                                       "/home/vagrant/brooklyn-managed-processes/apps/MkGDqcQc/entities/TomcatServer_SjJ6TDsR"   
service.isUp                                    Whether the service is active and availability (confirmed and monitored)                                         true   
service.notUp.diagnostics                       A map of namespaced diagnostics, from when the service is not up                                                 {}   
service.notUp.indicators                        A map of namespaced indicators that the service is not up                                                        {}   
service.process.isRunning                       Whether the process for the service is confirmed as running                                                      true   
service.state                                   Actual lifecycle state of the service                                                                            "RUNNING"   
service.state.expected                          Last controlled change to service state, indicating what the expected state should be                            "running @ 1448895119929 / Mon Nov 30 14:51:59 GMT 2015"   
softwareprocess.pid.file                        PID file                                                                                                            
softwareservice.provisioningLocation            Location used to provision a machine where this is running                                                       {"type":"org.apache.brooklyn.api.location.Location","id":"cu8846Za"}   
tomcat.shutdownport                             Suggested shutdown port                                                                                          31880   
webapp.deployedWars                             Names of archives/contexts that are currently deployed                                                           []   
webapp.enabledProtocols                         List of enabled protocols (e.g. http, https)                                                                     ["http"]   
webapp.https.ssl                                SSL Configuration for HTTPS                                                                                         
webapp.reqs.bytes.received                      Total bytes received by the webserver                                                                            ""   
webapp.reqs.bytes.sent                          Total bytes sent by the webserver                                                                                ""   
webapp.reqs.errors                              Request errors                                                                                                   2   
webapp.reqs.perSec.last                         Reqs/sec (last datapoint)                                                                                        0.0   
webapp.reqs.perSec.windowed                     Reqs/sec (over time window)                                                                                      0.0   
webapp.reqs.processingTime.fraction.last        Fraction of time spent processing, reported by webserver (percentage, last datapoint)                            "0%"   
webapp.reqs.processingTime.fraction.windowed    Fraction of time spent processing, reported by webserver (percentage, over time window)                          "0%"   
webapp.reqs.processingTime.max                  Max processing time for any single request, reported by webserver (millis)                                       ""   
webapp.reqs.processingTime.total                Total processing time, reported by webserver (millis)                                                            "41ms"   
webapp.reqs.total                               Request count                                                                                                    2   
webapp.tomcat.connectorStatus                   Catalina connector state name                                                                                    "STARTED"   
webapp.url                                      URL                                                                                                              "http://10.10.10.101:8080/" 
{% endhighlight %}


To study selected sensors, use the command  "show sensor <APPID> <ENTITYID> <SENSOR>".  Here, SENSOR can be the name
of an individual sensor, e.g. 

{% highlight bash %}
$ br show sensor MkGDqcQc SjJ6TDsR  MkGDqcQc SjJ6TDsR webapp.url 
"http://10.10.10.101:8080/"
{% endhighlight %}

In this case only the value of the sensor is returned.

Alternatively you can supply the "list sensor" command an item of text that may match multiple sensors (using a "globbing" syntax like the unix command line):


{% highlight bash %}
$ br list sensor MkGDqcQc SjJ6TDsR service.isUp "webapp.reqs.per*"
Name                                                   Description                                                              
webapp.reqs.perSec.last                         Reqs/sec (last datapoint)                                                                                        0.0   
webapp.reqs.perSec.windowed                     Reqs/sec (over time window)                                                                                      0.0   
{% endhighlight %}


## Effectors

Effectors are the means by which you can manipulate the entities in an application.  For an application you can list them 
with "bk list effector <APPID>":

{% highlight bash %}
$ br list effector MkGDqcQc MkGDqcQc
Name            Description                                                                                                                                                                            Parameters   
restart         Restart the process/service represented by an entity                                                                                                                                      
start           Start the process/service represented by an entity                                                                                                                                     locations   
stop            Stop the process/service represented by an entity                                                                                                                                         
{% endhighlight %}

Note that these three "lifecycle" related effectors, start, stop, and restart, are common to all software process entities in Brooklyn.

For an entity supply the entity id:

{% highlight bash %}
$ br list effector MkGDqcQc SjJ6TDsR
Name                              Description                                                                               Parameters   
deploy                            Deploys the given artifact, from a source URL, to a given deployment filename/context     url,targetName   
populateServiceNotUpDiagnostics   Populates the attribute service.notUp.diagnostics, with any available health indicators      
restart                           Restart the process/service represented by an entity                                      restartChildren,restartMachine   
start                             Start the process/service represented by an entity                                        locations   
stop                              Stop the process/service represented by an entity                                         stopProcessMode,stopMachineMode   
undeploy                          Undeploys the given context/artifact                                                      targetName   
{% endhighlight %}

To view just one effector's documentation, supply its name to the show command:

{% highlight bash %}
$ br show effector MkGDqcQc SjJ6TDsR deploy
Name            Description                                                                                                                                                                            Parameters   
deploy          Deploys the given artifact, from a source URL, to a given deployment filename/context                                                                                                  url,targetName   
{% endhighlight %}

These effectors can be invoked using the command "invoke", supplying the application and entity id of the entity to invoke the effector on.   For example, to stop an application, use the "Stop" effector. This will cleanly shutdown all components in the application and return any cloud machines that were being used.

{% highlight bash %}
$ br invoke MkGDqcQc MkGDqcQc stop
{% endhighlight %}

Some effectors require parameters for their invocation, as in the example of "deploy" above.  A description of the effector parameters can be obtained using "show effector --params", for example

{% highlight bash %}
$ br show effector --params MkGDqcQc SjJ6TDsR deploy
Name         Type                 Description                                                           Default Value
url          java.lang.String     URL of WAR file                                                       null
targetName   java.lang.String     context path where WAR should be deployed (/ for ROOT)                null
{% endhighlight %}

Now the effector can be invoked by supplying the parameters [NOTE, syntax of how to supply the parameters will be 
dependent on the capabilities of the underlying CLI library, so this is just a guess for now]

{% highlight bash %}
$ br invoke MkGDqcQc SjJ6TDsR deploy --url https://tomcat.apache.org/tomcat-6.0-doc/appdev/sample/sample.war --targetName /sample
{% endhighlight %}


## Activities

