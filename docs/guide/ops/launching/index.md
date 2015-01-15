---
title: Launching Brooklyn
layout: website-normal
---

There are several ways to launch applications with brooklyn, and useful configuration options,
as well as a debug-view web console.

This chapter describes how to launch brooklyn.


Startup Configuration
---------------------

brooklyn can read configuration from a variety of places:

* the file ``~/.brooklyn/brooklyn.properties``
* ``-D`` defines on the brooklyn (java) command-line
* shell environment variables

Default properties are described in the Javadoc and code of the class ``BrooklynProperties``,
but some of the most common ones are:
 
{% highlight properties %}
brooklyn.location.jclouds.aws-ec2.identity=AKA50M30N3S1DFR0MAW55
brooklyn.location.jclouds.aws-ec2.credential=aT0Ps3cr3tC0D3wh1chAW5w1llG1V3y0uTOus333
brooklyn.location.jclouds.aws-ec2.privateKeyFile=~/path/to/id_rsa       # use specified key (default is ~/.ssh/id_rsa)
brooklyn.location.jclouds.aws-ec2.publicKeyFile=~/path/to/id_rsa.pub    # (optional, inferred from previous if omitted)
{% endhighlight %} 

These can be specified as a shell environment variable or as a Java system property,
although in those contexts the conventional format ``BROOKLYN_JCLOUDS_AWS_EC2_IDENTITY`` 
is supported and recommended.


Command Line Interface
----------------------

Brooklyn comes with a Command Line Interface (cli) that makes it easier to launch an application.

In order to have easy access to the cli it is useful to configure the PATH environment variable to also point to the cli's bin directory:

{% highlight bash %}
BROOKLYN_HOME=/path/to/brooklyn/
export PATH=$PATH:$BROOKLYN_HOME/usage/dist/target/brooklyn-dist/bin/
{% endhighlight %}

If you have set this up correctly you should be able to invoke the ```brooklyn``` command:

{% highlight bash %}
brooklyn
{% endhighlight %}

To get information about all the supported cli options just run:

{% highlight bash %}
brooklyn help
usage: brooklyn [(-q | --quiet)] [(-v | --verbose)] <command> [<args>]

The most commonly used brooklyn commands are:
    help     Display help information about brooklyn
    info     Display information about brooklyn
    launch   Starts a brooklyn application. Note that a BROOKLYN_CLASSPATH environment variable needs to be set up beforehand to point to the user application classpath.

See 'brooklyn help <command>' for more information on a specific command.
{% endhighlight %}

Here is an example of the commands you might run to get the Brooklyn code, compile it and launch an application:

{% highlight bash %}
git clone https://github.com/apache/incubator-brooklyn.git
cd brooklyn
mvn clean install -DskipTests
BROOKLYN_HOME=$(pwd)
export PATH=${PATH}:${BROOKLYN_HOME}/usage/dist/target/brooklyn-dist/bin/
export BROOKLYN_CLASSPATH=${BROOKLYN_HOME}/examples/simple-web-cluster/target/classes
brooklyn launch --app brooklyn.demo.SingleWebServerExample --location localhost
{% endhighlight %}

You can add things to the brooklyn classpath in a number of ways:

* Add ``.jar`` files to brooklyn's ``./lib/dropins/`` directory. These are added at the end of the classpath.
* Add ``.jar`` files to brooklyn's ``./lib/patch/`` directory. These are added at the front of the classpath.
* Add resources to brooklyn's ``./conf/`` directory. This directory is at the very front of the classpath.
* Use the ``BROOKLYN_CLASSPATH`` environment variable. If set, this is prepended to the brooklyn classpath.


