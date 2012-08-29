---
layout: page
title: IDE Setup
toc: /toc.json
---

Gone are the days when IDE integration always just works...  Maven and Eclipse fight, 
neither quite gets along perfectly with Groovy, let alone Grails,
git branch switches (sooo nice) can be slow, etc etc.

But with a bit of a dance the IDE can still be your friend,
making it much easier to run tests and debug.

Here are some general tips:

* Add your favourite plugins for Maven, Groovy, and Git, if necessary

* Turn off auto-rebuild if it bothers you

* Don't always trust the IDE to build correctly; if you hit a snag,
  do a command-line ``mvn clean install`` (optionally with ``-DskipTests``)
  then refresh the project (optionally regenerate dependencies).
  Sometimes closing projects (so that it reads from your ``~/.m2/repository``) helps.

See instructions below for specific IDEs.

    
## Eclipse

If you're an Eclipse user, you'll probably want the Groovy, Maven (m2e), Git, and TestNG plugins. 
Via Help -> Install New Software, or from the Eclipse Marketplace,
we recommend:

{% readj /use/guide/quickstart/eclipse.include.md %}

As of this writing, with Eclipse 4.2, m2e 1.1, and GrEclipse 2.7, 
the codebase has been imported (Import -> Existing Maven Projects) 
and successfully run with no errors.
However this isn't always the case.

If you encounter issues, the following hints may be helpful:

* If m2e reports import problems, it is usually okay (even good) to mark all to "Resolve All Later".
  The build-helper connector is useful if you're prompted for it, but
  do *not* install the Tycho OSGi configurator (this causes show-stopping IAE's, and we don't need Eclipse to make bundles anyway).
  You can manually mark as permanently ignored certain errors;
  this updates the pom.xml (and should be current).

* m2e likes to put ``excluding="**"`` on `resources`` directories; if you're seeing funny missing files
  (things like not resolving jclouds/aws-ec2 locations or missing WARs), try building clean install
  from the command-line then doing Maven -> Update Project (clean uses a maven-replacer-plugin to fix ``.classpath``s).
  Alternatively you can go through and remove these manually in Eclipse (Build Path -> Configure)
  or the filesystem, or use
  the following command to remove these rogue blocks in the generated .classpath files:

  ``find . -name .classpath -exec sed -i.bak 's/[ ]*..cluding="[\*\/]*\(\.java\)*"//g' {} \;``

* Getting the web console project to build nicely can be trickier. 

* You may need to ensure ``src/main/{java,resources}`` is created in each project dir,
  if (older versions) complain about missing directories,
  and the same for ``src/test/{java,resources}`` *if* there are tests (``src/test`` exists):

  ``find . \( -path "*/src/main" -or -path "*/src/test" \) -exec echo {} \; -exec mkdir -p {}/{java,resources} \;``

* You may need to add the groovy nature (or even java nature) to projects;
  with some maven-eclipse plugins this works fine, 
  but for others (older ones) you may need to handcraft these 
  (either right-click the project in the Package Explorer and choose Configure,
  or edit the ``.project`` manually adding it to the project properties).
  The tips [for jclouds maven-eclipse](http://www.jclouds.org/documentation/devguides/using-eclipse) might be helpful. 

* Getting the web console project to build nicely can be trickier. 
  If you're lucky it will just work...
  But you may find you have to first build from the command-line, 
  then add all the source folders, and ``target/plugin-classes`` as a class file folder.
  Easier may be just to run "grails run-app" in the directory to launch it (plus that does dynamic restarts),
  and use the IDE as just a text editor (or use a different text editor for that project only).
  Note too that some jiggery-pokery may currently be needed to get Groovy 1.8 playing nicely with Grails 1.3.
  

If the pain starts to be too much, come find us on IRC #brooklyncentral or [elsewhere]({{site.url}}/meta/contact.html) and we can hopefully share our pearls.
(And if you have a tip we haven't mentioned please let us know that too!)



## Intelli-J IDEA

Tips from Intelli-J users wanted!



## Netbeans

Tips from Netbeans users wanted!

