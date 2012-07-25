Instructions for running examples
=================================

The commands below assume that the `brooklyn` script is already on your $PATH, and you are in the examples directory:

  export BROOKLYN_EXAMPLES_DIR=$(pwd)
  export BROOKLYN_CLASSPATH=${BROOKLYN_EXAMPLES_DIR}/simple-web-cluster/target/classes
  
  # An app with a single web-server
  brooklyn launch --app brooklyn.demo.SingleWebServerExample --location localhost

  # A cluster of web-servers
  brooklyn launch --app brooklyn.demo.WebClusterExample --location localhost
  
  # A cluster of web-servers, plus MySql
  brooklyn launch --app brooklyn.demo.WebClusterDatabaseExample --location localhost

---

For more information, please visit: http://brooklyncentral.github.com/use/examples/webcluster/
