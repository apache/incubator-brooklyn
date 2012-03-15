#!/bin/bash
#
# Brooklyn Management
#
# Run this, then you should see the Brooklyn mgmt webapp at localhost:8081/
#
#set -x # debug

CLASS=brooklyn.launcher.Main

ROOT=$(cd $(dirname $0) && pwd)
cd $ROOT

JAVA_OPTS="-Xms256m -Xmx1g -XX:MaxPermSize=256m"
export CP=`ls ./target/brooklyn-launcher-*-with-dependencies.jar | awk '{print $1}'`

if [ -z "$CP" ]; then
	echo "Cannot find the with-dependencies jar"
	exit 1
fi

echo running Brooklyn Web Console using $CLASS from $CP at $LOCATIONS

echo java $JAVA_OPTS -cp "$CP" $CLASS $LOCATIONS
java $JAVA_OPTS -cp "$CP" $CLASS $LOCATIONS
