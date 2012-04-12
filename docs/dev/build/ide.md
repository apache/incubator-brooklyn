---
layout: page
title: IDE Setup
toc: /toc.json
---

Gone are the days when IDE integration just works...  Maven and Eclipse fight, 
neither quite gets along perfectly with Groovy (let alone Grails),
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

If you're an Eclipse user, you'll probably want the Groovy, Maven, and Git plugins. 
Via Help -> Install New Software, or from the Eclipse Marketplace,
we recommend:

{% readj /use/guide/quickstart/eclipse.include.md %}

Once you've got the code imported, the following hints may be helpful:

* You may need to ensure ``src/main/{java,resources}`` is created in each project dir,
  and same for ``src/test/{java,resources}`` *if* there are tests (``src/test`` exists):

  ``find . \( -path "*/src/main" -or -path "*/src/test" \) -exec echo {} \; -exec mkdir -p {}/{java,resources} \;``

* May need to add groovy nature (or even java nature) to projects;
  with some maven-eclipse plugins this works fine, but for others you
  may need to handcraft these (either right-click the project in the Package Explorer and choose Configure,
  or edit the ``.project`` manually adding it to the project properties (;
  the tips [for jclouds maven-eclipse](http://www.jclouds.org/documentation/devguides/using-eclipse) might be helpful. 

* Some maven integration sets up crazy filters in the generated ``.classpath`` files,
  excluding * or ** or including only *.java.
  You can go through and remove these manually in Eclipse (Build Path -> Configure)
  or the filesystem.
  The following command has been suggested to remove these rogue blocks in the generated .classpath files:

  ``find . -name .classpath -exec sed -i.bak 's/[ ]*..cluding="[\*\/]*\(\.java\)*"//g' {} \;``


* Getting the web console project to build nicely is much trickier; basically you build from the
  command-line, then add all the source folders, and ``target/plugin-classes`` as a class file folder.
  Much easier is to run "grails run-app" in the directory to launch it (plus that does dynamic restarts),
  although some jiggery-pokery may currently be needed to get Groovy 1.8 playing nicely with Grails 1.3.
  This may be easier with the Grails plug-in; and will hopefully get much easier if we
  upgrade to Grails 2.0 (see github issues).



## Intelli-J IDEA

Tips from Intelli-J users wanted!



## Netbeans

Tips from Netbeans users wanted!
