---
title: SSH Keys
layout: website-normal
---

SSH keys are one of the simplest and most secure ways to access remote servers.
They consist of two parts:

* A private key (e.g. `id_rsa`) which is known only to one party or group
  
* A public key (e.g. `id_rsa.pub`) which can be given to anyone and everyone,
  and which can be used to confirm that a party has a private key
  (or has signed a communication with the private key)
  
In this way, someone -- such as you -- can have a private key,
and can install a public key on a remote machine (in an `authorized_keys` file)
for secure automated access.
Commands such as `ssh` (and Brooklyn) can log in without
revealing the private key to the remote machine,
the remote machine can confirm it is you accessing it (if no one else has the private key),
and no one snooping on the network can decrypt of any of the traffic.
 

### Creating an SSH Key

If you don't have an SSH key, create one with:

{% highlight bash %}
$ ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa
{% endhighlight %}


### Localhost Setup

If you want to deploy to `localhost`, ensure that you have a public and private key,
and that your key is authorized for ssh access:

{% highlight bash %}
# _Appends_ id_rsa.pub to authorized_keys. Other keys are unaffected.
$ cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
{% endhighlight %}

You should be able to run `ssh localhost` without any password required.

**Got a passphrase?** Set `brooklyn.location.localhost.privateKeyPassphrase`
as described [here](index.html#os-setup)

**MacOS user?** In addition to the above, enable "Remote Login" in "System Preferences > Sharing".


### Potential Problems

* `~/.ssh/` or files in that directory too open: they should be visible only to the user (apart from public keys),
  both on the source machine and the target machine
 
* Sometimes machines are configured with different sets of support SSL/TLS versions and ciphers;
  if command-line `ssh` and `scp` work, but Brooklyn/java does not, check the versions enabled in Java and on both servers.

* Missing entropy: creating and using ssh keys requires randomness available on the servers,
  usually in `/dev/random`; see [here]({{ site.path.website }}/documentation/increase-entropy.html) for more information
