---
layout: website-normal
title: "Troubleshooting: Going Deep in Java and Logs"
toc: /guide/toc.json
---

This guide takes a deep look at the Java and log messages for some failure scenarios,
giving common steps used to identify the issues.

## Script Failure

Many blueprints run bash scripts as part of the installation. This section highlights how to identify a problem with
a bash script.

First let's take a look at the `customize()` method of the Tomcat server blueprint:

{% highlight java %}
  @Override
  public void customize() {
      newScript(CUSTOMIZING)
          .body.append("mkdir -p conf logs webapps temp")
          .failOnNonZeroResultCode()
          .execute();

      copyTemplate(entity.getConfig(TomcatServer.SERVER_XML_RESOURCE), Os.mergePaths(getRunDir(), "conf", "server.xml"));
      copyTemplate(entity.getConfig(TomcatServer.WEB_XML_RESOURCE), Os.mergePaths(getRunDir(), "conf", "web.xml"));

      if (isProtocolEnabled("HTTPS")) {
          String keystoreUrl = Preconditions.checkNotNull(getSslKeystoreUrl(), "keystore URL must be specified if using HTTPS for " + entity);
          String destinationSslKeystoreFile = getHttpsSslKeystoreFile();
          InputStream keystoreStream = resource.getResourceFromUrl(keystoreUrl);
          getMachine().copyTo(keystoreStream, destinationSslKeystoreFile);
      }

      getEntity().deployInitialWars();
  }
{% endhighlight %}

Here we can see that it's running a script to create four directories before continuing with the customization. Let's
introduce an error by changing `mkdir` to `mkrid`:

{% highlight java %}
      newScript(CUSTOMIZING)
          .body.append("mkrid -p conf logs webapps temp") // `mkdir` changed to `mkrid`
          .failOnNonZeroResultCode()
          .execute();
{% endhighlight %}

Now let's try deploying this using the following YAML:

{% highlight yaml %}

name: Tomcat failure test
location: localhost
services:
- type: brooklyn.entity.webapp.tomcat.TomcatServer

{% endhighlight %}

Shortly after deployment, the entity fails with the following error:

`Failure running task ssh: customizing TomcatServerImpl{id=e1HP2s8x} (HmyPAozV): 
Execution failed, invalid result 127 for customizing TomcatServerImpl{id=e1HP2s8x}`

[![Script failure error in the Brooklyn debug console.](images/script-failure.png)](images/script-failure-large.png)

By selecting the `Activities` tab, we can drill into the task that failed. The list of tasks shown (where the 
effectors are shown as top-level tasks) are clickable links. Selecting that row will show the details of
that particular task, including its sub-tasks. We can eventually get to the specific sub-task that failed:

[![Task failure error in the Brooklyn debug console.](images/failed-task.png)](images/failed-task-large.png)

By clicking on the `stderr` link, we can see the script failed with the following error:

{% highlight console %}
/tmp/brooklyn-20150721-132251052-l4b9-customizing_TomcatServerImpl_i.sh: line 10: mkrid: command not found
{% endhighlight %}

This tells us *what* went wrong, but doesn't tell us *where*. In order to find that, we'll need to look at the
stack trace that was logged when the exception was thrown.

It's always worth looking at the Detailed Status section as sometimes this will give you the information you need.
In this case, the stack trace is limited to the thread that was used to execute the task that ran the script:

{% highlight console %}
Failed after 40ms

STDERR
/tmp/brooklyn-20150721-132251052-l4b9-customizing_TomcatServerImpl_i.sh: line 10: mkrid: command not found


STDOUT
Executed /tmp/brooklyn-20150721-132251052-l4b9-customizing_TomcatServerImpl_i.sh, result 127: Execution failed, invalid result 127 for customizing TomcatServerImpl{id=e1HP2s8x}

