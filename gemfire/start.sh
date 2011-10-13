#!/bin/bash

SCRIPTS_DIR=`cd \`dirname $0\` && pwd`

PORT=$1
CONFIG_FILE=$2
ADDITIONAL_CLASSPATH=$3

java -cp $SCRIPTS_DIR/bin:$SCRIPTS_DIR/lib/gemfire.jar:$SCRIPTS_DIR/lib/antlr.jar:$ADDITIONAL_CLASSPATH brooklyn.gemfire.demo.Server $PORT $CONFIG_FILE $SCRIPTS_DIR/gemfire.log $SCRIPTS_DIR/lib/gemfireLicense.zip
