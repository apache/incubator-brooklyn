Instructions for running examples
=================================

The commands below assume that the `brooklyn` script is already on your $PATH, and you are in the examples directory:

  export BROOKLYN_EXAMPLES_DIR=$(pwd)
  export BROOKLYN_CLASSPATH=${BROOKLYN_EXAMPLES_DIR}/simple-messaging-pubsub/target/classes
  
  # Launches a qpid broker on localhost
  brooklyn launch --app brooklyn.demo.StandaloneBrokerExample --location localhost

  # Test publishing a message to the broker
  # You can get the broker's URL from the brooklyn web-console at http://localhost:8081, 
  # by looking at the broker entity's sensors
  java -cp "${BROOKLYN_EXAMPLES_DIR}/simple-messaging-pubsub/brooklyn-example-simple-messaging-pubsub/lib/*" brooklyn.demo.Publish ${URL}

  # Test subscribing, to receive a message from the broker
  java -cp "${BROOKLYN_EXAMPLES_DIR}/simple-messaging-pubsub/brooklyn-example-simple-messaging-pubsub/lib/*" brooklyn.demo.Subscribe ${URL}

---

For more information, please visit: http://brooklyncentral.github.com/use/examples/messaging/
