---
title: FAQ
layout: website-normal
---

## What's the difference between Brooklyn and...

#### Chef and Puppet and other server config management tools?

#### Cloudformation and Heat and other infrastructure declarative tools?
 
#### CloudFoundry and other PaaS platforms?

  
## Why is this page blank?

Supplying the answers are a TODO.


## How do I supply answers?

Click the "Edit this Page" link in the bottom right.


# Common Problems:

## java.lang.OutOfMemoryError: unable to create new native thread

You could encounter this error when running with many entities.

Please **increase the ulimit** if you see such error:

On the VM running Apache Brooklyn, we recommend ensuring nproc and nofile are reasonably high (e.g. higher than 1024, which is often the default).
We recommend setting it limits to a value above 16000.

If you want to check the current limits run `ulimit -a`.

Here are instructions for how to increase the limits for RHEL like distributions.
Run `sudo vi /etc/security/limits.conf` and add (if it is "brooklyn" user running Apache Brooklyn):

    brooklyn           soft    nproc           16384
    brooklyn           hard    nproc           16384
    brooklyn           soft    nofile          16384
    brooklyn           hard    nofile          16384


Generally you do not have to reboot to apply ulimit values. They are set per session.
So after you have the correct values, quit the ssh session and log back in.

For more details, see one of the many posts such as http://tuxgen.blogspot.co.uk/2014/01/centosrhel-ulimit-and-maximum-number-of.html
