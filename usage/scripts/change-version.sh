#!/bin/bash

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

GREP_ARGS="-r -l --exclude_dir=\\..* --exclude=.*.(log|war)"

# look for lines (where we can put the literal $LABEL1 in an inline comment) matching
# ... ${CURRENT_VERSION} ... BROOKLYN_VERSION

FILES1=`pcregrep $GREP_ARGS "${CURRENT_VERSION}.*${LABEL1}" .`
for x in $FILES1 ; do
  sed -i "s/${CURRENT_VERSION}\(.*\)${LABEL1}/${NEW_VERSION}\1${LABEL1}/" $x
done

echo "One-line pattern changed these files: $FILES1"

# or two-lines (where we only have entire-line comments, used on the line before the version) matching
# ... BROOKLYN_VERSION_BELOW ...
# ... ${CURRENT_VERSION} ...

FILES2=`pcregrep $GREP_ARGS -M "${LABEL2}.*\n.*${CURRENT_VERSION}" .`
for x in $FILES2 ; do
  sed -n -i '1h; 1!H; ${ g; s/'"${LABEL2}"'\([^\n]*\n[^\n]*\)'"${CURRENT_VERSION}"'/'"${LABEL2}"'\1'"${NEW_VERSION}"'/g p }' $x
done

echo "Two-line pattern changed these files: $FILES2"

echo "Changed ${CURRENT_VERSION} to ${NEW_VERSION} for "`echo $FILES1 $FILES2 | wc | awk '{print $2}'`" files"