java.lang.IllegalStateException: Execution failed, invalid result 127 for customizing TomcatServerImpl{id=e1HP2s8x}
    at brooklyn.entity.basic.lifecycle.ScriptHelper.logWithDetailsAndThrow(ScriptHelper.java:390)
    at brooklyn.entity.basic.lifecycle.ScriptHelper.executeInternal(ScriptHelper.java:379)
    at brooklyn.entity.basic.lifecycle.ScriptHelper$8.call(ScriptHelper.java:289)
    at brooklyn.entity.basic.lifecycle.ScriptHelper$8.call(ScriptHelper.java:287)
    at brooklyn.util.task.DynamicSequentialTask$DstJob.call(DynamicSequentialTask.java:343)
    at brooklyn.util.task.BasicExecutionManager$SubmissionCallable.call(BasicExecutionManager.java:469)
    at java.util.concurrent.FutureTask.run(FutureTask.java:262)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1145)
    at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:615)
at java.lang.Thread.run(Thread.java:745)
{% endhighlight %}

In order to find the exception, we'll need to look in Brooklyn's debug log file. By default, the debug log file
is named `brooklyn.debug.log`. Usually the easiest way to navigate the log file is to use `less`, e.g.
`less brooklyn.debug.log`. We can quickly find find the stack trace by first navigating to the end of the log file
with `Shift-G`, then performing a reverse-lookup by typing `?Tomcat` and pressing `Enter`. If searching for the 
blueprint type (in this case Tomcat) simply matches tasks unrelated to the exception, you can also search for 
the text of the error message, in this case `? invalid result 127`. You can make the search case-insensitivity by
typing `-i` before performing the search. To skip the current match and move to the next one (i.e. 'up' as we're
performing a reverse-lookup), simply press `n`

In this case, the `?Tomcat` search takes us directly to the full stack trace (Only the last part of the trace
is shown here):

{% highlight console %}

at com.google.common.util.concurrent.ForwardingFuture.get(ForwardingFuture.java:63) ~[guava-17.0.jar:na]
at brooklyn.util.task.BasicTask.get(BasicTask.java:343) ~[classes/:na]
at brooklyn.util.task.BasicTask.getUnchecked(BasicTask.java:352) ~[classes/:na]
... 9 common frames omitted
Caused by: brooklyn.util.exceptions.PropagatedRuntimeException: 
at brooklyn.util.exceptions.Exceptions.propagate(Exceptions.java:97) ~[classes/:na]
at brooklyn.util.task.BasicTask.getUnchecked(BasicTask.java:354) ~[classes/:na]
at brooklyn.entity.basic.lifecycle.ScriptHelper.execute(ScriptHelper.java:339) ~[classes/:na]
at brooklyn.entity.webapp.tomcat.TomcatSshDriver.customize(TomcatSshDriver.java:72) ~[classes/:na]
at brooklyn.entity.basic.AbstractSoftwareProcessDriver$8.run(AbstractSoftwareProcessDriver.java:150) ~[classes/:na]
at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:471) ~[na:1.7.0_71]
at brooklyn.util.task.DynamicSequentialTask$DstJob.call(DynamicSequentialTask.java:343) ~[classes/:na]
... 5 common frames omitted
Caused by: java.util.concurrent.ExecutionException: java.lang.IllegalStateException: Execution failed, invalid result 127 for customizing TomcatServerImpl{id=e1HP2s8x}
at java.util.concurrent.FutureTask.report(FutureTask.java:122) [na:1.7.0_71]
at java.util.concurrent.FutureTask.get(FutureTask.java:188) [na:1.7.0_71]
at com.google.common.util.concurrent.ForwardingFuture.get(ForwardingFuture.java:63) ~[guava-17.0.jar:na]
at brooklyn.util.task.BasicTask.get(BasicTask.java:343) ~[classes/:na]
at brooklyn.util.task.BasicTask.getUnchecked(BasicTask.java:352) ~[classes/:na]
... 10 common frames omitted
Caused by: java.lang.IllegalStateException: Execution failed, invalid result 127 for customizing TomcatServerImpl{id=e1HP2s8x}
at brooklyn.entity.basic.lifecycle.ScriptHelper.logWithDetailsAndThrow(ScriptHelper.java:390) ~[classes/:na]
at brooklyn.entity.basic.lifecycle.ScriptHelper.executeInternal(ScriptHelper.java:379) ~[classes/:na]
at brooklyn.entity.basic.lifecycle.ScriptHelper$8.call(ScriptHelper.java:289) ~[classes/:na]
at brooklyn.entity.basic.lifecycle.ScriptHelper$8.call(ScriptHelper.java:287) ~[classes/:na]
... 6 common frames omitted

