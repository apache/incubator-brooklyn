#!/bin/bash

# usage: run in the root dir of a project, it will grep in poms up to 3 levels deep
# e.g. where are shaded jars defined?
# brooklyn% grep-in-poms -i slf4j

grep $* {.,*,*/*,*/*/*}/pom.xml
