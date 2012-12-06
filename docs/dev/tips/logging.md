---
layout: page
title: Logging
toc: /toc.json
---

## Logging: A Quick Overview

For logging, we use **logback** which implements the slf4j API.
This means you can use any slf4j compliant logging framework,
or if you just want something that works logback will work out of the box.

The CLI launcher includes a ``logback.xml`` which logs at DEBUG level 
to a file ``./brooklyn.log`` and INFO to the console,
with a few exceptions.  Exceptions --- and the inverse, favourites which
you might want to enable even if the root logger level is bumped to INFO ---
are in files in ``core/src/main/resources/brooklyn/`` which can easily
be included in your own ``logback.xml`` (one of the nicest features of logback).

Tests have debug logging across the board, included by default from 
``usage/test-support/src/main/resources/logback-tests.xml``.

You can set a specific logback config file to use with:

    -Dlogback.configurationFile=/path/to/config.xml

## Caveats

* logback uses SLF4J version 1.6 which is **not compatible** with 1.5.x. 
  If you have dependent projects using 1.5.x (such as older Grails) things may break.

* If you're not getting the logging you expect in the IDE, make sure 
  ``src/main/resources`` is included in the classpath.
  (In eclipse, right-click the project, the Build Path -> Configure,
  then make sure all dirs are included (All) and excluded (None) -- 
  ``mvn clean install`` should do this for you.)
  You may find that your IDE logs to a file ``brooklyn-tests.log`` 
  if it doesn't distinguish between test build classpaths and normal classpaths.

