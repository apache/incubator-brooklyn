#!/bin/bash
#
# Brooklyn Example Demo
#
# Run this, then you should see the brooklyn mgmt console at localhost:8081, 
# and the webapp from an appserver at 8080, and from nginx at 8000.
#
# Refresh the webapp at 8080 a few times, and you'll see activity in the details pane at 8081.
# Then start a load-generator (e.g. jmeter, using the test plan in this directory). 
# You'll see it scale out, picking up ports 8082, 8083, ...!
#
# Pass --port 8084 to start the webapp on a different port (e.g. to run multiple instances).
#
# To launch to a cloud (instead of localhost), 
# supply additional arguments being the location(s), e.g. one or more of:
# aws-ec2:eu-west-1 aws-ec2:us-west-1 cloudservers-uk
# (you'll need credentials set in your ~/.brooklyn/brooklyn.properties)

CLASS=brooklyn.demo.WebClusterDatabaseExample

ROOT=$(cd $(dirname $0) && pwd)
cd $ROOT

if [ -z "$BROOKLYN_VERSION" ] ; then 
  BROOKLYN_VERSION=0.4.0-SNAPSHOT   # BROOKLYN_VERSION
fi

if [[ -z "$JAVA_OPTS" ]] ; then
  JAVA_OPTS="-Xmx256m -Xmx1g -XX:MaxPermSize=256m"
fi

if [[ -z "$BROOKLYN_ALL_JAR" ]] ; then
  if [[ -f ~/.m2/repository/io/brooklyn/brooklyn-all/$BROOKLYN_VERSION/brooklyn-all-${BROOKLYN_VERSION}-with-dependencies.jar ]] ; then
    BROOKLYN_ALL_JAR=~/.m2/repository/io/brooklyn/brooklyn-all/$BROOKLYN_VERSION/brooklyn-all-${BROOKLYN_VERSION}-with-dependencies.jar
  elif [[ -f ../../usage/all/target/brooklyn-all-${BROOKLYN_VERSION}-with-dependencies.jar ]] ; then
    BROOKLYN_ALL_JAR=../../usage/all/target/brooklyn-all-${BROOKLYN_VERSION}-with-dependencies.jar
  elif [[ -z "$CLASSPATH" ]] ; then
    echo "Cannot find brooklyn-all-${BROOKLYN_VERSION}-with-dependencies.jar; either set CLASSPATH or place in your maven repo (by building this project)"
    exit 1
  else
    BROOKLYN_ALL_JAR=""
  fi 
fi

if [[ -z "$BROOKLYN_THIS_JAR" ]] ; then
  if [[ ! -z `ls target/classes/* 2> /dev/null` ]] ; then
    BROOKLYN_THIS_JAR=target/classes
  elif [[ -f `ls target/*.jar` ]] ; then
    BROOKLYN_THIS_JAR=`ls target/*.jar | awk '{print $1}'`
  elif [[ -z "$CLASSPATH" ]] ; then
    echo "Cannot find project JAR or classes/ in target/ ; either set CLASSPATH or mvn clean install this project"
    exit 1
  else
    BROOKLYN_THIS_JAR=""
  fi 
fi

export CP=$CLASSPATH:$BROOKLYN_THIS_JAR:$BROOKLYN_ALL_JAR

JAVA_OPTS="-Dbrooklyn.localhost.address=127.0.0.1 $JAVA_OPTS"

echo running demo for $CLASS from $CP at $@
echo java $JAVA_OPTS -cp "$CP" $CLASS $@

java $JAVA_OPTS -cp "$CP" $CLASS $@
