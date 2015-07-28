---
layout: website-normal
title: Release Prerequisites
navgroup: developers
---

Subversion repositories for release artifacts
---------------------------------------------

Apache releases are posted to dist.apache.org, which is a Subversion repository.

We have two directories here:

- https://dist.apache.org/repos/dist/release/incubator/brooklyn - this is where IPMC approved releases go. Do not upload
  here until we have a vote passed on dev@brooklyn and incubator-general. Check out this folder and name it
  `apache-dist-release-brooklyn`
- https://dist.apache.org/repos/dist/dev/incubator/brooklyn - this is where releases to be voted on go. Make the release
  artifact, and post it here, then post the [VOTE] thread with links here. Check out this folder and name it
  `apache-dist-dev-brooklyn`.

Example:

{% highlight bash %}
svn co https://dist.apache.org/repos/dist/release/incubator/brooklyn apache-dist-release-brooklyn
svn co https://dist.apache.org/repos/dist/dev/incubator/brooklyn apache-dist-dev-brooklyn
{% endhighlight %}

When working with these folders, **make sure you are working with the correct one**, otherwise you may be publishing
pre-release software to the global release mirror network!


GPG keys
--------

The release manager must have a GPG key to be used to sign the release.

If you have an existing GPG key, but it does not include your Apache email address, you can add your email address as
described [in this Superuser.com posting](https://superuser.com/a/293283). Otherwise, create a new GPG key giving your
Apache email address.

Upload your GPG public key (complete with your Apache email address on it) to a public keyserver - e.g. run
`gpg2 --export --armor richard@apache.org` and paste it into the “submit” box on http://pgp.mit.edu/

Look up your key fingerprint with `gpg2 --fingerprint richard@apache.org` - it’s the long sequence of hex numbers
separated by spaces. Log in to [https://id.apache.org/](https://id.apache.org/) then copy-and-paste the fingerprint into
“OpenPGP Public Key Primary Fingerprint”. Submit.

Now add your key to the `apache-dist-release-brooklyn/KEYS` file:

{% highlight bash %}
cd apache-dist-release-brooklyn
(gpg2 --list-sigs richard@apache.org && gpg2 --armor --export richard@apache.org) >> KEYS
svn commit -m 'Update incubator/brooklyn/KEYS'
{% endhighlight %}

References: [Post on the general@incubator list](https://mail-archives.apache.org/mod_mbox/incubator-general/201410.mbox/%3CCAOGo0VawupMYRWJKm%2Bi%2ByMBqDQQtbv-nQkfRud5%2BV9PusZ2wnQ%40mail.gmail.com%3E)


Software packages
-----------------

The following software packages are required during the build. Make sure you have them installed.

- A Java Development Kit, version 1.7
- `maven` and `git`
- `xmlstarlet` is required by the release script to process version numbers in `pom.xml` files
- `zip` and `unzip`
- `pinentry` for secure entry of GPG passphrases. If you are building remotely on a Linux machine, `pinentry-curses` is
  recommended; building on a mac, `port install pinentry-mac` is recommended.
- `gnupg2`, and `gnupg-agent` if it is packaged separately (it is on Ubuntu Linux)
- `md5sum` and `sha1sum` - these are often present by default on Linux, but not on Mac. MacPorts provides these in the
  package `md5sha1sum`.


Maven configuration
-------------------

The release will involve uploading artifacts to Apache's Nexus instance - therefore you will need to configure your
Maven install with the necessary credentials.

You will need to add something like this to your `~/.m2/settings.xml` file:

{% highlight xml %}
<?xml version="1.0"?>
<settings xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.1.0 http://maven.apache.org/xsd/settings-1.1.0.xsd"
          xmlns="http://maven.apache.org/SETTINGS/1.1.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

    <!-- ... -->

    <servers>

        <!-- ... -->

        <!-- Required for uploads to Apache's Nexus instance. These are LDAP credentials - the same credentials you
           - would use to log in to Git and Jenkins (but not JIRA) -->
        <server>
            <id>apache.snapshots.https</id>
            <username>xxx</username>
            <password>xxx</password>
        </server>
        <server>
            <id>apache.releases.https</id>
            <username>xxx</username>
            <password>xxx</password>
        </server>

        <!-- ... -->

    </servers>

    <!-- ... -->

</settings>
{% endhighlight %}
