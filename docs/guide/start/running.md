---
title: Running Brooklyn
title_in_menu: Running Brooklyn
layout: guide-normal
menu_parent: index.md
---

This guide will walk you through deploying an example 3-tier web application to a public cloud. 

This tutorial assumes that you are using Linux or Mac OSX.

## Install Brooklyn

Download Brooklyn and obtain a binary build as described on [the download page]({{site.path.website}}/download.html).

{% if brooklyn_version contains 'SNAPSHOT' %}
Expand the `tar.gz` archive (note: as this is a -SNAPSHOT version, your filename will be slightly different):
{% else %}
Expand the `tar.gz` archive:
{% endif %}

{% if brooklyn_version contains 'SNAPSHOT' %}
{% highlight bash %}
$ tar -zxf brooklyn-dist-{{ site.data.brooklyn.version }}-timestamp-dist.tar.gz
{% endhighlight %}
{% else %}
{% highlight bash %}
$ tar -zxf brooklyn-{{ site.data.brooklyn.version }}-dist.tar.gz
{% endhighlight %}
{% endif %}

This will create a `brooklyn-{{ site.data.brooklyn.version }}` folder.

Note: You'll need a Java JRE or SDK installed (version 6 or later), as Brooklyn is Java under the covers.

## Verify SSH

Brooklyn uses SSH extensively and therefore it is worth making sure that you have a known working SSH setup before
starting.

Please check the following items:

- If you are using Mac OSX, open System Preferences, go to the Sharing item, and enable 'Remote Login'.
- You have two files named `~/.ssh/id_rsa` and `~/.ssh/id_rsa.pub`.
  - If these files do not exist, they can be created with `ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa`.
- `~/.ssh/id_rsa` is NOT readable by any other user.
  - You can verify this with `ls -l ~/.ssh/id_rsa` - the line should start with `-rw-------` or `-r--------`. If it
    does not, execute `chmod 0600 ~/.ssh/id_rsa`.
- The file `~/.ssh/authorized_keys` exists and contains a copy of your public key from `~/.ssh/id_rsa.pub`.
  - Note that it is normal for it to contain other items as well.
- The key in `~/.ssh/id_rsa` does *not* have a passphrase.
  - You can test this by executing `ssh-keygen -y`. If it does *not* ask for a passphrase, then your key is OK.
  - If your key does have a passphrase, remove it. You can do this by running `ssh-keygen -p`. Enter the passphrase,
    then when prompted for the new passphrase, hit Enter.

Now verify your setup by running the command: `ssh localhost echo hello world`

If you see a message similar to this:

<pre>
The authenticity of host 'localhost (::1)' can't be established.
RSA key fingerprint is 7b:e3:8e:c6:5b:2a:05:a1:7c:8a:cf:d1:6a:83:c2:ad.
Are you sure you want to continue connecting (yes/no)?
</pre>

then answer 'yes', and then repeat the command run again.

If the response is `hello world`, with no other output or prompts, then your SSH setup is good and Brooklyn should be
able to use it without a problem.

If these steps are not working, [these instructions]({{ site.path.guide }}/use/guide/locations/) may be
useful.




## Launch Brooklyn

Let's setup some paths for easy commands.

(Click the clipboard on these code snippets for easier c&p.)

{% highlight bash %}
$ cd brooklyn-{{ site.data.brooklyn.version }}
$ BROOKLYN_DIR="$(pwd)"
$ export PATH=$PATH:$BROOKLYN_DIR/bin/
{% endhighlight %}

We can do a quick test drive by launching Brooklyn:

{% highlight bash %}
$ brooklyn launch
{% endhighlight %}

Brooklyn will output the address of the management interface:


`INFO  Starting brooklyn web-console on loopback interface because no security config is set`

`INFO  Started Brooklyn console at http://127.0.0.1:8081/, running classpath://brooklyn.war and []`

### Next

Now that Brooklyn is up and running we can look at getting it to manage some applications. 
