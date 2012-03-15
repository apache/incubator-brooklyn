#!/bin/bash

# changes the BROOKLYN version everywhere

[ -d .git ] || {
  echo "Must run in brooklyn project root directory"
  exit 1
}

# remove binaries and stuff
mvn clean

# TODO look for lines matching

... ${CURRENT_VERSION ... BROOKLYN_VERSION

# or two-lines matching

... BROOKLYN_VERSION_BELOW ...
... ${CURRENT_VERSION} ...

