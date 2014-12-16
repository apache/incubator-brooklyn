#!/bin/bash
#
# this generates the site in _site
# override --url /myMountPoint  (as an argument to this script) if you don't like the default set in /_config.yml

if [ ! -x _build/build.sh ] ; then
  echo ERROR: script must be run in root of docs dir
  exit 1
fi

function help() {
  echo "This will build the documentation in _site/."
  echo "Usage:  _build/build.sh MODE [ARGS]"
  echo "where MODE is:"
  echo "* website-root  : to build the website only, in the root"
  echo "* guide-latest  : to build the guide only, in /v/latest/"
  # BROOKLYN_VERSION_BELOW
  echo "* guide-version : to build the guide only, in the versioned namespace /v/0.7.0-SNAPSHOT/"
  echo "* test-guide-root : to build the guide only, in the root (for testing)"
  echo "* test-both : to build the website to root and guide to /v/latest/ (for testing)"
  echo "* test-both-sub : to build the website to /sub/ and guide to /sub/v/latest/ (for testing)"
  echo "* original : to build the files in their original location (website it /website and guide in /guide/, for testing)"
  echo "and supported ARGS are:"
  echo "* --skip-javadoc : to skip javadoc build"
  echo "* --serve : serve files from _site after building (for testing)"
  echo 'with any remaining ARGS passed to jekyll as `jekyll build --config ... ARGS`.'
}

function parse_command() {
  case $1 in
  help)
    help
    exit 0 ;;
  website-root)
    JEKYLL_CONFIG=_config.yml,_build/config-production.yml,_build/config-exclude-guide.yml,_build/config-website-root.yml
    DIRS_TO_MOVE[0]=website
    DIRS_TO_MOVE_TARGET[0]=""
    SKIP_JAVADOC=true
    SUMMARY="website files in the root"
    ;;
  guide-latest)
    JEKYLL_CONFIG=_config.yml,_build/config-production.yml,_build/config-exclude-all-but-guide.yml,_build/config-guide-latest.yml,_build/config-style-latest.yml
    DIRS_TO_MOVE[0]=guide
    DIRS_TO_MOVE_TARGET[0]=v/latest
    DIRS_TO_MOVE[1]=style
    DIRS_TO_MOVE_TARGET[1]=v/latest/style
    JAVADOC_TARGET=_site/${DIRS_TO_MOVE_TARGET[0]}/use/api/
    SUMMARY="user guide files in /${DIRS_TO_MOVE_TARGET[0]}"
    ;;
  guide-version)
    JEKYLL_CONFIG=_config.yml,_build/config-production.yml,_build/config-exclude-all-but-guide.yml,_build/config-guide-version.yml
    # Mac bash defaults to v3 not v4, so can't use assoc arrays :(
    DIRS_TO_MOVE[0]=guide
    # BROOKLYN_VERSION_BELOW
    DIRS_TO_MOVE_TARGET[0]=v/0.7.0-SNAPSHOT
    DIRS_TO_MOVE[1]=style
    DIRS_TO_MOVE_TARGET[1]=${DIRS_TO_MOVE_TARGET[0]}/style
    JAVADOC_TARGET=_site/${DIRS_TO_MOVE_TARGET[0]}/use/api/
    SUMMARY="user guide files in /${DIRS_TO_MOVE_TARGET[0]}"
    ;;
  test-guide-root)
    JEKYLL_CONFIG=_config.yml,_build/config-production.yml,_build/config-exclude-all-but-guide.yml,_build/config-guide-root.yml
    DIRS_TO_MOVE[0]=guide
    DIRS_TO_MOVE_TARGET[0]=""
    JAVADOC_TARGET=_site/use/api/
    SUMMARY="user guide files in the root"
    ;;
  test-both)
    JEKYLL_CONFIG=_config.yml,_build/config-production.yml,_build/config-website-root.yml,_build/config-guide-latest.yml
    DIRS_TO_MOVE[0]=guide
    DIRS_TO_MOVE_TARGET[0]=v/latest
    DIRS_TO_MOVE[1]=website
    DIRS_TO_MOVE_TARGET[1]=""
    JAVADOC_TARGET=_site/${DIRS_TO_MOVE_TARGET[0]}/use/api/
    SUMMARY="all files, website in root and guide in /${DIRS_TO_MOVE_TARGET[0]}"
    ;;
  test-both-sub)
    JEKYLL_CONFIG=_config.yml,_build/config-production.yml,_build/config-subpath-brooklyn.yml
    DIRS_TO_MOVE[0]=guide
    DIRS_TO_MOVE_TARGET[0]=brooklyn/v/latest
    DIRS_TO_MOVE[1]=website
    DIRS_TO_MOVE_TARGET[1]=brooklyn
    DIRS_TO_MOVE[2]=style
    DIRS_TO_MOVE_TARGET[2]=brooklyn/style
    JAVADOC_TARGET=_site/${DIRS_TO_MOVE_TARGET[0]}/use/api/
    SUMMARY="all files in /brooklyn"
    ;;
  original)
    JEKYLL_CONFIG=_config.yml,_build/config-production.yml
    SUMMARY="all files in their original place"
    ;;
  "")
    echo "ERROR: arguments are required; try 'help'"
    exit 1 ;;
  *)
    echo "ERROR: invalid argument '$1'; try 'help'"
    exit 1 ;;
  esac
}

function parse_arguments() {
  while (( "$#" )); do
    case $1 in
    "--serve")
      SERVE_AFTERWARDS=true
      shift
      ;;
    "--skip-javadoc")
      SKIP_JAVADOC=true
      shift
      ;;
    "--")
      shift
      break
      ;;
    *)
      break
      ;;
    esac
  done
  JEKYLL_ARGS="$@"
}

function make_jekyll() {
  echo JEKYLL running with: jekyll build $JEKYLL_CONFIG $JEKYLL_ARGS
  jekyll build --config $JEKYLL_CONFIG $JEKYLL_ARGS || return 1
  echo JEKYLL completed
  for DI in "${!DIRS_TO_MOVE[@]}"; do
    D=${DIRS_TO_MOVE[$DI]}
    DT=${DIRS_TO_MOVE_TARGET[$DI]}
    echo moving _site/$D/ to _site/$DT
    mkdir -p _site/$DT
    # the generated files are already in _site/ due to url rewrites along the way, but images etc are not
    cp -r _site/$D/* _site/$DT
    rm -rf _site/$D
  done
  # normally we exclude things but we can also set TARGET as long_grass and it will get destroyed
  rm -rf _site/long_grass
}

function make_javadoc() {
  if [ "$SKIP_JAVADOC" == "true" ]; then
    return
  fi
  pushd _build > /dev/null
  rm -rf target/apidocs
  ./make-javadoc.sh || { echo ERROR: failed javadoc build ; exit 1 ; }
  popd > /dev/null
  if [ ! -z "$JAVADOC_TARGET" ]; then
    mv _build/target/apidocs/* $JAVADOC_TARGET
  fi
}

rm -rf _site

parse_command $@
shift
parse_arguments $@

make_jekyll || { echo ERROR: failed jekyll docs build in `pwd` ; exit 1 ; }

make_javadoc || { echo ERROR: failed javadoc build ; exit 1 ; }

# TODO build catalog

echo FINISHED: $SUMMARY of `pwd`/_site 

if [ $SERVE_AFTERWARDS == "true" ]; then
  _build/serve-site.sh
fi
