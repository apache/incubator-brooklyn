---
title: CLI Usage Guide
layout: website-normal
menu_parent: index.md
children:
- { section: Login }
- { section: Applications }
- { section: Entities }
- { section: Sensors }
- { section: Effectors }
- { section: Policies }
- { section: Activities }
- { section: YAML Blueprint }
---

This document provides a brief overview of using the most common Brooklyn CLI commands,
by using the CLI to deploy an application then examine various aspects of it.

The YAML blueprint for the application that will be deployed is shown at the end of this document.

**NOTE:** In the sample output, some additional line-wrapping has been used to aid readabilty.

## Login
First, login to the running Brooklyn server.  This example assumes that the Brooklyn server
is running on `localhost`; change the URL and credentials as necessary.

{% highlight text %}
$ br login http://localhost:8081 admin
Enter Password: *
Connected to Brooklyn version 0.9.0-SNAPSHOT at http://localhost:8081
{% endhighlight %}

The version of the connected Brooklyn server may be viewed with the `version` command:

{% highlight text %}
$ br version
0.9.0-SNAPSHOT
{% endhighlight %}

## Applications
Deploy the application; on success the Id of the new application is displayed:

{% highlight text %}
$ br deploy webapp-policy.yaml
Id:       lmOcZbsT   
Name:     WebCluster   
Status:   In progress   
{% endhighlight %}

The `application` command can be used to list a summary of all the running applications.
After all of the entities have been started, the application status changes to `RUNNING`:

{% highlight text %}
$ br application
Id         Name         Status    Location   
YeEQHwgW   AppCluster   RUNNING   CNTBOtjI
lmOcZbsT   WebCluster   RUNNING   CNTBOtjI  
{% endhighlight %}

Further details of an application can be seen by using the ApplicationID or Name as a
parameter for the `application` command:

{% highlight text %}
$ br application WebCluster
Id:              lmOcZbsT   
Name:            WebCluster   
Status:          RUNNING   
ServiceUp:       true   
Type:            org.apache.brooklyn.entity.stock.BasicApplication   
CatalogItemId:   null   
LocationId:      CNTBOtjI   
LocationName:    FixedListMachineProvisioningLocation:CNTB   
LocationSpec:    byon   
LocationType:    org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation
{% endhighlight %}

The configuration details of an application can be seen with the `config` command:

{% highlight text %}
$ br application WebCluster config
Key                    Value   
camp.template.id       TYWVroRz   
brooklyn.wrapper_app   true
{% endhighlight %}


## Entities
The entities of an application can be viewed with the `entity` command:

{% highlight text %}
$ br app WebCluster entity
Id        Name    Type   
xOcMooka  WebApp  org.apache.brooklyn.entity.webapp.ControlledDynamicWebAppCluster
thHnLFkP  WebDB   org.apache.brooklyn.entity.database.mysql.MySqlNode
{% endhighlight %}

It is common for an entity to have child entities; these can be listed by providing an
entity-scope for the `entity` command:

{% highlight text %}
$ br app WebCluster entity WebApp entity
Id         Name                     Type   
e5pWAiHf   Cluster of TomcatServer  org.apache.brooklyn.entity.webapp.DynamicWebAppCluster   
CZ8QUVgX   NginxController:CZ8Q     org.apache.brooklyn.entity.proxy.nginx.NginxController   
{% endhighlight %}

or by using `-c` (or `--children`) flag with the `entity` command:

{% highlight text %}
$ br app WebCluster entity -c e5pWAiHf
Id         Name               Type   
x0P2LRxZ   quarantine         org.apache.brooklyn.entity.group.QuarantineGroup   
QK6QjmrW   TomcatServer:QK6Q  org.apache.brooklyn.entity.webapp.tomcat.TomcatServer
{% endhighlight %}

As for applications, the configuration details of an entity can be seen with the `config`
command:

{% highlight text %}
$ br app WebCluster entity thHnLFkP config
Key                             Value   
install.unique_label            MySqlNode_5.6.26   
brooklyn.wrapper_app            true   
datastore.creation.script.url   https://bit.ly/brooklyn-visitors-creation-script   
camp.template.id                dnw3GqN0   
camp.plan.id                    db   
onbox.base.dir                  /home/vagrant/brooklyn-managed-processes   
onbox.base.dir.resolved         true   

{% endhighlight %}

The value of a single configuration item can be displayed by using the configuration key
as a parameter for the `config` command:

