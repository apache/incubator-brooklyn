---
title: CLI
layout: website-normal
---

To launch Brooklyn, from the directory where Brooklyn is unpacked, run:

{% highlight bash %}
% bin/brooklyn launch
{% endhighlight %}

With no configuration, this will launch the Brooklyn web console and REST API on [`http://localhost:8081/`](http://localhost:8081/). No password is set, but the server is listening only on the loopback network interface for security. Once [security is configured](brooklyn_properties.html), Brooklyn will listen on all network interfaces by default.

You may wish to [add Brooklyn to your path](#path-setup); assuming you've done this, to get information the supported CLI options at any time, just run `brooklyn help`:

{% highlight bash %}
% bin/brooklyn help

usage: brooklyn [(-q | --quiet)] [(-v | --verbose)] <command> [<args>]

The most commonly used brooklyn commands are:
    help     Display help information about brooklyn
    info     Display information about brooklyn
    launch   Starts a brooklyn application. Note that a BROOKLYN_CLASSPATH environment variable needs to be set up beforehand to point to the user application classpath.

See 'brooklyn help <command>' for more information on a specific command.
{% endhighlight %}


## Configuration

Brooklyn can read configuration from a variety of places:
        
* the file `~/.brooklyn/brooklyn.properties` (unless `--noGlobalBrooklynProperties` is specified)
* another file, if the `--localBrooklynProperties <local brooklyn.properties file>`
* ``-D`` defines on the brooklyn (java) command-line
* shell environment variables

These properties are described in more detail [here](brooklyn_properties.html).


## Path Setup

In order to have easy access to the CLI it is useful to configure the PATH environment variable to also point to the CLI's bin directory:

{% highlight bash %}
BROOKLYN_HOME=/path/to/brooklyn/
export PATH=$PATH:$BROOKLYN_HOME/usage/dist/target/brooklyn-dist/bin/
{% endhighlight %}


## Running from a Source Build

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


## Extending the Classpath

You can add things to the Brooklyn classpath in a number of ways:

* Add ``.jar`` files to Brooklyn's ``./lib/dropins/`` directory. These are added at the end of the classpath.
* Add ``.jar`` files to Brooklyn's ``./lib/patch/`` directory. These are added at the front of the classpath.
* Add resources to Brooklyn's ``./conf/`` directory. This directory is at the very front of the classpath.
* Use the ``BROOKLYN_CLASSPATH`` environment variable. If set, this is prepended to the Brooklyn classpath.


## Cloud Explorer

The `brooklyn` command line tool includes support for querying (and managing) cloud compute resources and blob-store resources. 

For example, `brooklyn cloud-compute list-instances --location aws-ec2:eu-west-1` will use the AWS credentials from `brooklyn.properties` and list the VM instances running in the given EC2 region.

Use `brooklyn help` and `brooklyn help cloud-compute` to find out more information.

This functionality is not intended as a generic cloud management CLI, but instead solves specific Brooklyn use-cases. The main use-case is discovering the valid configuration options on a given cloud, such as for `imageId` and `hardwareId`.


### Cloud Compute

The command `brooklyn cloud-compute` has the following options:

* `list-images`: lists VM images within the given cloud, which can be chosen when provisioning new VMs. This is useful for finding the possible values for the `imageId` configuration.

* `get-image <imageId1> <imageId2> ...`: retrieves metadata about the specific images.

* `list-hardware-profiles`: lists the IDs and the details of the hardware profiles available when provisioning. This is useful for finding the possible values for the `hardwareId` configuration.

* `default-template`: retrieves metadata about the image and hardware profile that will be used by Brooklyn for that location, if no additional configuration options are supplied.

* `list-instances`: lists the VM instances within the given cloud.

* `terminate-instances <instanceId1> <instanceId2> ...`: Terminates the instances with the given IDs.


## Blob Store

The command `brooklyn cloud-blobstore` is used to access a given object store, such as S3
or Swift. It has the following options:

* `list-containers`: lists the containers (i.e. buckets in S3 terminology) within the given object store.

* `list-container <containerName>`: lists all the blobs (i.e. objects) contained within the given container.

* `blob --container <containerName> --blob <blobName>`: retrieves the given blob (i.e. object), including metadata and its contents.


## Other Topics

The CLI arguments for [persistence and HA](persistence/) are described separately, as is [detailed configuration](brooklyn_properties.html).
