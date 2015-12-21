---
layout: website-normal
title: Brooklyn Remote Debugging
toc: /guide/toc.json
---

Usually during development, you will be running Brooklyn from your IDE (see [IDE Setup](../env/ide/)), in which case
debugging is as simple as setting a breakpoint. There may however be times when you need to debug an existing remote
Brooklyn instance (often referred to as Resident Brooklyn, or rBrooklyn) on another machine, usually in the cloud.

Thankfully, the tools are available to do this, and setting it up is quite straightforward. The steps are as follows:

* [Getting the right source code version](#sourceCodeVersion)
* [Starting Brooklyn with a debug listener](#startingBrooklyn)
* [Creating an SSH tunnel](#sshTunnel)
* [Connecting your IDE](#connectingIDE)

## <a name="sourceCodeVersion"></a>Getting the right source code version
The first step is to ensure that your local copy of the source code is at the version used to build the remote Brooklyn
instance. The git commit that was used to build Brooklyn is available via the REST API:

    http://<remote-address>:<remote-port>/v1/server/version

This should return details of the build as a JSON string similar to the following (formatted for clarity):

{% highlight json %}
{
    "version": "0.9.0-SNAPSHOT",  {% comment %}BROOKLYN_VERSION{% endcomment %}
    "buildSha1": "c0fdc15291702281acdebf1b11d431a6385f5224",
    "buildBranch": "UNKNOWN"
}
{% endhighlight %}

The value that we're interested in is `buildSha1`. This is the git commit that was used to build Brooklyn. We can now
checkout and build the Brooklyn code at this commit by running the following in the root of your Brooklyn repo:

{% highlight bash %}
% git checkout c0fdc15291702281acdebf1b11d431a6385f5224
% mvn clean install -DskipTests
{% endhighlight %}

Whilst building the code isn't strictly necessary, it can help prevent some IDE issues.

## <a name="startingBrooklyn"></a>Starting Brooklyn with a debug listener
By default, Brooklyn does not listen for a debugger to be attached, however this behaviour can be set by setting JAVA_OPTS,
which will require a restart of the Brooklyn node. To do this, SSH to the remote Brooklyn node and run the following in the
root of the Brooklyn installation:

{% highlight bash %}
# NOTE: Running this kill command will lose existing apps and machines if persistence is disabled.
% kill `cat pid_java`
% export JAVA_OPTS="-Xms256m -Xmx1g -XX:MaxPermSize=256m -agentlib:jdwp=transport=dt_socket,address=127.0.0.1:8888,server=y,suspend=n"
% bin/brooklyn launch &
{% endhighlight %}

If `JAVA_OPTS` is not set, Brooklyn will automatically set it to `"-Xms256m -Xmx1g -XX:MaxPermSize=256m"`, which is why
we have prepended the agentlib settings with these values here.

You should see the following in the console output:

    Listening for transport dt_socket at address: 8888

This will indicate the Brooklyn is listening on port 8888 for a debugger to be attached.

## <a name="sshTunnel"></a>Creating an SSH tunnel

If port 8888 is accessible on the remote Brooklyn server, then you can skip this step and simply use the address of the
server in place of 127.0.0.1 in the [Connecting your IDE](#connectingIDE) section below. It will normally be possible to
make the port accessible by configuring Security Groups, iptables, endpoints etc., but for a quick ad-hoc connection it's
usually simpler to create an SSH tunnel. This will create an open SSH connection that will redirect traffic from a port
on a local interface via SSH to a port on the remote machine. To create the tunnel, run the following on your local
machine:

{% highlight bash %}
# replace this with the address or IP of the remote Brooklyn node
REMOTE_HOST=<remote-address>
# if you wish to use a different port, this value must match the port specified in the JAVA_OPTS
REMOTE_PORT=8888 
# if you wish to use a different local port, this value must match the port specified in the IDE configuration
LOCAL_PORT=8888 
# set this to the login user you use to SSH to the remote Brooklyn node
SSH_USER=root 
# The private key file used to SSH to the remote node. If you use a password, see the alternative command below
PRIVATE_KEY_FILE=~/.ssh/id_rsa 

% ssh -YNf -i $PRIVATE_KEY_FILE -l $SSH_USER -L $LOCAL_PORT:127.0.0.1:$REMOTE_PORT $REMOTE_HOST
{% endhighlight %}

If you use a password to SSH to the remote Brooklyn node, simply remove the `-i $PRIVATE_KEY_FILE` section like so:

    ssh -YNf -l $SSH_USER -L $LOCAL_PORT:127.0.0.1:$REMOTE_PORT $REMOTE_HOST

If you are using a password to connect, you will be prompted to enter your password to connect to the remote node upon
running the SSH command.

The SSH tunnel should now be redirecting traffic from port 8888 on the local 127.0.0.1 network interface via the SSH 
tunnel to port 8888 on the remote 127.0.0.1 interface. It should now be possible to connect the debugger and start
debugging.

## <a name="connectingIDE"></a> Connecting your IDE

Setting up your IDE will differ depending upon which IDE you are using. Instructions are given here for Eclipse and
IntelliJ, and have been tested with Eclipse Luna and IntelliJ Ultimate 14.

### Eclipse Setup

To debug using Eclipse, first open the Brooklyn project in Eclipse (see [IDE Setup](../env/ide/)).

Now create a debug configuration by clicking `Run` | `Debug Configurations...`. You will then be presented with the 
Debug Configuration dialog.

Select `Remote Java Application` from the list and click the 'New' button to create a new configuration. Set the name
to something suitable such as 'Remote debug on 8888'. The Project can be set to any of the Brooklyn projects, the 
Connection Type should be set to 'Standard (Socket Attach)'. The Host should be set to either localhost or 127.0.0.1
and the Port should be set to 8888. Click 'Debug' to start debugging.

### IntelliJ Setup

To debug using IntelliJ, first open the Brooklyn project in IntelliJ (see [IDE Setup](../env/ide/)).

Now create a debug configuration by clicking `Run` | `Edit Configurations`. You will then be presented with the
Run/Debug Configurations dialog.

Click on the `+` button and select 'Remote' to create a new remote configuration. Set the name to something suitable
such as 'Remote debug on 8888'. The first three sections simply give the command line arguments for starting the java
process using different versions of java, however we have already done this in 
[Starting Brooklyn with a debug listener](#startingBrooklyn). The Transport option should be set to 'Socket', the Debugger Mode should be set to 'Attach', the
Host should be set to localhost or 127.0.0.1 (or the address of the remote machine if you are not using an SSH tunnel),
and the Port should be set to 8888. The 'Search sources' section should be set to `<whole project>`. Click OK to save the
configuration, then select the configuration from the configurations drop-down and click the debug button to start
debugging.

### Testing

The easiest way to test that remote debugging has been setup correctly is to set a breakpoint and see if it is hit. An
easy place to start is to create a breakpoint in the `ServerResource.java` class, in the `getStatus()` 
method. 

