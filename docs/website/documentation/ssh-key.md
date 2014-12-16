---
title: SSH keys
layout: website-normal
---
Brooklyn requires an SSH key, which will be used to connect to cloud VMs. By default Brooklyn will look for SSH keys named `~/.ssh/id_rsa` or `~/.ssh/id_dsa`. If you do not already have an SSH key installed, create a new key.

{% highlight bash %}
$ ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa
{% endhighlight %}
