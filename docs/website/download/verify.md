---
layout: website-normal
title: Verify the Integrity of Downloads
---

It is essential for security that you verify the integrity of the downloaded files using their PGP signatures or SHA-1 checksums.


## Verifying hashes

You can download MD5, SHA1 and SHA256 hashes using these links. Note that these links are for first-class Apache
Software Foundation mirrors so there will be reduced opportunity for tampering with these files.

<table class="table">
<tr>
<th>Artifact</th>
<th colspan="2">MD5 hash</th>
<th colspan="2">SHA1 hash</th>
<th colspan="2">SHA256 hash</th>
</tr>
<tr>
<td>apache-brooklyn-0.7.0-M2-incubating.tar.gz</td>
<td><a href="https://www.us.apache.org/dist/incubator/brooklyn/0.7.0-M2-incubating/apache-brooklyn-0.7.0-M2-incubating.tar.gz.md5">US</a></td>
<td><a href="https://www.eu.apache.org/dist/incubator/brooklyn/0.7.0-M2-incubating/apache-brooklyn-0.7.0-M2-incubating.tar.gz.md5">EU</a></td>
<td><a href="https://www.us.apache.org/dist/incubator/brooklyn/0.7.0-M2-incubating/apache-brooklyn-0.7.0-M2-incubating.tar.gz.sha1">US</a></td>
<td><a href="https://www.eu.apache.org/dist/incubator/brooklyn/0.7.0-M2-incubating/apache-brooklyn-0.7.0-M2-incubating.tar.gz.sha1">EU</a></td>
<td><a href="https://www.us.apache.org/dist/incubator/brooklyn/0.7.0-M2-incubating/apache-brooklyn-0.7.0-M2-incubating.tar.gz.sha256">US</a></td>
<td><a href="https://www.eu.apache.org/dist/incubator/brooklyn/0.7.0-M2-incubating/apache-brooklyn-0.7.0-M2-incubating.tar.gz.sha256">EU</a></td>
</tr>
</table>

You can verify the SHA1 or SHA256 hashes easily by placing the files in the same folder as the download artifact and
then running `shasum`, which is included in most UNIX-like systems:

{% highlight bash %}
shasum -c apache-brooklyn-{{ site.data.brooklyn.version }}.tar.gz.sha1
shasum -c apache-brooklyn-{{ site.data.brooklyn.version }}.tar.gz.sha256
{% endhighlight %}

You can verify the MD5 hashes by running a command like this, and comparing the output to the contents of the `.md5` file:

{% highlight bash %}
md5 apache-brooklyn-{{ site.data.brooklyn.version }}.tar.gz
{% endhighlight %}


### Verifying PGP signatures using PGP or GPG

You can download PGP/GPG signatures using these links. Note that these links are for first-class Apache
Software Foundation mirrors so there will be reduced opportunity for tampering with these files.

<table class="table">
<tr>
<th>Artifact</th>
<th colspan="2">Mirror</th>
</tr>
<tr>
<td>Release Manager's public keys (KEYS)</td>
<td><a href="https://www.us.apache.org/dist/incubator/brooklyn/KEYS">US</a></td>
<td><a href="https://www.eu.apache.org/dist/incubator/brooklyn/KEYS">EU</a></td>
</tr>
<tr>
<td>apache-brooklyn-0.7.0-M2-incubating.tar.gz.asc</td>
<td><a href="https://www.us.apache.org/dist/incubator/brooklyn/0.7.0-M2-incubating/apache-brooklyn-0.7.0-M2-incubating.tar.gz.asc">US</a></td>
<td><a href="https://www.eu.apache.org/dist/incubator/brooklyn/0.7.0-M2-incubating/apache-brooklyn-0.7.0-M2-incubating.tar.gz.asc">EU</a></td>
</tr>
</table>

In order to validate the release signature, download both the release `.asc` file for the release, and the `KEYS` file
which contains the public keys of key individuals in the Apache Brooklyn project.

Verify the signatures using one of the following commands:

	pgpk -a KEYS
	pgpv brooklyn-{{ site.brooklyn-version }}-dist.tar.gz.asc

or

	pgp -ka KEYS
	pgp brooklyn-{{ site.brooklyn-version }}-dist.zip.asc

or

	gpg --import KEYS
	gpg --verify brooklyn-{{ site.brooklyn-version }}-dist.tar.gz.asc
