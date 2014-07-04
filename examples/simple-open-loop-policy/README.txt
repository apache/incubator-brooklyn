Instructions for Running Examples
=================================

The commands below assume that the `brooklyn` script is on your $PATH, this project has been built,
and you are in this directory.  Adjust to taste for other configurations.

  export BROOKLYN_CLASSPATH=$(pwd)/target/classes
  
  # Three-tier: auto-scaling app-server cluster fronted by nginx, MySql backend wired up, on localhost
  brooklyn launch --app brooklyn.demo.WebClusterDatabaseOpenLoopExample --location localhost

The above requires passwordless `ssh localhost` and requires `gcc` to build `nginx`.
You could instead target your favourite cloud, where this has been tried and tested:


Redistributable embedded example:

  # To build a redistributable tar.gz with a start.sh script
  # which invokes the `main` method in the example class to start
  # (the redistributable will be at:  target/brooklyn-*-bin.tar.gz )
  mvn clean assembly:assembly

For more information, please visit:

  http://brooklyncentral.github.com/use/examples/webcluster/


Developer Notes
===============

This example sends an SMS message when the cluster has reached its max size (and where the auto-scaler policy
would continue to increase the size if it were not capped). The message is sent using clickatell.com,
using http://smsj.sourceforge.net.

Because smsj is not available on maven central, a custom local maven repo has been built (and checked in to git):

  wget http://sourceforge.net/projects/smsj/files/smsj/smsj-snapshot-20051126/smsj-20051126.jar/download

  mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
      -Dfile=smsj-20051126.jar \
      -DgroupId=io.brooklyn \ 
      -DartifactId=org.marre.smsj \
      -Dversion=1.0.0-20051126 \
      -Dpackaging=jar \
      -DlocalRepositoryPath=localrepo

And in the pom, this local repo is referenced:

  <repositories>
    <repository>
      <id>brooklyn-examples-localrepo</id>
      <url>file://${basedir}/localrepo</url>
    </repository>
  </repositories>

----
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.