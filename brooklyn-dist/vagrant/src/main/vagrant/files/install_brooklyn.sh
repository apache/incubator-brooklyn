#!/usr/bin/env bash

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

BROOKLYN_VERSION=""
INSTALL_FROM_LOCAL_DIST="false"
TMP_ARCHIVE_NAME=apache-brooklyn.tar.gz

do_help() {
  echo "./install.sh -v <Brooklyn Version> [-l <install from local file: true|false>]"
  exit 1
}

while getopts ":hv:l:" opt; do
    case "$opt" in
    v)  BROOKLYN_VERSION=$OPTARG ;;
        # using a true/false argopt rather than just flag to allow easier integration with servers.yaml config
    l)  INSTALL_FROM_LOCAL_DIST=$OPTARG ;;
    h)  do_help;;
    esac
done

# Exit if any step fails
set -e

if [ "x${BROOKLYN_VERSION}" == "x" ]; then
  echo "Error: you must supply a Brooklyn version [-v]"
  do_help
fi

if [ ! "${INSTALL_FROM_LOCAL_DIST}" == "true" ]; then
  if [ ! -z "${BROOKLYN_VERSION##*-SNAPSHOT}" ] ; then
    # url for official release versions
    BROOKLYN_URL="https://www.apache.org/dyn/closer.lua?action=download&filename=brooklyn/apache-brooklyn-${BROOKLYN_VERSION}/apache-brooklyn-${BROOKLYN_VERSION}-bin.tar.gz"
    BROOKLYN_DIR="apache-brooklyn-${BROOKLYN_VERSION}-bin"
  else
    # url for community-managed snapshots
    BROOKLYN_URL="https://repository.apache.org/service/local/artifact/maven/redirect?r=snapshots&g=org.apache.brooklyn&a=brooklyn-dist&v=${BROOKLYN_VERSION}&c=dist&e=tar.gz"
    BROOKLYN_DIR="brooklyn-dist-${BROOKLYN_VERSION}"
  fi
else
  echo "Installing from a local -dist archive [ /vagrant/brooklyn-dist-${BROOKLYN_VERSION}-dist.tar.gz]"
  # url to install from mounted /vagrant dir
  BROOKLYN_URL="file:///vagrant/brooklyn-dist-${BROOKLYN_VERSION}-dist.tar.gz"
  BROOKLYN_DIR="brooklyn-dist-${BROOKLYN_VERSION}"

  # ensure local file exists
  if [ ! -f /vagrant/brooklyn-dist-${BROOKLYN_VERSION}-dist.tar.gz ]; then
    echo "Error: file not found /vagrant/brooklyn-dist-${BROOKLYN_VERSION}-dist.tar.gz"
    exit 1
  fi
fi

echo "Installing Apache Brooklyn version ${BROOKLYN_VERSION} from [${BROOKLYN_URL}]"

echo "Downloading Brooklyn release archive"
curl --fail --silent --show-error --location --output ${TMP_ARCHIVE_NAME} "${BROOKLYN_URL}"
echo "Extracting Brooklyn release archive"
tar zxf ${TMP_ARCHIVE_NAME}

echo "Creating Brooklyn dirs and symlinks"
ln -s ${BROOKLYN_DIR} apache-brooklyn
sudo mkdir -p /var/log/brooklyn
sudo chown -R vagrant:vagrant /var/log/brooklyn
mkdir -p /home/vagrant/.brooklyn

echo "Copying default vagrant Brooklyn properties file"
cp /vagrant/files/brooklyn.properties /home/vagrant/.brooklyn/
chmod 600 /home/vagrant/.brooklyn/brooklyn.properties

echo "Installing JRE"
sudo sh -c 'export DEBIAN_FRONTEND=noninteractive; apt-get install --yes openjdk-8-jre-headless'

echo "Copying Brooklyn systemd service unit file"
sudo cp /vagrant/files/brooklyn.service /etc/systemd/system/brooklyn.service