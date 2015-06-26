---
layout: website-normal
title: How to Contribute Documentation
navgroup: community
---

<!-- TODO do we want this page?  can be simplified a lot since the process is identical as for how-to-contribute,
     with the exception of the "Edit this Page" (and reminder we can accept small changes without CLA or Jira);
     we could promote much of the content from README.md to here... -->


Welcome and thank you for your interest in contributing to Apache Brooklyn! This guide will take you through the
process of making contributions to the Apache Brooklyn website and documentation.


Two types of documentation
--------------------------

The Brooklyn docs live in the **docs** project in the Brooklyn codebase,
built using standard jekyll/markdown with a few extensions.

The Brooklyn documentation is split into two parts:

- **The main website and shared documentation**. This covers the root website
  and all pages that are not part of the version-specific user guide.
  Content for this is in the `website` directory.
  
- **Version-specific user guide**. These pages have a URL with a path that
  begins /v/*version-number*: for example,
  https://brooklyn.incubator.apache.org/v/0.8.0-SNAPSHOT and {% comment %}BROOKLYN_VERSION{% endcomment %}
  the special *latest* set at https://brooklyn.incubator.apache.org/v/latest .  
  Content for this is in the `guide` directory.


Contributor license agreement
-----------------------------

Apache Brooklyn is licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0). All
contributions will be under this license, so please read and understand this license before contributing.

For all but the most trivial patches, you are required to file a Contributor License Agreement with the Apache
Software Foundation. Please read the [guide to CLAs](https://www.apache.org/licenses/#clas) to find out how to file a
CLA with the Foundation.


Before you start
----------------

### Join the community

If it's your first contribution or it's a particularly big or complex contribution, things typically go much more
smoothly when they start off with a conversation. Visit our [Community](index.html) page to see how you can contact
us via IRC or email.

### Create an issue in Jira

Your first step is to create or find an issue in [Brooklyn's Jira](https://issues.apache.org/jira/browse/BROOKLYN)
for your feature request or fix. For small changes this isn't necessary, but it's good to see if your change fixes an
existing issue anyway.


Contributing using *Edit this page*
-----------------------------------

Most of the pages on the website have an *Edit this page* button at the bottom.
Clicking this button will take you to the GitHub repository and immediately
begin editing the file. This approach makes editing easy, as you do not need to
clone the repository to your workstation and make changes there; they can be
changed directly on the GitHub website for the repository.

Once you have made your edits, there is a short form titled *Propose file
change*. Provide a short description of the change in the first box; optionally,
provide a longer description in the second box. If your change fixes or
addresses a Jira issue, be sure to mention it. Finally, click the *Propose file
change* button to prepare a pull request, and then *Create pull request* to
formally submit your change request and notify the dev team of your proposed
change.

The website is not a Wiki and your changes will not appear immediately. The
community mailing list will receive a notification of your proposed change, and
an Apache Brooklyn committer will review your changes. Once your change has
passed review, the committer will merge it and re-publish the public website.

While the *Edit this page* button is great for quickly editing a single page, if
you want to do anything that involves editing multiple pages, you will need to
fork and clone the repository and make the changes on your own workstation - see
the next section for information on this.


Contributing using GitHub
-------------------------

Our GitHub repository is located at
[https://github.com/apache/incubator-brooklyn](https://github.com/apache/incubator-brooklyn)

Your commit messages must properly describe the changes that have been made and
their purpose ([here are some
guidelines](http://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html)).
If your contributions fix a Jira issue, then ensure that you reference the issue
(like `BROOKLYN-9876`) in the commit message.

Create a pull request (PR) in GitHub for the change you're interested in making.
The comment section of the PR must contain a link to the Jira issue (if it has
one).

Some good references for working with GitHub are below.  We ask that you keep
your change rebased to master as much as possible, and we will ask you to rebase
again if master has moved before accepting your patch.

- [Setting Up Git with GitHub](https://help.github.com/articles/set-up-git)
- [Forking a Repository](https://help.github.com/articles/fork-a-repo)
- [Submitting Pull Requests](https://help.github.com/articles/using-pull-requests)
- [Rebasing your Branch](https://help.github.com/articles/interactive-rebase)

Finally, add a comment in the Jira issue with a link to the pull request so we
know the code is ready to be reviewed.


### Reviews

The Apache Brooklyn community will review your pull request before it is merged.
This process can take a while, so please be patient. If we are slow to respond,
please feel free to post a reminder to the PR, Jira issue, IRC channel or
mailing list - see the [Community](index.html) page to see how to contact us.

During the review process you may be asked to make some changes to your
submission. While working through feedback, it can be beneficial to create new
commits so the incremental change is obvious.  This can also lead to a complex
set of commits, and having an atomic change per commit is preferred in the end.
Use your best judgement and work with your reviewer as to when you should revise
a commit or create a new one.

A pull request is considered ready to be merged once it gets at least one +1
from a committer. Once all the changes have been completed and the pull request
is accepted, you may be asked to rebase it against the latest code. You may also
wish to squash some commits together and make other history revisions, to leave
the commit history clean and easily understood.


### Contributing without using GitHub

If you prefer to not use GitHub, then that is fine - we are also happy to accept
patches attached to a Jira issue.  Our canonical repository is located at
`https://git-wip-us.apache.org/repos/asf/incubator-brooklyn.git`; for example:

    $ git clone https://git-wip-us.apache.org/repos/asf/incubator-brooklyn.git

When producing patches, please use `git format-patch` or a similar mechanism -
this will ensure that you are properly attributed as the author of the patch
when a committer merges it.


For More Information
--------------------

Advanced instructions for building, previewing and publishing docs are in a `README.md` file
in the `docs` folder; see those instructions
[here](https://github.com/apache/incubator-brooklyn/tree/master/docs/README.md).
