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
# /etc/os-release is the new-ish standard for specifying OS details.
# See http://www.freedesktop.org/software/systemd/man/os-release.html.
# There are a multitude of system-dependent files we can check (see list 
# at http://linuxmafia.com/faq/Admin/release-files.html). We can support 
# them as we need.

# Survey of CentOS 6.5, Debian Jessie, Fedora 17, OSX and Ubuntu 12.04 suggests
# uname -m is the most reliable flag for architecture
ARCHITECTURE=$(uname -m)

# Tests for existence of commands
function exists {
    command -v $1 >/dev/null 2>&1
}

# OS info
if [ -f /etc/os-release ]; then
    source /etc/os-release
elif [ -f /etc/redhat-release ]; then
    # Example: Red Hat Enterprise Linux Server release 6.3 (Santiago)
    # Match everything up to ' release'
    NAME=$(cat /etc/redhat-release | sed 's/ release.*//')
    # Match everything between 'release ' and the next space
    VERSION_ID=$(cat /etc/redhat-release | sed 's/.*release \([^ ]*\).*/\1/')
elif exists lsb_release; then
    NAME=$(lsb_release -s -i)
    VERSION_ID=$(lsb_release -s -r)
elif exists sw_vers; then
    # sw_vers is an OSX command
    NAME=$(sw_vers -productName)
    VERSION_ID=$(sw_vers -productVersion)
fi

# Debian os-release doesn't set versions, and Debian 6 doesn't have os-release or lsb_release
if [ -z $VERSION_ID ] && [ -f /etc/debian_version ]; then
    NAME=Debian
    VERSION_ID=$(cat /etc/debian_version)
fi

# Hardware info
# Is the loss of precision converting bytes and kilobytes to megabytes acceptable?
# We can do floating point calculations with precision with the bc program, but it
# isn't available by default on all systems.
if exists sw_vers; then
    # sysctl outputs total in bytes, linux meminfo uses kilobytes
    bytes=$(sysctl -n hw.memsize)
    RAM=$((bytes/1048576))
    CPU_COUNT=$(sysctl -n hw.ncpu)
else
    # e.g. "MemTotal:        1019352 kB" -> "1019352"
    # grep -o '[0-9]*' would be simpler than the sed command but I've observed it match
    # nothing on Centos 5.6 instances.
    kilobytes=$(grep MemTotal /proc/meminfo | sed 's/^MemTotal:[ ]*\([0-9]*\) kB/\1/')
    RAM=$((kilobytes/1024))
    CPU_COUNT=$(grep processor /proc/cpuinfo | wc -l)
fi

echo "name:$NAME"
echo "version:$VERSION_ID"
echo "architecture:$ARCHITECTURE"
echo "ram:$RAM"
echo "cpus:$CPU_COUNT"
