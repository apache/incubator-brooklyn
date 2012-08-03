Instructions for running examples
=================================

The commands below assume that the `brooklyn` script is already on your $PATH, and you are in the "examples" directory:

  cd portable-cloudfoundry
  export BROOKLYN_CLASSPATH=$(pwd)/target/classes
  
  # Launches a web-app in VMware's cloudfoundry
  brooklyn launch --app brooklyn.example.cloudfoundry.MovableCloudFoundryClusterExample --location localhost

---

For more information, please visit: http://brooklyncentral.github.com/use/examples/portable-cloudfoundry/
