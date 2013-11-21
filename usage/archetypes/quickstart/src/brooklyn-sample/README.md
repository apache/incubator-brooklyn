brooklyn-sample
===

This is a Sample Brooklyn project, showing how to define an application
which Brooklyn will deploy and manage.

To build an assembly, simply run:

    mvn clean assembly:assembly

This creates a tarball with a full standalone application which can be installed in any *nix machine at:
    target/brooklyn-datastax-healthcare-0.1.0-SNAPSHOT-dist.tar.gz

It also installs an unpacked version which you can run locally:
 
     cd target/brooklyn-datastax-healthcare-0.1.0-SNAPSHOT-dist/brooklyn-datastax-healthcare-0.1.0-SNAPSHOT
     ./start.sh server
 
For more information see the README (or `./start.sh help`) in that directory.
On OS X and Linux, this application will deploy to localhost *if* you have key-based 
password-less (and passphrase-less) ssh enabled.

To configure cloud and fixed-IP locations, see the README file in the built application directly.
For more information you can run `./start.sh help`) in that directory.


### Opening in an IDE

To open this project in an IDE, you will need maven support enabled
(e.g. with the relevant plugin).  You should then be able to develop
it and run it as usual.  For more information on IDE support, visit:

    http://brooklyncentral.github.io/dev/build/ide.html


### Customizing the Assembly

The artifacts (directory and tar.gz by default) which get built into
`target/` can be changed.  Simply edit the relevant files under
`src/main/assembly`.

You will likely wish to customize the `SampleMain` class as well as
the `Sample*App` classes provided.  That is the intention!
You will also likely want to update the `start.sh` script and
the `README.*` files.

To easily find the bits you should customize, do a:

    grep -ri sample src/ *.*


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
