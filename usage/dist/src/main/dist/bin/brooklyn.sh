#!/bin/bash
#
# Brooklyn
#
#set -x # debug

ROOT=$(cd $(dirname $0) && pwd)
cd ${ROOT}

CLASS=brooklyn.Main

if [ -z "${JAVA_OPTS}" ] ; then
    JAVA_OPTS="-Xmx256m -Xmx1g -XX:MaxPermSize=256m"
fi

CLASSPATH="${CLASSPATH:-.}:lib/*"

if [ $# -ne 0 ]; then
    ARGS="$*"
else
    ARGS="localhost"
    JAVA_OPTS="-Dbrooklyn.localhost.address=127.0.0.1 ${JAVA_OPTS}"
fi

echo Running Brooklyn using ${CLASS} and ${ARGS}
echo java ${JAVA_OPTS} -cp ${CLASSPATH} ${CLASS} ${ARGS}
java ${JAVA_OPTS} -cp "${CLASSPATH}" ${CLASS} ${ARGS}
