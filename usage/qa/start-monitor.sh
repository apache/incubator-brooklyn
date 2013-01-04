#!/bin/bash
#
# Brooklyn Management
#
# Run this, then you should see the Brooklyn mgmt webapp at localhost:8081/
#
#set -x # debug

CLASS=brooklyn.qa.longevity.Monitor
# BROOKLYN_VERSION_BELOW
VERSION=0.4.0-SNAPSHOT

ROOT=$(cd $(dirname $0) && pwd)
cd $ROOT

JAVA_OPTS="-Xms256m -Xmx1g -XX:MaxPermSize=256m"
export CP="./target/brooklyn-qa-$VERSION.jar:./target/lib/*"

echo running Brooklyn Web Console using $CLASS from $CP with $*

echo java $JAVA_OPTS -cp "$CP" $CLASS $*
java $JAVA_OPTS -cp "$CP" $CLASS $*
