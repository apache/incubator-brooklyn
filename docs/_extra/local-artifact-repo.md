---
layout: website-normal
title: Prepopulating a Local Artifact Repository
toc: /guide/toc.json
---

On occasion it can be useful to have/control/prepopulate a local repository of entity installers <small>[1]</small>.

The following command (run from `~/`) may be used to sync Cloudsoft's fallback repository to the local `~/.brooklyn/repository/` folder:

	wget --directory-prefix=".brooklyn/repository/" --no-parent --relative --no-host-directories --reject="index.html*" --cut-dirs=2 --recursive -e robots=off --user-agent="Brooklyn Repository Sync" http://downloads.cloudsoftcorp.com/brooklyn/repository/

Brooklyn's default search behaviour for installation artifacts is as follows:

1.  The local `~/.brooklyn/repository/` folder.
2.	The entity's installer's public download url (or an overridden url if one has been specified).
3.	Cloudsoft's fallback repository.

Cloudsoft's fallback repository <small>[2]</small> contains many of the installation artifacts used by current Brooklyn entities. 

It is intended to prevent problems occurring when the public url for an installer changes (e.g. when several new versions of MySQL have been released). It is provided on an as-is and as-available basis.

If you use this command to create a local repository, please respect the `--user-agent`. In future this will allow Cloudsoft to easily filter repository syncing behaviour from  fallback behaviour, allowing out-of-date entities to be more easily identified and updated. 

<br />
<small>
<ol>
<li>For example, when establishing a local cache or enterprise golden source, or when developing Brooklyn while offline, in planes, trains and automobiles, or other such situations of exemplary derring-do <small>[3]</small>.</li> 
<li><a href="http://downloads.cloudsoftcorp.com/brooklyn/repository/">downloads.cloudsoftcorp.com/brooklyn/repository</a></li>
<li>This one time, Cloudsoft ran a team hackathon in a castle in the remote Highlands of Scotland. Remote Highlands != reliable big pipe internet.</li>
</ol>
</small>
