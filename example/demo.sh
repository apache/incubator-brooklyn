#!/bin/bash
#
# Brooklyn Spring Travel Wide-Area Demo
#
#set -x # debug

VERSION=0.0.1
ROOT=$(cd $(dirname $0) && pwd)
JAVA_OPTS=-Xmx256m -Xmx1g -XX:MaxPermSize=256m
CLASSPATH=target/brooklyn-example-${VERSION}-SNAPSHOT-with-dependencies.jar

LOCATIONS="eu-west-1 edinburgh"
#LOCATIONS="eu-west-1 us-west-1 monterey-east"
#LOCATIONS="eu-west-1 us-east-1 us-west-1 ap-northeast-1 ap-southeast-1 monterey-east edinburgh"

cd $ROOT
java brooklyn.demo.Demo ${LOCATIONS}
