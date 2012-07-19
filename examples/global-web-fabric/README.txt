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
To get the `brooklyn` script and add it to your path, you can do one of the following:

Build Brooklyn from source:
  git clone https://github.com/brooklyncentral/brooklyn.git
  cd brooklyn
  mvn clean install -DskipTests
  BROOKLYN_HOME=$(pwd)
  export PATH=$PATH:$BROOKLYN_HOME/usage/dist/target/brooklyn-0.4.0-SNAPSHOT-dist/brooklyn-0.4.0-SNAPSHOT/bin/
  EXAMPLES_HOME=$BROOKLYN_HOME/examples

Or download the Brooklyn distro:
  VERSION=0.4.0-M1
  curl -O http://developers.cloudsoftcorp.com/download/maven2/io/brooklyn/brooklyn-dist/$VERSION/brooklyn-dist-$VERSION-dist.tar.gz
  tar xzf brooklyn-dist-$VERSION-dist.tar.gz
  cd brooklyn
  BROOKLYN_HOME=$(pwd)
  export PATH=$PATH:$BROOKLYN_HOME/bin/
  
---
The steps explained in more detail...

1. Download the "distro", or checkout + build the source code
   Instructions available at http://brooklyncentral.github.com/use/guide/management/index.html

2. Add the `brooklyn` script to your path
   In the distro, this is in $DISTRO_HOME/bin
   When compiling from source, the brooklyn script will be in usage/dist/target/brooklyn-0.4.0-SNAPSHOT-dist/brooklyn-0.4.0-SNAPSHOT/bin/

3. Use `mvn clean install` to build the examples
   Note if you checked out and built the brooklyn repo, it will already have built the examples.

4. Add the examples jar to the brooklyn classpath 
   The maven build will put this jar in the target/ sub-directory

5. Run the brooklyn script to launch your app, supplying the app's fully qualified class name 
   and the location to use.
