---
layout: website-normal
title: Make the release artifacts
navgroup: developers
---

A release script is provided in `brooklyn-dist/release/make-release-artifacts.sh`. This script will prepare all the release artifacts.
It is written to account for several Apache requirements, so you are strongly advised to use it rather than "rolling your own".

The release script will:

- **Create source code and binary distribution artifacts** and place them in a temporary staging directory on your workstation, usually `brooklyn-dist/release/tmp/`.
- **Create Maven artifacts and upload them to a staging repository** located on the Apache Nexus server.

The script has a single required parameter `-r` which is given the release candidate number - so `-r1` will create
release candidate 1 and will name the artifacts appropriately.

Before running this however, it is a good idea to check the `apache-release` profile build is healthy.
This will catch glitches such as PGP or javadoc problems without doing time-consuming uploads:

{% highlight bash %}
mvn clean install -Papache-release
{% endhighlight %}

To run the script:

{% highlight bash %}
./brooklyn-dist/release/make-release-artifacts.sh -r$RC_NUMBER
{% endhighlight %}

It will show you the release information it has deduced, and ask yes-or-no if it can proceed. Please note that the
script will thoroughly clean the Git workspace of all uncommited and unadded files.

**You really probably want to run this against a secondary checkout.** It will wipe `.project` files and other IDE metadata, and bad things can happen if an IDE tries to write while the script is running. Also as it takes a long time, this means your workspace is not tied up. One quick and easy way to do this is to `git clone` the local directory of your primary checkout to a secondary location.

A few minutes into the script you will be prompted for the passphrase to your GnuPG private key. You should only be
asked this question once; the GnuPG agent will cache the password for the remainder of the build.

Please note that uploading to the Nexus staging repository is a slow process. Expect this stage of the build to take
2 hours.

The release script will:

1. Prepare a staging directory for the source code release
2. Create .tar.gz and .zip artifacts of the source code
3. Invoke Maven to build the source code (including running unit tests), and deploy artifacts to a Maven remote
   repository
4. Save the .tar.gz and .zip artifacts produced by the build of `brooklyn-dist`
5. For each of the produced files, produce MD5, SHA1, SHA256 and GnuPG signatures

At the end of the script, it will show you the files it has produced and their location.

Make a signed tag for this release, and then push the tag:

{% highlight bash %}
git tag -s -m "Tag release ${VERSION_NAME} release candidate ${RC_NUMBER}" apache-brooklyn-${VERSION_NAME}-rc${RC_NUMBER}
git push apache apache-brooklyn-${VERSION_NAME}-rc${RC_NUMBER}
{% endhighlight %}
