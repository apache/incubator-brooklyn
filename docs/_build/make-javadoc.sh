#!/usr/bin/env bash

JAVADOC_TARGET1_SUBPATH=javadoc
JAVADOC_TARGET2_SUBPATH=misc/javadoc

if [ ! -x make-javadoc.sh ]; then
  echo This command must be run from the _build directory, not its parent.
  exit 1
fi

if [ -z "$BROOKLYN_JAVADOC_SOURCE_PATHS" ]; then
  echo detecting source paths for javadoc
  export SOURCE_PATHS=`find ../.. -name java | grep "src/main/java$" | grep -v "^../../sandbox" | tr "\\n" ":"`
else
  echo using pre-defined source paths $BROOKLYN_JAVADOC_SOURCE_PATHS
  export SOURCE_PATHS=$BROOKLYN_JAVADOC_SOURCE_PATHS
fi

mkdir -p target
rm -rf target/$JAVADOC_TARGET1_SUBPATH/

export YEARSTAMP=`date "+%Y"`
export DATESTAMP=`date "+%Y-%m-%d"`
export SHA1STAMP=`git rev-parse HEAD`

# BROOKLYN_VERSION_BELOW
export BROOKLYN_JAVADOC_CLASSPATH=$( mvn -f ../../pom.xml --projects :brooklyn-all dependency:build-classpath | grep -E -v '^\[[A-Z]+\]' )

echo "building javadoc at $DATESTAMP from:
$SOURCE_PATHS"

javadoc -sourcepath $SOURCE_PATHS \
  -public \
  -d target/$JAVADOC_TARGET1_SUBPATH/ \
  -subpackages "org.apache.brooklyn:io.brooklyn:brooklyn" \
  -classpath "${BROOKLYN_JAVADOC_CLASSPATH}" \
  -doctitle "Apache Brooklyn" \
  -windowtitle "Apache Brooklyn" \
  -notimestamp \
  -overview javadoc-overview.html \
  -header '<a href="/" class="brooklyn-header">Apache Brooklyn <div class="img"></div></a>' \
  -footer '<b>Apache Brooklyn - Multi-Cloud Application Management</b> <br/> <a href="http://brooklyn.io/" target="_top">brooklyn.io</a>. Apache License. &copy; '$YEARSTAMP'.' \
2>&1 1>/dev/null | tee target/javadoc.log

if ((${PIPESTATUS[0]})) ; then echo ; echo ; echo "ERROR: javadoc process exited non-zero" ; exit 1 ; fi
echo ; echo

if [ ! -f target/$JAVADOC_TARGET1_SUBPATH/brooklyn/entity/Entity.html ]; then echo "ERROR: missing expected content. Are the paths right?" ; exit 1 ; fi

if [ ! -z "`grep warnings target/javadoc.log`" ] ; then echo "WARNINGs occurred during javadoc build. See target/javadoc.log for more information." ; fi

sed -i.bak s/'${DATESTAMP}'/"${DATESTAMP}"/ target/$JAVADOC_TARGET1_SUBPATH/overview-summary.html
sed -i.bak s/'${SHA1STAMP}'/"${SHA1STAMP}"/ target/$JAVADOC_TARGET1_SUBPATH/overview-summary.html
rm target/$JAVADOC_TARGET1_SUBPATH/*.bak

if [ -d ../_site/guide/$JAVADOC_TARGET2_SUBPATH/ ] ; then
  echo "API directory detected in test structure _site, copying docs there so they can be served with serve-site.sh"
  cp -r target/$JAVADOC_TARGET1_SUBPATH/* ../_site/guide/$JAVADOC_TARGET2_SUBPATH/
fi

