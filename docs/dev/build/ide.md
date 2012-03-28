---
layout: page
title: IDE Setup
toc: /toc.json
---

TODO

gone are the days when it just works.  maven and eclipse fight, neither quite gets along perfectly with groovy,
    but with a bit of a dance the IDE can be your friend.

* Add the Maven and Groovy plugins
    
## Eclipse

* You may need to ensure src/{main,test}/{java,resources} is created in each project dir:

  ``find . -name src -exec mkdir -p {}/src/{main,test}/{java,resources} \;``

  (TODO check the above)

* Some maven integration sets up crazy filters in the generated ``.classpath`` files,
  excluding * or **
  You can go through and remove these manually in Eclipse (Build Path -> Configure)
  or the filesystem.
  The following command has been suggested to remove these rogue blocks in the generated .classpath files:

  ``find . -name .classpath -exec sed -i 's/excluding=.... //' {} \;``

  (TODO the command above assumes two stars)
    
  
## Intelli-J IDEA

TODO


## Netbeans

TODO


