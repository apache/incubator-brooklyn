Release Scripts and Helpers
===========================

This folder contains a number of items that will assist in the production of Brooklyn releases.


Release scripts - change-version.sh and make-release-artifacts.sh
-----------------------------------------------------------------

`change-version.sh` will update version numbers across the whole distribution.  It is recommended to use this script
rather than "rolling your own" or using a manual process, as you risk missing out some version numbers (and
accidentally changing some that should not be changed).

`make-release-artifacts.sh` will produce the release artifacts with appropriate signatures. It is recommended to use
this script rather than "rolling your own" or using a manual process, as this script codifies several Apache
requirements about the release artifacts.

These scripts are fully documented in **Release Process** pages on the website.


Vagrant configuration
---------------------

The `Vagrantfile` and associated files `settings.xml` and `gpg-agent.conf` are for setting up a virtual machine hosting
a complete and clean development environment. You may benefit from using this environment when making the release, but
it is not required that you do so.

The environment is a single VM that configured with all the tools needed to make the release. It also configures GnuPG
by copying your `gpg.conf`, `secring.gpg` and `pubring.gpg` into the VM; also copied is your `.gitconfig`. The
GnuPG agent is configured to assist with the release signing by caching your passphrase, so you will only need to enter
it once during the build process. A Maven `settings.xml` is provided to assist with the upload to Apache's Nexus server.
Finally the canonical Git repository for Apache Brooklyn is cloned into the home directory.

You should edit `settings.xml` before deployment, or `~/.m2/settings.xml` inside the VM after deployment, to include
your Apache credentials.

Assuming you have VirtualBox and Vagrant already installed, you should simply be able to run `vagrant up` to create the
VM, and then `vagrant ssh` to get a shell prompt inside the VM. Finally run `vagrant destroy` to clean up afterwards.

This folder is mounted at `/vagrant` inside the VM - this means the release helpers are close to hand, so you can
run for example `/vagrant/make-release/artifacts.sh`.


Pull request reporting
----------------------

`pr_report.rb` (and associated files `Gemfile` and `Gemfile.lock`) uses the GitHub API to extract a list of open pull
requests, and writes a summary into `pr_report.tsv`. This could then be imported into Google Sheets to provide a handy
way of classifying and managing outstanding PRs ahead of making a release.
