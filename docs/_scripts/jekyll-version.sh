#!/bin/bash

# BROOKLYN_VERSION_BELOW  (this tag helps auto-update the link below)
jekyll --pygments --server --auto --base-url /brooklyn/v/0.4.0-SNAPSHOT $*

