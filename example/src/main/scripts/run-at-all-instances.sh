CMD=$1

for x in `cat instances.txt | cut -d " " -f2-` ; do
  echo invoking $CMD at $x
  ssh -f $x "$CMD" | awk '{print "OUTPUT '$x': " $0}' &
done

wait