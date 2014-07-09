#!/bin/bash
# Creates an instance in each of the given region.
# Must supply the ami to use,
# the region,
# the directory with the ssh-key that aws generated for that region,
# and optionally supply a public key to be added to ~/.ssh/authorized_keys on the created instance.

SCRIPTS_DIR=`cd \`dirname $0\` && pwd`
SECRETS_DIR=$SCRIPTS_DIR/../scripts/secrets

# Command line argument parsing
while [ "${1:0:1}" == "-" ]; do
    case $1 in
	--ami | -a)
		shift
	    VANILLA_AMI=$1
	    ;;
	--region | -r)  #one of eu-west-1,us-east-1,us-west-1,ap-southeast-1,ap-northeast-1
	    shift
	    REGION=$1
	    ;;
	--ssh-dir)
	    shift
	    SECRETS_DIR=$1
	    ;;
	--authorized-key)
	    shift
	    AUTHORIZED_KEY_FILE=$1
	    ;;
    esac
    shift
done

if [ -z "$VANILLA_AMI" ] || [ -z "$REGION" ]; then
   echo "Usage: $0  --ami <vanilla-ami-ID> --region <region> [--ssh-dir <dir>] [--authorized-key <file>]"
   exit 1
fi 

SSH_KEY=$SECRETS_DIR/id_rsa-monterey.$REGION
INSTANCE_TYPE=t1.micro

if [ ! -z "$AUTHORIZED_KEY_FILE" ] && [ ! -f "$AUTHORIZED_KEY_FILE" ]; then
   echo "Authorized key file not found at $AUTHORIZED_KEY_FILE"
   echo "Usage: $0  --ami <vanilla-ami-ID> --region <region> [--ssh-dir <dir>] [--authorized-key <file>]"
   exit 1
fi 

if [ ! -f "$SSH_KEY" ]; then
   echo "SSH key not found at $SSH_KEY"
   echo "Usage: $0  --ami <vanilla-ami-ID> --region <region> [--ssh-dir <dir>] [--authorized-key <file>]"
   exit 1
fi 

chmod 0600 $SECRETS_DIR/id*

export EC2_REGION=$REGION
export EC2_URL=https://$REGION.ec2.amazonaws.com

echo "Creating Instance: 
      vanilla-ami=$VANILLA_AMI region=$REGION name=$NAME sshKeyFile=$SSH_KEY instanceType=$INSTANCE_TYPE authorizedKey=$AUTHORIZED_KEY_FILE"


# Launch the instance
# Note that result can include "RESERVATION" 
result=`ec2-run-instances -k monterey.$EC2_REGION -t $INSTANCE_TYPE $VANILLA_AMI`
if [ $? != 0 ]; then
   echo "Failed to create instance in $REGION"
   exit 1
fi
instance=`echo "$result" | grep INSTANCE | cut -f 2`

echo "   waiting for instance $instance..."

while [ "$instance_status" != "running" ]; do
   sleep 10
   result=`ec2-describe-instances | grep $instance`
   instance_status=`echo "$result" | cut -f 6`
   address=`echo "$result" | cut -f 4`
   instance_id=`echo "$result" | cut -f 2`
done
echo "   done ($address)"


# Keep waiting until we can successfully log-in.
echo "   waiting for SSH service"
while [ 1 ]; do
   ssh -i $SSH_KEY -o "StrictHostKeyChecking no" -o "CheckHostIP no" -o "GSSAPIAuthentication no" -o "VerifyHostKeyDNS no" root@$address \
       date &>/dev/null
   if [ $? == 0 ]; then break; fi
   sleep 5
done

# Install the public key to the instance's authorized keys
if [ ! -z "$AUTHORIZED_KEY_FILE" ]; then
    authorized_key=`cat $AUTHORIZED_KEY_FILE`
    ssh -i $SSH_KEY -o "StrictHostKeyChecking no" -o "CheckHostIP no" -o "GSSAPIAuthentication no" -o "VerifyHostKeyDNS no" root@$address "
        echo $authorized_key >> ~/.ssh/authorized_keys
    "
fi

echo $REGION $instance_id $address >> instances.txt

echo "   created $instance, $address in $REGION"
