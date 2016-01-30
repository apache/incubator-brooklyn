---
layout: website-normal
title: Vote on dev@brooklyn
navgroup: developers
---

Start the vote
--------------

A script to generate the voting email can be found in `brooklyn-dist/release/print-vote-email.sh`,
taking a single argument being the staging repo link. For example:

    brooklyn-dist/release/print-vote-email.sh orgapachebrooklyn-1234 | pbcopy

You should move the subject and put your name at the end, and simply eyeball the rest. This should be sent to **dev@brooklyn.incubator.apache.org**.

Alternatively, copy-paste the e-mail template below, being sure to substitute:

- VERSION_NAME
- RC_NUMBER
- URLs containing version numbers
- URL for your own PGP key
- Checksums
- URL for the Maven staging repository

### Subject: [VOTE] Release Apache Brooklyn ${VERSION_NAME} [rc${RC_NUMBER}]

{% highlight text %}
This is to call for a vote for the release of Apache Brooklyn ${VERSION_NAME}.

This release comprises of a source code distribution, and a corresponding
binary distribution, and Maven artifacts.

The source and binary distributions, including signatures, digests, etc. can
be found at:
https://dist.apache.org/repos/dist/dev/incubator/brooklyn/apache-brooklyn-${VERSION_NAME}-rc${RC_NUMBER}

The artifact SHA-256 checksums are as follows:
c3b5c581f14b44aed786010ac7c8c2d899ea0ff511135330395a2ff2a30dd5cf *apache-brooklyn-${VERSION_NAME}-rc${RC_NUMBER}-bin.tar.gz
cef49056ba6e5bf012746a72600b2cee8e2dfca1c39740c945c456eacd6b6fca *apache-brooklyn-${VERSION_NAME}-rc${RC_NUMBER}-bin.zip
8069bfc54e7f811f6b57841167b35661518aa88cabcb070bf05aae2ff1167b5a *apache-brooklyn-${VERSION_NAME}-rc${RC_NUMBER}-src.tar.gz
acd2229c44e93e41372fd8b7ea0038f15fe4aaede5a3bcc5056f28a770543b82 *apache-brooklyn-${VERSION_NAME}-rc${RC_NUMBER}-src.zip

The Nexus staging repository for the Maven artifacts is located at:
https://repository.apache.org/content/repositories/orgapachebrooklyn-1004

All release artifacts are signed with the following key:
https://people.apache.org/keys/committer/richard.asc

KEYS file available here:
https://dist.apache.org/repos/dist/release/incubator/brooklyn/KEYS

The artifacts were built from Git commit ID
24a23c5a4fd5967725930b8ceaed61dfbd225980
https://git-wip-us.apache.org/repos/asf?p=incubator-brooklyn.git;a=commit;h=24a23c5a4fd5967725930b8ceaed61dfbd225980


Please vote on releasing this package as Apache Brooklyn ${VERSION_NAME}.

The vote will be open for at least 72 hours.
[ ] +1 Release this package as Apache Brooklyn ${VERSION_NAME}
[ ] +0 no opinion
[ ] -1 Do not release this package because ...


Thanks,
[Release manager name]
{% endhighlight %}


Reply to vote
-------------

Here is an example checklist (thanks Andrew Phillips for your thoroughness on jclouds!)

Checklist (all items optional, mark only those personally verified):

- [ ] Checksums and PGP signatures are valid.
- [ ] Expanded source archive matches contents of RC tag.
- [ ] Expanded source archive builds and passes tests.
- [ ] LICENSE is present and correct.
- [ ] NOTICE is present and correct, including copyright date.
- [ ] All files have license headers where appropriate.
- [ ] All dependencies have compatible licenses.
- [ ] No compiled archives bundled in source archive.
- [ ] I follow this project's commits list.


Count the vote results
----------------------

If the release email stated a deadline (normally 72 hours), then you should wait at least that long. If there are
insufficient votes you may need to extend the deadline - as an informal aim, we should look to get 2/3rds of the PPMC
and some mentors voting +1. If a release-critical issue is raised and confirmed, then you may end the vote early with a
negative result.

Votes from PPMC members are binding; votes from others are non-binding. In the case of non-binding negative votes,
please consider these carefully even if you are not bound by them.

If there are any negative or zero votes, consider these carefully. Aim to “convert” negative or zero votes into positive
by addressing any concerns. A negative vote is not necessarily a veto[citation required], but it should be a clear
warning sign to not proceed if somebody strongly believes that the release should not proceed as is.

Finally, count up the +1s and separate into binding (PPMC) and non-binding.

Email the vote result
---------------------

This is a new email thread with a different subject
(the same as before with `[RESULT]` prepended).

Note that you must find the URL for the previous thread at [mail-archives.apache.org](https://mail-archives.apache.org/).

### Subject: [RESULT]\[VOTE] Release Apache Brooklyn ${VERSION_NAME} [rc${RC_NUMBER}]

{% highlight text %}
The vote for releasing Apache Brooklyn ${VERSION_NAME} passed with 5 binding +1s, 1 non-binding +1s, and no 0 or -1.

Vote thread link:
https://mail-archives.apache.org/mod_mbox/incubator-brooklyn-dev/201507.mbox/%3CCABQFKi1WapCMRUqQ93E7Qow5onKgL3nyG3HW9Cse7vo%2BtUChRQ%40mail.gmail.com%3E

Binding +1s:
Hadrian Zbarcea (IPMC)
Richard Downer
Sam Corbett
Aled Sage
Andrea Turli

Non-binding +1s:
Ciprian Ciubotariu

Thanks to everyone that tested our release and voted.

We will shortly begin a vote on the incubator-general list.


Thanks.

{% endhighlight %}
