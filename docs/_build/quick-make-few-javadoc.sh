#!/usr/bin/env bash

export BROOKLYN_JAVADOC_SOURCE_PATHS="../../api/src/main/java"
echo LIMITING build to $BROOKLYN_JAVADOC_SOURCE_PATHS for speed
./make-javadoc.sh

