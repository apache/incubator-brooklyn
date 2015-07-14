---
title: Launching Brooklyn
layout: website-normal
---

## Launch command

To launch Brooklyn, from the directory where Brooklyn is unpacked, run:

{% highlight bash %}
% nohup bin/brooklyn launch > /dev/null 2&>1 &
{% endhighlight %}

With no configuration, this will launch the Brooklyn web console and REST API on [`http://localhost:8081/`](http://localhost:8081/).
No password is set, but the server is listening only on the loopback network interface for security.
Once [security is configured](brooklyn_properties.html), Brooklyn will listen on all network interfaces by default.
By default, Brooklyn will write log messages at the INFO level or above to `brooklyn.info.log` and messgages at the
DEBUG level or above to `brooklyn.debug.log`. Redirecting the output to `/dev/null` prevents the default console output
being written to `nohup.out`.

You may wish to [add Brooklyn to your path](#path-setup);
assuming you've done this, to get information the supported CLI options 
at any time, just run `brooklyn help`:

{% highlight bash %}
% bin/brooklyn help

usage: brooklyn [(-q | --quiet)] [(-v | --verbose)] <command> [<args>]

The most commonly used brooklyn commands are:
    help     Display help information about brooklyn
    info     Display information about brooklyn
    launch   Starts a brooklyn application. Note that a BROOKLYN_CLASSPATH environment variable needs to be set up beforehand to point to the user application classpath.

See 'brooklyn help <command>' for more information on a specific command.
{% endhighlight %}

It is important that Brooklyn is launched with either `nohup ... &` or `... & disown`, to ensure 
it keeps running after the shell terminates.


### Other CLI Arguments

The CLI arguments for [persistence and HA](persistence/) are described separately.


### Path Setup

In order to have easy access to the cli it is useful to configure the PATH environment 
variable to also point to the cli's bin directory:

{% highlight bash %}
BROOKLYN_HOME=/path/to/brooklyn/
export PATH=$PATH:$BROOKLYN_HOME/usage/dist/target/brooklyn-dist/bin/
{% endhighlight %}


### Memory Usage

The amount of memory required by the Brooklyn process depends on the usage 
- for example the number of entities/VMs under management.

For a standard Brooklyn deployment, the defaults are to start with 256m, and to grow to 1g of memory.
These numbers can be overridden by setting the environment variable `JAVA_OPTS` before launching
the `brooklyn script`:

    JAVA_OPTS=-Xms1g -Xmx1g -XX:MaxPermSize=256m

Brooklyn stores a task history in-memory using [soft references](http://docs.oracle.com/javase/7/docs/api/java/lang/ref/SoftReference.html).
This means that, once the task history is large, Brooklyn will continually use the maximum allocated 
memory. It will only expunge tasks from memory when this space is required for other objects within the
Brooklyn process.


## Configuration

### Configuration Files

Brooklyn reads configuration from a variety of places. It aggregates the configuration.
The list below shows increasing precedence (i.e. the later ones will override values
from earlier ones, if exactly the same property is specified multiple times).

1. `classpath://brooklyn/location-metadata.properties` is shipped as part of Brooklyn, containing 
   generic metadata such as jurisdiction and geographic information about Cloud providers.        
1. The file `~/.brooklyn/location-metadata.properties` (unless `--noGlobalBrooklynProperties` is specified).
   This is intended to contain custom metadata about additional locations.
1. The file `~/.brooklyn/brooklyn.properties` (unless `--noGlobalBrooklynProperties` is specified).
1. Another properties file, if the `--localBrooklynProperties <local brooklyn.properties file>` is specified.
1. Shell environment variables
1. System properties, supplied with ``-D`` on the brooklyn (Java) command-line.

These properties are described in more detail [here](brooklyn_properties.html).


### Extending the Classpath

The default Brooklyn directory structure includes:

* `./conf/`: for configuration resources.
* `./lib/patch/`: for Jar files containing patches.
* `./lib/brooklyn/`: for the brooklyn libraries.
* `./lib/dropins/`: for additional Jars.

Resources added to `conf/` will be available on the classpath.

A patch can be applied by adding a Jar to the `lib/patch/` directory, and restarting Brooklyn.
All jars in this directory will be at the head of the classpath.

Additional Jars should be added to `lib/dropins/`, prior to starting Brooklyn. These jars will 
be at the end of the classpath.

The initial classpath, as set in the `brooklyn` script, is:

    conf:lib/patch/*:lib/brooklyn/*:lib/dropins/*

Additional entries can be added at the head of the classpath by setting the environment variable 
`BROOKLYN_CLASSPATH` before running the `brooklyn` script. 


### Replacing the web-console

*Work in progress.*

The Brooklyn web-console is loaded from the classpath as the resource `classpath://brooklyn.war`.

To replace this, an alternative WAR with that name can be added at the head of the classpath.
However, this approach is likely to change in a future release - consider this feature as "beta".


## Cloud Explorer

The `brooklyn` command line tool includes support for querying (and managing) cloud
compute resources and blob-store resources. 

For example, `brooklyn cloud-compute list-instances --location aws-ec2:eu-west-1`
will use the AWS credentials from `brooklyn.properties` and list the VM instances
running in the given EC2 region.

Use `brooklyn help` and `brooklyn help cloud-compute` to find out more information.

This functionality is not intended as a generic cloud management CLI, but instead 
solves specific Brooklyn use-cases. The main use-case is discovering the valid 
configuration options on a given cloud, such as for `imageId` and `hardwareId`.


### Cloud Compute

The command `brooklyn cloud-compute` has the following options:

* `list-images`: lists VM images within the given cloud, which can be chosen when
  provisioning new VMs.
  This is useful for finding the possible values for the `imageId` configuration.

* `get-image <imageId1> <imageId2> ...`: retrieves metadata about the specific images.

* `list-hardware-profiles`: lists the ids and the details of the hardware profiles
  available when provisioning. 
  This is useful for finding the possible values for the `hardwareId` configuration.

* `default-template`: retrieves metadata about the image and hardware profile that will
  be used by Brooklyn for that location, if no additional configuration options
  are supplied.

* `list-instances`: lists the VM instances within the given cloud.

* `terminate-instances <instanceId1> <instanceId2> ...`: Terminates the instances with
  the given ids.


### Blob Store

The command `brooklyn cloud-blobstore` is used to access a given object store, such as S3
or Swift. It has the following options:

* `list-containers`: lists the containers (i.e. buckets in S3 terminology) within the 
  given object store.

* `list-container <containerName>`: lists all the blobs (i.e. objects) contained within 
  the given container.

* `blob --container <containerName> --blob <blobName>`: retrieves the given blob
  (i.e. object), including metadata and its contents.

  
## Running from a Source Build

Here is an example of the commands you might run to get the Brooklyn code, 
compile it and launch an application:

{% highlight bash %}
git clone https://github.com/apache/incubator-brooklyn.git
cd brooklyn
mvn clean install -DskipTests
BROOKLYN_HOME=$(pwd)
export PATH=${PATH}:${BROOKLYN_HOME}/usage/dist/target/brooklyn-dist/bin/
export BROOKLYN_CLASSPATH=${BROOKLYN_HOME}/examples/simple-web-cluster/target/classes
nohup brooklyn launch --app brooklyn.demo.SingleWebServerExample --location localhost &
{% endhighlight %}


  
