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

# creates a source release - this is a .tar.gz file containing all the source code files that are permitted to be released.

show_help() {
    echo >&2 "usage: make-source-release [-v version] [-r rc_number]"
    echo >&2 "    -v version: specifies the name of this version."
    echo >&2 "                defaults to the version given in pom.xml."
    echo >&2 "    -r rc_number: specifies the release candidate number."
    echo >&2 "                  defaults to blank, meaning no release candidate suffix."
    echo >&2 "When a RC number is given, the produced artifact names include the 'rc' suffix,"
    echo >&2 "but the contents of the archive artifact do *not* include the suffix. Therefore,"
    echo >&2 "turning a release candidate into a release requires only renaming the artifacts."
}

confirm () {
    # call with a prompt string or use a default
    read -r -p "${1:-Are you sure? [y/N]} " response
    case $response in
        [yY][eE][sS]|[yY]) 
            true
            ;;
        *)
            false
            ;;
    esac
}

rc_suffix=
OPTIND=1
while getopts "h?v:r:" opt; do
    case "$opt" in
        h|\?)
            show_help
            exit 0
            ;;
        v)
            current_version=$OPTARG
            ;;
        r)
            rc_suffix=$OPTARG
            ;;
        *)
            show_help
            exit 1
    esac
done

shift $((OPTIND-1))
[ "$1" = "--" ] && shift

[ -d .git ] || {
echo "Must run in brooklyn project root directory"
exit 1
}

if [ -z "${current_version}" ]; then
    # Some magic to derive the anticipated version of the release.
    # Use xpath to query the version number in the pom
    xpath='xpath'
    type -P $xpath &>/dev/null && {
    set +e
    current_version=$( xpath pom.xml '/project/version/text()' 2>/dev/null )
    set -e
} || {
echo "Cannot guess version number as $xpath command not found."
}
fi
if [ -z "${current_version}" ]; then
    echo >&2 "Version could not be guessed and not given as a command line option"
    exit 1
fi

release_name=apache-brooklyn-${current_version}
if [ -z "$rc_suffix"]; then
    artifact_name=${release_name}
else
    artifact_name=${release_name}-rc${rc_suffix}
fi

echo "The version is ${current_version}"
echo "The rc suffix is ${rc_suffix}"
echo "The release name is ${release_name}"
echo "The artifact name is ${artifact_name}"
echo ""
echo "WARNING! This script will run 'git clean -dxf' to remove ALL files that are not under Git source control."
echo "This includes build artifacts and all uncommitted local files and directories."
echo "If you want to check what will happen, answer no and run 'git clean -dxn' to dry run."
confirm || exit
git clean -dxf

echo "Creating source release folder ${release_name}"
mkdir -p ${release_name}
rsync -rtp --exclude ${release_name} --exclude .git\* --exclude '**/*.[ejw]ar' . ${release_name}

mkdir -p ${artifact_name}
echo "Creating artifact ${artifact_name}/${artifact_name}.tar.gz"
tar czf ${artifact_name}/${artifact_name}.tar.gz apache-brooklyn-${current_version}
( cd ${artifact_name} && md5 ${artifact_name}.tar.gz > ${artifact_name}.tar.gz.md5 )
( cd ${artifact_name} && shasum -b -a 1 ${artifact_name}.tar.gz > ${artifact_name}.tar.gz.sha1 )
( cd ${artifact_name} && shasum -b -a 256 ${artifact_name}.tar.gz > ${artifact_name}.tar.gz.sha256 )

echo "Sign the release? This will require a working gpg2 and your private key passphrase."
confirm && ( cd ${artifact_name} && gpg2 --armor --output ${artifact_name}.tar.gz.asc --detach-sig ${artifact_name}.tar.gz )

