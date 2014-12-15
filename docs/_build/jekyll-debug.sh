#!/bin/bash
#
# launches jekyll as a server at the / location, for easy debug

jekyll --pygments --server --auto --url ""  $*

