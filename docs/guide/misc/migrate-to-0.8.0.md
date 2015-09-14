---
layout: website-normal
title: Migrating to 0.8.0
---

As noted in the [release notes](release-notes.html),
this version introduces major package renames.

However migrating your code should not be hard:

* For small Java projects, simply "Optimizing Imports" in your IDE should fix code issues.
   
* For YAML blueprints and larger projects, 
a set of regexes has been prepared [here](migrate-to-0.8.0-regexes.sed)
detailing all class renames.

To download and apply this to an entire directory, you can use the following snippet.
If running this on a Java project, you should enter the `src` directory
or `rm -rf target` first.  For other use cases it should be easy to adapt,
noting the use of `sed` and the arguments (shown for OS X / BSD here). 
Do make a `git commit` or other backup before applying,
to make it easy to inspect the changes.
It may add a new line to any file which does not terminate with one,
so do not run on binary files.

{% highlight bash %}   
$ curl {{ site.url_root }}{{ site.path.guide }}/misc/migrate-to-0.8.0-regexes.sed -o /tmp/migrate.sed
$ for x in `find . -type file` ; do sed -E -i .bak -f /tmp/migrate.sed $x ; done
$ find . -name "*.bak" -delete
{% endhighlight %}

If you encounter any issues, please [contact us](/community/).
