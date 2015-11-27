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

# prints a sample email with all the correct information

set +x

fail() {
    echo >&2 "$@"
    exit 1
}

if [ -z "${VERSION_NAME}" ] ; then fail VERSION_NAME must be set ; fi
if [ -z "${RC_NUMBER}" ] ; then fail RC_NUMBER must be set ; fi

base=apache-brooklyn-${VERSION_NAME}-rc${RC_NUMBER}

if [ -z "$1" ] ; then fail "A single argument being the staging repo ID must be supplied, e.g. orgapachebrooklyn-1234" ; fi

staging_repo_id=$1
archetype_check=`curl https://repository.apache.org/content/repositories/${staging_repo_id}/archetype-catalog.xml 2> /dev/null`
if ! echo $archetype_check | grep brooklyn-archetype-quickstart > /dev/null ; then
  fail staging repo looks wrong at https://repository.apache.org/content/repositories/${staging_repo_id}
fi
if ! echo $archetype_check | grep ${VERSION_NAME} > /dev/null ; then
  fail wrong version at https://repository.apache.org/content/repositories/${staging_repo_id}
fi

artifact=release/tmp/${base}/${base}-bin.tar.gz
if [ ! -f $artifact ] ; then
  fail could not find artifact $artifact
fi
if [ -z "$APACHE_ID" ] ; then
  APACHE_ID=`gpg2 --verify ${artifact}.asc ${artifact} 2>&1 | egrep -o '[^<]*@apache.org>' | cut -d @ -f 1`
fi
if [ -z "$APACHE_ID" ] ; then
  fail "could not deduce APACHE_ID (your apache username); are files signed correctly?"
fi
if ! ( gpg2 --verify ${artifact}.asc ${artifact} 2>&1 | grep ${APACHE_ID}@apache.org > /dev/null ) ; then
  fail "could not verify signature; are files signed correctly and ID ${APACHE_ID} correct?"
fi

cat <<EOF

Subject: [VOTE] Release Apache Brooklyn ${VERSION_NAME} [rc${RC_NUMBER}]


This is to call for a vote for the release of Apache Brooklyn ${VERSION_NAME}.

This release comprises of a source code distribution, and a corresponding
binary distribution, and Maven artifacts.

The source and binary distributions, including signatures, digests, etc. can
be found at:

  https://dist.apache.org/repos/dist/dev/incubator/brooklyn/${base}

The artifact SHA-256 checksums are as follows:

EOF

cat release/tmp/${base}/*.sha256 | awk '{print "  "$0}'

cat <<EOF

The Nexus staging repository for the Maven artifacts is located at:

    https://repository.apache.org/content/repositories/${staging_repo_id}

All release artifacts are signed with the following key:

    https://people.apache.org/keys/committer/${APACHE_ID}.asc

KEYS file available here:

    https://dist.apache.org/repos/dist/release/incubator/brooklyn/KEYS


The artifacts were built from git commit ID $( git rev-parse HEAD ):

    https://git-wip-us.apache.org/repos/asf?p=incubator-brooklyn.git;a=commit;h=$( git rev-parse HEAD )


Please vote on releasing this package as Apache Brooklyn ${VERSION_NAME}.

The vote will be open for at least 72 hours.
[ ] +1 Release this package as Apache Brooklyn ${VERSION_NAME}
[ ] +0 no opinion
[ ] -1 Do not release this package because ...


Thanks!
EOF

cat <<EOF



CHECKLIST for reference

[ ] Download links work.
[ ] Binaries work.
[ ] Checksums and PGP signatures are valid.
[ ] Expanded source archive matches contents of RC tag.
[ ] Expanded source archive builds and passes tests.
[ ] LICENSE is present and correct.
[ ] NOTICE is present and correct, including copyright date.
[ ] All files have license headers where appropriate.
[ ] All dependencies have compatible licenses.
[ ] No compiled archives bundled in source archive.
[ ] I follow this project’s commits list.

EOF
