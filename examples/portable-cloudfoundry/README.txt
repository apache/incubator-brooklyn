Instructions for running examples
=================================

The commands below assume that the `brooklyn` script is already on your $PATH, and you are in the examples directory:

  EXAMPLES_HOME=$(pwd)
  export BROOKLYN_CLASSPATH=$EXAMPLES_HOME/portable-cloudfoundry/target/brooklyn-example-portable-cloudfoundry-0.4.0-SNAPSHOT.jar
  
  # Launches a web-app in VMware's cloudfoundry
  brooklyn -v launch --app brooklyn.example.cloudfoundry.MovableCloudFoundryClusterExample --location localhost

---

For more information, please visit: http://brooklyncentral.github.com/use/examples/portable-cloudfoundry/
