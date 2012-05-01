#!/bin/bash
#
# Brooklyn Example Demo
#
# Run this, then you should see the brooklyn mgmt console at localhost:8081, 
#
# To launch to a cloud (instead of localhost), 
# supply additional arguments being the location(s), e.g. one or more of:
# aws-ec2:eu-west-1 aws-ec2:us-west-1 cloudservers-uk
# (you'll need credentials set in your ~/.brooklyn/brooklyn.properties)
#set -x # debug


ROOT=$(cd $(dirname $0) && pwd)
cd ${ROOT}

CLASS=brooklyn.demo.StandaloneBrokerExample

if [ -z "${JAVA_OPTS}" ] ; then
    JAVA_OPTS="-Xmx256m -Xmx1g -XX:MaxPermSize=256m"
fi
CLASSPATH="${CLASSPATH:-.}:lib/*"

if [ $# -ne 0 ]; then
    ARGS="$*"
else
    ARGS="localhost"
    JAVA_OPTS="-Dbrooklyn.localhost.address=127.0.0.1 ${JAVA_OPTS}"
fi

echo running demo for ${CLASS}
echo java ${JAVA_OPTS} -cp "${CLASSPATH}" ${CLASS} ${ARGS}
java ${JAVA_OPTS} -cp "${CLASSPATH}" ${CLASS} ${ARGS}
