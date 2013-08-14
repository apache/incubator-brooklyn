---
layout: page
title: Release Process
toc: /toc.json
---

<!--
TODO

vote required?  see governance.


     branch-twice-then-reversion-twice
     e.g. from master=1.0.0_SNAPSHOT we will go to
          create branch: v1.0.0_SNAPSHOT
          reversion master:  1.1.0_SNAPSHOT
          create branch and reversion:  v1.0.0_RC1, v1.0.0_SNAPSHOT
     describe scripts for releasing
     docs

update version, using scripts

push examples to repo

push docs to branch and publish

-->

Brooklyn is published to two locations:

* Sonatype, for snapshots and for staging releases
* Maven Central, for full (GA and milestone) releases

Brooklyn artifacts are generally downloaded from:

1. [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.brooklyn%22),
2. [Sonatype](https://oss.sonatype.org/index.html#nexus-search;quick~io.brooklyn),
3. [GitHub](https://github.com/brooklyncentral/brooklyn).


To publish:

* a snapshot release:
	* mvn deploy to Sonatype
	* (optional) publish versioned docs to brooklyncentral.github.com project
* a (milestone) release:
	* same as above, but with some git versioning 
	* deploy to Sonatype, then release to Maven Central
	* deploy a version branch to brooklyn-examples 
	* deploy (or update) versioned docs
* a major release:
	* same as above, and
	* in addition to versioned examples,  update brooklyn-examples master to match the current (stable) release
	* in addition to versioned docs, publish full (front page) docs to brooklyncentral.github.com project
	* bump the snapshot version in brooklyn master to the next release


## Preparing a Snapshot Release

### Prepare a Release Branch

{% highlight bash %}

export BROOKLYN_DIR=/path/to/brooklyncentral-brooklyn
export EXAMPLES_DIR=/path/to/brooklyncentral-brooklyn-examples
export SITE_DIR=/path/to/brooklyncentral-brooklyncentral.github.com

export SNAPSHOT_VERSION=0.6.0-SNAPSHOT
export RELEASE_VERSION=0.6.0-M1

cd $BROOKLYN_DIR
git checkout -b $RELEASE_VERSION
usage/scripts/change-version.sh $SNAPSHOT_VERSION $RELEASE_VERSION
git commit -a -m "Changed version to $RELEASE_VERSION"
git push -u central $RELEASE_VERSION

{% endhighlight %}
	

### Deploying to Sonatype

Your .m2/settings.xml must be configured with the right credentials for Sonatype

  	<servers>
	...
		<server>
			<username> ... </username>
			<password> ... </password>
			<id>sonatype-nexus-snapshots</id>
		</server>
		<server>
			<username> ... </username>
			<password> ... </password>
			<id>sonatype-nexus-staging</id>
		</server>
	...
	</servers>

Some good tips are at:  https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide
You must be configured to sign artifacts using PGP.

Execute the following:
{% highlight bash %}
mvn -Dbrooklyn.deployTo=sonatype -DskipTests clean install deploy
{% endhighlight %}

* Go to [oss.sonatype.org ... #stagingRepositories](https://oss.sonatype.org/index.html#stagingRepositories) (again, need credentials)
* 'Close' the repo
* Email the closed repo address to brooklyn-dev list, have people download and confirm it works.

### Update the Examples Repo Version Branch

{% highlight bash %}

cd $EXAMPLES_DIR

pushd $BROOKLYN_DIR
git checkout $RELEASE_VERSION
popd

if [ ! -d simple-web-cluster ] ; then echo "wrong dir" ; exit 1 ; fi
git checkout master
git checkout -b $RELEASE_VERSION
rm -rf *
cp -r $BROOKLYN_DIR/examples/* .
rm -rf target
git add -A
git commit -m "branch for $RELEASE_VERSION"
git push -u origin $RELEASE_VERSION

{% endhighlight %}


### Update the Versioned Docs

{% highlight bash %}

cd $BROOKLYN_DIR/docs
git checkout $RELEASE_VERSION

if [ ! -f $SITE_DIR/index.html ] ; then echo "could not find docs in $SITE_DIR" ; exit 1 ; fi

# Build the docs
_scripts/build.sh || { echo "failed to build docs" ; exit 1 ; }

# Wipe any previous edition of the same version, replacing with new build.
rm -rf $SITE_DIR/v/$RELEASE_VERSION
mkdir $SITE_DIR/v/$RELEASE_VERSION
cp -r _site/* $SITE_DIR/v/$RELEASE_VERSION/

# and push, causing GitHub to republish with updated /v/$RELEASE_VERSION/
pushd $SITE_DIR
git add -A .
git commit -m "Updated version docs for version $RELEASE_VERSION"
git push
popd

{% endhighlight %}
	
## Preparing a Full/Milestone Release

Complete *all* above steps for a Snapshot release.

### Deploy to Maven Central

* Confirm that the closed Sonatype repo has no errors
* Return to [Sonatype: Staging Repositories](https://oss.sonatype.org/index.html#stagingRepositories)
* 'Release' the repo

### Deploy the Examples master branch.

{% highlight bash %}

cd $EXAMPLES_DIR

pushd $BROOKLYN_DIR
git checkout $RELEASE_VERSION
popd

if [ ! -d simple-web-cluster ] ; then echo "wrong dir" ; exit 1 ; fi
git checkout master
rm -rf *
cp -r $BROOKLYN_DIR/examples/* .
rm -rf target
git add -A
git commit -m "Updated to $RELEASE_VERSION"
git push -u origin master

{% endhighlight %}

### Update the brooklyn.io Front Page Version

{% highlight bash %}

cd $BROOKLYN_DIR/docs

pushd $SITE_DIR
# remove old root files, but not the previous version in /v/
if [ -f start/index.html ] ; then
  for x in * ; do if [[ $x != "v" ]] ; then rm -rf $x ; fi ; done
else
  echo IN WRONG DIRECTORY $SITE_DIR - export SITE_DIR to continue
  exit 1
fi
popd

# re-build for hosting at / rather than at /v/VERSION/
_scripts/build.sh --url "" || { echo "failed to build docs" ; exit 1 ; }

# copy to site dir
cp -r _site/* $SITE_DIR/

# and git push
pushd $SITE_DIR
git add -A .
git commit -m "Updated root docs for version $RELEASE_VERSION"
git push
popd

{% endhighlight %}


### Announce
* Email the Dev and Users mailing lists.
* Tweet?

### Update Snapshot Version

{% highlight bash %}

export NEW_SNAPSHOT_VERSION=0.7.0-SNAPSHOT

cd $BROOKLYN_DIR
git checkout master
usage/scripts/change-version.sh $SNAPSHOT_VERSION $NEW_SNAPSHOT_VERSION
git commit -a -m "Changed version to $NEW_SNAPSHOT_VERSION"
git push -u central master

{% endhighlight %}