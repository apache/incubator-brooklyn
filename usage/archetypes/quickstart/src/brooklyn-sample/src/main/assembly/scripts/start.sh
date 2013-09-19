#!/bin/bash

if [ ! -z "$JAVA_HOME" ] ; then 
    JAVA=$JAVA_HOME/bin/java
else
    JAVA=`which java`
fi

if [ ! -x "$JAVA" ] ; then
  echo Cannot find java. Set JAVA_HOME or add java to path.
  exit 1
fi

if [[ ! `ls brooklyn-sample-*.jar 2> /dev/null` ]] ; then
  echo Command must be run from the directory where the JAR is installed.
  exit 4
fi

$JAVA -Xms256m -Xmx1024m -XX:MaxPermSize=1024m \
    -classpath "conf/:*:lib/*" \
    com.acme.sample.brooklyn.SampleMain \
    "$@"
