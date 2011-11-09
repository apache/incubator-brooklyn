#!/bin/bash

SCRIPTS_DIR=`cd \`dirname $0\` && pwd`
TARGET="$SCRIPTS_DIR/target"
LIB="$SCRIPTS_DIR/lib"

PORT=$1
CONFIG_FILE=$2
ADDITIONAL_CLASSPATH=$3
LICENSE=$4

# (& at end of command makes the pid available in $!)
java -cp $SCRIPTS_DIR/src/main/java:$TARGET/classes:$LIB/gemfire-6.5.1.4.jar:$LIB/antlr.jar:$LIB/guava-10.0.1.jar:$ADDITIONAL_CLASSPATH brooklyn.gemfire.demo.Server $PORT $CONFIG_FILE $TARGET/gemfire.log $LICENSE &

echo $! > server-pid.txt
