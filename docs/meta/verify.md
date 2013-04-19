---
layout: page
title: Verify the integrity of the files
toc: /toc.json
---
{% include fields.md %}

It is essential that you verify the integrity of the downloaded files using the PGP signatures or SHA-1 checksums.

The PGP signatures can be verified using PGP or GPG. First download the [brooklyn-gpg-public-key.asc](brooklyn-gpg-public-key.asc)
file as well as the `.asc` PGP signature file for the relevant artefact. Make sure you get these files from the main
{% if SNAPSHOT %}
[Maven Central]({{ mavencentral_repo_groupid_url }})
{% else %}
[Sonatype]({{ sonatype_repo_groupid_url }})
{% endif %}
repository rather than from a mirror. Then verify the signatures using one of the following commands:

```
% pgpk -a brooklyn-gpg-public-key.asc
% pgpv brooklyn-{{ site.brooklyn-version }}-dist.tar.gz.asc
```

or

```
% pgp -ka brooklyn-gpg-public-key.asc
% pgp brooklyn-{{ site.brooklyn-version }}-dist.zip.asc
```

or

```
% gpg --import brooklyn-gpg-public-key.asc
% gpg --verify brooklyn-{{ site.brooklyn-version }}-dist.tar.gz.asc
```

You can also verify the SHA-1 checksum of the files. A program called `sha1` or `sha1sum` is included in
most Linux distributions and OSX. For Windows users, `fsum` supports SHA-1. Ensure your generated checksum
string matches the contents of the `.sha1` file for the relevant artefact, again making sure you get this
file from the main
{% if SNAPSHOT %}
[Maven Central]({{ mavencentral_repo_groupid_url }})
{% else %}
[Sonatype]({{ sonatype_repo_groupid_url }})
{% endif %}
repository, rather than from a mirror.
