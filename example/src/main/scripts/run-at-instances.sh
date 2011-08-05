if [ -z "$1" ]; then
   echo "command not found"
   echo "Usage: $0  <ssh command> [regions]"
   echo "       where ssh command is the command to run at each instance"
   echo "       where regions is an option list of regions to use, such as eu-west-1 us-east-1; defaults to all regions"
   exit 1
fi

CMD=$1
shift

INSTANCES=`cat instances.txt | cut -d " " -f3-`

if [ -n "$*" ]; then
	INSTANCES=""
	for region in $* ; do
		INSTANCE=`grep $region instances.txt | cut -d " " -f3-`
		INSTANCES=${INSTANCES}${INSTANCE}' '
	done
fi

for instance in $INSTANCES ; do
  echo invoking $CMD at $instance
  ssh -f root@$instance "$CMD" | awk '{print "OUTPUT '$instance': " $0}' &
done

wait