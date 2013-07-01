---
layout: page
title: Maven Build
toc: /toc.json
---

## The Basics

To build the code, you need Maven (v3) installed and Java (1.6).
With that in place, you should be able to build everything with a:

{% highlight bash %}
brooklyn% mvn clean install
{% endhighlight %}

Key things to note if you're new to Maven:

* You may need more JVM memory, e.g. at the command-line (or in `.profile`):

  ``export MAVEN_OPTS="-Xmx1024m -Xms512m -XX:MaxPermSize=256m``

* You can do this in specific projects as well.

* Add ``-DskipTests`` to skip tests. 

* Run ``-PIntegration`` to run integration tests, or ``-PLive`` to run live tests
  ([tests described here](tests.html))

* Nearly all the gory details are in the root ``pom.xml``, which is referenced by child project poms.

* You can also open and use the code in your favourite IDE,
  although you may hit a few **[snags](ide.html)**
  (that link has some tips for resolving them too)


## Other Handy Hints

* On some **Ubuntu** (e.g. 10.4 LTS) maven v3 is not currently available from the repositories.
  Some instructions for installing at are [at superuser.com](http://superuser.com/questions/298062/how-do-i-install-maven-3).

* The **mvnf** script 
  ([get the gist here](https://gist.github.com/2241800)) 
  simplifies building selected projects, so if you just change something in ``software-webapp`` 
  and then want to re-run the examples you can do:
  
  ``examples/simple-web-cluster% mvnf ../../{software/webapp,usage/all}`` 

* The **developers catalog** ([developers-catalog.xml](developers-catalog.xml)) uses artifacts from your local `~/.m2/repository/...` (after building from source). This avoids unnecessary web requests to Maven Central or Sonatype, and will allow you to work off-line.
  
  ``wget {{site.url}}/dev/build/developers-catalog.xml > ~/.brooklyn/catalog.xml`` 

## Appendix: Sample Output

A healthy build will look something like the following,
including a few warnings (which we have looked into and
understand to be benign and hard to get rid of them,
although we'd love to if anyone can help!):

{% highlight bash %}
brooklyn% mvn clean install
[INFO] Scanning for projects...
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Build Order:
[INFO] 
[INFO] Brooklyn Parent Project
[INFO] Brooklyn API
[INFO] Brooklyn Test Support
[INFO] Brooklyn Core

...

Mar 29, 2012 4:30:17 PM java.util.jar.Attributes read
WARNING: Duplicate name in Manifest: Manifest-Version.
Ensure that the manifest does not have duplicate entries, and
that blank lines separate individual sections in both your
manifest and in the META-INF/MANIFEST.MF entry in the jar file.

...

[WARNING] We have a duplicate org/jclouds/cloudsigma/CloudSigmaAsyncClient.class in 
/Users/alex/.m2/repository/org/jclouds/provider/cloudsigma-zrh/1.4.0/cloudsigma-zrh-1.4.0.jar

...

[INFO] --- maven-jar-plugin:2.3.1:jar (default-jar) @ brooklyn-all ---
[WARNING] JAR will be empty - no content was marked for inclusion!

...

[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary:
[INFO] 
[INFO] Brooklyn Parent Project ........................... SUCCESS [0.813s]
[INFO] Brooklyn API ...................................... SUCCESS [6.115s]
[INFO] Brooklyn Test Support ............................. SUCCESS [4.592s]
[INFO] Brooklyn Core ..................................... SUCCESS [1:20.536s]
[INFO] Brooklyn Policies ................................. SUCCESS [57.996s]
[INFO] Brooklyn Software Base ............................ SUCCESS [29.869s]
[INFO] Brooklyn OSGi Software Entities ................... SUCCESS [9.392s]
[INFO] Brooklyn Web App Software Entities ................ SUCCESS [49.606s]
[INFO] Brooklyn Messaging Software Entities .............. SUCCESS [12.198s]
[INFO] Brooklyn NoSQL Data Store Software Entities ....... SUCCESS [9.205s]
[INFO] Brooklyn Database Software Entities ............... SUCCESS [7.815s]
[INFO] Brooklyn Whirr Base Entities ...................... SUCCESS [21.292s]
[INFO] Brooklyn Hadoop System Entities ................... SUCCESS [9.972s]
[INFO] Brooklyn OpenShift PaaS System Entities ........... SUCCESS [11.857s]
[INFO] Brooklyn Web Console .............................. SUCCESS [1:00.814s]
[INFO] Brooklyn Launcher ................................. SUCCESS [1:00.603s]
[INFO] Brooklyn All Things ............................... SUCCESS [23.358s]
[INFO] hello-world-webapp Maven Webapp ................... SUCCESS [2.521s]
[INFO] Brooklyn Simple Web Cluster Example ............... SUCCESS [5.860s]
[INFO] Brooklyn Hadoop and Whirr Example ................. SUCCESS [4.892s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 7:52.328s
[INFO] Finished at: Thu Mar 29 16:30:17 BST 2012
[INFO] Final Memory: 180M/528M
[INFO] ------------------------------------------------------------------------

{% endhighlight %}