{% endhighlight %}

Brooklyn's use of tasks and helper classes can make the stack trace a little harder than usual to follow, but a good
place to start is to look through the stack trace for the node's implementation or ssh driver classes (usually
named `FooNodeImpl` or `FooSshDriver`). In this case we can see the following:

{% highlight console %}
at brooklyn.entity.webapp.tomcat.TomcatSshDriver.customize(TomcatSshDriver.java:72) ~[classes/:na]
{% endhighlight %}

Combining this with the error message of `mkrid: command not found` we can see that indeed `mkdir` has been
misspelled `mkrid` on line 72 of `TomcatSshDriver.java`.


## Non-Script Failure

The section above gives an example of a failure that occurs when a script is run. In this section we will look at
a failure in a non-script related part of the code. We'll use the `customize()` method of the Tomcat server again,
but this time, we'll correct the spelling of 'mkdir' and add a line that attempts to copy a nonexistent resource 
to the remote server:

{% highlight java %}

newScript(CUSTOMIZING)
    .body.append("mkdir -p conf logs webapps temp")
    .failOnNonZeroResultCode()
    .execute();

copyTemplate(entity.getConfig(TomcatServer.SERVER_XML_RESOURCE), Os.mergePaths(getRunDir(), "conf", "server.xml"));
copyTemplate(entity.getConfig(TomcatServer.WEB_XML_RESOURCE), Os.mergePaths(getRunDir(), "conf", "web.xml"));
copyTemplate("classpath://nonexistent.xml", Os.mergePaths(getRunDir(), "conf", "nonexistent.xml")); // Resource does not exist!

{% endhighlight %}

Let's deploy this using the same YAML from above. Here's the resulting error in the Brooklyn debug console:

[![Resource exception in the Brooklyn debug console.](images/resource-exception.png)](images/resource-exception-large.png)

Again, this tells us *what* the error is, but we need to find *where* the code is that attempts to copy this file. In
this case it's shown in the Detailed Status section, and we don't need to go to the log file:

{% highlight console %}

Failed after 221ms: Error getting resource 'classpath://nonexistent.xml' for TomcatServerImpl{id=PVZxDKU1}: java.io.IOException: Error accessing classpath://nonexistent.xml: java.io.IOException: nonexistent.xml not found on classpath

java.lang.RuntimeException: Error getting resource 'classpath://nonexistent.xml' for TomcatServerImpl{id=PVZxDKU1}: java.io.IOException: Error accessing classpath://nonexistent.xml: java.io.IOException: nonexistent.xml not found on classpath
    at brooklyn.util.ResourceUtils.getResourceFromUrl(ResourceUtils.java:297)
    at brooklyn.util.ResourceUtils.getResourceAsString(ResourceUtils.java:475)
    at brooklyn.entity.basic.AbstractSoftwareProcessDriver.getResourceAsString(AbstractSoftwareProcessDriver.java:447)
    at brooklyn.entity.basic.AbstractSoftwareProcessDriver.processTemplate(AbstractSoftwareProcessDriver.java:469)
    at brooklyn.entity.basic.AbstractSoftwareProcessDriver.copyTemplate(AbstractSoftwareProcessDriver.java:390)
    at brooklyn.entity.basic.AbstractSoftwareProcessDriver.copyTemplate(AbstractSoftwareProcessDriver.java:379)
    at brooklyn.entity.webapp.tomcat.TomcatSshDriver.customize(TomcatSshDriver.java:79)
    at brooklyn.entity.basic.AbstractSoftwareProcessDriver$8.run(AbstractSoftwareProcessDriver.java:150)
    at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:471)
    at brooklyn.util.task.DynamicSequentialTask$DstJob.call(DynamicSequentialTask.java:343)
    at brooklyn.util.task.BasicExecutionManager$SubmissionCallable.call(BasicExecutionManager.java:469)
    at java.util.concurrent.FutureTask.run(FutureTask.java:262)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1145)
    at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:615)
