---
layout: page
title: Tests
toc: /toc.json
---

We have the following tests groups:

*     normal (i.e. no group) -- should run quickly, not need internet, and not side effect the machine (apart from a few /tmp files)          
*     Integration -- deploys locally, may read and write from internet, takes longer.
          If you change an entity, rerun the relevant integration test to make sure all is well!
*     Live -- deploys remotely, may provision machines (but should clean up, getting rid of them in a try block)
*     Live-sanity -- a sub-set of "Live" that can be run regularly; a trade-off of optimal code coverage for the 
      time/cost of those tests.
*     WIP -- short for "work in progress", this will disable the test from being run by the normal brooklyn maven profiles,
      while leaving the test enabled so that one can work on it in IDEs or run the selected test(s) from the command line.
*     Acceptance -- this (currently little-used) group is for very long running tests, such as soak tests

To run these from the command line, use something like the following:

*     normal: `mvn clean install`
*     integration: `mvn clean verify -PEssentials,Locations,Entities,Integration -Dmaven.test.failure.ignore=true`
*     Live: `mvn clean verify -PEntities,Locations,Entities,Live -Dmaven.test.failure.ignore=true`
*     Live-sanity: `mvn clean verify -PEntities,Locations,Entities,Live-sanity -Dmaven.test.failure.ignore=true`

<!-- TODO describe how to run each of these, as a group, and individually; and profiles -->
