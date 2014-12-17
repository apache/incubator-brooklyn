#!/bin/bash

export BROOKLYN_JAVADOC_SOURCE_PATHS="../../api"
echo LIMITING build to $BROOKLYN_JAVADOC_SOURCE_PATHS for speed
./make-javadoc.sh

