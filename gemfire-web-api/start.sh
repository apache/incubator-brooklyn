#!/bin/bash

SCRIPTS_DIR=`cd \`dirname $0\` && pwd`
TARGET="$SCRIPTS_DIR/target"

PORT=$1
CONFIG_FILE=$2
ADDITIONAL_CLASSPATH=$3
LICENSE=$4

# (& at end of command makes the pid available in $!)
java -cp $TARGET/brooklyn-gemfire-web-api-0.2.0-SNAPSHOT-with-dependencies.jar:$ADDITIONAL_CLASSPATH brooklyn.gemfire.api.Server $PORT $CONFIG_FILE $TARGET/gemfire.log $LICENSE &

echo $! > $TARGET/server-pid.txt
