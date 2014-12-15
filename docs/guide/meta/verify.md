---
layout: guide-normal
title: Verify the Integrity of Downloads
toc: /toc.json
---
{% include fields.md %}

It is essential for security that you verify the integrity of the downloaded files using their PGP signatures or SHA-1 checksums.

### Verifying PGP signatures using PGP or GPG

Download the [brooklyn-gpg-public-key.asc](brooklyn-gpg-public-key.asc)
file and the `.asc` PGP signature file for the relevant artefact.

(Make sure you get these files from the main {% if SNAPSHOT %}[Maven Central]({{ mavencentral_repo_groupid_url }}){% else %}[Sonatype]({{ sonatype_repo_groupid_url }}){% endif %} repository rather than from a mirror.)

Verify the signatures using one of the following commands:

	pgpk -a brooklyn-gpg-public-key.asc
	pgpv brooklyn-{{ site.brooklyn-version }}-dist.tar.gz.asc

or

	pgp -ka brooklyn-gpg-public-key.asc
	pgp brooklyn-{{ site.brooklyn-version }}-dist.zip.asc

or

	gpg --import brooklyn-gpg-public-key.asc	
	gpg --verify brooklyn-{{ site.brooklyn-version }}-dist.tar.gz.asc

You can also verify the SHA-1 checksum of the files.

A program called `sha1` or `sha1sum` is included in most Linux distributions and OSx. For Windows users, `fsum` supports SHA-1. 

Ensure the generated checksum string matches the contents of the `.sha1` file for the relevant artefact (and again download from {% if SNAPSHOT %}[Maven Central]({{ mavencentral_repo_groupid_url }}){% else %}[Sonatype]({{ sonatype_repo_groupid_url }}){% endif %} repository, rather than from a mirror).
