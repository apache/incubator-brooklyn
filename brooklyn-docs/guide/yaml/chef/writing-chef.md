---
title: Writing Chef for Blueprints
title_in_menu: Writing Chef for Blueprints
layout: website-normal
---

## Making it Simpler

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


## Using Chef Server

The examples so far have not required Chef Server, so they will work without any external
Chef dependencies (besides the built-in install from `https://www.opscode.com/chef/install.sh`
and the explicitly referenced cookbooks).  If you use Chef Server, however, you'll want your
managed nodes to be integrated with it.  This is easy to set up, with a few options:

If you have `knife` set up in your shell environment, the Brooklyn Chef support will use it
by default. If the recipes are installed in your Chef server, you can go ahead and remove
the `cookbooks_url` section!

Use of `solo` or `knife` can be forced by setting the `chef_mode` flag (`brooklyn.chef.mode` config key)
to either of those values.  (It defaults to `autodetect`, which will use `knife` if it is on the path and satisfies
sanity checks).

If you want to specify a different configuration, there are a number of config keys you can use:

* `brooklyn.chef.knife.executableFile`: this should be point to the knife binary to use
* `brooklyn.chef.knife.configFile`: this should point to the knife configuration to use
* `brooklyn.chef.knife.setupCommands`: an optional set of commands to run prior to invoking knife,
  for example to run `rvm` to get the right ruby version on the Brooklyn server

If you're interested in seeing the Chef REST API be supported directly (without knife),
please let us know.  We'd like to see this too, and we'll help you along the way!
 

## Tips and Tricks

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

