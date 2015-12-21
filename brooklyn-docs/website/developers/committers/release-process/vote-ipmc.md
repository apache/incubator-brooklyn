---
layout: website-normal
title: Vote on general@incubator
navgroup: developers
---

Copy-paste the e-mail below, being sure to substitute:

- version number
- RC number
- “source release of” or “release of”
- URLs for the [VOTE] and [RESULT][VOTE] messages on dev@brooklyn
- URLs containing version numbers
- URL for your own asc key
- Checksums

### Subject: [VOTE] Release Apache Brooklyn 0.7.0-incubating [rc1]

{% highlight text %}
This is to call for a vote for the release of Apache Brooklyn 0.7.0-incubating.

The Apache Brooklyn community have voted in favour of making this release:
Vote thread:
https://mail-archives.apache.org/mod_mbox/incubator-brooklyn-dev/201507.mbox/%3CCABQFKi1WapCMRUqQ93E7Qow5onKgL3nyG3HW9Cse7vo%2BtUChRQ%40mail.gmail.com%3E
Result email:
https://mail-archives.apache.org/mod_mbox/incubator-brooklyn-dev/201507.mbox/%3CCABQFKi2aJHHfXGC0xsMFU0odfB5X6FF5xhpHbs93%2BNfS-fNRZw%40mail.gmail.com%3E

We now ask the IPMC to vote on this release.

This release comprises of a source code distribution, and a corresponding
binary distribution, and Maven artifacts.

The source and binary distributions, including signatures, digests, etc. can
be found at:
https://dist.apache.org/repos/dist/dev/incubator/brooklyn/apache-brooklyn-0.7.0-incubating-rc1

The artifact SHA-256 checksums are as follows:
c3b5c581f14b44aed786010ac7c8c2d899ea0ff511135330395a2ff2a30dd5cf *apache-brooklyn-0.7.0-incubating-rc1-bin.tar.gz
cef49056ba6e5bf012746a72600b2cee8e2dfca1c39740c945c456eacd6b6fca *apache-brooklyn-0.7.0-incubating-rc1-bin.zip
8069bfc54e7f811f6b57841167b35661518aa88cabcb070bf05aae2ff1167b5a *apache-brooklyn-0.7.0-incubating-rc1-src.tar.gz
acd2229c44e93e41372fd8b7ea0038f15fe4aaede5a3bcc5056f28a770543b82 *apache-brooklyn-0.7.0-incubating-rc1-src.zip

The Nexus staging repository for the Maven artifacts is located at:
https://repository.apache.org/content/repositories/orgapachebrooklyn-1004

All release artifacts are signed with the following key:
https://people.apache.org/keys/committer/richard.asc

KEYS file available here:
https://dist.apache.org/repos/dist/release/incubator/brooklyn/KEYS

The artifacts were built from Git commit ID
24a23c5a4fd5967725930b8ceaed61dfbd225980
https://git-wip-us.apache.org/repos/asf?p=incubator-brooklyn.git;a=commit;h=24a23c5a4fd5967725930b8ceaed61dfbd225980


Please vote on releasing this package as Apache Brooklyn 0.7.0-incubating.

The vote will be open for at least 72 hours.
[ ] +1 Release this package as Apache Brooklyn 0.7.0-incubating
[ ] +0 no opinion
[ ] -1 Do not release this package because ...


Thanks,
[Release manager name]
{% endhighlight %}

Email out the vote result
-------------------------

This is a similar process to counting the votes on the dev@brooklyn list. You will need 3 IPMC members to issue a “+1
binding” vote, and no IPMC “0 binding” or “-1 binding” votes. Once the voting period has elapsed and the required votes
received, email out a vote result email. Again this should be a new email thread with the subject prefixed
“[RESULT][VOTE]”.

### Subject: [RESULT][VOTE] Release Apache Brooklyn 0.7.0-incubating [rc1]

{% highlight text %}
The vote for releasing Apache Brooklyn 0.7.0-incubating passed with 3 binding +1s, 0 non-binding +1s, and no 0 or -1.

Vote thread link:
https://mail-archives.apache.org/mod_mbox/incubator-general/201507.mbox/%3CCABQFKi1xMzduVruYXdA15BQkZGVaYnmOChSfUvMw3uWcHA1Beg%40mail.gmail.com%3E


Binding +1s:
Hadrian Zbarcea
Justin Mclean
Jean-Baptiste Onofré


Thanks to everyone that tested our release and voted.

We will shortly publish the release artifacts.


Thanks,
[Release manager name]
{% endhighlight %}
