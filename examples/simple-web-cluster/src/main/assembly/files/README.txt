Brooklyn Example
================

To use, configure your cloud credentials then run  ./start.sh  in this directory.
You can then access the management context in your browser, typically on  localhost:8081.


### Cloud Credentials

To run, you'll need to specify credentials for your preferred cloud.  This can be done 
in `~/.brooklyn/brooklyn.properties`:

    brooklyn.jclouds.aws-ec2.identity=AKXXXXXXXXXXXXXXXXXX
    brooklyn.jclouds.aws-ec2.credential=secret01xxxxxxxxxxxxxxxxxxxxxxxxxxx

Alternatively these can be set as shell environment parameters or JVM system properties.

Many other clouds are supported also, as well as pre-existing machines ("bring your own nodes"),
custom endpoints for private clouds, and specifying custom keys and passphrases.
For more information see:

    https://github.com/brooklyncentral/brooklyn/blob/master/docs/use/guide/defining-applications/common-usage.md#off-the-shelf-locations


### Run

Usage:

    ./start.sh [--port 8081+] location

Where location might be `localhost` (the defaul), or `aws-ec2:us-east-1`, `openstack:endpoint`, etc.
