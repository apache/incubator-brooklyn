---
layout: page
title: Publish-Subscribe Messagiung
toc: /toc.json
---

This example shows how a simple messaging application can be build
in brooklyn, starting with configuring and launching a broker. For
these examples we will use the Apache [Qpid](http://qpid.apache.org/)
Java AMQP message broker and clients using the
[JMS](http://docs.oracle.com/javaee/6/tutorial/doc/bnceh.html) API.

{% readj ../before-begin.include.md %}

Now, go to this particular example's directory:

{% highlight bash %}
% cd simple-messaging-pubsub
{% endhighlight %}

The CLI needs to know where to find your compiled examples. You can set this up by exporting
the ``BROOKLYN_CLASSPATH`` environment variable in the following way:

{% highlight bash %}
% export BROOKLYN_CLASSPATH=$(pwd)/target/classes
{% endhighlight %}

The project ``simple-messaging-pubsub`` includes a deployment
descriptor for our example messaging application and simple _Publish_
and _Subscribe_ JMS test client scripts.

## Single Broker

The first example will include a Qpid broker, which we will customize
to use the Oracle [BDB](http://www.oracle.com/technetwork/products/berkeleydb/overview/index.html)
message store as an example of a typical production setup. We will
also create a queue for use by a pair of test clients.

The ``QpidBroker`` entity is created like this, which uses the
default configuration, specifying only the AMQP port and creates
no queues or topics:

{% highlight java %}
public class StandaloneBrokerExample extends AbstractApplication {
    @Override
    public void init() {
        // Configure the Qpid broker entity
    	QpidBroker broker = addChild(EntitySpec.create(QpidBroker.class)
    	        .configure("amqpPort", 5672));
    }
}
{% endhighlight %}

To install the custom configuration files and extra libraries for
BDB, we specify some files to copy to the broker installation, using
the ``runtimeFiles`` property. These files should be available in
the classpath of the application when it is running, usually by
copying them to the ``src/main/resources`` directory. For example,
here we copy a custom XML configuration file and a new password
file:

{% highlight java %}
        final String CUSTOM_CONFIG_PATH = "classpath://custom-config.xml";
        final String PASSWD_PATH = "classpath://passwd";

    	QpidBroker broker = addChild(EntitySpec.create(QpidBroker.class)
    	        .configure("amqpPort", 5672)
    	        .configure("amqpVersion", AmqpServer.AMQP_0_10)
    	        .configure("runtimeFiles", ImmutableMap.builder()
    	                .put(QpidBroker.CONFIG_XML, CUSTOM_CONFIG_PATH)
    	                .put(QpidBroker.PASSWD, PASSWD_PATH)
    	                .build()));
{% endhighlight %}

Finally, we come to the complete configuration of our ``QpidBroker``
entity using the BDB store. The additional properties here specify
the AMQP version and that a queue named _testQueue_ should be created
on startup.

{% highlight java %}
        final String CUSTOM_CONFIG_PATH = "classpath://custom-config.xml";
        final String PASSWD_PATH = "classpath://passwd";
        final String QPID_BDBSTORE_JAR_PATH = "classpath://qpid-bdbstore-0.14.jar";
        final String BDBSTORE_JAR_PATH = "classpath://je-5.0.34.jar";

    	QpidBroker broker = addChild(EntitySpec.create(QpidBroker.class)
    	        .configure("amqpPort", 5672)
    	        .configure("amqpVersion", AmqpServer.AMQP_0_10)
    	        .configure("runtimeFiles", ImmutableMap.builder()
    	                .put(QpidBroker.CONFIG_XML, CUSTOM_CONFIG_PATH)
    	                .put(QpidBroker.PASSWD, PASSWD_PATH)
    	                .put("lib/opt/qpid-bdbstore-0.14.jar", QPID_BDBSTORE_JAR_PATH)
    	                .put("lib/opt/je-5.0.34.jar", BDBSTORE_JAR_PATH)
    	                .build())
    	        .configure("queue", "testQueue"));
{% endhighlight %}


### Running the Example

You can build and run the example (on *nix or Mac) after checking
out the Brooklyn [repository](https://www.github.com/brooklyncentral/brooklyn)
as follows:

{% highlight bash %}
% ${BROOKLYN_HOME}/bin/brooklyn -v launch --app brooklyn.demo.StandaloneBrokerExample --location localhost
{% endhighlight %}

Now, visit the Brooklyn web console on port 8081 (for pre 0.6 releases,
use the credentials admin/password). This allows you to view the Brooklyn 
entities and their current state for debugging.

Note that the installation may take some time, because the default
deployment downloads the software from the official repos.  You can
monitor start-up activity for each entity in the ``Activity`` pane
in the management console, and see more detail by tailing the log
file (``tail -f brooklyn.log``).

After starting up, the demo script should display a summary of all
the Brooklyn managed entities and their attributes. This will show
both the Qpid broker and its child entity, the queue _testQueue_
which was created at startup. The queue entity has sensors that
monitor the depth of unread messages, which you can check while
running the test client scripts later.

If the ``-v`` flag is passed to the startup command, all configured
entity and sensor details will be output. This includes the broker URL,
which is used to configure JMS clients to connect to this broker.
This URL can also be viewed as a sensor attribute in the web console,
named _broker.url_.

This sensor is common to _all_ messaging brokers that Brooklyn
provides, and is usually accessed by applications to allow them to
provide it as a parameter to other entities, as shown in the code
fragment below.

{% highlight java %}
String url = broker.getAttribute(MessageBroker.BROKER_URL)
{% endhighlight %}

Using the URL the demo script printed, you can run the test ``Subscribe``
and then ``Publish`` classes, to send messages using the broker. Simply
run the commands in another window, with the provided URL as the
only argument. Note that the URLs may be different to those printed
below, and that any unquoted ``&`` characters *must* be escaped,
if present.

{% highlight bash %}
% URL="amqp://guest:guest@/localhost?brokerlist='tcp://localhost:5672'"
% java -cp "./resources/lib/*:./target/classes" brooklyn.demo.Subscribe ${URL}
% java -cp "./resources/lib/*:./target/classes" brooklyn.demo.Publish ${URL}
{% endhighlight %}

In the _Publish_ window you should see a log message every time a
message is sent, like this:

{% highlight bash %}
2012-05-02 14:04:38,521 INFO  Sent message 65
2012-05-02 14:04:39,522 INFO  Sent message 66
{% endhighlight %}

Similarly, the _Subscribe_ windows should log on reciept of these
messages, as follows:

{% highlight bash %}
2012-05-02 14:04:32,522 INFO  got message 41 test message 41
2012-05-02 14:04:33,523 INFO  got message 42 test message 42
{% endhighlight %}

### Cloud Deployment

With appropriate setup (as described
[here]({{ site.url }}/use/guide/management/index.html#startup-config))
this can also be deployed to your favourite cloud, let's pretend
it's Amazon Ireland, as follows:

{% highlight bash %}
% ${BROOKLYN_HOME}/bin/brooklyn launch --app brooklyn.demo.StandaloneBrokerExample --location aws-ec2:eu-west-1
{% endhighlight %}

If you encounter any difficulties, please
[tell us]({{ site.url }}/meta/contact.html) and we'll do our best
to help.
