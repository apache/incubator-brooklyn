---
layout: website-normal
title: Environment variables for the release
navgroup: developers
---

Many example commands in this section using variable names as placeholders for information that will vary between
releases. To allow these example commands to run unmodified, set these environment variables appropriately.

{% highlight bash %}
# The version currently set on the master branch
OLD_MASTER_VERSION=0.7.0-SNAPSHOT
# The next version to be set on the master branch
NEW_MASTER_VERSION=0.8.0-SNAPSHOT

# The version we are releasing now. While Brooklyn is in the Apache Incubator, this must be suffixed `-incubating`.
VERSION_NAME=0.7.0-incubating

# The release candidate number we are making now.
RC_NUMBER=1
{% endhighlight %}