{% highlight text %}
$ br app WebCluster entity thHnLFkP config datastore.creation.script.url
https://bit.ly/brooklyn-visitors-creation-script
{% endhighlight %}

The value of a configuration item can be changed by using the `set` command:

{% highlight text %}
$ br app WebCluster entity thHnLFkP config datastore.creation.script.url set \"https://bit.ly/new-script\"
{% endhighlight %}

## Sensors
The sensors associated with an application or entity can be listed with the `sensor` command:

{% highlight text %}
$ br app WebCluster entity CZ8QUVgX sensor
Name                                    Value
download.addon.urls:                    {"stickymodule":"https://bitbucket.org/nginx-goodies/n  
                                        ginx-sticky-module-ng/get/${addonversion}.tar.gz","pcr  
                                        e":"ftp://ftp.csx.cam.ac.uk/pub/software/programming/p  
                                        cre/pcre-${addonversion}.tar.gz"}
download.url:                           http://nginx.org/download/nginx-${version}.tar.gz
expandedinstall.dir:                    /home/vagrant/brooklyn-managed-processes/installs/Ngi  
                                        nxController_1.8.0/nginx-1.8.0
host.address:                           192.168.52.102
host.name:                              192.168.52.102
host.sshAddress:                        vagrant@192.168.52.102:22
host.subnet.address:                    192.168.52.102
host.subnet.hostname:                   192.168.52.102
http.port:                              8000
install.dir:                            /home/vagrant/brooklyn-managed-processes/installs/Ngin  
                                        xController_1.8.0
log.location:                           /home/vagrant/brooklyn-managed-processes/apps/FoEXXwJ2  
                                        /entities/NginxController_CZ8QUVgX/console
main.uri:                               http://192.168.52.102:8000/
member.sensor.hostandport:
member.sensor.hostname:                 {"typeToken":null,"type":"java.lang.String","name":"ho  
                                        st.subnet.hostname","description":"Host name as known   
                                        internally in the subnet where it is running (if diffe  
                                        rent to host.name)","persistence":"REQUIRED"}
member.sensor.portNumber:               {"typeToken":null,"type":"java.lang.Integer","name":"h  
                                        ttp.port","description":"HTTP port","persistence":"RE  
                                        QUIRED","configKey":{"name":"http.port","typeToken":nu  
                                        ll,"type":"org.apache.brooklyn.api.location.PortRange"  
                                        ,"description":"HTTP port","defaultValue":{"ranges":[{  
                                        "port":8080},{"start":18080,"end":65535,"delta":1}]},"  
                                        reconfigurable":false,"inheritance":null,"constraint":  
                                        "ALWAYS_TRUE"}}
nginx.log.access:                       /home/vagrant/brooklyn-managed-processes/apps/FoEXXwJ2  
                                        /entities/NginxController_CZ8QUVgX/logs/access.log
nginx.log.error:                        /home/vagrant/brooklyn-managed-processes/apps/FoEXXwJ2  
                                        /entities/NginxController_CZ8QUVgX/logs/error.log
nginx.pid.file:                         /home/vagrant/brooklyn-managed-processes/apps/FoEXXwJ2  
                                        /entities/NginxController_CZ8QUVgX/pid.txt
nginx.url.answers.nicely:               true
proxy.domainName:
proxy.http.port:                        8000
proxy.https.port:                       8443
proxy.protocol:                         http
proxy.serverpool.targets:               {"TomcatServerImpl{id=QK6QjmrW}":"192.168.52.103:8080"}
run.dir:                                /home/vagrant/brooklyn-managed-processes/apps/FoEXXwJ2  
                                        /entities/NginxController_CZ8QUVgX
service.isUp:                           true
service.notUp.diagnostics:              {}
service.notUp.indicators:               {}
service.problems:                       {}
service.process.isRunning:              true
service.state:                          RUNNING
service.state.expected:                 running @ 1449314377781 / Sat Dec 05 11:19:37 GMT 2015
softwareprocess.pid.file:
softwareservice.provisioningLocation:   {"type":"org.apache.brooklyn.api.location.Location","i  
                                        d":"zhYBc6xt"}
webapp.url:                             http://192.168.52.102:8000/
{% endhighlight %}

Details for an individual sensor can be shown by providing the Sensor Name as a
parameter to the `sensor` command:

{% highlight text %}
$ br app WebCluster entity CZ8QUVgX sensor service.state.expected
"running @ 1449314377781 / Sat Dec 05 11:19:37 GMT 2015"
{% endhighlight %}

## Effectors
The effectors for an application or entity can be listed with the `effector` command:

{% highlight text %}
$ br app WebCluster effector
Name      Description                                            Parameters   
restart   Restart the process/service represented by an entity      
start     Start the process/service represented by an entity     locations   
stop      Stop the process/service represented by an entity         
{% endhighlight %}

{% highlight text %}
$ br app WebCluster entity NginxController:CZ8Q effector
Name                              Description                     Parameters   
deploy                            Deploys an archive ...
getCurrentConfiguration           Gets the current ...      
populateServiceNotUpDiagnostics   Populates the attribute ...
reload                            Forces reload of ...  
restart                           Restart the process/service ... restartChildren,restartMachine
start                             Start the process/service ...   locations
stop                              Stop the process/service ...    stopProcessMode,stopMachineMode
update                            Updates the entities ...         
{% endhighlight %}

Details of an individual effector can be viewed by using the name as a parameter for
the `effector` command:

{% highlight text %}
$ br app WebCluster entity NginxController:CZ8Q effector update
Name:         update
Description:  Updates the entities configuration, and then forces reload of that configuration
Parameters:   
{% endhighlight %}

An effector can be invoked by using the `invoke` command with an effector-scope:

{% highlight text %}
$ br app WebCluster entity NginxController:CZ8Q effector update invoke
{% endhighlight %}

Parameters can also be passed to the effector:

{% highlight text %}
$ br app WebCluster entity NginxController:CZ8Q effector restart invoke restartChildren=true
{% endhighlight %}

Shortcut commands are available for the 3 standard effectors of `start`, `restart` and `stop`.
These commands can be used directly with an app-scope or entity-scope:

{% highlight text %}
$ br app WebCluster entity NginxController:CZ8Q restart
$ br app WebCluster stop
{% endhighlight %}

## Policies
The policies associated with an application or entity can be listed with the `policy` command:

{% highlight text %}
$ br app WebCluster entity NginxController:CZ8Q policy
Id         Name                         State   
VcZ0cfeO   Controller targets tracker   RUNNING
{% endhighlight %}

Details of an individual policy may be viewed by using the PolicyID as a parameter to
the `policy` command:

{% highlight text %}
$ br app WebCluster entity NginxController:CZ8Q policy VcZ0cfeO
Name                 Value                                    Description   
group                DynamicWebAppClusterImpl{id=TpbkaK4D}    group   
notifyOnDuplicates   false                                    Whether to notify listeners when
                                                              a sensor is published with the
                                                              same value as last time   
sensorsToTrack       [Sensor: host.subnet.hostname            Sensors of members to be monitored
                     (java.lang.String), Sensor: http.port    (implicitly adds service-up
                     (java.lang.Integer)]                     to this list, but that
                                                              behaviour may be deleted in a
                                                              subsequent release!)
{% endhighlight %}

## Activities
The activities for an application or entity may be listed with the `activity` command:

{% highlight text %}
$ br app WebCluster activity
Id         Task                                   Submitted                      Status      Streams   
Wb6GV5rt   start                                  Sat Dec 19 11:08:01 GMT 2015   Completed      
q2MbyyTo   invoking start[locations] on 2 nodes   Sat Dec 19 11:08:01 GMT 2015   Completed      
{% endhighlight %}

{% highlight text %}
$ br app WebCluster entity NginxController:CZ8Q activity
Id         Task                                       Submitted                      Status      Streams   
GVh0pyKG   start                                      Sun Dec 20 19:18:06 GMT 2015   Completed          
WJm908rA   provisioning (FixedListMachineProvisi...   Sun Dec 20 19:18:06 GMT 2015   Completed          
L0cKFBrW   pre-start                                  Sun Dec 20 19:18:06 GMT 2015   Completed              
D0Ab2esP   ssh: initializing on-box base dir ./b...   Sun Dec 20 19:18:06 GMT 2015   Completed   env,stderr,stdin,stdout
tumLAdo4   start (processes)                          Sun Dec 20 19:18:06 GMT 2015   Completed                  
YbF2czKM   copy-pre-install-resources                 Sun Dec 20 19:18:06 GMT 2015   Completed                                     
o3YdqxsQ   pre-install                                Sun Dec 20 19:18:06 GMT 2015   Completed                 
TtGw4qMZ   pre-install-command                        Sun Dec 20 19:18:06 GMT 2015   Completed        
duPvOSDB   setup                                      Sun Dec 20 19:18:06 GMT 2015   Completed       
WLtkbhgW   copy-install-resources                     Sun Dec 20 19:18:06 GMT 2015   Completed           
ZQtrImnl   install                                    Sun Dec 20 19:18:06 GMT 2015   Completed           
hzi49YD6   ssh: setting up sudo                       Sun Dec 20 19:18:06 GMT 2015   Completed   env,stderr,stdin,stdout
eEUHcpfi   ssh: Getting machine details for: Ssh...   Sun Dec 20 19:18:07 GMT 2015   Completed   env,stderr,stdin,stdout
juTe2qLG   ssh: installing NginxControllerImpl{i...   Sun Dec 20 19:18:08 GMT 2015   Completed   env,stderr,stdin,stdout
hXqwEZJl   post-install-command                       Sun Dec 20 19:18:08 GMT 2015   Completed          
vZliYwBI   customize                                  Sun Dec 20 19:18:08 GMT 2015   Completed            
O4Wwb0bP   ssh: customizing NginxControllerImpl{...   Sun Dec 20 19:18:08 GMT 2015   Completed   env,stderr,stdin,stdout
sDwMSkE2   copy-runtime-resources                     Sun Dec 20 19:18:08 GMT 2015   Completed         
yDYkdkS8   ssh: create run directory                  Sun Dec 20 19:18:08 GMT 2015   Completed   env,stderr,stdin,stdout
W7dI8r1c   pre-launch-command                         Sun Dec 20 19:18:08 GMT 2015   Completed           
OeZKwM5z   launch                                     Sun Dec 20 19:18:08 GMT 2015   Completed          
y50Gne5E   scheduled:nginx.url.answers.nicely @ ...   Sun Dec 20 19:18:08 GMT 2015   Scheduler,      
ARTninGE   scheduled:service.process.isRunning @...   Sun Dec 20 19:18:08 GMT 2015   Scheduler,      
tvZoNUTN   ssh: launching NginxControllerImpl{id...   Sun Dec 20 19:18:08 GMT 2015   Completed   env,stderr,stdin,stdout
YASrjA4w   post-launch-command                        Sun Dec 20 19:18:09 GMT 2015   Completed             
jgLYv8pE   post-launch                                Sun Dec 20 19:18:09 GMT 2015   Completed          
UN9OcWLS   post-start                                 Sun Dec 20 19:18:09 GMT 2015   Completed           
nmiv97He   reload                                     Sun Dec 20 19:18:09 GMT 2015   Completed            
FJfPbNtp   ssh: restarting NginxControllerImpl{i...   Sun Dec 20 19:18:10 GMT 2015   Completed   env,stderr,stdin,stdout
Xm1tjvKf   update                                     Sun Dec 20 19:18:40 GMT 2015   Completed        
Row67vfa   reload                                     Sun Dec 20 19:18:40 GMT 2015   Completed           
r8QZXlxJ   ssh: restarting NginxControllerImpl{i...   Sun Dec 20 19:18:40 GMT 2015   Completed   env,stderr,stdin,stdout
{% endhighlight %}

The detail for an individual activity can be viewed by providing the ActivityID as a
parameter to the `activity` command (an app-scope or entity-scope is not not needed for viewing
the details of an activity):

{% highlight text %}
$ br activity tvZoNUTN
Id:                  tvZoNUTN   
DisplayName:         ssh: launching NginxControllerImpl{id=OxPUBk1p}   
Description:            
EntityId:            OxPUBk1p   
EntityDisplayName:   NginxController:OxPU   
Submitted:           Sun Dec 20 19:18:08 GMT 2015   
Started:             Sun Dec 20 19:18:08 GMT 2015   
Ended:               Sun Dec 20 19:18:09 GMT 2015   
CurrentStatus:       Completed   
IsError:             false   
IsCancelled:         false   
SubmittedByTask:     OeZKwM5z   
Streams:             stdin: 1133, stdout: 162, stderr: 0, env 0   
DetailedStatus:      "Completed after 1.05s

Result: 0"
{% endhighlight %}

The activity command output shows whether any streams were associated with it.  The streams
and environment for an activity can be viewed with the commands `stdin`, `stdout`,
`stderr` and `env`:

{% highlight text %}
$ br activity tvZoNUTN stdin
export RUN_DIR="/home/vagrant/brooklyn-managed-processes/apps/V5GQCpIT/entities/NginxController_OxPUBk1p"
mkdir -p $RUN_DIR
cd $RUN_DIR
cd /home/vagrant/brooklyn-managed-processes/apps/V5GQCpIT/entities/NginxController_OxPUBk1p
{ which "./sbin/nginx" || { EXIT_CODE=$? && ( echo "The required executable \"./sbin/nginx\" does not exist" | tee /dev/stderr ) && exit $EXIT_CODE ; } ; }
nohup ./sbin/nginx -p /home/vagrant/brooklyn-managed-processes/apps/V5GQCpIT/entities/NginxController_OxPUBk1p/ -c conf/server.conf > /home/vagrant/brooklyn-managed-processes/apps/V5GQCpIT/entities/NginxController_OxPUBk1p/console 2>&1 &
for i in {1..10}
do
    test -f /home/vagrant/brooklyn-managed-processes/apps/V5GQCpIT/entities/NginxController_OxPUBk1p/logs/nginx.pid && ps -p `cat /home/vagrant/brooklyn-managed-processes/apps/V5GQCpIT/entities/NginxController_OxPUBk1p/logs/nginx.pid` && exit
    sleep 1
done
echo "No explicit error launching nginx but couldn't find process by pid; continuing but may subsequently fail"
cat /home/vagrant/brooklyn-managed-processes/apps/V5GQCpIT/entities/NginxController_OxPUBk1p/console | tee /dev/stderr
{% endhighlight %}

{% highlight text %}
$ br activity tvZoNUTN stdout
./sbin/nginx
  PID TTY          TIME CMD
 6178 ?        00:00:00 nginx
Executed /tmp/brooklyn-20151220-191808796-CaiI-launching_NginxControllerImpl_.sh, result 0
{% endhighlight %}

The child activities of an activity may be listed by providing an activity-scope for the
`activity` command:

{% highlight text %}
$ br activity OeZKwM5z
Id:                  OeZKwM5z   
DisplayName:         launch   
Description:            
EntityId:            OxPUBk1p   
EntityDisplayName:   NginxController:OxPU   
Submitted:           Sun Dec 20 19:18:08 GMT 2015   
Started:             Sun Dec 20 19:18:08 GMT 2015   
Ended:               Sun Dec 20 19:18:09 GMT 2015   
CurrentStatus:       Completed   
IsError:             false   
IsCancelled:         false   
SubmittedByTask:     tumLAdo4   
Streams:                
DetailedStatus:      "Completed after 1.06s

No return value (null)"

$ br activity OeZKwM5z activity
Id         Task                                       Submitted                      Status      Streams   
tvZoNUTN   ssh: launching NginxControllerImpl{id...   Sun Dec 20 19:18:08 GMT 2015   Completed   env,stderr,stdin,stdout   
{% endhighlight %}

or by using the `-c` (or `--children`) flag with the `activity` command:

{% highlight text %}
$ br activity -c OeZKwM5z
Id         Task                                       Submitted                      Status      Streams   
tvZoNUTN   ssh: launching NginxControllerImpl{id...   Sun Dec 20 19:18:08 GMT 2015   Completed   env,stderr,stdin,stdout   
{% endhighlight %}

## YAML Blueprint
This the YAML blueprint used for this document.

{% highlight text %}
name: WebCluster

location:
  byon:
    user: vagrant
    password: vagrant
    hosts:
      - 192.168.52.101
      - 192.168.52.102
      - 192.168.52.103
      - 192.168.52.104
      - 192.168.52.105

services:

- type: org.apache.brooklyn.entity.webapp.ControlledDynamicWebAppCluster
  name: WebApp
  brooklyn.config:
    wars.root: http://search.maven.org/remotecontent?filepath=io/brooklyn/example/brooklyn-example-hello-world-sql-webapp/0.6.0/brooklyn-example-hello-world-sql-webapp-0.6.0.war
    java.sysprops:
      brooklyn.example.db.url: >
        $brooklyn:formatString("jdbc:%s%s?user=%s&password=%s",
        component("db").attributeWhenReady("datastore.url"),
        "visitors", "brooklyn", "br00k11n")
  brooklyn.policies:
  - type: org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy
    brooklyn.config:
      metric: webapp.reqs.perSec.windowed.perNode
      metricLowerBound: 2
      metricUpperBound: 10
      minPoolSize: 1
      maxPoolSize: 2
      resizeUpStabilizationDelay: 1m
      resizeDownStabilizationDelay: 5m

- type: org.apache.brooklyn.entity.database.mysql.MySqlNode
  id: db
  name: WebDB
  brooklyn.config:
    creationScriptUrl: https://bit.ly/brooklyn-visitors-creation-script
{% endhighlight %}
