---
title: Creating from a Maven Archetype
layout: website-normal
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

### Maven Archetype

Brooklyn includes a maven archetype, which can be used to create the project structure for a new application.

This can be done interactively using:
{% highlight bash %}
$ mvn archetype:generate
{% endhighlight %}

The user will be prompted for the archetype to use (i.e. group "io.brooklyn" 
and artifact "brooklyn-archetype-quickstart"), as well as options for the project 
to be created.

Alternatively, all options can be supplied at the command line. For example, 
if creating a project named "autobrick" for "com.acme":

{% highlight bash %}
$ mvn archetype:generate \
	-DarchetypeGroupId=org.apache.brooklyn \
	-DarchetypeArtifactId=brooklyn-archetype-quickstart \
	-DarchetypeVersion={{ site.brooklyn-version }} \
	-DgroupId=com.acme -DartifactId=autobrick \
	-Dversion=0.1.0-SNAPSHOT \
	-DpackageName=com.acme.autobrick \
	-DinteractiveMode=false
{% endhighlight %}

This will create a directory with the artifact name (e.g. "autobrick" in the example above).
Note that if run from a directory containing a pom, it will also modify that pom to add this as a module!

The project will contain an example app. You can run this, and also replace it with your own
application code.

To build, run the commands:

{% highlight bash %}
$ cd autobrick
$ mvn clean install assembly:assembly
{% endhighlight %}

The assembly command will build a complete standalone distribution archive in `target/autobrick-0.1.0-SNAPSHOT-dist.tar.gz`,
suitable for redistribution and containing `./start.sh` in the root.

An unpacked equivalent is placed in `target/autobrick-0.1.0-SNAPSHOT-dist`,
thus you can run the single-node sample locally with:

{% highlight bash %}
$ cd target/autobrick-0.1.0-SNAPSHOT-dist/autobrick-0.1.0-SNAPSHOT/
$ ./start.sh launch --single
{% endhighlight %}

This `start.sh` script has all of the same options as the default `brooklyn` script, 
including `./start.sh help` and the `--location` argument for `launch`,
with a couple of extra `launch` options for the sample blueprints in the archetype project:

- `./start.sh launch --single` will launch a single app-server instance
- `./start.sh launch --cluster` will launch a cluster of app-servers
