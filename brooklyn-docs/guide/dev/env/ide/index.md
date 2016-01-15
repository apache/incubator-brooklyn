---
layout: website-normal
title: IDE Setup
toc: /guide/toc.json
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

The default Eclipse downloads already include all of the plugins needed for
working with the Brooklyn project. Optionally you can install the
Groovy and TestNG plugins, but they are not required for building the project.
You can install these using Help -> Install New Software, or from the Eclipse Marketplace:

{% readj eclipse.include.md %}

As of this writing, Eclipse 4.5 and Eclipse 4.4 are commonly used,
and the codebase can be imported (Import -> Existing Maven Projects)
and successfully built and run inside an IDE.
However there are quirks, and mileage may vary. Disable ``Build Automatically``
from the ``Project`` menu if the IDE is slow to respond.

If you encounter issues, the following hints may be helpful:

* If m2e reports import problems, it is usually okay (even good) to mark all to "Resolve All Later".
  The build-helper connector is useful if you're prompted for it, but
  do *not* install the Tycho OSGi configurator (this causes show-stopping IAE's, and we don't need Eclipse to make bundles anyway).
  You can manually mark as permanently ignored certain errors;
  this updates the pom.xml (and should be current).

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

* You may need to ensure ``src/main/{java,resources}`` is created in each project dir,
  if (older versions) complain about missing directories,
  and the same for ``src/test/{java,resources}`` *if* there are tests (``src/test`` exists):

{% highlight bash %}
find . \( -path "*/src/main" -or -path "*/src/test" \) -exec echo {} \; -exec mkdir -p {}/{java,resources} \;
{% endhighlight %}

If the pain starts to be too much, come find us on IRC #brooklyncentral or
[elsewhere]({{site.path.website}}/community/) and we can hopefully share our pearls.
(And if you have a tip we haven't mentioned please let us know that too!)



## IntelliJ IDEA

To develop or debug Brooklyn in IntelliJ, you will need to ensure that the Groovy and TestNG plugins are installed
via the IntelliJ IDEA | Preferences | Plugins menu. Once installed, you can open Brooklyn from the root folder,
(e.g. ``~/myfiles/brooklyn``) which will automatically open the subprojects.

Brooklyn has informally standardized on arranging `import` statements as per Eclipse's default configuration.
IntelliJ's default configuration is different, which can result in unwanted "noise" in commits where imports are
shuffled backward and forward between the two types - PRs which do this will likely fail the review. To avoid this,
reconfigure IntelliJ to organize imports similar to Eclipse. See [this StackOverflow answer](http://stackoverflow.com/a/17194980/68898)
for a suitable configuration.


## Netbeans

Tips from Netbeans users wanted!



## Debugging Tips

To debug Brooklyn, create a launch configuration which launches the ``BrooklynJavascriptGuiLauncher`` class. NOTE: You may
need to add additional projects or folders to the classpath of the run configuration (e.g. add the brooklyn-software-nosql
project if you wish to deploy a MongoDBServer). You will also need to ensure that the working directory is set to the jsgui
folder. For IntelliJ, you can set the 'Working directory' of the Run/Debug Configuration to ``$MODULE_DIR$/../jsgui``. For
Eclipse, use the default option of ``${workspace_loc:brooklyn-jsgui}``.

To debug the jsgui (the Brooklyn web console), you will need to build Brooklyn with -DskipOptimization to prevent the build from minifying the javascript.
When building via the command line, use the command ``mvn clean install -DskipOptimization``, and if you are using IntelliJ IDEA, you can add the option
to the Maven Runner by clicking on the Maven Settings icon in the Maven Projects tool window  and adding the ``skipOptimization`` property with no value.

When running at the command line you can enable remote connections so that one can attach a debugger to the Java process:
    Run Java with the following on the command line or in JAVA_OPTS: ``-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005``

To debug a brooklyn instance that has been run with the above JAVA_OPTS, create a remote build configuration (IntelliJ -
Run | Edit Configurations | + | Remote) with the default options, ensuring the port matches the address specified in JAVA_OPTS.
