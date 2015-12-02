---
title: Blueprinting Tips
layout: website-normal
---

## YAML Recommended

The recommended way to write a blueprint is as a YAML file. This is true both for building
an application out of existing blueprints, and for building new integrations.

The use of Java is reserved for those use-cases where the provisioning or configuration logic 
is very complicated.


## Faster Dev-Test

Writing a blueprint is most efficient and simple when testing is fast, and when testing is
done incrementally as features of the blueprint are written.

The slowest stages of deploying a blueprint are usually VM provisioning and downloading/installing
of artifacts (e.g. RPMs, zip files, etc).

Options for speeding up provisioning include those below.

### Deploying to the "localhost" location

This is fast and simple, but has some obvious downsides:

* Artifacts are installed directly on your desktop/server.

* The artifacts installed during previous runs can interfere with subsequent runs.

* Some entities require `sudo` rights, which must be granted to the user running Brooklyn.


### Deploying to Bring Your Own Nodes (BYON)

A BYON location can be defined, which avoids the time required to provision VMs. This is fast,
but has the downside that artifacts installed during a previous run can interfere with subsequent 
runs.

A variant of this is to use Vagrant (e.g. with VirtualBox) to create VMs on your local machine,
and to use these as the target for a BYON location.


### Deploying to Clocker

Docker containers provide a convenient way to test blueprints (and also to run blueprints in
production!).

The [Clocker project](www.clocker.io) allows the simple setup of Docker Engine(s), and for Docker
containers to be used instead of VMs. For testing, this allows each run to start from a fresh 
container (i.e. no install artifacts from previous runs), while taking advantage of the speed
of starting containers.


### Caching Install Artifacts

To avoid re-downloading install artifacts on every run, these can be saved to `~/.brooklyn/repository/`.
The file structure is a sub-directory with the entity's simple name, then a sub-directory with the
version number, and then the files to be downloaded. For example, 
`~/.brooklyn/repository/TomcatServer/7.0.56/apache-tomcat-7.0.56.tar.gz`.

If using BYON or localhost, the install artifacts will by default be installed to a directory like
`/tmp/brooklyn-myname/installs/`. If install completed successfully, then the install stage will 
be subsequently skipped. To re-test the install phase, delete the install directory (e.g. delete
`/tmp/brooklyn-myname/installs/TomcatServer_7.0.56/`).

Where installation used something like `apt-get install` or `yum install`, then re-testing the
install phase will require uninstalling these artifacts manually.


## Custom Entity Development

If writing a custom integration, the following recommendations may be useful:

* For the software to be installed, use its Installation and Admin guides to ensure best practices
  are being followed. Use blogs and advice from experts, when available.

* Where there is a choice of installation approaches, use the approach that is most appropriate for
  production use-cases (even if this is harder to test on locahost). For example, 
  prefer the use of RPMs versus unpacking zip files, and prefer the use of services versus invoking
  a `bin/start` script.

* Ensure every step is scriptable (e.g. manual install does not involve using a text editor to 
  modify configuration files, or clicking on things in a web-console).

* Write scripts (or Chef recipes, or Puppet manifests, etc), and test these by executing manually. 
  Only once these work in isolation, add them to the entity blueprint.

* Externalise the configuration where appropriate. For example, if there is a configuration file
  then include a config key for the URL of the configuration file to use. Consider using FreeMarker
  templating for such configuration files.

* Focus on a single OS distro/version first, and clearly document these assumptions.

* Breakdown the integration into separate components, where possible (and thus develop/test them separately). 
  For example, if deploying a MongoDB cluster then first focus on single-node MongoDB, and then make that
  configurable and composable for a cluster.

* Use the test framework to write test cases, so that others can run these to confirm that the 
  entity works in their environment.

* Where appropriate, share the new entity with the Brooklyn community so that it can be reviewed, 
  tested and improved by others in the community!
