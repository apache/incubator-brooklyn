#!/bin/bash

# deletes all vmc applications against the current target (if no args) or against all targets specified

vmc_delete_all() {
  for x in `vmc apps | grep brooklyn | awk '{print $2}'` ; do vmc delete $x ; done  
}

if [ -z "$1" ]; then
  vmc_delete_all
else
  for x in $@ ; do
    vmc target $x
    vmc_delete_all
  done
fi
