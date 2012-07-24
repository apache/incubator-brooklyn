Instructions for running examples
=================================

The commands below assume that the `brooklyn` script is already on your $PATH, and you are in the examples directory:

  EXAMPLES_HOME=$(pwd)
  export BROOKLYN_CLASSPATH=$EXAMPLES_HOME/simple-web-cluster/target/brooklyn-example-simple-web-cluster-0.4.0-SNAPSHOT.jar
  
  # An app with a single web-server
  brooklyn launch --app brooklyn.demo.SingleWebServerExample --location localhost

  # A cluster of web-servers
  brooklyn launch --app brooklyn.demo.WebClusterExample --location localhost
  
  # A cluster of web-servers, plus MySql
  brooklyn launch --app brooklyn.demo.WebClusterDatabaseExample --location localhost

---

For more information, please visit: http://brooklyncentral.github.com/use/examples/webcluster/
