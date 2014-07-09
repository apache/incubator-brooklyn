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