at java.lang.Thread.run(Thread.java:745)
    Caused by: java.io.IOException: Error accessing classpath://nonexistent.xml: java.io.IOException: nonexistent.xml not found on classpath
at brooklyn.util.ResourceUtils.getResourceFromUrl(ResourceUtils.java:233)
    ... 14 more
    Caused by: java.io.IOException: nonexistent.xml not found on classpath
    at brooklyn.util.ResourceUtils.getResourceViaClasspath(ResourceUtils.java:372)
at brooklyn.util.ResourceUtils.getResourceFromUrl(ResourceUtils.java:230)
    ... 14 more

{% endhighlight %}

Looking for `Tomcat` in the stack trace, we can see in this case the problem lies at line 79 of `TomcatSshDriver.java`


## External Failure

Sometimes an entity will fail outside the direct commands issues by Brooklyn. When installing and launching an entity,
Brooklyn will check the return code of scripts that were run to ensure that they completed successfully (i.e. the
return code of the script is zero). It is possible, for example, that a launch script completes successfully, but
the entity fails to start.

We can simulate this type of failure by launching Tomcat with an invalid configuration file. As seen in the previous
examples, Brooklyn copies two xml configuration files to the server: `server.xml` and `web.xml`

The first few non-comment lines of `server.xml` are as follows (you can see the full file [here](https://github.com/apache/incubator-brooklyn/blob/master/software/webapp/src/main/resources/brooklyn/entity/webapp/tomcat/server.xml)):

{% highlight xml %}

<Server port="${driver.shutdownPort?c}" shutdown="SHUTDOWN">
     <Listener className="org.apache.catalina.core.AprLifecycleListener" SSLEngine="on" />
     <Listener className="org.apache.catalina.core.JasperListener" />

{% endhighlight%}

Let's add an unmatched XML element, which will make this XML file invalid:

{% highlight xml %}

<Server port="${driver.shutdownPort?c}" shutdown="SHUTDOWN">
     <unmatched-element> <!-- This is invalid XML as we won't add </unmatched-element> -->
     <Listener className="org.apache.catalina.core.AprLifecycleListener" SSLEngine="on" />
     <Listener className="org.apache.catalina.core.JasperListener" />

{% endhighlight%}

As Brooklyn doesn't know how these types of resources are used, they're not validated as they're copied to the remote machine.
As far as Brooklyn is concerned, the file will have copied successfully.

Let's deploy Tomcat again, using the same YAML as before. This time, the deployment runs for a few minutes before failing
with `Timeout waiting for SERVICE_UP`:

[![External error in the Brooklyn debug console.](images/external-error.png)](images/external-error-large.png)

If we drill down into the tasks in the `Activities` tab, we can see that all of the installation and launch tasks
completed successfully, and stdout of the `launch` script is as follows:

{% highlight console %}

Executed /tmp/brooklyn-20150721-153049139-fK2U-launching_TomcatServerImpl_id_.sh, result 0

{% endhighlight %}

The task that failed was the `post-start` task, and the stack trace from the Detailed Status section is as follows:

{% highlight console %}

Failed after 5m 1s: Timeout waiting for SERVICE_UP from TomcatServerImpl{id=BUHgQeOs}

java.lang.IllegalStateException: Timeout waiting for SERVICE_UP from TomcatServerImpl{id=BUHgQeOs}
    at brooklyn.entity.basic.Entities.waitForServiceUp(Entities.java:1073)
    at brooklyn.entity.basic.SoftwareProcessImpl.waitForServiceUp(SoftwareProcessImpl.java:388)
    at brooklyn.entity.basic.SoftwareProcessImpl.waitForServiceUp(SoftwareProcessImpl.java:385)
    at brooklyn.entity.basic.SoftwareProcessDriverLifecycleEffectorTasks.postStartCustom(SoftwareProcessDriverLifecycleEffectorTasks.java:164)
    at brooklyn.entity.software.MachineLifecycleEffectorTasks$7.run(MachineLifecycleEffectorTasks.java:433)
    at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:471)
    at brooklyn.util.task.DynamicSequentialTask$DstJob.call(DynamicSequentialTask.java:343)
    at brooklyn.util.task.BasicExecutionManager$SubmissionCallable.call(BasicExecutionManager.java:469)
    at java.util.concurrent.FutureTask.run(FutureTask.java:262)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1145)
    at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:615)
