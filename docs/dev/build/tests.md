---
layout: page
title: Tests
toc: /toc.json
---

We have the following tests groups:

*     normal -- should run quickly, not need internet, and not side effect the machine (apart from a few /tmp files)          
*     integration -- deploys locally, may read and write from internet, takes longer
          if you change an entity, rerun the relevant integration test to make sure all is well!
*     live -- deploys remotely, may provision machines (but should clean up, getting rid of them in a try block)

<!-- TODO describe how to run each of these, as a group, and individually; and profiles -->
