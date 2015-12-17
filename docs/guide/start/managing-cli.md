---
title: Monitoring and Managing Applications
title_in_menu: Monitoring and Managing Applications
layout: website-normal
menu_parent: index-cli.md
children:
- { section: Applications } 
- { section: Entities } 
- { section: Sensors  } 
- { section: Effectors  } 
- { section: Activities } 
---



So far we have touched on Brooklyn's ability to *deploy* an application blueprint to a cloud provider, but this just 
the beginning. The sections below outline how to manage the application that has been deployed.

## Applications

Having created the application we can query its status.  We can find a summary of all deployed apps:
{% highlight bash %}
$ br app
 Id         Name     Status    Location   
 hTPAF19s   Tomcat   RUNNING   ajVVAhER  
{% endhighlight %}

or the details of a given app.  The ID, hTPAF19s, can also be used instead of the name "Tomcat".
{% highlight bash %}
$ br app Tomcat
  Id:              hTPAF19s   
  Name:            Tomcat   
  Status:          RUNNING   
  ServiceUp:       true   
  Type:            org.apache.brooklyn.entity.stock.BasicApplication   
  CatalogItemId:   null   
  LocationId:      ajVVAhER   
  LocationName:    FixedListMachineProvisioningLocation:ajVV   
  LocationSpec:    vagrantbyon   
  LocationType:    org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation  
{% endhighlight %}

To ease management of multiple applications, or even to reduce the amount of typing required, it is convenient
to create an alias for the commonly used application scope:
{% highlight bash %}
alias tom="br app Tomcat"
{% endhighlight %}

However, for simplicity the examples below show the full command in all cases.

We can explore the management hierarchy of all applications, which will show us the entities they are composed of.
{% highlight bash %}
$ br tree
|- Tomcat
+- org.apache.brooklyn.entity.stock.BasicApplication
  |- TomcatServer:Wx7r
  +- org.apache.brooklyn.entity.webapp.tomcat.TomcatServer
{% endhighlight %}



You can view the blueprint for the application again:
{% highlight bash %}
$ br app Tomcat spec
"name: Tomcat\nlocation:\n  mylocation\nservices:\n- serviceType: brooklyn.entity.webapp.tomcat.TomcatServer\n"
{% endhighlight %}

You can view the config of the application:
{% highlight bash %}
$ br app Tomcat config
Key                    Value   
camp.template.id       l67i25CM   
brooklyn.wrapper_app   true   
{% endhighlight %}

## Entities
To explore the entities of the application you can use the "entity" command. This will show the 
immediate child entities of a given application or one of its child entities.

{% highlight bash %}
$ br app Tomcat entity
Id         Name                Type   
Wx7r1C4e   TomcatServer:Wx7r   org.apache.brooklyn.entity.webapp.tomcat.TomcatServer      
{% endhighlight %}

You can get summary information for an entity by providing its name (or ID).

{% highlight bash %}
$ br app Tomcat entity TomcatServer:Wx7r
Id:              Wx7r1C4e   
Name:            TomcatServer:Wx7r   
Status:          RUNNING   
ServiceUp:       true   
Type:            org.apache.brooklyn.entity.webapp.tomcat.TomcatServer   
CatalogItemId:   null   
{% endhighlight %}

Also you can see the config of the entity with the "config" command.

{% highlight bash %}
$ br app Tomcat entity TomcatServer:Wx7r config
Key                       Value   
jmx.agent.mode            JMXMP_AND_RMI   
brooklyn.wrapper_app      true   
camp.template.id          yBcQuFZe   
onbox.base.dir            /home/vagrant/brooklyn-managed-processes   
onbox.base.dir.resolved   true   
install.unique_label      TomcatServer_7.0.65   
{% endhighlight %}

If an entity name is annoyingly long to type, the entity can be renamed:

{% highlight bash %}
$ br app Tomcat entity TomcatServer:Wx7r rename server
{% endhighlight %}

## Sensors

"Sensors" on entities provide a real-time picture of the status and operation of an entity of the application.

To view the sensors on the application itself, use the command below:

