#!/bin/bash
#
# Brooklyn Example Demo
#
# Run this, then you should see the brooklyn mgmt console at localhost:8081, 
# and zookeeper launched using whirr, just by running the WhirrExample class 
# (source code in this project).
#
# Currently hard-coded to aws eu-west-1 (unless you edit the *Example.groovy files here) 
# so you need your credentials set up in ~/.brooklyn/brooklyn.properties

CLASS=brooklyn.extras.whirr.WhirrExample

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
  if [[ -f `ls target/*.jar` ]] ; then
    BROOKLYN_THIS_JAR=`ls target/*.jar | awk '{print $1}'`
  elif [[ -d target/classes ]] ; then
    BROOKLYN_THIS_JAR=target/classes
  elif [[ -z "$CLASSPATH" ]] ; then
    echo "Cannot find project JAR or classes/ in target/ ; either set CLASSPATH or mvn clean install this project"
    exit 1
  else
    BROOKLYN_THIS_JAR=""
  fi 
fi

export CP=$CLASSPATH:$BROOKLYN_THIS_JAR:$BROOKLYN_ALL_JAR

if [ $# -gt 0 ]; then
    ARGS="$*"
else
    ARGS=""
fi

echo running demo for $CLASS from $CP at $ARGS
echo java $JAVA_OPTS -cp "$CP" $CLASS $ARGS

java $JAVA_OPTS -cp "$CP" $CLASS $ARGS
