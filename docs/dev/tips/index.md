---
layout: page
title: Miscellaneous Tips and Tricks
toc: /toc.json
---

TODO

* caching / maven, ivy

* groovy bothersome, use tests heavily
     pretty good to run in IDE (quicker to build) once it is set up
     in Eclipse
          set groovy nature (add groovy support in your IDE)
          remove src exclude everything (**) lines in .classpath
               find . -name .classpath -exec sed -i 's/excluding="[*\/]*" //' {} \;
          web console is trickier, you have to (after a successful maven build)
                    add a whole bunch of folders as source folders on the build path
                    add target/plugin-classes as a class file folder on the build path
               see grails discussions for more info; easier is to run "grails run-app" in the directory,
               if working on the grails project

* consult SSH output when debugging entities
