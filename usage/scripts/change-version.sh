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

GREP_ARGS="-r -l --exclude_dir=^\..*|\/\..* --exclude=.*\.(log|war)"

# look for lines (where we can put the literal $LABEL1 in an inline comment) matching
# ... ${CURRENT_VERSION} ... BROOKLYN_VERSION
# Repeatedly replace, until no more occurrences of current_version.*label

FILES1=`pcregrep $GREP_ARGS "${CURRENT_VERSION}.*${LABEL1}" .`
for x in $FILES1 ; do
  while grep --quiet -E "${CURRENT_VERSION}.*${LABEL1}" $x; do
    sed -i .bak "s/${CURRENT_VERSION}\(.*\)${LABEL1}/${NEW_VERSION}\1${LABEL1}/" $x
  done
done

echo "One-line pattern changed these files: $FILES1"

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
