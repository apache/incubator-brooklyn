---
layout: website-normal
title: Merging Contributed Code
---

The Apache Brooklyn Git repositories are hosted in the ASF infrastructure and mirrored to Github. This is the current
repository layout:

- [Apache](https://git-wip-us.apache.org/repos/asf?s=incubator-brooklyn) - the main and official repository
- [GitHub](https://github.com/apache/incubator-brooklyn) - mirror of the ASF repository, used to accept contributions
  and do code reviews


Before
------

For everything except the most trivial changes, the submitter must have a CLA on file. Check the [list of Apache
committers, and non-commiters with ICLAs on record](https://people.apache.org/committer-index.html) and prompt the
contributor to file an appropriate CLA if required.

For all significant changes, there must be a Jira issue. If a Jira issue is not referenced in the PR and/or commit
messages, prompt the contributor to open a Jira issue.


Rules of thumb
--------------

1. Every contribution is a piece of intellectual property.  This is the precious sustenance that nourishes our
   project.  Please treat it with respect.
2. Always give credit where it is due, ensure every merged commit reflects properly the individual who authored that
   commit.  Preserve both the name and email address.
3. Ensure your name and email address are there as the committer prior to pushing it to the Apache repositories.
4. Always strive for linear commit history, avoid merge commits while pulling in contributor's changes.


Setting up your repository
--------------------------

Clone the canonical ASF repo using this command. The `--origin` option tells git to name the remote `apache` instead
of the default, `origin`; this will reduce ambiguity when we later add a second remote upstream.

    git clone --origin apache https://git-wip-us.apache.org/repos/asf/incubator-brooklyn.git

Add a second remote, for the GitHub repository.

    git remote add github https://github.com/apache/incubator-brooklyn.git

For the GitHub remote, add an additional `fetch` reference which will cause
every pull request to be made available as a remote branch in your workspace.

    git config --local --add remote.github.fetch '+refs/pull/*/head:refs/remotes/github/pr/*'

Finally, run `git fetch --all` to update from all remote repositories - you will see all the pull requests appear:

    * [new ref]         refs/pull/98/head -> github/pr/98
    * [new ref]         refs/pull/99/head -> github/pr/99


Merging a pull request
----------------------

Fetch the latest remote branches, which will cause a remote branch for the PR to become available to you.

    git fetch --all

If you want to inspect the PR and/or run tests, check out the branch:

    git checkout github/pr/1234

To perform the merge, first update your master branch to the latest:

    git checkout master
    git pull --rebase

Then merge and push:

    git merge --no-ff -m 'This closes #1234' github/pr/1234
    git push apache master

Note that this commit message is important, as this is what will trigger the
pull request to be automatically closed, and the `--no-ff` means that a merge
commit will always be created.


Alternative options
-------------------

### Adding the remote reference to the contributor's repository

Fetch the branch of the user you want to merge from

    git fetch https://github.com/user-to-merge-from/incubator-brooklyn.git branch-to-merge-from

If you commonly merge from a particular user, you'll want to add their repo as a remote to make fetching branches easier.

    git remote add user-to-merge-from https://github.com/user-to-merge-from/incubator-brooklyn.git
    git fetch user-to-merge-from


### Merging from a patch file

Save the patch from the Github patch link (just append '.patch' to the pull request link to get it). This patch will
keep the authorship of the commit, so we should use it instead of the diff.

Apply the patch preserving the original author:

    git am pull-request-9876.patch


Additional information
----------------------

Particularly for new committers, you may find the following information useful:

* [Guide for new project
  committers](https://www.apache.org/dev/new-committers-guide.html)
* [Committers FAQ](https://www.apache.org/dev/committers.html)
* [Git at Apache](https://git-wip-us.apache.org/)
