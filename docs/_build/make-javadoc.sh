#!/bin/bash

if [ ! -x make-javadoc.sh ]; then
  echo This command must be run from the _build directory, not its parent.
  exit 1
fi

if [ -z "$GROOVY_CMD" ] ; then
  if [ ! -z "$GROOVY_HOME" ] ; then
    GROOVY_CMD=$GROOVY_HOME/bin/groovysh
  else
    GROOVY_CMD=`which groovysh`
  fi
fi

if [ ! -x "$GROOVY_CMD" ] ; then
  echo groovy executable must be available on the path or in \$GROOVY_HOME/bin
  exit 1
fi

if [ -z "$BROOKLYN_JAVADOC_SOURCE_PATHS" ]; then
  echo detecting source paths for javadoc
  export SOURCE_PATHS=`find ../.. -name java | grep "src/main/java$" | grep -v web-console/plugins | grep -v "^../../sandbox"`
else
  echo using pre-defined source paths $BROOKLYN_JAVADOC_SOURCE_PATHS
  export SOURCE_PATHS=$BROOKLYN_JAVADOC_SOURCE_PATHS
fi

rm -rf target/apidocs/

export DATESTAMP=`date "+%Y-%m-%d"`
echo "building javadoc from $SOURCE_PATH at $DATESTAMP"


$GROOVY_CMD -q << END
sourcePaths = System.env['SOURCE_PATHS'].split('\\\\s+').join(':')
title = "Brooklyn"
ant = new AntBuilder()
ant.taskdef(name: "groovydoc", classname: "org.codehaus.groovy.ant.Groovydoc")
ant.groovydoc(
    destdir      : "target/apidocs/",
    sourcepath   : "\${sourcePaths}",
    packagenames : "**.*",
    use          : "true",
    windowtitle  : "\${title}",
    doctitle     : "\${title}",
    header       : "\${title}",
    footer       : '<b>Apache Brooklyn - Multi-Cloud Application Management Platform</b> <br/> <a href="http://brooklyn.io/" target="_top">brooklyn.io</a>. Apache License. &copy; '+System.env['DATESTAMP']+'.',
    private      : "false")
println "\njavadoc built in target/apidocs\n" 
END

if (($!)) ; then echo ; echo ; echo "Groovy docs had an error." ; exit 1 ; fi
echo ; echo

if [ -z 'ls target/apidocs' ]; then echo ; echo ; echo "Error - no content. Are the paths right?" ; exit 1 ; fi

if [ -d ../_site/use/api/ ] ; then
  echo "API directory detected in parent, installing docs there"
  cp -r target/apidocs/* ../_site/use/api/
fi

