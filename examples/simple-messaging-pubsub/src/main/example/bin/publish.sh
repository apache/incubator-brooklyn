#!/bin/bash
#
# Brooklyn Messaging Example Publisher
#
# Run this with the broker URL as the only argument to test publishing messages
#
#set -x # debug

# Set example directory
DIR=$(cd $(dirname $0)/.. && pwd)

# Check arguments
if [ $# -ne 1 ]; then
    echo "Usage: $0 url"
    exit 255
else
    URL="$1"
fi

# Run Subscribe client
java -cp "${DIR}/lib/*" brooklyn.demo.Publish ${URL}
