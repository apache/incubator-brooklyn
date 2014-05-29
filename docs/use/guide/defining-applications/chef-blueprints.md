---
title: Using Chef in YAML Blueprints
layout: page
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

This guide describes how Brooklyn entities can be easily created from Chef cookbooks.
As of this writing (May 2014) some of the integration points are under active development,
and comments are welcome.
A plan for the full integration is online [here](https://docs.google.com/a/cloudsoftcorp.com/document/d/18ZwzmncbJgJeQjnSvMapTWg6N526cvGMz5jaqdkxMf8).  

This guide assumes you are familiar with the basics of [creating YAML blueprints](creating-yaml.html).


## Creating Blueprints from Chef

In a nutshell, a new Chef-based entity can be defined as a service by specifying
`chef:cookbook_name` as the `service_type`, along with a collection of optional configuration.
An illustrative example is below:

{% highlight yaml %}
{% readj example_yaml/mysql-chef-1.yaml %}
{% endhighlight %}

*This works without any installation: try it now, copying-and-pasting to the Brooklyn console.
(Don't forget to add your preferred `location: some-cloud` to the spec.)*  

We'll now walk through the important constituent parts,
and then proceed to describing things which can be done to simplify the deployment.


### Cookbook Primary Name

The first thing to note is the type definition:

    - type: chef:mysql

This indicates that the Chef entity should be used (`brooklyn.entity.chef.ChefEntity`) 
to interpret and pass the configuration,
and that it should be parameterised with a `brooklyn.chef.cookbook.primary.name` of `mysql`.
This is the cookbook namespace used by default for determining what to install and run.


### Importing Cookbooks

Next we specify which cookbooks are required and where they can be pulled from:

      cookbook_urls:
        mysql: https://github.com/opscode-cookbooks/mysql/archive/v4.0.12.tar.gz
        openssl: https://github.com/opscode-cookbooks/openssl/archive/v1.1.0.tar.gz
        build-essential: https://github.com/opscode-cookbooks/build-essential/archive/v1.4.4.tar.gz

Here, specific versions are being downloaded from the canonical github repository.
Any URL can be used, so long as it is resolvable on either the target machine or the
Brooklyn server; this includes `file:` and `classpath:` URLs.

The archive can be ZIP or TAR or TGZ.

The structure of the archive must be that a single folder is off the root,
and in that folder contains the usual Chef recipe and auxiliary files.
For example, the archive might contain `mysql-master/recipes/server.rb`.
Archives such as those above from github match this format.  
The name of the that folder does not matter, as often they contain version information.
When deployed, these will be renamed to match the short name (the key in the `cookbooks_url` map,
for instance `mysql` or `openssl`).

If Chef server is configured (see below), this section can be omitted.


### Launch Run List and Attributes

The next part is to specify the Chef run list and attributes to store when launching the entity: 

      launch_run_list:
      - mysql::server
      
      launch_attributes:
        mysql:
          server_root_password: p4ssw0rd
          server_repl_password: p4ssw0rd
          server_debian_password: p4ssw0rd

For the `launch_run_list`, you can use either the YAML `- recipe` syntax or the JSON `[ "recipe" ]` syntax.

The `launch_attributes` key takes a map which will be stored against the `node` object in Chef.
Thus in this example, the parameter `node['mysql']['server_root_password']` required by the mysql blueprint
is set as specified.

You can of course set many other attributes in this manner, in addition to those that are required!  


### Simple Monitoring

The final section determines how Brooklyn confirms that the service is up.
Sophisticated solutions may install monitoring agents as part of the `launch_run_list`,
with Brooklyn configured to read monitoring information to confirm the launch was successful.
However for convenience, two common mechanisms are available out of the box:

      #service_name: mysqld
      pid_file: /var/run/mysqld/mysqld.pid

If `service_name` is supplied, Brooklyn will check the return code of the `status` command
run against that service, ensuring it is 0.  (Note that this is not universally reliable,
although it is the same mechanism which Chef typically uses to test status when determining
whether to start a service. Some services, e.g. postgres, will return 0 even if the service
is not running.)

If a `pid_file` is supplied, Brooklyn will check whether a process with the PID specified in that
file is running. This has been selected for mysql because it is appears to be more portable:
the service name varies among OS's:  it is `mysqld` on CentOS but `mysql` on Ubuntu!


## Making it Simpler: Writing Chef for Blueprints

The example we've just seen shows how existing Chef cookbooks can be
used as the basis for entities.  If you're *writing* the Chef recipes, 
there are a few simple techniques we've established with the Chef community
which make blueprints literally as simple as:

    - type: chef:mysql
      brooklyn.config:
        mysql_password: p4ssw0rd
        pid_file: /var/run/mysqld/mysqld.pid


### Some Basic Conventions

* **A `start` recipe**:
  The first step is to provide a `start` recipe in `recipes/start.rb`;
  if no `launch_run_list` is supplied, this is what will be invoked to launch the entity.
  It can be as simple as a one-line file:

      include_recipe 'mysql::server'

* **Using `brooklyn.config`**:
  All the `brooklyn.config` is passed to Chef as node attributes in the `node['brooklyn']['config']` namespace.
  Thus if the required attributes in the mysql recipe are set to take a value set in
  `node['brooklyn']['config']['mysql_password']`, you can dispense with the `launch_attributes` section.


### Using Chef Server

The examples so far have not required Chef Server, so they will work without any external
Chef dependencies (besides the built-in install from `https://www.opscode.com/chef/install.sh`
and the explicitly referenced cookbooks).  If you use Chef Server, however, you'll want your
managed nodes to be integrated with it.  This is easy to set up, with a few options:

* **Option 1: Knife Shell Environment**

  If you have `knife` set up in your shell environment, the Brooklyn Chef support will use it
  by default. If the recipes are installed in your Chef server, you can go ahead and remove
  the `cookbooks_url` section!
  
  Use of `solo` or `knife` can be forced by setting the `chef_mode` flag (`brooklyn.chef.mode` config key)
  to either of those values.  (It defaults to `autodetect`.)

* **Option 2: Configuring Knife**

  If `knife` is not configured by default, or you want to specify a different configuration,
  there are a number of config keys you can use:
  
  * `brooklyn.chef.knife.executableFile`: this should be point to the knife binary to use
  * `brooklyn.chef.knife.configFile`: this should point to the knife configuration to use
  * `brooklyn.chef.knife.setupCommands`: an optional set of commands to run prior to invoking knife,
    for example to run `rvm` to get the right ruby version on the Brooklyn server

If you're interested in seeing the Chef REST API be supported directly (without knife),
please let us know.  We'd like to see this too, and we'll help you along the way!
 

### Tips and Tricks

To help you on your way writing Chef blueprints, here are a handful of pointers
particularly useful in this context:

* Configuration keys can be inherited from the top-level and accessed using `$brooklyn:component('id').config('key_name')`.
  An example of this is shown in the `mysql-chef.yaml` sample recipe contained in the Brooklyn code base
  and [here](example_yaml/mysql-chef-2.yaml) for convenience.
  Here, `p4ssw0rd` is specified only once and then used for all the attributes required by the stock mysql cookbook.  

* Github tarball downloads! You'll have noticed these in the example already, but they are so useful we thought
  we'd call them out again. Except when you're developing, we recommend using specific tagged versions rather than master.

* The usual machine `provisioning.properties` are supported with Chef blueprints, 
  so you can set things like `minRam` and `osFamily`

* To see more configuration options, and understand the ones presented here in more detail, see the javadoc or
  the code for the class `ChefConfig` in the Brooklyn code base.


## Advanced Chef Integration

### Adding Sensors and Effectors

Custom sensors and effectors can be added using an `entity.initializer` section in the YAML blueprint.

One common pattern is to have sensors which extract information from Ohai.
Another common pattern is to install a monitoring agent as part of the run list,
configured to talk to a monitoring store, and then to add a sensor feed which reads data from that store.

On the effector side, you can add SSH-based effectors in the usual way.
You can also describe additional chef converge targets following the pattern set down in
`ChefLifecycleEffectorTasks`, making use of conveniences in `ChefSoloTasks` and `ChefServerTasks`,
or provide effectors which invoke network API's of the systems under management
(for example to supply the common `executeScript` effector as on the standard `MySqlNode`). 
   

### Next Steps: Simpifying sensors and effectors, transferring files, and configuring ports

The Brooklyn-Chef integration is work in progress, with a few open issues we'd still like to add.
Much of the thinking for this is set forth in the [Google document](https://docs.google.com/a/cloudsoftcorp.com/document/d/18ZwzmncbJgJeQjnSvMapTWg6N526cvGMz5jaqdkxMf8)
indicated earlier.  If you'd like to work with us to implement these, please let us know.


