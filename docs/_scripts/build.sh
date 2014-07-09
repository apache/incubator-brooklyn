#!/bin/bash
#
# this generates the site in _site
# override --url /myMountPoint  (as an argument to this script) if you don't like the default set in /_config.yml

if [ ! -x _scripts/build.sh ] ; then
  echo script must be run in root of docs dir
  exit 1
fi

rm -rf _site/

jekyll --pygments $* "$@" || { echo failed jekyll site build ; exit 1 ; }
echo

pushd _javadoc > /dev/null
./make-javadoc.sh || { echo failed javadoc build ; exit 1 ; }
popd > /dev/null
echo

echo docs build complete, in _site
