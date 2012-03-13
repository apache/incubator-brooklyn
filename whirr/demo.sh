#!/bin/bash
#
# Brooklyn Demo
#
# Run this, then you should see the brooklyn management console at localhost:8081
# set -x # debug

CLASS=brooklyn.extras.whirr.WhirrExample

ROOT=$(cd $(dirname $0) && pwd)
cd $ROOT

JAVA_OPTS="-Xmx256m -Xmx1g -XX:MaxPermSize=256m"
export CP=`ls ./target/brooklyn-whirr-*-with-dependencies.jar | awk '{print $1}'`

if [ -z "$CP" ]; then
	echo "Cannot find the with-dependencies jar"
	exit 1
fi

echo "Running demo for $CLASS from $CP"

pwd
java $JAVA_OPTS -cp "$CP" "$CLASS"
