Brooklyn Website and Docs Source
================================

Contributor Workflow
--------------------

The contributor workflow is identical to that used by the main project, with
pull requests and contributor licenses requried. Therefore you should 
familiarise yourself with the standard workflow for Apache Brooklyn:

* [Guide for contributors][CONTRIB]
* [Guide for committers][COMMIT]

[CONTRIB]: https://brooklyn.apache.org/community/how-to-contribute-docs.html
[COMMIT]: https://brooklyn.apache.org/developers/committers/index.html


Workstation Setup
-----------------

First, if you have not already done so, clone the `incubator-brooklyn` repository 
and set up the remotes as described in [Guide for committers][COMMIT].

The Brooklyn documentation uses Markdown notation (what this file is written in)
and the Jekyll process. This in turn requires Ruby and gems as described in the `Gemfile`:
install [RVM](http://rvm.io/) to manage Ruby installations and sets of Ruby gems.

    \curl -sSL https://get.rvm.io | bash -s stable

Close your shell session and start a new one, to get the new
environment that RVM has configured. Change directory to the location where
you checked out your repository and then to the `docs/` subdirectory (where this file is located).

RVM should detect its configuration inside `Gemfile` and try to configure itself. 
Most likely it will report that the required version of Ruby is not installed,
and it will show the command that you need to run to install the correct version. 
Follow the instructions it shows.

Once the correct version of Ruby is installed, change to your home directory
and then change back (`cd ~ ; cd -`).
This will cause RVM to re-load configuration from `Gemfile` with the correct version of Ruby.

Finally, run this command to install all the required Gems 
at the correct versions:

    bundle install

Any time you need to reset your Ruby environment for `jekyll` to run correctly,
return to the `_build` directory and re-run the above command.

On some platforms there may be some fiddling required before `jekyll` runs without errors,
but the ecosystem is fairly mature and most problems can be resolved with a bit of googling.
Some issues we've encountered are:

 * on Mac, install xcode and its command-line tools
 * if ruby gets confused about versions,
   [clean out your gems](http://judykat.com/ken-judy/force-bundler-rebuild-ruby-rails-gemset/)
 * if `libxml2` fails, set `bundle config build.nokogiri --use-system-libraries` before the install
   (more details [here](http://www.nokogiri.org/tutorials/installing_nokogiri.html))
 * on Ubuntu, `sudo apt-get install libxslt-dev libxml2-dev`


Seeing the Website and Docs
---------------------------

To build and most of see the documentation, run this command in your `docs` folder:

    jekyll serve
    
This will start up a local web server. The URL is printed by Jekyll when the server starts,
e.g. http://localhost:4000/ . The server will continue to run until you press Ctrl+C.
Modified files will be detected and regenerated (but that might take up to 1m).
Add `--no-watch` argument to turn off regeneration, or use `jekyll build` instead
to generate a site in `_site` without a server.

This does <i>not</i> generate API docs and certain other material;
see the notes on `_build/build.sh` below for that.


Project Structure
-----------------

Note that there are two interlinked micro-sites in this project:

* `/website`: this contains the main Brooklyn website, including committer instructions,
  download instructions, and "learn more" pages;
  this content has **only one instance** on the live website,
  and as changes are published they replace old content
  
* `/guide`: this contains the user guide and information pertaining to a 
  specific Brooklyn version, including code structure and API documentation;
  the live website contains a **copy of the guide for each Brooklyn version**,
  with the code coming from the corresponding branch in `git`

In addition note the following folders:

* `/style`: contains JS, CSS, and image resources;
  on the live website, this folder is installed at the root *and* 
  into archived versions of the guide. 
  
* `/_build`: contains build scripts and configuration files,
  and tests for some of the plugins

* `/_plugins`: contains Jekyll plugins which supply tags and generation
  logic for the sites, including links and tables of contents

* `/_layouts`: contains HTML templates used by pages

* `/_includes`: contains miscellaneous content used by templates and pages

Jekyll automatically excludes any file or folder beginning with `_`
from direct processing, so these do *not* show up in the `_site` folder
(except where they are embedded in other files).  

**A word on branches:**  The `/website` folder can be built against any branch;
typically changes are made and published from `master`, to ensure that all versions
are listed correctly.
In contrast the `/guide` folder should be updated and built against the branch for which 
instructions are being made, e.g. `master` for latest snapshot updates, 
or `0.7.0-M2` for that milestone release.
It *is* permitted to make changes to docs (and docs only!) after a release has
been made. In most cases, these changes should also be made to master.


Website Structure
-----------------

The two micro-sites above are installed on the live website as follows:

* `/`: contains the website
* `/v/<version>`: contains specific versions of the guide,
  with the special folder `/v/latest` containing the recent preferred stable/milestone version 

The site itself is hosted at `brooklyn.apache.org` with a `CNAME`
record from `brooklyn.io`.

Content is published to the site by updating an 
Apache subversion repository, `incubator-brooklyn-site-public` at
`https://svn.apache.org/repos/asf/incubator/brooklyn/site`.
See below for more information.


Building the Website and Guide
------------------------------

For most users, the `jekyll serve` command described above is sufficient to test changes locally.
The main reason to use the build scripts (and to read this section) is to push changes to the server
(requires Apache Brooklyn commit rights), or to test generated content such as API docs.

The build is controlled by config files in `_build/` and accessed through `_build/build.sh`.
There are a number of different builds possible; to list these, run:

    _build/build.sh help

The normal build outputs to `_site/`.  The three builds which are most relevant to updating the live site are:

* **website-root**: to build the website only, in the root
* **guide-latest**: to build the guide only, in `/v/latest/`
* **guide-version**: to build the guide only, in the versioned namespace e.g. `/v/<version>/`

There are some others, including `test-both`, which apply slightly different configurations
useful for testing.
Supported options beyond that include `--serve`, to start a web browser serving the content of `_site/`,
and `--skip-javadoc`, to speed up the build significantly by skipping javadoc generation.
A handy command for testing the live files, analogous to `jekyll serve` 
but with the correct file structure, and then checking links, is:

    _build/build.sh test-both --skip-javadoc --serve

And to run link-checks quickly (without validating external links), use:

    htmlproof --href_ignore "https?://127.*" --alt_ignore ".*" --disable_external _site



Preparing for a Release
-----------------------

When doing a release and changing versions:

* Before branching:
  * Change the `brooklyn-stable-version` variable in `_config.yml`
  * Update `website/meta/versions.md` with a bit of info on this release
*  In the branch, with `change-version.sh` run (e.g. from `N.SNAPSHOT` to `N`)
  * Ensure the `guide/start/release-notes.md` file is current
  * Build and publish `website-root`, `guide-latest`, and `guide-version`
* In master, with `change-version.sh` run (e.g. to `N+1-SNAPSHOT`)
  * Clear old stuff in the `guide/start/release-notes.md` file
  * Optionally build and public `guide-version`
 

Publishing the Website and Guide
--------------------------------

The Apache website publication process is based around the Subversion repository; 
the generated HTML files must be checked in to Subversion, whereupon an automated process 
will publish the files to the live website.
So, to push changes the live site, you will need to have the website directory checked out 
from the Apache subversion repository. We recommend setting this up as a sibling to your
`incubator-brooklyn` git project directory:

    # verify we're in the right location and the site does not already exist
    ls _build/build.sh || { echo "ERROR: you should be in the docs/ directory to run this command" ; exit 1 ; }
    ls ../../incubator-brooklyn-site-public > /dev/null && { echo "ERROR: incubator-brooklyn-site-public dir already exists" ; exit 1 ; }
    pushd `pwd -P`/../..
    
    svn --non-interactive --trust-server-cert co https://svn.apache.org/repos/asf/incubator/brooklyn/site incubator-brooklyn-site-public
    
    # verify it
    cd incubator-brooklyn-site-public
    ls style/img/apache-brooklyn-logo-244px-wide.png || { echo "ERROR: checkout is wrong" ; exit 1 ; }
    export BROOKLYN_SITE_DIR=`pwd`
    popd
    echo "SUCCESS: checked out site in $BROOKLYN_SITE_DIR"

With this checked out, the `build.sh` script can automatically copy generated files into the right subversion sub-directories
with the `--install` option.  (This assumes the relative structure described above; if you have a different
structure, set BROOKLYN_SITE_DIR to point to the directory as above.  Alternatively you can copy files manually,
using the instructions in `build.sh` as a guide.)

A typical update consists of the following commands (or a subset),
copied to `${BROOKLYN_SITE_DIR-../../incubator-brooklyn-site-public}`:

    # ensure svn repo is up-to-date (very painful otherwise)
    cd ${BROOKLYN_SITE_DIR-../../incubator-brooklyn-site-public}
    svn up
    cd -

    # versioned guide, safe for snapshots, relative to /v/<version>/
    _build/build.sh guide-version --install

    # main website, if desired, relative to / 
    _build/build.sh website-root --install
    
    # this version as the latest guide, if desired, relative to /v/latest/
    _build/build.sh guide-latest --install
    
(If HTML-Proofer find failures, then fix the links etc. Unfortunately, the javadoc build 
gives a lot of warnings. Fixing those is not part of this activity).

You can then preview the public site of [localhost:4000](http://localhost:4000) with:

    _build/serve-public-site.sh

Next it is recommended to go to the SVN dir and 
review the changes using the usual `svn` commands -- `status`, `diff`, `add`, `rm`, etc.
Note in particular that deleted files need special attention (there is no analogue of
`git add -A`!). Look at deletions carefully, to try to avoid breaking links, but once
you've done that these commands might be useful:

    cd ${BROOKLYN_SITE_DIR-../../incubator-brooklyn-site-public}
    svn add * --force
    export DELETIONS=$( svn status | sed -e '/^!/!d' -e 's/^!//' )
    if [ ! -z "${DELETIONS}" ] ; then svn rm ${DELETIONS} ; fi

Then check in the changes (probably picking a better message than shown here):

    svn ci -m 'Update Brooklyn website'

The changes should become live within a few minutes.

SVN commits can be **slow**, particularly if you've regenerated javadoc.
(The date is included in all javadoc files so the commands above will cause *all* javadoc to be updated.)
Use `_build/build.sh guide-version --install --skip-javadoc` to update master while re-using the previously installed javadoc.
That command will fail if javadoc has not been generated for that version.


More Notes on the Code
----------------------

# Plugins

We use some custom Jekyll plugins, in the `_plugins` dir:

* include markdown files inside other files (see, for example, the `*.include.md` files 
  which contain text which is used in multiple other files)
* generate the site structure / menu objects
* parse JSON which we can loop over in our markdown docs (to build up models; previously used
  for the TOC in the guide, but now replaced with site_structure)
* trim whitespace of ends of variables


# Versions

Archived versions are kept under `/v/` in the website.  New versions should be added with
the appropriate directory (`_build/build.sh guide-version` above will do this).  
These versions take their own copy of the `style` files so that changes there will not affect future versions.

A list of available versions is in `website/meta/versions.md`.
