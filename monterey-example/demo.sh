#!/bin/bash
#
# Brooklyn Spring Travel Wide-Area Demo
#
#set -x # debug

VERSION=0.0.21-SNAPSHOT
ROOT=$(cd $(dirname $0) && pwd)
export JAVA_OPTS="-Xms256m -Xmx1g -XX:MaxPermSize=512m"
export CLASSPATH=./target/brooklyn-monterey-example-${VERSION}-with-dependencies.jar

if [ $# -gt 0 ]; then
    LOCATIONS="$*"
else
    LOCATIONS="edinburgh"
    #LOCATIONS="eu-west-1 us-west-1 monterey-east"
    #LOCATIONS="eu-west-1 us-east-1 us-west-1 ap-northeast-1 ap-southeast-1 monterey-east edinburgh"
fi

cd $ROOT
java ${JAVA_OPTS} com.cloudsoftcorp.monterey.brooklyn.example.MontereyDemo ${LOCATIONS}
