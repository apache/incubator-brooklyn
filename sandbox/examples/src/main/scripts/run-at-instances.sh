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

if [ -z "$1" ]; then
   echo "command not found"
   echo "Usage: $0  <ssh command> [regions]"
   echo "       where ssh command is the command to run at each instance"
   echo "       where regions is an option list of regions to use, such as eu-west-1 us-east-1; defaults to all regions"
   exit 1
fi

CMD=$1
shift

INSTANCES=`cat instances.txt | cut -d " " -f3-`

if [ -n "$*" ]; then
	INSTANCES=""
	for region in $* ; do
		INSTANCE=`grep $region instances.txt | cut -d " " -f3-`
		INSTANCES=${INSTANCES}${INSTANCE}' '
	done
fi

for instance in $INSTANCES ; do
  echo invoking $CMD at $instance
  ssh -f root@$instance "$CMD" | awk '{print "OUTPUT '$instance': " $0}' &
done

wait
