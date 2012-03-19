#!/bin/bash
# this generates the site in _site
# override --url /myMountPoint   if you don't like the default set it /_config.yml
jekyll --pygments $*
