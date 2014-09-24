#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Brooklyn remote install script.
#
# Usage:
#     brooklyn-install.sh [-h] [-q] [-s] [-e] [-u user] [-k key] [-r root] [-p port] hostname
#
#set -x # DEBUG

function help() {
    cat <<EOF

Brooklyn remote install script.

Options

    -q  Quiet install
    -s  Create and set up user account
    -e  Set up random entropy for SSH
    -u  Change the Brooklyn username (default 'brooklyn')
    -r  Change the remote root username (default 'root')
    -k  The private key to use for SSH (default '~/.ssh/id_rsa')
    -p  The SSH port to connect to (default 22)

Usage

    brooklyn-install.sh [-q] [-s] [-e] [-u user] [-r root] [-k key] [-p port] hostname

Installs Brooklyn on the given hostname as 'brooklyn' or the specified
user. Optionally it creates and configures the Brooklyn user. 
Passwordless SSH access as root to the remote host must be enabled with the given key.

EOF
    exit 0
}

function log() {
    if ! ${QUIET}; then
        echo $@
    fi
    date +"Timestamp: %Y-%m-%d %H:%M:%S.%s" >> ${LOG}
    if [ "$1" == "-n" ]; then
        shift
    fi
    if [ "$*" != "..." ]; then
        echo "Log: $*" | sed -e "s/\.\.\.//" >> ${LOG}
    fi
}

function fail() {
    log "...failed!"
    error "$*"
}

function error() {
    echo "Error: $*" | tee -a "${LOG}"
    usage
}

function usage() {
    echo "Usage: $(basename ${0}) [-h] [-q] [-s] [-e] [-u user] [-r root] [-k key] [-p port] hostname"
    exit 1
}

function retry() {
    n=1
    COMMAND="ssh ${SSH_OPTS} ${USER}@${HOST} \"test -x ./brooklyn-${BROOKLYN_VERSION}/brooklyn-console.log\""
    INTERVAL="${2:-1}"
    MAX_ATTEMPTS="${2:-10}"
    log "Execute command '${1}' every ${INTERVAL} second(s) for ${MAX_ATTEMPTS} attempts"
    while [[ $n -le $MAX_ATTEMPTS ]]; do
        eval "${1}" 2>&1
        RESULT=$?
        if [[ $RESULT -eq 0 ]]; then         
            return 0
        fi
            log "waiting ${INTERVAL} before next retry [${n} of ${MAX_ATTEMPTS}]"
            sleep 1
            let n=$n+1
    done
    return 1
}

QUIET=false
LOG="brooklyn-install.log"
BROOKLYN_VERSION="0.7.0-M1"
SSH=ssh
ROOT=root

while getopts ":hqseu:r:k:p:" o; do
    case "${o}" in
        h)  help
            ;;
        q)  QUIET=true
            ;;
        s)  SETUP_USER=true
            ;;
        e)  SETUP_RANDOM=true
            ;;
        u)  BROOKLYN_USER="${OPTARG}"
            ;;
        r)  ROOT="${OPTARG}"
            ;;
        k)  PRIVATE_KEY_FILE="${OPTARG}"
            ;;
        p)  PORT="${OPTARG}"
            ;;            
        *)  usage "Invalid option: $*"
            ;;
    esac
done
shift $((OPTIND-1))

