#!/bin/bash
#
# Brooklyn Example Cloud Foundry Demo
#
# Run this, then you should see the brooklyn mgmt console at localhost:8081, 
# and the webapp launched in Cloud Foundry (URL will be displayed, and in mgmt console).
#
# This assumes vmc is set up to connect to cloudfoundry (or cloudfoundries).
# Specify location e.g. cloudfoundry:api.customcf.com to use a specific endpoint
# (just as an argument to this script).
# You can also specify localhost, aws-ec2, etc, as per previous demos;
# and it will deploy the web app to a self-built cluster (nginx, jboss, etc).
#
# Pass --port 8084 to start the webapp on a different port (e.g. to run multiple instances).

CLASS=brooklyn.example.cloudfoundry.MovableCloudFoundryClusterExample

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

if [[ -z "$BROOKLYN_CF_SANDBOX_JAR" ]] ; then
  echo BROOKLYN_CF_SANDBOX_JAR must be specified, pointing to the sandbox cloudfoundry JAR
  exit 1
fi

export CP=$CLASSPATH:$BROOKLYN_THIS_JAR:$BROOKLYN_ALL_JAR:$BROOKLYN_CF_SANDBOX_JAR

if [ $# -gt 0 ]; then
    ARGS="$*"
else
    ARGS="cloudfoundry"
    JAVA_OPTS="-Dbrooklyn.localhost.address=127.0.0.1 $JAVA_OPTS"
fi

echo running demo for $CLASS from $CP at $ARGS
echo java $JAVA_OPTS -cp "$CP" $CLASS $ARGS

java $JAVA_OPTS -cp "$CP" $CLASS $ARGS
