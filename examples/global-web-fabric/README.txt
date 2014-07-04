Instructions for running examples
=================================

The commands below assume that the `brooklyn` script is on your $PATH, this project has been built,
and you are in this directory.  Adjust to taste for other configurations.

  export BROOKLYN_CLASSPATH=$(pwd)/target/classes

  # Launch the app in aws-ec2 regions eu-west-1 and us-east-1
  brooklyn launch --app brooklyn.demo.GlobalWebFabricExample --location "aws-ec2:eu-west-1,aws-ec2:us-east-1"

---

The aws-ec2 credentials are retrieved from ~/.brooklyn/brooklyn.properties

This file should contain something like:
  brooklyn.jclouds.aws-ec2.identity=AKA50M30N3S1DFR0MAW55
  brooklyn.jclouds.aws-ec2.credential=aT0Ps3cr3tC0D3wh1chAW5w1llG1V3y0uTOus333

Brooklyn defaults to using ~/.ssh/id_rsa and ~/.ssh/id_rsa.pub.

----
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.