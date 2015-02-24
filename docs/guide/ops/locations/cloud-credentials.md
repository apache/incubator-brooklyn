---
title: Cloud Setup
layout: website-normal
---

To connect to a Cloud, Brooklyn requires appropriate credentials. These comprise the "identity" and 
"credential" in Brooklyn terminology. 

For private clouds (and for some clouds being targeted using a standard API), the "endpoint"
must also be specified, which is the cloud's URL.

The [jclouds guides](https://jclouds.apache.org/guides) includes documentation on configuring 
different clouds.


## AWS

### Credentials

AWS has an "access key" and a "secret key", which correspond to Brooklyn's identity and credential 
respectively.

These keys are the way for any programmatic mechanism to access the AWS API.

To generate an access key and a secret key, see [jclouds instructions](http://jclouds.apache.org/guides/aws) 
and [AWS IAM instructions](http://docs.aws.amazon.com/IAM/latest/UserGuide/ManagingCredentials.html).

An example of the expected format is shown below:

    brooklyn.location.jclouds.aws-ec2.identity=ABCDEFGHIJKLMNOPQRST
    brooklyn.location.jclouds.aws-ec2.credential=abcdefghijklmnopqrstu+vwxyzabcdefghijklm


### Tidying up after jclouds

Security groups are not always deleted by jclouds. This is due to a limitation in AWS (see
https://issues.apache.org/jira/browse/JCLOUDS-207). In brief, AWS prevents the security group
being deleted until there are no VMs using it. However, there is eventual consistency for
recording which VMs still reference those security groups. After deleting the VM, it can sometimes
take several minutes before the security group can be deleted. jclouds retries for 3 seconds, but 
does not block for longer.

There is utility written by Cloudsoft for deleting these unused resources:
http://www.cloudsoftcorp.com/blog/2013/03/tidying-up-after-jclouds.


## Google Compute Engine

### Credentials

Google Compute Engine (GCE) uses a service account e-mail address for the identity, and a private key 
as the credential.

To obtain these from GCE, see the [jclouds instructions](https://jclouds.apache.org/guides/google).

An example of the expected format is shown below (note the credential is one long line, 
with `\n` to represent the new line characters):

    brooklyn.location.jclouds.google-compute-engine.identity=123456789012@developer.gserviceaccount.com
    brooklyn.location.jclouds.google-compute-engine.credential=-----BEGIN RSA PRIVATE KEY-----\nabcdefghijklmnopqrstuvwxyznabcdefghijk/lmnopqrstuvwxyzabcdefghij\nabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghij+lm\nnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklm\nnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxy\nzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijk\nlmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvw\nxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghi\njklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstu\nvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefg\nhijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrs\ntuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcde\nfghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvw\n-----END RSA PRIVATE KEY-----


### Quotas

GCE accounts can have low default [quotas](https://cloud.google.com/compute/docs/resource-quotas).

It is easy to requesta quota increase by submitting a [quota increase form](https://support.google.com/cloud/answer/6075746?hl=en).
 

### Networks

GCE accounts often have a limit to the number of networks that can be created. One work around 
is to manually create a network with the required open ports, and to refer to that named network
in Brooklyn's location configuration.

To create a network, see [GCE network instructions](https://cloud.google.com/compute/docs/networking#networks_1).

For example, for dev/demo purposes an "everything" network could be created that opens all ports.

| Name                        | everything                  |
| Description                 | opens all tcp ports         |
| Source IP Ranges            | 0.0.0.0/0                   |
| Allowed protocols and ports | tcp:0-65535 and udp:0-65535 |


