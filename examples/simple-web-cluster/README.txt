Instructions for Running Examples
=================================

The commands below assume that the `brooklyn` script is on your $PATH, this project has been built,
and you are in this directory.  Adjust to taste for other configurations.

  export BROOKLYN_CLASSPATH=$(pwd)/target/classes
  
  # Three-tier: auto-scaling app-server cluster fronted by nginx, MySql backend wired up, on localhost
  brooklyn launch --app brooklyn.demo.WebClusterDatabaseExample --location localhost

The above requires passwordless `ssh localhost` and requires `gcc` to build `nginx`.
You could instead target your favourite cloud, e.g. `--location aws-ec2:eu-west-1`.

Other examples:

  # Same three-tier, but in Amazon California (location arg can be changed for any example, of course)
  brooklyn launch --app brooklyn.demo.WebClusterDatabaseExample --location aws-ec2:us-west-1

  # Very simple: an app with a single web-server
  brooklyn launch --app brooklyn.demo.SingleWebServerExample --location localhost

  # Very simple Brooklyn App, but more interesting results: a cluster of app-servers with nginx
  brooklyn launch --app brooklyn.demo.WebClusterExample --location localhost

  # The three tier example, but written in pure Java
  brooklyn launch --app brooklyn.demo.WebClusterDatabaseExampleAltJava --localhost localhost


For more information, please visit:

  http://brooklyncentral.github.com/use/examples/webcluster/