at java.lang.Thread.run(Thread.java:745)

{% endhighlight %}

This doesn't really tell us what we need to know, and looking in the `brooklyn.debug.log` file yields no further
clues. The key here is the error message `Timeout waiting for SERVICE_UP`. After running the installation and
launch scripts, assuming all scripts completed successfully, Brooklyn will periodically check the health of the node
and will set the node on fire if the health check does not pass within a pre-prescribed period (the default is
two minutes, and can be configured using the `start.timeout` config key). The periodic health check also continues
after the successful launch in order to check continued operation of the node, but in this case it fails to pass
at all.

The first thing we need to do is to find out how Brooklyn determines the health of the node. The health-check is 
often implemented in the `isRunning()` method in the entity's ssh driver. Tomcat's implementation of `isRunning()`
is as follows:

{% highlight java %}
@Override
public boolean isRunning() {
    return newScript(MutableMap.of(USE_PID_FILE, "pid.txt"), CHECK_RUNNING).execute() == 0;
}
{% endhighlight %}

The `newScript` method has conveniences for default scripts to check if a process is running based on its PID. In this
case, it will look for Tomcat's PID in the `pid.txt` file and check if the PID is the PID of a running process

It's worth a quick sanity check at this point to check if the PID file exists, and if the process is running.
By default, the pid file is located in the run directory of the entity. You can find the location of the entity's run
directory by looking at the `run.dir` sensor. In this case it is `/tmp/brooklyn-martin/apps/jIzIHXtP/entities/TomcatServer_BUHgQeOs`.
To find the pid, you simply cat the pid.txt file in this directory:

{% highlight console %}
$ cat /tmp/brooklyn-martin/apps/jIzIHXtP/entities/TomcatServer_BUHgQeOs/pid.txt
73714
{% endhighlight %}

In this case, the PID in the file is 73714. You can then check if the process is running using `ps`. You can also
pipe the output to `fold` so the full launch command is visible:

{% highlight console %}

$ ps -p 73714 | fold -w 120
PID TTY           TIME CMD
73714 ??         0:08.03 /Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/bin/java -Dnop -Djava.util.logg
ing.manager=org.apache.juli.ClassLoaderLogManager -javaagent:/tmp/brooklyn-martin/apps/jIzIHXtP/entities/TomcatServer_BU
HgQeOs/brooklyn-jmxmp-agent-shaded-0.8.0-SNAPSHOT.jar -Xms200m -Xmx800m -XX:MaxPermSize=400m -Dcom.sun.management.jmxrem
ote -Dbrooklyn.jmxmp.rmi-port=1099 -Dbrooklyn.jmxmp.port=31001 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.manage
ment.jmxremote.authenticate=false -Djava.endorsed.dirs=/tmp/brooklyn-martin/installs/TomcatServer_7.0.56/apache-tomcat-7
.0.56/endorsed -classpath /tmp/brooklyn-martin/installs/TomcatServer_7.0.56/apache-tomcat-7.0.56/bin/bootstrap.jar:/tmp/
brooklyn-martin/installs/TomcatServer_7.0.56/apache-tomcat-7.0.56/bin/tomcat-juli.jar -Dcatalina.base=/tmp/brooklyn-mart
in/apps/jIzIHXtP/entities/TomcatServer_BUHgQeOs -Dcatalina.home=/tmp/brooklyn-martin/installs/TomcatServer_7.0.56/apache
-tomcat-7.0.56 -Djava.io.tmpdir=/tmp/brooklyn-martin/apps/jIzIHXtP/entities/TomcatServer_BUHgQeOs/temp org.apache.catali
na.startup.Bootstrap start

{% endhighlight %}

This confirms that the process is running. The next thing we can look at is the `service.notUp.indicators` sensor. This
reads as follows:

{% highlight json %}

{"service.process.isRunning":"The software process for this entity does not appear to be running"}

{% endhighlight %}

