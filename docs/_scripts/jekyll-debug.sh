#!/bin/bash

# launches jekyll as a server at the /brooklyn location, for easy debug

jekyll --pygments --server --auto --base-url /brooklyn --url /brooklyn  $*

