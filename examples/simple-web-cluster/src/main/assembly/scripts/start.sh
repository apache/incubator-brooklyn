#!/bin/bash

if [ -z "$BROOKLYN_APP_CLASS" ] ; then 
    BROOKLYN_APP_CLASS=brooklyn.demo.WebClusterDatabaseExample
fi

if [ -z "$JAVA" ] ; then 
    JAVA=`which java`
fi
if [ ! -z "$JAVA_HOME" ] ; then 
    JAVA=$JAVA_HOME/bin/java
else
    JAVA=`which java`
fi
if [ ! -x "$JAVA" ] ; then
  echo Cannot find java. Set JAVA_HOME or add java to path.
  exit 1
fi

if [[ ! `ls *.jar 2> /dev/null` || ! `ls lib/*.jar 2> /dev/null` ]] ; then
  echo Command must be run from the directory where it is installed.
  exit 1
fi

$JAVA -Xms256m -Xmx1024m -XX:MaxPermSize=1024m -classpath "*:lib/*" $BROOKLYN_APP_CLASS "$@"