This confirms that the problem is indeed due to the `service.process.isRunning` sensor. We assumed earlier that this was
set by the `isRunning()` method in `TomcatSshDriver.java`, but this isn't always the case. The `service.process.isRunning`
sensor is wired up by the `connectSensors()` method in the node's implementation class, in this case 
`TomcatServerImpl.java`. Tomcat's implementation of `connectSensors()` is as follows:

{% highlight java %}

@Override
public void connectSensors() {
    super.connectSensors();

    if (getDriver().isJmxEnabled()) {
        String requestProcessorMbeanName = "Catalina:type=GlobalRequestProcessor,name=\"http-*\"";

        Integer port = isHttpsEnabled() ? getAttribute(HTTPS_PORT) : getAttribute(HTTP_PORT);
        String connectorMbeanName = format("Catalina:type=Connector,port=%s", port);

        jmxWebFeed = JmxFeed.builder()
            .entity(this)
            .period(3000, TimeUnit.MILLISECONDS)
            .pollAttribute(new JmxAttributePollConfig<Integer>(ERROR_COUNT)
                    .objectName(requestProcessorMbeanName)
                    .attributeName("errorCount"))
            .pollAttribute(new JmxAttributePollConfig<Integer>(REQUEST_COUNT)
                    .objectName(requestProcessorMbeanName)
                    .attributeName("requestCount"))
            .pollAttribute(new JmxAttributePollConfig<Integer>(TOTAL_PROCESSING_TIME)
                    .objectName(requestProcessorMbeanName)
                    .attributeName("processingTime"))
            .pollAttribute(new JmxAttributePollConfig<String>(CONNECTOR_STATUS)
                    .objectName(connectorMbeanName)
                    .attributeName("stateName"))
            .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_PROCESS_IS_RUNNING)
                    .objectName(connectorMbeanName)
                    .attributeName("stateName")
                    .onSuccess(Functions.forPredicate(Predicates.<Object>equalTo("STARTED")))
                    .setOnFailureOrException(false))
            .build();

        jmxAppFeed = JavaAppUtils.connectMXBeanSensors(this);
    } else {
        // if not using JMX
        LOG.warn("Tomcat running without JMX monitoring; limited visibility of service available");
        connectServiceUpIsRunning();
    }
}

{% endhighlight %}

We can see here that if jmx is not enabled, the method will call `connectServiceUpIsRunning()` which will use the
default PID-based method of determining if a process is running. However, as JMX *is* running, the `service.process.isRunning`
sensor (denoted here by the `SERVICE_PROCESS_IS_RUNNING` variable) is set to true if and only if the
`stateName` JMX attribute equals `STARTED`. We can see from the previous call to `.pollAttribute` that this
attribute is also published to the `CONNECTOR_STATUS` sensor. The `CONNECTOR_STATUS` sensor is defined as follows:

{% highlight java %}

AttributeSensor<String> CONNECTOR_STATUS =
    new BasicAttributeSensor<String>(String.class, "webapp.tomcat.connectorStatus", "Catalina connector state name");

{% endhighlight %}

Let's go back to the Brooklyn debug console and look for the `webapp.tomcat.connectorStatus`:

[![Sensors view in the Brooklyn debug console.](images/jmx-sensors.png)](images/jmx-sensors-large.png)

As the sensor is not shown, it's likely that it's simply null or not set. We can check this by clicking
the `Show/hide empty records` icon (highlighted in yellow above):

[![All sensors view in the Brooklyn debug console.](images/jmx-sensors-all.png)](images/jmx-sensors-all-large.png)

We know from previous steps that the installation and launch scripts completed, and we know the procecess is running,
but we can see here that the server is not responding to JMX requests. A good thing to check here would be that the
JMX port is not being blocked by iptables, firewalls or security groups
(see the [troubleshooting connectivity guide](connectivity.html)). 
Let's assume that we've checked that and they're all open. There is still one more thing that Brooklyn can tell us.


Still on the `Sensors` tab, let's take a look at the `log.location` sensor:

{% highlight console %}

/tmp/brooklyn-martin/apps/c3bmrlC3/entities/TomcatServer_C1TAjYia/logs/catalina.out

