---
title: Locations
layout: page
toc: ../guide_toc.json
categories: [use, guide]

---

Locations are the environments to which Brooklyn deploys applications.
These can be clouds (public or private), fixed infrastructure environments, or your laptop.

Brooklyn looks for Location configuration in `~/.brooklyn/brooklyn.properties`.

### Must have an SSH key

To access any locations, Brooklyn must have access to an SSH key. By default Brooklyn looks for a key at `~/.ssh/id_rsa` and `~/.ssh/id_dsa`.

If you do not already have an SSH key installed, create a new id_rsa key:

{% highlight bash %}
$ ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa
{% endhighlight %}

If you wish to use an existing key SSH, or an SSH key
that has a passphrase, or a location other than `~/.ssh`, you can specify this in
`brooklyn.properties` using `brooklyn.location.localhost.privateKeyFile` and
`brooklyn.location.localhost.privateKeyPassphrase`.

## Localhost

To allow Brooklyn to access locahost the SSH key must be added to the `authorized_keys` on that machine.

{% highlight bash %}
# _Appends_ id_rsa.pub to authorized_keys. Other keys are unaffected.
$ cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
{% endhighlight %}

(MacOS user?: In addition to the above, enable 'Remote Login' in System Preferences >
 Sharing.)


## Cloud Endpoints (via jclouds)

[Apache jclouds](http://www.jclouds.org) is a multi-cloud library that Brooklyn uses to access [many clouds](http://www.jclouds.org/documentation/reference/supported-providers/).

### Example: AWS Virginia Large Centos

{% highlight bash %}
# Provide jclouds with AWS API credentials.
brooklyn.jclouds.aws-ec2.identity = AKA_YOUR_ACCESS_KEY_ID
brooklyn.jclouds.aws-ec2.credential = YourSecretKeyWhichIsABase64EncodedString

# Name this location 'AWS Virginia Large Centos' and wire to AWS US-East-1
brooklyn.location.named.AWS\ Virginia\ Large\ Centos = jclouds:aws-ec2:us-east-1

# Specify image, user and minimum ram size (ie instance size)
brooklyn.location.named.AWS\ Virginia\ Large\ Centos.imageId=us-east-1/ami-7d7bfc14
brooklyn.location.named.AWS\ Virginia\ Large\ Centos.user=root
brooklyn.location.named.AWS\ Virginia\ Large\ Centos.minRam=4096

# Snippet from ~/.brooklyn/brooklyn.properties.
{% endhighlight %}

This will  appear as 'AWS Virginia Large Centos' in the web console, but will need to be escaped on the command line:  `AWS\ Virginia\ Large\ Centos`.

See the Getting Started [template brooklyn.properties](/use/guide/quickstart/brooklyn.properties) for more examples of using cloud endpoints.


## Fixed Infrastructure

Bring-ing your own nodes (BYON) to Brooklyn is straightforward.

You will need the IP addresses of the nodes and the access credentials. Both SSH and password based login are supported.

### Example: On-Prem Iron

{% highlight bash %}
# Use the byon prefix, and provide the IP addresss (or IP ranges)
brooklyn.location.named.On-Prem\ Iron\ Example=byon:(hosts="10.9.1.1,10.9.1.2,produser2@10.9.2.{10,11,20-29}")
brooklyn.location.named.On-Prem\ Iron\ Example.user=produser1
brooklyn.location.named.On-Prem\ Iron\ Example.privateKeyFile=~/.ssh/produser_id_rsa
brooklyn.location.named.On-Prem\ Iron\ Example.privateKeyPassphrase=s3cr3tpassphrase

# Snippet from ~/.brooklyn/brooklyn.properties.
{% endhighlight %}


## Advanced Options

Unusual provider? 'Featureful' API? Brooklyn can cope.

[This spreadsheet](https://docs.google.com/a/cloudsoftcorp.com/spreadsheet/ccc?key=0Avy7Tdf2EOIqdGQzSlNiT2M0V19SejBScDhSdzMtT2c) provides explanation, guidance, and examples for the majority of location options.



