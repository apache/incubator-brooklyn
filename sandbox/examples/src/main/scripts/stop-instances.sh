#!/bin/bash

INSTANCES=`cat instances.txt | cut -d " " -f 2`

if [ -n "$*" ]; then
    INSTANCES=""
    for region in $* ; do
        INSTANCE=`grep $region instances.txt | cut -d " " -f 2`
        INSTANCES=${INSTANCES}${INSTANCE}' '
    done
fi

for instance in $INSTANCES ; do
  REGION=`grep $instance instances.txt | cut -d " " -f 1`
  export EC2_REGION=$REGION
  export EC2_URL=https://$REGION.ec2.amazonaws.com
  echo "stopping $instance"
  ec2-stop-instances $instance
  sed -i.bk /${instance}/D instances.txt
done

wait