{% highlight bash %}
$ br app Tomcat sensor
Name                       Description                                                                             Value   
service.isUp               Whether the service is active and availability (confirmed and monitored)                true   
service.notUp.indicators   A map of namespaced indicators that the service is not up                               {}   
service.problems           A map of namespaced indicators of problems with a service                               {}   
service.state              Actual lifecycle state of the service                                                   "RUNNING"   
service.state.expected     Last controlled change to service state, indicating what the expected state should be   "running @ 1450356994928 / Thu Dec 17 12:56:34 GMT 2015"
{% endhighlight %}

To explore all sensors available on an entity use the sensor command with an entity scope.
Note, again, the name of the application or entity can be used or the ID:

{% highlight bash %}
br app Tomcat ent TomcatServer:Wx7r sensor
Name                                            Description                                                                                                      Value   
download.addon.urls                             URL patterns for downloading named add-ons (will substitute things like ${version} automatically)                   
download.url                                    URL pattern for downloading the installer (will substitute things like ${version} automatically)                 "http://download.nextag.com/apache/tomcat/tomcat-7/v${version}/bin/apache-tomcat-${version}.tar.gz"   
expandedinstall.dir                             Directory for installed artifacts (e.g. expanded dir after unpacking .tgz)                                       "/home/vagrant/brooklyn-managed-processes/installs/TomcatServer_7.0.65/apache-tomcat-7.0.65"   
host.address                                    Host IP address                                                                                                  "10.10.10.101"   
host.name                                       Host name                                                                                                        "10.10.10.101"   
host.sshAddress                                 user@host:port for ssh'ing (or null if inappropriate)                                                            "vagrant@10.10.10.101:22"   
host.subnet.address                             Host address as known internally in the subnet where it is running (if different to host.name)                   "10.10.10.101"   
host.subnet.hostname                            Host name as known internally in the subnet where it is running (if different to host.name)                      "10.10.10.101"   
# etc. etc.
{% endhighlight %}


To study selected sensors, give the command the sensor name as an argument

{% highlight bash %}
$ br app Tomcat ent TomcatServer:Wx7r sensor webapp.url   
"http://10.10.10.101:8080/"
{% endhighlight %}


## Effectors

Effectors are the means by which you can manipulate the entities in an application.  For an application you can list them 
with 

{% highlight bash %}
$ br app Tomcat effector
Name            Description                                                                                                                                                                            Parameters   
restart         Restart the process/service represented by an entity                                                                                                                                      
start           Start the process/service represented by an entity                                                                                                                                     locations   
stop            Stop the process/service represented by an entity                                                                                                                                         
{% endhighlight %}

For an entity supply the entity scope:

{% highlight bash %}
$ br app Tomcat ent TomcatServer:Wx7r effector
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
$ br app Tomcat ent TomcatServer:Wx7r effector deploy
Name            Description                                                                                                                                                                            Parameters   
deploy          Deploys the given artifact, from a source URL, to a given deployment filename/context                                                                                                  url,targetName   
{% endhighlight %}

These effectors can be invoked using the command "invoke", supplying the application and entity id of the entity to 
invoke the effector on.   

For example, to stop an application, use the "stop" effector. This will cleanly shutdown all components in the 
application and return any cloud machines that were being used. Do the invocation by supplying the effector name in 
the scope, and using the command 'invoke'. 

{% highlight bash %}
$ br app Tomcat ent TomcatServer:Wx7r eff stop invoke
{% endhighlight %}

Note that the three "lifecycle" related effectors, start, stop, and restart, are common to all software process 
entities in Brooklyn. They are so commonly used that they have their own aliases. The above could also have been done
by:

{% highlight bash %}
$ br app Tomcat ent TomcatServer:Wx7r stop
{% endhighlight %}

Some effectors require parameters for their invocation, as in the example of "deploy" above.  

{% highlight bash %}
br app Tomcat ent TomcatServer:Wx7r effector deploy
Name     Description                                                                             Parameters   
deploy   Deploys the given artifact, from a source URL, to a given deployment filename/context   url,targetName   
{% endhighlight %}

Now the effector can be invoked by supplying the parameters using ```--param parm=value``` or just ```-P parm=value```.

In the example below, a sample Tomcat war file is deployed, a variable is created for the root URL using the appropriate
sensor, and the index page is fetched. Note that at present a "tr" command is required in the second line below to strip
quotation characters from the returned sensor value. 

