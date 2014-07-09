Instructions for Running Examples
=================================

The commands below assume that the `brooklyn` script is on your $PATH, this project has been built,
and you are in this directory.  Adjust to taste for other configurations.

  export BROOKLYN_CLASSPATH=$(pwd)/target/classes
  
  # Three-tier: auto-scaling app-server cluster fronted by nginx, MySql backend wired up, on localhost
  brooklyn launch --app brooklyn.demo.WebClusterDatabaseExample --location localhost

The above requires passwordless `ssh localhost` and requires `gcc` to build `nginx`.
You could instead target your favourite cloud, where this has been tried and tested:

  # Same three-tier, but in Amazon California, prompting for credentials
  # (the location arg can be changed for any example, of course, and other clouds are available)
  export JCLOUDS_AWS_EC2_IDENTITY=AKA50M30N3S1DFR0MAW55
  export JCLOUDS_AWS_EC2_CREDENTIAL=aT0Ps3cr3tC0D3wh1chAW5w1llG1V3y0uTOus333
  brooklyn launch --app brooklyn.demo.WebClusterDatabaseExample --location aws-ec2:us-west-1


Other examples:

  # A very simple app: a single web-server
  brooklyn launch --app brooklyn.demo.SingleWebServerExample --location localhost

  # A simple app: just load-balancer and appservers
  brooklyn launch --app brooklyn.demo.WebClusterExample --location localhost

  # Three-tier example
  brooklyn launch --app brooklyn.demo.WebClusterDatabaseExample --location localhost


Redistributable embedded example:

  # To build a redistributable tar.gz with a start.sh script
  # which invokes the `main` method in the example class to start
  # (the redistributable will be at:  target/brooklyn-*-bin.tar.gz )
  mvn clean assembly:assembly

For more information, please visit:

  http://brooklyncentral.github.com/use/examples/webcluster/

