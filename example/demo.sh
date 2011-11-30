#!/bin/bash
#
# Brooklyn Spring Travel Wide-Area Demo
#
#set -x # debug

VERSION=0.2.0-SNAPSHOT
ROOT=$(cd $(dirname $0) && pwd)
JAVA_OPTS="-Xmx256m -Xmx1g -XX:MaxPermSize=256m"
export CLASSPATH=./target/brooklyn-example-${VERSION}-with-dependencies.jar

if [ $# -gt 0 ]; then
    LOCATIONS="$*"
else
    LOCATIONS="localhost"
    #LOCATIONS="eu-west-1 us-west-1 monterey-east"
    #LOCATIONS="eu-west-1 us-east-1 us-west-1 ap-northeast-1 ap-southeast-1 monterey-east edinburgh"
fi

cd $ROOT
java $JAVA_OPTS brooklyn.demo.TomcatWideAreaExample ${LOCATIONS}
