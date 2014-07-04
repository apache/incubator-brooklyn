Instructions for running examples
=================================

The commands below assume that the `brooklyn` script is already on your $PATH, and you are in the "examples" directory:

  cd simple-messaging-pubsub
  export BROOKLYN_CLASSPATH=$(pwd)/target/classes
  
  # Launches a qpid broker on localhost
  brooklyn -v launch --app brooklyn.demo.StandaloneQpidBrokerExample --location localhost

  # You can get the broker's URL from the brooklyn web-console at http://localhost:8081
  # by looking at the broker entity's sensors or from the verbose output from the application startup
  URL="amqp://guest:guest@/localhost?brokerlist='tcp://localhost:5672'"

  # Test subscribing, to receive a message from the broker
  java -cp "./resources/lib/*:./target/classes" brooklyn.demo.Subscribe ${URL}

  # Test publishing a message to the broker
  java -cp "./resources/lib/*:./target/classes" brooklyn.demo.Publish ${URL}

To test a Kafka distributed messaging cluster example, use the following command:

  # Launches a Kafka cluster on AWS EC2 with two brokers
  brooklyn -v launch --app brooklyn.demo.KafkaClusterExample --location aws-ec2:eu-west-1

---

For more information, please visit: http://brooklyncentral.github.com/use/examples/messaging/

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