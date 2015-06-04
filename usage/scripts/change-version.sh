#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

set -e

# changes the version everywhere
# usage, e.g.:  change-version.sh 0.3.0-SNAPSHOT 0.3.0-RC1
#          or:  change-version.sh MARKER 0.3.0-SNAPSHOT 0.3.0-RC1

[ -d .git ] || {
  echo "Must run in brooklyn project root directory"
  exit 1
}

if [ "$#" -eq 2 ]; then
  VERSION_MARKER=BROOKLYN_VERSION
elif [ "$#" -eq 3 ]; then
  VERSION_MARKER=$1_VERSION
  shift;
else
  echo "Usage:  "$0" [VERSION_MARKER] CURRENT_VERSION NEW_VERSION"
  echo " e.g.:  "$0" BROOKLYN 0.3.0-SNAPSHOT 0.3.0-RC1"
  exit 1
fi

# remove binaries and stuff
if [ -f pom.xml ] && [ -d target ] ; then mvn clean ; fi

VERSION_MARKER_NL=${VERSION_MARKER}_BELOW
CURRENT_VERSION=$1
NEW_VERSION=$2

# grep --exclude-dir working only in recent versions, not on all platforms, replace with find;
# skip folders named "ignored" or .xxx (but not the current folder ".");
# exclude log, war, etc. files;
# use null delimiters so files containing spaces are supported;
# pass /dev/null as the first file to search in, so the command doesn't fail if find doesn't match any files;
# add || true for the case where grep doesn't have matches, so the script doesn't halt
# If there's an error "Argument list too long" add -n20 to xargs arguments and loop over $FILE around sed
FILES=`find . -type d \( -name ignored -or -name .?\* \) -prune \
       -o -type f -not \( -name \*.log -or -name '*.war' -or -name '*.min.js' -or -name '*.min.css' \) -print0 | \
       xargs -0 grep -l "${VERSION_MARKER}\|${VERSION_MARKER_NL}" /dev/null || true`

FILES_COUNT=`echo $FILES | wc | awk '{print $2}'`

if [ ${FILES_COUNT} -ne 0 ]; then
    # search for files containing version markers
    sed -i.bak -e "/${VERSION_MARKER}/s/${CURRENT_VERSION}/${NEW_VERSION}/g" $FILES
    sed -i.bak -e "/${VERSION_MARKER_NL}/{n;s/${CURRENT_VERSION}/${NEW_VERSION}/g;}" $FILES
fi

echo "Changed ${CURRENT_VERSION} to ${NEW_VERSION} for "${FILES_COUNT}" files"
echo "(Do a \`find . -name \"*.bak\" -delete\`  to delete the backup files.)"
