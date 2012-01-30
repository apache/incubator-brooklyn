#!/bin/bash
#
# Brooklyn Demo
#
# Run this, then you should see the brooklyn mgmt console at localhost:8081, and a webapp at 8080, or 8000 (nginx).
# Refresh the webapp at 8080 a few times, and you'll see activity in the details pane at 8081.
# Then start a load-generator (e.g. jmeter, using the test plan in this directory). You'll see it scale out!
# Use --port 8084 to start the webapp on a different port.
#set -x # debug

CLASS=brooklyn.demo.WebClusterExample

ROOT=$(cd $(dirname $0) && pwd)
cd $ROOT

JAVA_OPTS="-Xmx256m -Xmx1g -XX:MaxPermSize=256m"
export CP=`ls ./target/brooklyn-example-*-with-dependencies.jar | awk '{print $1}'`

if [ -z "$CP" ]; then
	echo "Cannot find the with-dependencies jar"
	exit 1
fi


if [ $# -gt 0 ]; then
    LOCATIONS="$*"
else
    LOCATIONS="localhost"
    #LOCATIONS="eu-west-1 us-west-1 monterey-east"
    #LOCATIONS="eu-west-1 us-east-1 us-west-1 ap-northeast-1 ap-southeast-1 monterey-east edinburgh"
fi

echo running demo for $CLASS from $CP at $LOCATIONS

pwd
echo java $JAVA_OPTS -cp "$CP" $CLASS $LOCATIONS
java $JAVA_OPTS -cp "$CP" $CLASS $LOCATIONS
