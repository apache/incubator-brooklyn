#!/bin/bash
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

# changes the BROOKLYN version everywhere

# usage, e.g.:  change-version.sh 0.3.0-SNAPSHOT 0.3.0-RC1

[ -d .git ] || {
  echo "Must run in brooklyn project root directory"
  exit 1
}

[[ -z "$3" && ! -z "$2" ]] || {
  echo "Usage:  "$0" CURRENT_VERSION NEW_VERSION"
  echo " e.g.:  "$0" 0.3.0-SNAPSHOT 0.3.0-RC1"
  exit 1
}

# remove binaries and stuff
if [[ -f pom.xml ]] ; then mvn clean ; fi

LABEL1=BROOKLYN_VERSION
LABEL2=BROOKLYN_VERSION_BELOW

CURRENT_VERSION=$1
NEW_VERSION=$2

# exclude dot files, and exclude log, war, and .min.js files
# TODO not sure if the \/\..+ is needed
GREP_ARGS="-r -l --exclude_dir=^\..+|\/\..+ --exclude=.*\.(log|war|min.js)"

# look for lines (where we can put the literal $LABEL1 in an inline comment) matching
# ... ${CURRENT_VERSION} ... BROOKLYN_VERSION
# Repeatedly replace, until no more occurrences of current_version.*label

# search for every file containing LABEL1
FILES1=`pcregrep $GREP_ARGS "${CURRENT_VERSION}.*${LABEL1}" .`
for x in $FILES1 ; do
  while grep --quiet -E "${CURRENT_VERSION}.*${LABEL1}" $x; do
    sed -i .bak "s/${CURRENT_VERSION}\(.*\)${LABEL1}/${NEW_VERSION}\1${LABEL1}/" $x
  done
done

echo "One-line pattern with label after changed these files: $FILES1"

# search for every file containing LABEL1
FILES1=`pcregrep $GREP_ARGS "${LABEL1}.*${CURRENT_VERSION}" .`
for x in $FILES1 ; do
  while grep --quiet -E "${LABEL1}.*${CURRENT_VERSION}" $x; do
    sed -i .bak "s/${LABEL1}\(.*\)${CURRENT_VERSION}/${LABEL1}\1${NEW_VERSION}/" $x
  done
done

echo "One-line pattern with label before changed these files: $FILES1"

# or two-lines for situations where comments must be entire-line (e.g. scripts)
# put the comment on the line before the version
# using sed as per http://blog.ergatides.com/2012/01/24/using-sed-to-search-and-replace-contents-of-next-line-in-a-file/
# to match:
# ... BROOKLYN_VERSION_BELOW ...
# ... ${CURRENT_VERSION} ...

FILES2=`pcregrep $GREP_ARGS -M "${LABEL2}.*\n.*${CURRENT_VERSION}" .`
for x in $FILES2 ; do
  sed -i .bak -e '/'"${LABEL2}"'/{n;s/'"${CURRENT_VERSION}"'/'"${NEW_VERSION}"'/g;}' $x
done

echo "Two-line pattern changed these files: $FILES2"

echo "Changed ${CURRENT_VERSION} to ${NEW_VERSION} for "`echo $FILES1 $FILES2 | wc | awk '{print $2}'`" files"
echo "(Do a \`find . -name \"*.bak\" -delete\`  to delete the backup files.)"
