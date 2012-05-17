#!/bin/bash
#
# Brooklyn Messaging Example Subscriber
#
# Run this with the broker URL as the only argument to test publishing messages
#
#set -x # debug


ROOT=$(cd $(dirname $0) && pwd)
cd $ROOT

CLASS=brooklyn.demo.Subscribe
CLASSPATH="${CLASSPATH:-.}:lib/*"

if [ $# -eq 0 ]; then
    echo "Usage: $0 url"
    exit 255
else
    URL=$1
fi

echo running ${CLASS}
java ${JAVA_OPTS} -cp "${CLASSPATH}" ${CLASS} ${URL}
