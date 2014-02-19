#!/usr/bin/env sh

# This script is a fairly grim workaround for the following scenario:
# * We'd like certain files to be compressed with gzip at build time so 
#   Jersey can serve the compressed content when the Accept-Encoding:gzip
#   header is given.
# * We want to keep the input files given to gzip so that we serve files 
#   correctly if a request's Accept-Encoding header is none.
# * By default gzip replaces input files with its compressed output. This
#   can be surpressed with the -k flag, but -k is a relatively new addition 
#   to gzip and its presence can't be relied on. So we're stuck with -c
#   and redirecting stdout.
# * The exec-maven-plugin is a handy tool for executing arbitrary commands
#   at build. Sadly, it does not produce valid .gz files when the outputFile
#   parameter is given.
# * Hence this script.

# When exec-maven-plugin 1.3 is released we should revisit this file.

for f in $@; do
    gzip --best -c $f > $f.gz
done

