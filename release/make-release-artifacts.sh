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

set -e

###############################################################################
fail() {
    echo >&2 "$@"
    exit 1
}

###############################################################################
show_help() {
    cat >&2 <<END
Usage: make-release-artifacts.sh [-v version] [-r rc_number]
Prepares and builds the source and binary distribution artifacts of a Brooklyn
release.

  -vVERSION                  overrides the name of this version, if detection
                             from pom.xml is not accurate for any reason.
  -rRC_NUMBER                specifies the release candidate number. The
                             produced artifact names include the 'rc' suffix,
                             but the contents of the archive artifact do *not*
                             include the suffix. Therefore, turning a release
                             candidate into a release requires only renaming
                             the artifacts.

Specifying the RC number is required. Specifying the version number is
discouraged; if auto detection is not working, then this script is buggy.
END
# ruler                      --------------------------------------------------
}

###############################################################################
confirm() {
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

###############################################################################
detect_version() {
    if [ \! -z "${current_version}" ]; then
        return
    fi

    set +e
    current_version=$( xmlstarlet select -t -v '/_:project/_:version/text()' pom.xml 2>/dev/null )
    success=$?
    set -e
    if [ "${success}" -ne 0 -o -z "${current_version}" ]; then
        fail Could not detect version number
    fi
}

###############################################################################
# Argument parsing
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

###############################################################################
# Prerequisite checks
[ -d .git ] || fail Must run in brooklyn project root directory

detect_version

###############################################################################
# Determine all filenames and paths, and confirm

release_name=apache-brooklyn-${current_version}
if [ -z "$rc_suffix" ]; then
    fail Specifying the RC number is required
else
    artifact_name=${release_name}-rc${rc_suffix}
fi

release_script_dir=$( cd $( dirname $0 ) && pwd )
brooklyn_dir=$( pwd )
staging_dir="${brooklyn_dir}/src-release-tmp/${release_name}-src"
bin_staging_dir="${brooklyn_dir}/bin-release-tmp/${release_name}-bin"
artifact_dir="${release_script_dir}/${artifact_name}"

echo "The version is ${current_version}"
echo "The rc suffix is rc${rc_suffix}"
echo "The release name is ${release_name}"
echo "The artifact name is ${artifact_name}"
echo "The artifact directory is ${artifact_dir}"
echo ""
confirm "Is this information correct? [y/N]" || exit
echo ""
echo "WARNING! This script will run 'git clean -dxf' to remove ALL files that are not under Git source control."
echo "This includes build artifacts and all uncommitted local files and directories."
echo "If you want to check what will happen, answer no and run 'git clean -dxn' to dry run."
echo ""
confirm || exit
echo ""
echo "This script will cause uploads to be made to a staging repository on the Apache Nexus server."
echo ""
confirm "Shall I continue?  [y/N]" || exit

###############################################################################
# Clean the workspace
git clean -dxf

###############################################################################
# Source release
echo "Creating source release folder ${release_name}"
set -x
mkdir -p ${staging_dir}
rsync -rtp --exclude src-release-tmp --exclude bin-release-tmp --exclude .git\* --exclude '**/*.[ejw]ar' --exclude docs/ . ${staging_dir}

mkdir -p ${artifact_dir}
set +x
echo "Creating artifact ${artifact_dir}/${artifact_name}.tar.gz and .zip"
set -x
( cd src-release-tmp && tar czf ${artifact_dir}/${artifact_name}-src.tar.gz apache-brooklyn-${current_version}-src )
( cd src-release-tmp && zip -qr ${artifact_dir}/${artifact_name}-src.zip apache-brooklyn-${current_version}-src )

###############################################################################
# Binary release
set +x
echo "Proceeding to build binary release"
set -x

# Set up GPG agent
eval $(gpg-agent --daemon --no-grab --write-env-file $HOME/.gpg-agent-info)
GPG_TTY=$(tty)
export GPG_TTY GPG_AGENT_INFO

# Workaround for bug BROOKLYN-1
( cd ${staging_dir} && mvn clean --projects :brooklyn-archetype-quickstart )

# Perform the build and deploy to Nexus staging repository
( cd ${staging_dir} && mvn deploy -Papache-release )

# Re-pack the archive with the correct names
mkdir -p bin-release-tmp
tar xzf ${staging_dir}/usage/dist/target/brooklyn-dist-${current_version}-dist.tar.gz -C bin-release-tmp
mv bin-release-tmp/brooklyn-dist-${current_version} bin-release-tmp/apache-brooklyn-${current_version}-bin

( cd bin-release-tmp && tar czf ${artifact_dir}/${artifact_name}-bin.tar.gz apache-brooklyn-${current_version}-bin )
( cd bin-release-tmp && zip -qr ${artifact_dir}/${artifact_name}-bin.zip apache-brooklyn-${current_version}-bin )

###############################################################################
# Signatures and checksums

# OSX doesn't have sha256sum, even if MacPorts md5sha1sum package is installed.
# Easy to fake it though.
which sha256sum >/dev/null || alias sha256sum='shasum -a 256'

( cd ${artifact_dir} &&
    for a in *.tar.gz *.zip; do
        md5sum -b ${a} > ${a}.md5
        sha1sum -b ${a} > ${a}.sha1
        sha256sum -b ${a} > ${a}.sha256
        gpg2 --armor --output ${a}.asc --detach-sig ${a}
    done
)

###############################################################################
# Conclusion

set +x
echo "The release is done - here is what has been created:"
ls ${artifact_dir}
echo "You can find these files in: ${artifact_dir}"
echo "The git commit ID for the voting emails is: $( git rev-parse HEAD )"
