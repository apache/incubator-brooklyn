brooklyn-sample
===

This is a sample application which is launched and managed by Brooklyn.
This README file is at the root of the built assembly, which can be uploaded
and run most anywhere.  This file provides end-user instructions.

To use this, configure any cloud credentials then run  ./start.sh  in this 
directory. You can then access the management context in your browser, 
typically on  http://localhost:8081 , and through that console deploy the
applications contained in this archive.


### Cloud Credentials

To run, you'll need to specify credentials for your preferred cloud.  This 
can be done in `~/.brooklyn/brooklyn.properties`.

A recommended starting point is the file at:

    http://brooklyncentral.github.io/use/guide/quickstart/brooklyn.properties

As a quick-start, you can specify:

    brooklyn.location.jclouds.aws-ec2.identity=AKXXXXXXXXXXXXXXXXXX
    brooklyn.location.jclouds.aws-ec2.credential=secret01xxxxxxxxxxxxxxxxxxxxxxxxxxx
    
    brooklyn.location.named.My_Amazon_US_west=jclouds:aws-ec2:us-west-1
    brooklyn.location.named.My_Amazon_EU=jclouds:aws-ec2:eu-west-1

Alternatively these can be set as shell environment parameters or JVM system properties.

Many other clouds are supported also, as well as pre-existing machines 
("bring your own nodes"), custom endpoints for private clouds, and specifying 
custom keys and passphrases. For more information see:

    http://brooklyncentral.github.io/use/guide/defining-applications/common-usage#locations


### Run

Usage:

    ./start.sh [ <command> ] \
        [--port 8081+] \
        [--location location] ]

The optional port argument specifies the port where the Brooklyn console 
will be running, such as localhost:8081. (The console is only bound to 
localhost, unless you set up users and security, as described at
http://brooklyncentral.github.io/use/guide/management/ .)

In the console, you can access the catalog and deploy applications to
configured locations.

Valid commands in this sample application include:

* help
* server - brooklyn server only (no application)
* simple - a single web server
* cluster - an elastic cluster with a database
 
The location might be `localhost` (the default), `aws-ec2:us-east-1`, 
`openstack:endpoint`, `softlayer`, or based on names, such as
`named:My_Amazon_US_west`, so long as it is configured as above. 
(Localhost does not need any configuration in `~/brooklyn/brooklyn.properties`, 
provided you have password-less ssh access enabled to localhost, using a 
private key with no passphrase.) 


### More About Brooklyn

Brooklyn is a code library and framework for managing applications in a 
cloud-first dev-ops-y way.  It has been used to create this sample project 
which shows how to define an application and entities for Brooklyn.

This project can be extended for more complex topologies and more 
interesting applications, and to develop the policies to scale or tune the 
deployment depending on what the application needs.

For more information consider:

* Visiting the open-source Brooklyn home page at  http://brooklyncentral.github.com
* Forking the Brooklyn project at  http://github.com/brooklyncentral/brooklyn
* Emailing  brooklyn-users@googlegroups.com 

For commercial enquiries -- including bespoke development and paid support --
contact Cloudsoft, the supporters of Brooklyn, at:

* www.CloudsoftCorp.com
* info@cloudsoftcorp.com

Brooklyn is (c) 2013 Cloudsoft Corporation and released as open source under 
the Apache License v2.0.

A sample Brooklyn project should specify its license.
