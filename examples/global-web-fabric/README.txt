Instructions for running examples
=================================

The commands below assume that the `brooklyn` script is on your $PATH, this project has been built,
and you are in this directory.  Adjust to taste for other configurations.

  export BROOKLYN_CLASSPATH=$(pwd)/target/classes

  # Launch the app in aws-ec2 regions eu-west-1 and us-east-1
  brooklyn launch --app brooklyn.demo.GlobalWebFabricExample --location "aws-ec2:eu-west-1,aws-ec2:us-east-1"

  # Launch the app in aws-ec2 regions eu-west-1 and us-east-1, and use an AppFog cloudfoundry account in AWS US-West
  brooklyn launch --app brooklyn.demo.GlobalWebFabricExample --location "aws-ec2:eu-west-1,aws-ec2:us-east-1,cloudfoundry:https://api.aws.af.cm/"

---

The aws-ec2 credentials are retrieved from ~/.brooklyn/brooklyn.properties

This file should contain something like:
  brooklyn.jclouds.aws-ec2.identity=AKA50M30N3S1DFR0MAW55
  brooklyn.jclouds.aws-ec2.credential=aT0Ps3cr3tC0D3wh1chAW5w1llG1V3y0uTOus333

Brooklyn defaults to using ~/.ssh/id_rsa and ~/.ssh/id_rsa.pub.

For Cloud Foundry you must have an AppFog account set up and the CloudFoundry client configured and on the path.

---

For more information, please visit: http://brooklyncentral.github.com/use/examples/global-web-fabric/
