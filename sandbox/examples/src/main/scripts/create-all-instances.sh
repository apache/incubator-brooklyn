#!/bin/bash -x
# Creates an instance in each of the given regions.
# Must supply the directory with the ssh-key that aws generated for that region,
# and optionally supply a public key to be added to ~/.ssh/authorized_keys on the created instance

# setup directories and files
SCRIPTS_DIR=$(cd $(dirname $0) && pwd)
LOG_DIR="${SCRIPTS_DIR}/log"
AMI_DICTIONARY=${SCRIPTS_DIR}/amis.txt

# defaults
DATESTAMP=$(date "+%Y%m%d-%H%M")

# Command line argument parsing
while [ "${1:0:1}" == "-" ]; do
    case $1 in
	--ssh-dir)
		shift
	    SSH_DIR=$1
	    ;;
	--authorized-key)
		shift
	    AUTHORIZED_KEY_FILE=$1
	    ;;
    esac
    shift
done

if [ -z "$*" ]; then
   echo "Authorized key file not found at $AUTHORIZED_KEY_FILE"
   echo "Usage: $0  --ssh-dir <dir> [--authorized-key <file>] <regions>"
   echo "       where regions is a list of regions to use, such as eu-west-1 us-east-1"
   echo "       the output for each region is written to a file in log/"
   exit 1
fi

echo "Creating instances in regions $*"
mkdir -p "${LOG_DIR}"

for region in $*; do
    echo $region
    ami=`grep $region $AMI_DICTIONARY | cut -d " " -f2-`
    echo $ami
    ${SCRIPTS_DIR}/create-instance.sh --ami "$ami" --region "$region" --ssh-dir $SSH_DIR --authorized-key "$AUTHORIZED_KEY_FILE" &> "${LOG_DIR}/create-instance-${DATESTAMP}-$region.out" &
done
