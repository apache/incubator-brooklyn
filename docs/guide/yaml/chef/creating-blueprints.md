---
title: Creating Blueprints from Chef
title_in_menu: Creating Blueprints from Chef
layout: website-normal
---

In a nutshell, a new Chef-based entity can be defined as a service by specifying
`chef:cookbook_name` as the `service_type`, along with a collection of optional configuration.
An illustrative example is below:

{% highlight yaml %}
{% readj example_yaml/mysql-chef-1.yaml %}
{% endhighlight %}

*This works without any installation: try it now, copying-and-pasting to the Brooklyn console.
(Don't forget to add your preferred `location: some-cloud` to the spec.)*  

Notice, if you target `google-compute-engine` location, you may need to specify `bind_address: 0.0.0.0` for the `mysql` cookbook, as described [here](https://github.com/chef-cookbooks/mysql/blob/46dccac22d282a05ee6a401e10ae8f5f8114fd66/README.md#parameters).

We'll now walk through the important constituent parts,
and then proceed to describing things which can be done to simplify the deployment.


### Cookbook Primary Name

The first thing to note is the type definition:

    - type: chef:mysql

This indicates that the Chef entity should be used (`org.apache.brooklyn.entity.chef.ChefEntity`) 
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
The name of that folder does not matter, as often they contain version information.
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
file is running. This has been selected for mysql because it appears to be more portable:
the service name varies among OS's:  it is `mysqld` on CentOS but `mysql` on Ubuntu!



