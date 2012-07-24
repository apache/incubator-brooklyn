Instructions for running examples
=================================

The commands below assume that the `brooklyn` script is already on your $PATH, and you are in the examples directory:

  EXAMPLES_HOME=$(pwd)
  export BROOKLYN_CLASSPATH=$EXAMPLES_HOME/global-web-fabric/target/brooklyn-example-global-web-fabric-0.4.0-SNAPSHOT.jar

  # Launch the app on localhost
  brooklyn -v launch --app brooklyn.demo.GlobalWebFabricExample --location locahost

  # Launch the app in aws-ec2 regions eu-west-1 and us-east-1
  brooklyn launch --app brooklyn.demo.SingleWebServerExample --location "aws-ec2:eu-west-1,aws-ec2:us-east-1"

---
The aws-ec2 credentials are retrieved from ~/.brooklyn/brooklyn.properties

This file should contain something like:
  brooklyn.jclouds.aws-ec2.identity=AKA50M30N3S1DFR0MAW55
  brooklyn.jclouds.aws-ec2.credential=aT0Ps3cr3tC0D3wh1chAW5w1llG1V3y0uTOus333

Brooklyn defaults to using ~/.ssh/id_rsa and ~/.ssh/id_rsa.pub

---

For more information, please visit: http://brooklyncentral.github.com/use/examples/global-web-fabric/
