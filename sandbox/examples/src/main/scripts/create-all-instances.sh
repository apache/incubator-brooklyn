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
# Creates an instance in each of the given regions.
# Must supply the directory with the ssh-key that aws generated for that region,
# and optionally supply a public key to be added to ~/.ssh/authorized_keys on the created instance

# setup directories and files
SCRIPTS_DIR=$(cd $(dirname $0) && pwd)
LOG_DIR="${SCRIPTS_DIR}/log"
AMI_DICTIONARY=${SCRIPTS_DIR}/amis.txt

# defaults
DATESTAMP=$(date "+%Y%m%d-%H%M")

# Command line argument parsing
while [ "${1:0:1}" == "-" ]; do
    case $1 in
	--ssh-dir)
		shift
	    SSH_DIR=$1
	    ;;
	--authorized-key)
		shift
	    AUTHORIZED_KEY_FILE=$1
	    ;;
    esac
    shift
done

if [ -z "$*" ]; then
   echo "Authorized key file not found at $AUTHORIZED_KEY_FILE"
   echo "Usage: $0  --ssh-dir <dir> [--authorized-key <file>] <regions>"
   echo "       where regions is a list of regions to use, such as eu-west-1 us-east-1"
   echo "       the output for each region is written to a file in log/"
   exit 1
fi

echo "Creating instances in regions $*"
mkdir -p "${LOG_DIR}"

for region in $*; do
    echo $region
    ami=`grep $region $AMI_DICTIONARY | cut -d " " -f2-`
    echo $ami
    ${SCRIPTS_DIR}/create-instance.sh --ami "$ami" --region "$region" --ssh-dir $SSH_DIR --authorized-key "$AUTHORIZED_KEY_FILE" &> "${LOG_DIR}/create-instance-${DATESTAMP}-$region.out" &
done
