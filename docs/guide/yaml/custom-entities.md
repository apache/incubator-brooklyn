---
title: Custom Entities
layout: website-normal
toc: ../guide_toc.json
categories: [use, guide, defining-applications]
---

So far we've covered how to configure and compose entities.
There's a large library of blueprints available, but
there are also times when you'll want to write your own.

For complex use cases, you can write JVM, but for many common situations,
some of the highly-configurable blueprints make it easy to write in YAML,
including `bash` and Chef.
 

### Vanilla Software using `bash`

The following blueprint shows how a simple script can be embedded in the YAML
(the `|` character is special YAML which makes it easier to insert multi-line text):

{% highlight yaml %}
{% readj example_yaml/vanilla-bash-netcat.yaml %}
{% endhighlight %}

This starts a simple `nc` listener on port 4321 which will respond `hello` to the first
session which connects to it. Test it by running `telnet localhost 4321`.  

This is just a simple script, but it shows how any script can be easily embedded here,
including a script to download and run other artifacts.
Many artifacts are already packaged such that they can be downloaded and launched 
with a simple script, and `VanillaSoftwareProcess` can also be used for them. 
We can specify a `download.url` which downloads artifacts (unpacking TAR, TGZ, and ZIP archives)
before running `launch.command` relative to where that file is installed (or unpacked),
with `./start.sh` being the default `launch.command`.

So if we create a file `/tmp/netcat-server.tgz` containing just `start.sh` in the root
which consists of the two lines in the previous example,
we can instead write our example as: 

{% highlight yaml %}
{% readj example_yaml/vanilla-bash-netcat-file.yaml %}
{% endhighlight %}

The one requirement of the script is that it store the process ID (PID) in the file
pointed to by `$PID_FILE`, hence the second line of the script.
This is because Brooklyn wants to monitor the services under management. 
(There are other options; you can set `checkRunning.command` and `stop.command` instead,
as documented on the Javadoc of the `VanillaSoftwareProcess` class,
and those scripts will be used instead of checking and stopping the process whose PID is in `$PID_FILE`.)

And indeed, once you've run one `telnet` to the server, you'll see that the 
service has gone "on fire" in Brooklyn -- because the `nc` process has stopped. 
Besides detecting this failure, Brooklyn policies can be added to the YAML to take appropriate 
action. A simple recovery here might just be to restart the process:

{% highlight yaml %}
{% readj example_yaml/vanilla-bash-netcat-restarter.yaml %}
{% endhighlight %}

Autonomic management in Brooklyn often follows the principle that complex behaviours emerge
from composing simple policies.
The blueprint above uses one policy to triggering a failure sensor when the service is down,
and another responds to such failures by restarting the service.
This makes it easy to configure various aspects, such as to delay to see if the service itself recovers
(which here we've set to 15 seconds) or to bail out on multiple failures within a time window (which again we are not doing).
Running with this blueprint, you'll see that the service shows as on fire for 15s after a `telnet`,
before the policy restarts it. 

For an even more interesting way to test it, look at the blueprint defining
[a netcat server and client](example_yaml/vanilla-bash-netcat-w-client.yaml).
This uses `initializers` to define an effector to `sayHiNetcat` on the `Simple Pinger` client,
using `env` variables to inject the `netcat-server` location and 
`parameters` to pass in per-effector data:

      env:
        TARGET_HOSTNAME: $brooklyn:component("netcat-server").attributeWhenReady("host.name")
      brooklyn.initializers:
      - type: org.apache.brooklyn.core.effector.ssh.SshCommandEffector
        brooklyn.config:
          name: sayHiNetcat
          description: Echo a small hello string to the netcat entity
          command: |
            echo $message | nc $TARGET_HOSTNAME 4321
          parameters:
            message:
              description: The string to pass to netcat
              defaultValue: hi netcat

This blueprint also uses initializers to define sensors on the `netcat-server` entity
so that the `$message` we passed above gets logged and reported back:

      launch.command: |
        echo hello | nc -l 4321 >> server-input &
        echo $! > $PID_FILE
      brooklyn.initializers:
      - type: org.apache.brooklyn.core.sensor.ssh.SshCommandSensor
        brooklyn.config:
          name: output.last
          period: 1s
          command: tail -1 server-input

This is still a simple example, but worth going through carefully.
It shows many of the building blocks used in real-world blueprints,
and how they can often be easily described and combined in Brooklyn YAML blueprints.
Next, if you need to drive off-piste, or you want to write tests against these blueprints,
have a look at, for example, `VanillaBashNetcatYamlTest.java` in the Brooklyn codebase,
or follow the other references below.
