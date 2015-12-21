---
layout: website-normal
title: Release Process
navgroup: developers
children:
- { path: prerequisites.md }
- { path: environment-variables.md }
- { path: release-version.md }
- { path: make-release-artifacts.md }
- { path: verify-release-artifacts.md }
- { path: publish-temp.md }
- { path: vote.md }
- { path: fix-release.md }
- { path: vote-ipmc.md }
- { path: publish.md }
- { path: announce.md }
---
1. [Prerequisites](prerequisites.html) - steps that a new release manager must do (but which only need to be done once)
2. [Set environment variables](environment-variables.html) - many example snippets here use environment variables to
   avoid repetition - this page describes what they are
2. [Create a release branch and set the version](release-version.html)
3. [Make the release artifacts](make-release-artifacts.html)
4. [Verify the release artifacts](verify-release-artifacts.html)
5. [Publish the release artifacts to the staging area](publish-temp.html)
6. [Vote on the dev@brooklyn list](vote.html)
   1. If the vote fails - [fix the release branch](fix-release.html) and resume from step 3
7. [Vote on the general@incubator list](vote-ipmc.html)
   1. If the vote fails - [fix the release branch](fix-release.html) and resume from step 3
8. [Publish the release artifacts to the public location](publish.html)
9. [Announce the release](announce.html)