{% endhighlight %}

This is the location of Tomcat's own log file. The location of the log file will differ from process to process
and when writing a custom entity you will need to check the software's own documentation. If your blueprint's
ssh driver extends `JavaSoftwareProcessSshDriver`, the value returned by the `getLogFileLocation()` method will
automatically be published to the `log.location` sensor. Otherwise, you can publish the value yourself by calling
`entity.setAttribute(Attributes.LOG_FILE_LOCATION, getLogFileLocation());` in your ssh driver

**Note:** The log file will be on the server to which you have deployed Tomcat, and not on the Brooklyn server.
Let's take a look in the log file:

{% highlight console %}

less /tmp/brooklyn-martin/apps/c3bmrlC3/entities/TomcatServer_C1TAjYia/logs/catalina.out

Jul 21, 2015 4:12:12 PM org.apache.tomcat.util.digester.Digester fatalError
SEVERE: Parse Fatal Error at line 143 column 3: The element type "unmatched-element" must be terminated by the matching end-tag "</unmatched-element>".
    org.xml.sax.SAXParseException; systemId: file:/tmp/brooklyn-martin/apps/c3bmrlC3/entities/TomcatServer_C1TAjYia/conf/server.xml; lineNumber: 143; columnNumber: 3; The element type "unmatched-element" must be terminated by the matching end-tag "</unmatched-element>".
    at com.sun.org.apache.xerces.internal.util.ErrorHandlerWrapper.createSAXParseException(ErrorHandlerWrapper.java:203)
    at com.sun.org.apache.xerces.internal.util.ErrorHandlerWrapper.fatalError(ErrorHandlerWrapper.java:177)
    at com.sun.org.apache.xerces.internal.impl.XMLErrorReporter.reportError(XMLErrorReporter.java:441)
    at com.sun.org.apache.xerces.internal.impl.XMLErrorReporter.reportError(XMLErrorReporter.java:368)
    at com.sun.org.apache.xerces.internal.impl.XMLScanner.reportFatalError(XMLScanner.java:1437)
    at com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl.scanEndElement(XMLDocumentFragmentScannerImpl.java:1749)
    at com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl$FragmentContentDriver.next(XMLDocumentFragmentScannerImpl.java:2973)
    at com.sun.org.apache.xerces.internal.impl.XMLDocumentScannerImpl.next(XMLDocumentScannerImpl.java:606)
    at com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl.scanDocument(XMLDocumentFragmentScannerImpl.java:510)
    at com.sun.org.apache.xerces.internal.parsers.XML11Configuration.parse(XML11Configuration.java:848)
    at com.sun.org.apache.xerces.internal.parsers.XML11Configuration.parse(XML11Configuration.java:777)
    at com.sun.org.apache.xerces.internal.parsers.XMLParser.parse(XMLParser.java:141)
    at com.sun.org.apache.xerces.internal.parsers.AbstractSAXParser.parse(AbstractSAXParser.java:1213)
    at com.sun.org.apache.xerces.internal.jaxp.SAXParserImpl$JAXPSAXParser.parse(SAXParserImpl.java:649)
    at org.apache.tomcat.util.digester.Digester.parse(Digester.java:1561)
    at org.apache.catalina.startup.Catalina.load(Catalina.java:615)
    at org.apache.catalina.startup.Catalina.start(Catalina.java:677)
    at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
    at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
    at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
    at java.lang.reflect.Method.invoke(Method.java:497)
    at org.apache.catalina.startup.Bootstrap.start(Bootstrap.java:321)
at org.apache.catalina.startup.Bootstrap.main(Bootstrap.java:455)

    Jul 21, 2015 4:12:12 PM org.apache.catalina.startup.Catalina load
    WARNING: Catalina.start using conf/server.xml: The element type "unmatched-element" must be terminated by the matching end-tag "</unmatched-element>".
    Jul 21, 2015 4:12:12 PM org.apache.catalina.startup.Catalina start
    SEVERE: Cannot start server. Server instance is not configured.

{% endhighlight %}

As expected, we can see here that the `unmatched-element` element has not been terminated in the `server.xml` file
