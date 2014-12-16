Brooklyn Website and Docs Source
================================

Contributor Workflow
--------------------

The contributor workflow is identical to that used by the main project, with
pull requests and contributor licenses requried. Therefore you should 
familiarise yourself with the standard workflow for Apache Brooklyn:

* [Guide for contributors][CONTRIB]
* [Guide for committers][COMMIT]

[CONTRIB]: https://brooklyn.incubator.apache.org/community/how-to-contribute.html
[COMMIT]: https://brooklyn.incubator.apache.org/community/committers.html


Workstation Setup
-----------------

First, if you have not already done so, clone the `incubator-brooklyn` repository 
and set up the remotes as described in [Guide for committers][COMMIT].

Install [RVM](http://rvm.io/); this manages Ruby installations and sets of Ruby
gems.

    \curl -sSL https://get.rvm.io | bash -s stable

At this point, close your shell session and start a new one, to get the new
environment that RVM has configured. Now change directory to the location where
you checked out your repository, and then to the `docs/_build` subdirectory.

RVM should detect its configuration inside `Gemfile` and try to configure itself. 
Most likely it will report that the required version of Ruby is not installed; 
it will show the command that you need to run to install the correct version. 
Follow these instructions.

Once the correct version of Ruby is installed, change to your home directory
(`cd ~`) and then change back to the `_build` dir again (`cd -`). This will cause
RVM to re-load configuration from `Gemfile` with the correct version of Ruby.

If you are running Ubuntu, there is a further dependency that is required:

    sudo apt-get install libxslt-dev libxml2-dev

Finally, run this command inside `_build` to install all the required Gems 
at the correct versions:

    bundle install

Any time you need to reset your Ruby environment for jekyll to run correctly,
return to the `_build` directory and re-run the above command.


Building and Previewing the Website
-----------------------------------

### Using Jekyll's in-built server

In the `docs` directory, run the command:

    jekyll serve --watch
    
This will start up a local web server. The URL is printed by Jekyll when the server starts,
e.g. http://localhost:4000/ . The server will continue to run until you press Ctrl+C.
Modified files will be detected and regenerated (but that might take up to 1m).
Leave off the `--watch` argument to turn off regeneration, or use `jekyll build` instead
to generate a site in `_site` without a server, for instance if your browser supports running from disk.


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

The site itself is hosted at `brooklyn.incubator.apache.org` with a `CNAME`
record from `brooklyn.io`.

Content is published to the site by updating an 
Apache subversion repository, `incubator-brooklyn-site-public` at
`https://svn.apache.org/repos/asf/incubator/brooklyn/site`.
See below for more information.


Building the Website and Guide
------------------------------

For most users, the `jekyll serve` command described above is sufficient to test changes locally.
The main reason to use the build scripts (and to read this section) is to push changes to the server.
This power is reserved to Brooklyn committers.

The build is controlled by config files in `_build/` and accessed through `_build/build.sh`.
There are a number of different builds possible; to list these, run:

    _build/build.sh help

The normal build outputs to `_site/`.  The three builds which are relevant to updating the live site are:

* **website-root**: to build the website only, in the root
* **guide-latest**: to build the guide only, in `/v/latest/`
* **guide-version**: to build the guide only, in the versioned namespace e.g. `/v/<version>/`

Publishing the Website and Guide
--------------------------------

The Apache website publication process is based around the Subversion repository; 
the generated HTML files must be checked in to Subversion, whereupon an automated process 
will publish the files to the live website.
So, to push changes the live site, you will need to have the website directory checked out 
from the Apache subversion repository. We recommend setting this up as a sibling to your
`incubator-brooklyn` git project directory:

    # verify we're in the right location and the site does not already exist
    ls _build/build.sh || { echo "ERROR: you should be in the docs/ directory to run this command" ; exit 1 }
    ls ../../incubator-brooklyn-site-public && { echo "ERROR: incubator-brooklyn-site-public dir already exists ; exit 1 }
    pushd ../..
    
    svn co https://svn.apache.org/repos/asf/incubator/brooklyn/site incubator-brooklyn-site-public
    
    # verify it
    cd incubator-brooklyn-site-public
    ls style/css/website.css || { echo "ERROR: checkout is wrong" ; exit 1 }
    export BROOKLYN_SITE_DIR=`pwd`
    popd
    echo "SUCCESS: checked out site in $BROOKLYN_SITE_DIR"

With this checked out, the `build.sh` script can automatically copy generated files into the right subversion sub-directories
with the `--install` option.  (This assumes the relative structure described above; if you have a different
structure, set BROOKLYN_SITE_DIR to point to the directory as above.  Alternatively you can copy files manually,
using the instructions in `build.sh` as a guide.)

**TODO: the `--install` option below is not yet implemented**

A typical update consists of the following commands (or a subset),
copied to `${BROOKLYN_SITE_DIR:../../incubator-brooklyn-site-public}`:

    # main website, relative to / 
    _build/build.sh website-root --install
    
    # latest guide, relative to /v/latest/
    _build/build.sh guide-latest --install
    
    # latest guide, relative to /v/<version>/
    _build/build.sh guide-version --install

Next it is recommended to go to the SVN dir and 
review the changes using the usual `svn` commands -- `status`, `diff`, `add`, `rm`, etc:

    cd ${BROOKLYN_SITE_DIR:../../incubator-brooklyn-site-public}

You must then check in the changes:

    svn ci -m 'Update Brooklyn website'

The changes should become live within a few minutes.


<!-- OLD notes:

Synchronise the generated site into the Subversion working copy - please amend this command to include the correct paths for your setup:

    rsync -rv --delete --exclude .svn --exclude v ~/incubator-brooklyn-site/_site/ ~/incubator-brooklyn-site-public
-->