if [ $# -ne 1 ]; then
    error "Must specify remote hostname as last argument"
fi

HOST="$1"
USER="${BROOKLYN_USER:-brooklyn}"
PRIVATE_KEY_FILE="${PRIVATE_KEY_FILE:-${HOME}/.ssh/id_rsa}"
SSH_PORT=${PORT:-22}
if [ "${ROOT}" != "root" ]; then
    SUDO="sudo"
fi

SSH_OPTS="-o StrictHostKeyChecking=no -p ${SSH_PORT}"
if [ -f "${PRIVATE_KEY_FILE}" ]; then
    SSH_OPTS="${SSH_OPTS} -i ${PRIVATE_KEY_FILE}"
else
    error "SSH private key '${PRIVATE_KEY_FILE}' not found"
fi
SSH_PUBLIC_KEY_DATA=$(ssh-keygen -y -f ${PRIVATE_KEY_FILE})

echo "Installing Brooklyn ${BROOKLYN_VERSION} on ${HOST}:${SSH_PORT} as user: '${USER}'"

# Pre-requisites for this script
log "Configuring '${HOST}:${PORT}'... "

# Install packages
log -n "Installing packages for curl, sed, tar, wget on '${HOST}:${SSH_PORT}'..."
ssh ${SSH_OPTS} ${ROOT}@${HOST} "${SUDO} yum check-update || ${SUDO} apt-get update" >> ${LOG} 2>&1
for package in "curl" "sed" "tar" "wget"; do
    ssh ${SSH_OPTS} ${ROOT}@${HOST} "which ${package} || { ${SUDO} yum -y --nogpgcheck -q install ${package} || ${SUDO} ${SUDO} apt-get -y --allow-unauthenticated install ${package}; }" >> ${LOG} 2>&1
done
log " done!"

# Install Java 7
log -n "Installing java 7 on '${HOST}:${SSH_PORT}'... "
if [ "${INSTALL_EXAMPLES}" ]; then
    check="javac"
else
    check="java"
    JAVA_HOME="/usr"
fi
ssh ${SSH_OPTS} ${ROOT}@${HOST} "which ${check} || { ${SUDO} yum -y -q install java-1.7.0-openjdk-devel || ${SUDO} apt-get -y install openjdk-7-jdk; }" >> ${LOG} 2>&1
for java in "jre" "jdk" "java-1.7.0-openjdk" "java-1.7.0-openjdk-amd64"; do
    if ssh ${SSH_OPTS} ${ROOT}@${HOST} "test -d /usr/lib/jvm/${java}"; then
        JAVA_HOME="/usr/lib/jvm/${java}/" && echo "Java: ${JAVA_HOME}" >> ${LOG}
    fi
done
ssh ${SSH_OPTS} ${ROOT}@${HOST}  "test -x ${JAVA_HOME}/bin/${check}" >> ${LOG} 2>&1 || fail "Java is not installed"
log "done!"

# Increase linux kernel entropy for faster ssh connections
if [ "${SETUP_RANDOM}" ]; then
    log -n "Installing rng-tool to increase entropy on '${HOST}:${SSH_PORT}'... "
    ssh ${SSH_OPTS} ${ROOT}@${HOST} "which rng-tools || { ${SUDO} yum -y -q install rng-tools || ${SUDO} apt-get -y install rng-tools; }" >> ${LOG} 2>&1
    if ssh ${SSH_OPTS} ${ROOT}@${HOST} "test -f /etc/default/rng-tools"; then
        echo "HRNGDEVICE=/dev/urandom" | ssh ${SSH_OPTS} ${ROOT}@${HOST} "${SUDO} cat >> /etc/default/rng-tools"
        ssh ${SSH_OPTS} ${ROOT}@${HOST} "${SUDO} /etc/init.d/rng-tools start" >> ${LOG} 2>&1
    else
        echo "EXTRAOPTIONS=\"-r /dev/urandom\"" | ssh ${SSH_OPTS} ${ROOT}@${HOST} "cat >> /etc/sysconfig/rngd"
        ssh ${SSH_OPTS} ${ROOT}@${HOST} "${SUDO} /etc/init.d/rngd start" >> ${LOG} 2>&1
    fi
    log "done!"
fi

# Create Brooklyn user if required
if ! ssh ${SSH_OPTS} ${ROOT}@${HOST} "id ${USER} > /dev/null 2>&1"; then
    if [ -z "${SETUP_USER}" ]; then
        error "User '${USER}' does not exist on ${HOST}"
    fi
    log -n "Creating user '${USER}'..."
    ssh ${SSH_OPTS} ${ROOT}@${HOST} "${SUDO} useradd ${USER} -s /bin/bash -d /home/${USER} -m" >> ${LOG} 2>&1
    ssh ${SSH_OPTS} ${ROOT}@${HOST} "id ${USER}" >> ${LOG} 2>&1 || fail "User was not created"
    log "done!"
fi

# Setup Brooklyn user
if [ "${SETUP_USER}" ]; then
    log -n "Setting up user '${USER}'... "
    echo "${USER} ALL = (ALL) NOPASSWD: ALL" | ssh ${SSH_OPTS} ${ROOT}@${HOST} "${SUDO} tee -a /etc/sudoers"
    ssh ${SSH_OPTS} ${ROOT}@${HOST} "${SUDO} sed -i.brooklyn.bak 's/.*requiretty.*/#brooklyn-removed-require-tty/' /etc/sudoers"
    ssh ${SSH_OPTS} ${ROOT}@${HOST} "${SUDO} mkdir -p /home/${USER}/.ssh"
    ssh ${SSH_OPTS} ${ROOT}@${HOST} "${SUDO} chmod 700 /home/${USER}/.ssh"
    echo "${SSH_PUBLIC_KEY_DATA}" | ssh ${SSH_OPTS} ${ROOT}@${HOST} "${SUDO} tee -a /home/${USER}/.ssh/authorized_keys"
    ssh ${SSH_OPTS} ${ROOT}@${HOST} "${SUDO} chown -R ${USER}.${USER} /home/${USER}/.ssh"
    ssh ${SSH_OPTS} ${USER}@${HOST} "ssh-keygen -q -t rsa -N \"\" -f .ssh/id_rsa"
    ssh ${SSH_OPTS} ${USER}@${HOST} "ssh-keygen -y -f .ssh/id_rsa >> .ssh/authorized_keys"
    log "done!"
fi

# Setup Brooklyn
log -n "Downloading Brooklyn... "
ssh ${SSH_OPTS} ${USER}@${HOST} "curl -s -o brooklyn-${BROOKLYN_VERSION}.tar.gz http://search.maven.org/remotecontent?filepath=io/brooklyn/brooklyn-dist/${BROOKLYN_VERSION}/brooklyn-dist-${BROOKLYN_VERSION}-dist.tar.gz"
ssh ${SSH_OPTS} ${USER}@${HOST} "tar zxvf brooklyn-${BROOKLYN_VERSION}.tar.gz" >> ${LOG} 2>&1
ssh ${SSH_OPTS} ${USER}@${HOST} "test -x brooklyn-${BROOKLYN_VERSION}/bin/brooklyn" || fail "Brooklyn was not downloaded correctly"
log "done!"

# Configure Brooklyn if no brooklyn.properties
if ! ssh ${SSH_OPTS} ${USER}@${HOST} "test -f .brooklyn/brooklyn.properties"; then
    log -n "Configuring Brooklyn... "
    ssh ${SSH_OPTS} ${USER}@${HOST} "mkdir -p .brooklyn"
    ssh ${SSH_OPTS} ${USER}@${HOST} "curl -s -o .brooklyn/brooklyn.properties http://brooklyncentral.github.io/use/guide/quickstart/brooklyn.properties"
    ssh ${SSH_OPTS} ${USER}@${HOST} "sed -i.bak 's/^# brooklyn.webconsole.security.provider = brooklyn.rest.security.provider.AnyoneSecurityProvider/brooklyn.webconsole.security.provider = brooklyn.rest.security.provider.AnyoneSecurityProvider/' .brooklyn/brooklyn.properties"
    ssh ${SSH_OPTS} ${USER}@${HOST} "curl -s -o .brooklyn/catalog.xml http://brooklyncentral.github.io/use/guide/quickstart/catalog.xml"
    log "done!"
fi

# Install example Jars and catalog
log -n "Installing examples and configure catalog.xml ..."

ssh ${SSH_OPTS} ${USER}@${HOST} "cat > .brooklyn/catalog.xml" <<EOF
<?xml version="1.0"?>
<catalog>
    <name>Brooklyn Demos</name>

    <template type="brooklyn.demo.WebClusterDatabaseExample" name="Demo Web Cluster with DB">
        <description>Deploys a demonstration web application to a managed JBoss cluster with elasticity, persisting to a MySQL</description>
        <iconUrl>http://downloads.cloudsoftcorp.com/brooklyn/catalog/logos/JBoss_by_Red_Hat.png</iconUrl>
    </template>

    <template type="brooklyn.demo.GlobalWebFabricExample" name="Demo GeoDNS Web Fabric DB">
        <description>Deploys a demonstration web application to JBoss clusters around the world</description>
        <iconUrl>http://downloads.cloudsoftcorp.com/brooklyn/catalog/logos/JBoss_by_Red_Hat.png</iconUrl>
    </template>

    <template type="brooklyn.demo.NodeJsTodoApplication" name="NodeJs TODO application">
        <description>Deploys a Nodejs TODO application around the world</description>
        <iconUrl>classpath://nodejs-logo.png</iconUrl>
    </template>    

    <template type="brooklyn.demo.SimpleCassandraCluster" name="Demo Cassandra Cluster">
        <description>Deploys a demonstration Cassandra clusters around the world</description>
        <iconUrl>http://downloads.cloudsoftcorp.com/brooklyn/catalog/logos/cassandra-sq-icon.jpg</iconUrl>
    </template>

    <template type="brooklyn.demo.SimpleCouchDBCluster" name="Demo CouchDB">
        <description>Deploys a demonstration CouchDB clusters around the world</description>
        <iconUrl>http://downloads.cloudsoftcorp.com/brooklyn/catalog/logos/couchdb-logo-icon.png</iconUrl>
    </template>

    <classpath>
        <entry>http://search.maven.org/remotecontent?filepath=io/brooklyn/example/brooklyn-example-simple-web-cluster/${BROOKLYN_VERSION}/brooklyn-example-simple-web-cluster-${BROOKLYN_VERSION}.jar</entry>
        <entry>http://search.maven.org/remotecontent?filepath=io/brooklyn/example/brooklyn-example-simple-nosql-cluster/${BROOKLYN_VERSION}/brooklyn-example-simple-nosql-cluster-${BROOKLYN_VERSION}.jar</entry>
    </classpath>
</catalog>
EOF

log "done!"

# Run Brooklyn
log "Starting Brooklyn..."

ssh -n -f ${SSH_OPTS} ${USER}@${HOST} "nohup ./brooklyn-${BROOKLYN_VERSION}/bin/brooklyn launch >> ./brooklyn-${BROOKLYN_VERSION}/brooklyn-console.log 2>&1 &"

retry "ssh ${SSH_OPTS} ${USER}@${HOST} \"test -e ./brooklyn-${BROOKLYN_VERSION}/brooklyn-console.log\""
retry "ssh ${SSH_OPTS} ${USER}@${HOST} \"grep -q \"Started Brooklyn console at\" ./brooklyn-${BROOKLYN_VERSION}/brooklyn-console.log &>/dev/null\""

URL=$(ssh ${SSH_OPTS} ${USER}@${HOST} "grep 'Started Brooklyn console at' ./brooklyn-${BROOKLYN_VERSION}/brooklyn-console.log | cut -d' ' -f9 | tr -d ," | sed -e "s/127\.0\.0\.1/${HOST}/g" -e "s/0\.0\.0\.0/${HOST}/g" 2>&1)
log "Brooklyn Console URL at ${URL}"

if wget -qO- --retry-connrefused --no-check-certificate ${URL} &> /dev/null; then
    log "Brooklyn is running at ${URL}"
else
    log "Brooklyn is not running at ${URL}"
fi
