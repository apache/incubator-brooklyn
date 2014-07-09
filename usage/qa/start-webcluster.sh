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
#
# Brooklyn Management
#
# Run this, then you should see the Brooklyn mgmt webapp at localhost:8081/
#
#set -x # debug

CLASS=brooklyn.qa.longevity.webcluster.WebClusterApp
VERSION=0.7.0-SNAPSHOT # BROOKLYN_VERSION

ROOT=$(cd $(dirname $0) && pwd)
cd $ROOT

JAVA_OPTS="-Xms256m -Xmx1g -XX:MaxPermSize=256m"
export CP="./target/brooklyn-qa-$VERSION.jar:./target/lib/*"

echo running Brooklyn Web Console using $CLASS from $CP at $LOCATIONS

echo java $JAVA_OPTS -cp "$CP" $CLASS $LOCATIONS $@
java $JAVA_OPTS -cp "$CP" $CLASS $LOCATIONS $@
