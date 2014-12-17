---
title: Increase entropy
layout: website-normal
---
If you are installing AMP on a virtual machine, you may find it useful to increase the Linux kernel entropy to speed up the ssh connections to the managed entities. You can install and configure `rng-tools` or just use /dev/urandom`.

### Installing rng-tool
if you are using a RHEL-based OS:
{% highlight bash %}
yum -y -q install rng-tools
echo "EXTRAOPTIONS=\"-r /dev/urandom\"" | cat >> /etc/sysconfig/rngd
/etc/init.d/rngd start
{% endhighlight %}

if you are using a Debian-based OS:
{% highlight bash %}
apt-get -y install rng-tools
echo "HRNGDEVICE=/dev/urandom" | cat >> /etc/default/rng-tools
/etc/init.d/rng-tools start
{% endhighlight %}

The following links contain further [information for RHEL or CentOS](http://my.itwnik.com/how-to-increase-linux-kernel-entropy/), and [Ubuntu](http://www.howtoforge.com/helping-the-random-number-generator-to-gain-enough-entropy-with-rng-tools-debian-lenny).

### Using /dev/urandom
You can also just mv /dev/random then create it again linked to /dev/urandom, by issuing the following commands:

{% highlight bash %}
sudo mv /dev/random /dev/random-real
sudo ln -s /dev/urandom /dev/random
{% endhighlight %}

