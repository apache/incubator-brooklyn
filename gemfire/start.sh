#!/bin/bash

SCRIPTS_DIR=`cd \`dirname $0\` && pwd`
TARGET="$SCRIPTS_DIR/target"
LIB="$SCRIPTS_DIR/lib"

PORT=$1
CONFIG_FILE=$2
ADDITIONAL_CLASSPATH=$3

java -cp $SCRIPTS_DIR/src/main/java:$TARGET/classes:$LIB/gemfire.jar:$LIB/antlr.jar:$ADDITIONAL_CLASSPATH brooklyn.gemfire.demo.Server $PORT $CONFIG_FILE $TARGET/gemfire.log $LIB/gemfireLicense.zip
