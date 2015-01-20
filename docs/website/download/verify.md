---
layout: website-normal
title: Verify the Integrity of Downloads
---

You can verify the integrity of the downloaded files using their PGP signatures or SHA-1 checksums.


## Verifying Hashes

To verify the downloads, first get the MD5, SHA1 and/or SHA256 hashes using these links. 
Note that all links are for first-class Apache Software Foundation mirrors 
so there is already reduced opportunity for anyone maliciously tampering with these files.

<table class="table">
<tr>
<th>Artifact</th>
<th colspan="3">Hashes</th>
</tr>

{% for v in site.brooklyn-stable-versions %}
<tr>
<td>apache-brooklyn-{{ v }}.tar.gz</td>
<td><a href="https://www.apache.org/dist/incubator/brooklyn/{{ v }}/apache-brooklyn-{{ v }}.tar.gz.md5">md5</a></td>
<td><a href="https://www.apache.org/dist/incubator/brooklyn/{{ v }}/apache-brooklyn-{{ v }}.tar.gz.sha1">sha1</a></td>
<td><a href="https://www.apache.org/dist/incubator/brooklyn/{{ v }}/apache-brooklyn-{{ v }}.tar.gz.sha256">sha256</a></td>
</tr>
{% endfor %}
</table>

You can verify the SHA1 or SHA256 hashes easily by placing the files in the same folder as the download artifact and
then running `shasum`, which is included in most UNIX-like systems:

{% highlight bash %}
shasum -c apache-brooklyn-{{ site.brooklyn-stable-version }}.tar.gz.sha1
shasum -c apache-brooklyn-{{ site.brooklyn-stable-version }}.tar.gz.sha256
{% endhighlight %}

You can verify the MD5 hashes by running a command like this, and comparing the output to the contents of the `.md5` file:

{% highlight bash %}
md5 apache-brooklyn-{{ site.brooklyn-stable-version }}.tar.gz
{% endhighlight %}


### Verifying PGP Signatures using PGP or GPG

You can download PGP/GPG signatures using these links. Note that these links are for first-class Apache
Software Foundation mirrors so there will be reduced opportunity for tampering with these files.

<table class="table">
<tr>
<th>Artifact</th>
<th colspan="2">Link</th>
</tr>
<tr>
<td>Release Manager's public keys</td>
<td><a href="https://www.apache.org/dist/incubator/brooklyn/KEYS">KEYS</a></td>
</tr>

{% for v in site.brooklyn-stable-versions %}
<tr>
<td>apache-brooklyn-{{ site.brooklyn-stable-version }}.tar.gz.asc</td>
<td><a href="https://www.apache.org/dist/incubator/brooklyn/{{ site.brooklyn-stable-version }}/apache-brooklyn-{{ site.brooklyn-stable-version }}.tar.gz.asc">asc</a></td>
</tr>
{% endfor %}

</table>

In order to validate the release signature, download both the release `.asc` file for the release, and the `KEYS` file
which contains the public keys of key individuals in the Apache Brooklyn project.

Verify the signatures using one of the following commands:

{% highlight bash %}
pgpk -a KEYS
pgpv brooklyn-{{ site.brooklyn-stable-version }}-dist.tar.gz.asc
{% endhighlight %}

or

{% highlight bash %}
pgp -ka KEYS
pgp brooklyn-{{ site.brooklyn-stable-version }}-dist.zip.asc
{% endhighlight %}

or

{% highlight bash %}
gpg --import KEYS
gpg --verify brooklyn-{{ site.brooklyn-stable-version }}-dist.tar.gz.asc
{% endhighlight %}
