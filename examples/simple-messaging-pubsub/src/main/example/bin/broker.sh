#!/bin/bash
#
# Simple Messaging PubSub Example
#set -x # debug

# Setup environment
EXAMPLE_HOME=$(cd $(dirname $0)/.. && pwd)
APPLICATION=brooklyn.demo.StandaloneBrokerExample
BROOKLYN_CLASSPATH="${EXAMPLE_HOME}/resources:${EXAMPLE_HOME}/lib/*"
BROOKLYN_OPTS="-Xms512m -Xmx512m"

# Launch Brooklyn with application
brooklyn launch --app ${APPLICATION} --location ${@:-localhost}
