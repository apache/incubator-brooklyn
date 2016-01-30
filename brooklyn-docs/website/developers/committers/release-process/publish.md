---
layout: website-normal
title: Publish to the public
navgroup: developers
---

Publish the source and binary distributions to the pre-release area
-------------------------------------------------------------------

You will need to have checked out the Apache distribution Subversion repository located at
https://dist.apache.org/repos/dist/release/incubator/brooklyn. Please refer to [Prerequisites](prerequisites.html) for
information.

In your workspace for the `dist.apache.org` repo, create a directory with the artifact name and version:

{% highlight bash %}
mkdir apache-brooklyn-${VERSION_NAME}
{% endhighlight %}

Refer back to the pre-release area Subversion (see [Publish to the staging area](publish-temp.html)), and copy all of
the release candidate artifacts - `-src` and `-bin`, `.tar.gz` and `.zip`, and all associated `.md5`, `.sha1`, `.sha256`
and `.asc` signatures - into this new folder.

Rename all of the files to remove the `-rcN` designation. If you have the `mmv` tool installed, this can be done with
this command:

{% highlight bash %}
mmv -v '*-rc'$RC_NUMBER'-*' '#1-#2'
{% endhighlight %}

The hash files will need patching to refer to the filenames without the `-rcN` designation:

{% highlight bash %}
sed -i.bak 's/-rc'$RC_NUMBER'-/-/' *.md5 *.sha1 *.sha256
rm -f *.bak
{% endhighlight %}

Note that the PGP signatures do not embed the filename so they do not need to be modified

As a final check, re-test the hashes and signatures:

{% highlight bash %}
for ext in -src.tar.gz -src.zip -bin.tar.gz -bin.zip; do
    artifact=apache-brooklyn-${VERSION_NAME}${ext}
    md5sum -c ${artifact}.md5
    shasum -a1 -c ${artifact}.sha1
    shasum -a256 -c ${artifact}.sha256
    gpg2 --verify ${artifact}.asc ${artifact}
done
{% endhighlight %}

(You may get warnings such as: `gpg: WARNING: This key is not certified with a trusted signature!` 
and `There is no indication that the signature belongs to the owner.` This happens if you have not trusted
the person's key. A key-signing party is a good way to extend this web of trust).


Then, add them to Subversion and commit.

{% highlight bash %}
svn add apache-brooklyn-${VERSION_NAME}
svn commit --message "Add apache-brooklyn-${VERSION_NAME} to dist/release/incubator/brooklyn"
{% endhighlight %}


Publish the staging repository on Apache's Nexus server
-------------------------------------------------------

*Releasing* the staging repository causes its contents to be copied to the main Apache Nexus repository. This in turn
is propagated to Maven Central, meaning all of our users can access the artifacts using a default Maven configuration
(there's no need to add a `<repository>` to their `pom.xml` or `~/.m2/settings.xml`).

Go to the Apache Nexus server at [https://repository.apache.org/](https://repository.apache.org/) and log in using the
link in the top right (the credentials are the same as your Git and Jenkins credentials). Go to the "Staging
Repositories" page, and tick the repository with the name starting `orgapachebrooklyn`. Click the **Release** button.
Provide a description which includes the version, e.g. `Apache Brooklyn 0.7.0-incubating`.

Note there is only one orgapachebrooklyn staging repository at a time; this will be the one created for the release
candidate with whatever name was used there (e.g. it might include "rc" in the name). If you really want, you can 
double-check under the "content" that brooklyn-dist has artifacts without rc in the name.


Update the website
------------------

*Instructions on uploading to the website are beyond the scope of these instructions. Refer to the 
[appropriate instructions](https://github.com/apache/incubator-brooklyn/tree/master/brooklyn-docs).*

### Publish documentation for the new release

Go to the release branch and perform a build:

{% highlight bash %}
git checkout ${VERSION_NAME}
mvn clean install -DskipTests
{% endhighlight %}

Ensure the SVN repo is up-to-date (very painful otherwise!)

{% highlight bash %}
cd ${BROOKLYN_SITE_DIR-../incubator-brooklyn-site-public}
svn up
cd -
{% endhighlight %}

Generate the permalink docs for the release:

{% highlight bash %}
cd brooklyn-docs
./_build/build.sh guide-version --install
{% endhighlight %}

Now publish _site/v/*${VERSION_NAME}* to the public website.

Update the "latest" docs to this release:

{% highlight bash %}
./_build/build.sh guide-latest --install
{% endhighlight %}

Now publish _site/v/latest to the public website:

{% highlight bash %}
cd ${BROOKLYN_SITE_DIR-../../incubator-brooklyn-site-public}
svn add * --force
export DELETIONS=$( svn status | sed -e '/^!/!d' -e 's/^!//' )
if [ ! -z "${DELETIONS}" ] ; then svn rm ${DELETIONS} ; fi
{% endhighlight %}


### Update the main website to link to the new release

This should be done on the `master` branch:

{% highlight bash %}
git checkout master
{% endhighlight %}

1. Edit the file `brooklyn-docs/_config.yml` - change `brooklyn-stable-version` to be the newly-release version, and
   `brooklyn-version` to be the current SNAPSHOT version on the master branch.
2. Edit the file `brooklyn-docs/website/download/verify.md` to add links to the MD5/SHA1/SHA256 hashes and PGP signatures for the
   new version.
3. Edit the file `brooklyn-docs/website/meta/versions.md` to add the new version.
4. Build the updated site with `./_build/build.sh website-root --install`.
5. Publish to the public website.
6. Commit your changes to master, e.g. with a message like "Update latest docs to 0.8.0-incubating"


Tag the release in git
----------------------

Make a signed tag for this release, based on the tag for the release candidate, and then push the tag:

{% highlight bash %}
git tag -s -m "Tag release ${VERSION_NAME}" apache-brooklyn-${VERSION_NAME} apache-brooklyn-${VERSION_NAME}-rc${RC_NUMBER}
git push apache apache-brooklyn-${VERSION_NAME}
{% endhighlight %}

Note the tag `apache-brooklyn-${VERSION_NAME}-rc${RC_NUMBER}` should have been created as part of the
RC creation - see [make-release-artifacts](make-release-artifacts.html).