{% highlight bash %}
$ br app Tomcat ent TomcatServer:Wx7r effector deploy invoke -P url=https://tomcat.apache.org/tomcat-6.0-doc/appdev/sample/sample.war -P targetName=sample
$ webapp=$(br app Tomcat ent TomcatServer:Wx7r sensor webapp.url | tr -d '"')
$ curl $webapp/sample/
<html>
<head>
<title>Sample "Hello, World" Application</title>
</head>
# etc. etc.
{% endhighlight %}


## Activities

The 'activity' command allows us to investigate the activities of an entity. 

To view a list of all activities associated with an entity simply use

{% highlight bash %}
$ br app Tomcat ent TomcatServer:Wx7r activity
Id         Task                                       Submitted                      Status      Streams   
LtD5P1cb   start                                      Thu Dec 17 15:04:43 GMT 2015   Completed   
l2qo4vTl   provisioning (FixedListMachineProvisi...   Thu Dec 17 15:04:43 GMT 2015   Completed   
wLD764HE   pre-start                                  Thu Dec 17 15:04:43 GMT 2015   Completed    
KLTxDkoa   ssh: initializing on-box base dir ./b...   Thu Dec 17 15:04:43 GMT 2015   Completed   env,stderr,stdin,stdout   
jwwcJWmF   start (processes)                          Thu Dec 17 15:04:43 GMT 2015   Completed        
# etc. etc.
{% endhighlight %}

To view the details of an individual activity provide its ID:

{% highlight bash %}
$ br app Tomcat ent TomcatServer:Wx7r activity jwwcJWmF
Id:                  jwwcJWmF   
DisplayName:         start (processes)   
Description:            
EntityId:            efUvVWAw   
EntityDisplayName:   TomcatServer:efUv   
Submitted:           Thu Dec 17 15:04:43 GMT 2015   
Started:             Thu Dec 17 15:04:43 GMT 2015   
Ended:               Thu Dec 17 15:08:59 GMT 2015   
CurrentStatus:       Completed   
IsError:             false   
IsCancelled:         false   
SubmittedByTask:     LtD5P1cb   
Streams:                
DetailedStatus:      "Completed after 4m 16s

No return value (null)"   
{% endhighlight %}

If an activity has failed, the "DetailedStatus" value will show information about the failure, as an aid to diagnosis.

Adding the "--children" or "-c" parameter will show the activity's child activities, to allow the hierarchical structure 
of the activities to be investigated:

{% highlight bash %}
$ br app Tomcat ent TomcatServer:Wx7r activity -c jwwcJWmF
Id         Task                         Submitted                      Status   
UpYRc3fw   copy-pre-install-resources   Thu Dec 17 15:04:43 GMT 2015   Completed   
ig8sBHQr   pre-install                  Thu Dec 17 15:04:43 GMT 2015   Completed   
Elp4HaVj   pre-install-command          Thu Dec 17 15:04:43 GMT 2015   Completed   
YOvNobJk   setup                        Thu Dec 17 15:04:43 GMT 2015   Completed   
VN3cDKki   copy-install-resources       Thu Dec 17 15:08:43 GMT 2015   Completed   
xDJXQC0J   install                      Thu Dec 17 15:08:43 GMT 2015   Completed   
zxMDXUxz   post-install-command         Thu Dec 17 15:08:58 GMT 2015   Completed   
qnQnw7Oc   customize                    Thu Dec 17 15:08:58 GMT 2015   Completed   
ug044ArS   copy-runtime-resources       Thu Dec 17 15:08:58 GMT 2015   Completed   
STavcRc8   pre-launch-command           Thu Dec 17 15:08:58 GMT 2015   Completed   
HKrYfH6h   launch                       Thu Dec 17 15:08:58 GMT 2015   Completed   
T1m8VXbq   post-launch-command          Thu Dec 17 15:08:59 GMT 2015   Completed   
n8eK5USE   post-launch                  Thu Dec 17 15:08:59 GMT 2015   Completed   
{% endhighlight %}

If an activity has associated input and output streams, these may be viewed by providing the activity scope and
using the commands, "env", "stdin", "stdout", and "stderr".  For example, for the "initializing on-box base dir"
activity from the result of the earlier example,

{% highlight bash %}
$ br app Tomcat ent TomcatServer:Wx7r act KLTxDkoa stdout
BASE_DIR_RESULT:/home/vagrant/brooklyn-managed-processes:BASE_DIR_RESULT

{% endhighlight %}

