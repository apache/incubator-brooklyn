Brooklyn Website source
=======================

Contributor worflow
-------------------

The contributor workflow is identical to that used by the main project, with
the exception of the different repository - `incubator-brooklyn-site` instead
of `incubator-brooklyn`. Therefore you should familiarise yourself with the
standard workflow for Apache Brooklyn:

* [Guide for contributors][CONTRIB]
* [Guide for committers][COMMIT]

[CONTRIB]: https://brooklyn.incubator.apache.org/community/how-to-contribute.html
[COMMIT]: https://brooklyn.incubator.apache.org/community/committers.html


Workstation setup
-----------------

First, clone the `incubator-brooklyn-site` repository and set up the remotes as
described in [Guide for committers][COMMIT].

Install [RVM](http://rvm.io/); this manages Ruby installations and sets of Ruby
gems.

    \curl -sSL https://get.rvm.io | bash -s stable

At this point, close your shell session and start a new one, to get the new
environment that RVM has configured. Now change directory to the location where
you checked out your repository, and then to the `_scripts` subdirectory.

RVM should detect its configuration inside `Gemfile` and try to configure itself. 
Most likely it will report that the required version of Ruby is not installed; 
it will show the command that you need to run to install the correct version. 
Follow these instructions.

Once the correct version of Ruby is installed, change to your home directory
(`cd ~`) and then change back to the repository again (`cd -`). This will cause
RVM to re-load configuration from `Gemfile` with the correct version of Ruby.

If you are running Ubuntu, there is a further dependency that is required:

    sudo apt-get install libxslt-dev libxml2-dev

Finally, run this command inside `_scripts` to install all the required Gems 
in the correct versions:

    bundle install

Anytime you need to reset your Ruby environment for jekyll to run correctly,
return to the `_scripts` directory and re-run the above command.


Building and previewing the website
-----------------------------------

### Using Jekyll's in-built server

Run the command:

    jekyll serve --watch
    
This will start up a local web server. The URL is printed by Jekyll when the server starts,
e.g. http://localhost:4000/brooklyn/ . The server will continue to run until you press Ctrl+C.
Modified files will be detected and regenerated (but that might take up to 1m).
Leave off the `--watch` argument to abandon that behaviour, or use `jekyll build` instead
to generate a site in `_site` without a server, for instance if your browser supports running from disk.



### Combining with Brooklyn user-manual / version docs

TODO: Some links should come from the incubator-brooklyn/docs/ project.
They can run in debug mode on a different port.


Building and publishing the website
-----------------------------------

Run this script:

    ./_scripts/build-for-publication

This will run Jekyll with the correct base URL for the live website and place
the output in the `_site` directory.

The Apache website publication process is based around the Subversion repository; the generated HTML files must be checked in to Subversion, whereupon an automated process will publish the files to the live website.

Starting in a suitable directory, check out the website directory from the repository:

    svn co https://svn.apache.org/repos/asf/incubator/brooklyn/site incubator-brooklyn-site-public
    cd incubator-brooklyn-site-public

Synchronise the generated site into the Subversion working copy - please amend this command to include the correct paths for your setup:

    rsync -rv --delete --exclude .svn --exclude v ~/incubator-brooklyn-site/_site/ ~/incubator-brooklyn-site-public

Review the changes using the usual `svn` commands - e.g., `status`, `diff`, `add`, `rm`, etc.

Once you are ready to publish, commit the changes to Subversion:

    svn ci -m 'Update Brooklyn (incubating) website'

The changes will become live within a few minutes.
