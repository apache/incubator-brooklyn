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

rm -rf target/apidocs/

echo "building javadoc for api package (run quickly, generate just a bit of sample output, to test styles or other things)"

$GROOVY_CMD -q << END
sourcePaths = "../../api"
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
    footer       : '<b>Brooklyn Multi-Cloud Application Management Platform</b> <br/> <a href="http://brooklyncentral.github.com/" target="_top">brooklyncentral.github.com</a>. Apache License. &copy; 2012.',
    private      : "false")
println "\njavadoc built in target/apidocs\n" 
END

if (($!)) ; then echo ; echo ; echo "Groovy docs had an error." ; exit 1 ; fi
echo ; echo
