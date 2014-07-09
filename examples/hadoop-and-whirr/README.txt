Instructions for running examples
=================================

The commands below assume that the `brooklyn` script is already on your $PATH, and you are in the "examples" directory:

  cd hadoop-and-whirr
  export BROOKLYN_CLASSPATH=$(pwd)/target/classes
  
  # Runs hadoop in aws-ec2 us-east-1
  brooklyn launch --app brooklyn.extras.whirr.WhirrHadoopExample --stopOnKeyPress --location aws-ec2:us-east-1
  
  # Runs whirr with a custom recipe in aws-ec2 us-east-1
  brooklyn launch --app brooklyn.extras.whirr.WhirrExample --stopOnKeyPress --location aws-ec2:us-east-1

  # Runs a web-cluster along with hadoop in aws-ec2, region us-east-1
  brooklyn launch --app brooklyn.extras.whirr.WebClusterWithHadoopExample --location aws-ec2:us-east-1

  # Runs web-clusters in eu-west-1 and us-east-1, along with hadoop
  brooklyn -v launch --app brooklyn.extras.whirr.WebFabricWithHadoopExample --stopOnKeyPress --location "aws-ec2:eu-west-1,aws-ec2:us-east-1"

---

The aws-ec2 credentials are retrieved from ~/.brooklyn/brooklyn.properties

This file should contain something like:
  brooklyn.jclouds.aws-ec2.identity=AKA50M30N3S1DFR0MAW55
  brooklyn.jclouds.aws-ec2.credential=aT0Ps3cr3tC0D3wh1chAW5w1llG1V3y0uTOus333

Brooklyn defaults to using ~/.ssh/id_rsa and ~/.ssh/id_rsa.pub

---

For more information, please visit: http://brooklyncentral.github.com/use/examples/whirrhadoop/
