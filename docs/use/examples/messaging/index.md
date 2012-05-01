---
layout: page
title: Publish-Subscribe Messagiung
toc: /toc.json
---

This example shows how a simple messaging application can be build in brooklyn, starting
with configuring and launching a broker. For these examples we will use the Apache
[Qpid](http://qpid.apache.org/) Java AMQP message broker and clients using the
[JMS](http://docs.oracle.com/javaee/6/tutorial/doc/bnceh.html) API. 

{% readj ../before-begin.include.md %}

The project ``examples/simple-messaging-pubsub`` includes a deployment descriptor for
our example messaging application.

## Single Broker

The first example will include a Qpid broker, which we will customize to use the Oracle
[BDB](http://www.oracle.com/technetwork/products/berkeleydb/overview/index.html) message
store as an example of a typical production setup. We will also create a queue
for use by a pair of test clients.

The ``QpidBroker`` entity is created like this, which uses the default configuration files
and creates no queues or topics.

{% highlight java %}
    QpidBroker broker = new QpidBroker(this, amqpPort:5672)
{% endhighlight %}

To install our custom configuration files and extra libraries for BDB, we specify some
files to copy to the broker installation, using the ``runtimeFiles`` property. These
files should be available in the classpath of the application when it is running, usually
by copying them to the ``src/main/resources`` directory. For example, here we copy a custom
XML configuration file and a new password file.

{% highlight java %}
    QpidBroker broker = new QpidBroker(this, amqpPort:5672,
        runtimeFiles:[ (QpidBroker.CONFIG_XML):"classpath://custom-config.xml",
                       (QpidBroker.PASSWD):"classpath://passwd" ])
{% endhighlight %}

Finally, we come to the complete configuration of our ``QpidBroker`` entity using
the BDB store. The additional properties here specify the AMQP version and that a queue
named _testQueue_ should be created on startup.

{% highlight java %}
    // Configure the Qpid broker entity
    QpidBroker broker = new QpidBroker(app,
        amqpPort:5672,
        amqpVersion:AmqpServer.AMQP_0_10,
        runtimeFiles:[ (QpidBroker.CONFIG_XML):CUSTOM_CONFIG_PATH,
                       (QpidBroker.PASSWD):PASSWD_PATH,
                       ("lib/opt/qpid-bdbstore-0.14.jar"):QPID_BDBSTORE_JAR_PATH ],
                       ("lib/opt/je-5.0.34.jar"):BDBSTORE_JAR_PATH ],
        queue:"testQueue")
{% endhighlight %}

### Running the Example

You can build and run the example (on *nix or Mac) as follows:

{% highlight bash %}
$ cd $EXAMPLES_DIR/simple-messaging-pubsub
$ mvn clean install
$ cd brooklyn-example-simple-messaging-pubsub
$ ./demo-broker.sh
{% endhighlight %}

Now, visit the the Brooklyn web console on port 8081 using credentials admin/password.
This allows you to view the Brooklyn entities and their current state for debugging.

Note that the installation may take some time, because the default deployment downloads
the software from the official repos.  You can monitor start-up activity for each entity
in the ``Activity`` pane in the management console, and see more detail by tailing the
log file (``tail -f brooklyn.log``).

After starting up, the demo script should display a summary of all the Brooklyn managed
entities and their attributes. Included in this output is the broker URL, which
can be used to connect JMS clients to the broker. This URL can also be found
as a sensor attribute in the web console, named _broker.url_. This sensor is
common to _all_ messaging brokers that Brooklyn provides, and is usually accessed
by applications to allow them to provide it as a parameter to other entities, as
shown in the fragment below.

{% highlight java %}
    String url = broker.getAttribute(MessageBroker.BROKER_URL)
{% endhighlight %}

Using the URL the demo script printed, you can run the test ``Publish`` and ``Subscribe``
classes, to send messages using the broker. Simply run the scripts in another window,
with the provided URL as the only argument. Note that the URLs may be different to those
printed below, and that any unquoted ``&`` characters *must* be escaped.

{% highlight bash %}
$ ./publish.sh "amqp://guest:guest@/localhost?brokerlist='tcp://localhost:5672?tcp_nodelay='true''&maxprefetch='1'"
$ ./subscribe.sh "amqp://guest:guest@/localhost?brokerlist='tcp://localhost:5672?tcp_nodelay='true''&maxprefetch='1'"
{% endhighlight %}

In the _Publish_ window you should see a log message evry time a message is sent, like this:

{% highlight bash %}
2012-05-02 14:04:38,521 INFO  Sent message 65
2012-05-02 14:04:39,522 INFO  Sent message 66
{% endhighlight %}

Similarly, the _Subscribe_ windows should log on reciept of these messages, as follows:

{% highlight bash %}
2012-05-02 14:04:32,522 INFO  got message 41 test message 41
2012-05-02 14:04:33,523 INFO  got message 42 test message 42
{% endhighlight %}

### Cloud Deployment

With appropriate setup (as described [here]({{ site.url }}/use/guide/management/index.html#startup-config)) 
this can also be deployed to your favourite cloud, let's pretend it's Amazon Ireland, as follows: 

{% highlight bash %}
$ ./demo-broker.sh aws-ecs:eu-west-1
{% endhighlight %}

If you encounter any difficulties, please [tell us]({{ site.url }}/meta/contact.html) and we'll do our best to help.
