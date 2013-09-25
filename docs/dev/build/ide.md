---
layout: page
title: IDE Setup
toc: /toc.json
---

Gone are the days when IDE integration always just works...  Maven and Eclipse fight, 
neither quite gets along perfectly with Groovy,
git branch switches (sooo nice) can be slow, etc etc.

But with a bit of a dance the IDE can still be your friend,
making it much easier to run tests and debug.

As a general tip, don't always trust the IDE to build correctly; if you hit a snag,
do a command-line ``mvn clean install`` (optionally with ``-DskipTests``)
then refresh the project. 

See instructions below for specific IDEs.


## Eclipse

If you're an Eclipse user, you'll probably want the Maven (m2e) plugin
and the Groovy Eclipse plugin (used for testing and examples primarily).
You may also want Git and TestNG plugins.
You can install these using Help -> Install New Software, or from the Eclipse Marketplace:

{% readj eclipse.include.md %}

As of this writing, Eclipse 4.2 and Eclipse 4.3 are commonly used, 
and the codebase can be imported (Import -> Existing Maven Projects) 
and successfully built and run inside an IDE.
However there are quicks, and mileage may vary.

If you encounter issues, the following hints may be helpful:

* A quick command-line build (`mvn clean install -DskipTests`) followed by a workspace refresh
  can be useful to re-populate files which need to be copied to `target/`
 
* m2e likes to put `excluding="**"` on `resources` directories; if you're seeing funny missing files
  (things like not resolving jclouds/aws-ec2 locations or missing WARs), try building clean install
  from the command-line then doing Maven -> Update Project (clean uses a maven-replacer-plugin to fix 
  `.classpath`s).
  Alternatively you can go through and remove these manually in Eclipse (Build Path -> Configure)
  or the filesystem, or use
  the following command to remove these rogue blocks in the generated `.classpath` files:

{% highlight bash %}
% find . -name .classpath -exec sed -i.bak 's/[ ]*..cluding="[\*\/]*\(\.java\)*"//g' {} \;
{% endhighlight %}

* If m2e reports import problems, it is usually okay (even good) to mark all to "Resolve All Later".
  The build-helper connector is useful if you're prompted for it, but
  do *not* install the Tycho OSGi configurator (this causes show-stopping IAE's, and we don't need Eclipse to make bundles anyway).
  You can manually mark as permanently ignored certain errors;
  this updates the pom.xml (and should be current).

* You may need to ensure ``src/main/{java,resources}`` is created in each project dir,
  if (older versions) complain about missing directories,
  and the same for ``src/test/{java,resources}`` *if* there are tests (``src/test`` exists):

{% highlight bash %}
find . \( -path "*/src/main" -or -path "*/src/test" \) -exec echo {} \; -exec mkdir -p {}/{java,resources} \;
{% endhighlight %}

* You may need to add the groovy nature (or even java nature) to projects;
  with some maven-eclipse plugins this works fine, 
  but for others (older ones) you may need to handcraft these 
  (either right-click the project in the Package Explorer and choose Configure,
  or edit the ``.project`` manually adding it to the project properties).
  The tips [for jclouds maven-eclipse](http://www.jclouds.org/documentation/devguides/using-eclipse) might be helpful. 

If the pain starts to be too much, come find us on IRC #brooklyncentral or [elsewhere]({{site.url}}/meta/contact.html) and we can hopefully share our pearls.
(And if you have a tip we haven't mentioned please let us know that too!)



## Intelli-J IDEA

Many of our contributers prefer Intelli-J.  However none of them have yet volunteered any set-up tips.
[Be the first!]({{site.url}}/dev/tips/update-docs.html)



## Netbeans

Tips from Netbeans users wanted!

