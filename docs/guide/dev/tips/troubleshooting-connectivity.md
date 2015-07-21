---
layout: website-normal
title: Troubleshooting Server Connectivity Issues in the Cloud
toc: /guide/toc.json
---

A common problem when setting up an application in the cloud is getting the basic connectivity right - how
do I get my service (e.g. a TCP host:port) publicly accessible over the internet?

This varies a lot - e.g. Is the VM public or in a private network? Is the service only accessible through
a load balancer? Should the service be globally reachable or only to a particular CIDR?

This guide gives some general tips for debugging connectivity issues, which are applicable to a 
range of different service types. Choose those that are appropriate for your use-case.

## VM reachable
If the VM is supposed to be accessible directly (e.g. from the public internet, or if in a private network
then from a jump host)...

### ping
Can you `ping` the VM from the machine you are trying to reach it from?

However, ping is over ICMP. If the VM is unreachable, it could be that the firewall forbids ICMP but still
lets TCP traffic through.

### telnet to TCP port
You can check if a given TCP port is reachable and listening using `telnet <host> <port>`, such as
`telnet www.google.com 80`, which gives output like:

```
    Trying 31.55.163.219...
    Connected to www.google.com.
    Escape character is '^]'.
```

If this is very slow to respond, it can be caused by a firewall blocking access. If it is fast, it could
be that the server is just not listening on that port.

### DNS and routing
If using a hostname rather than IP, then is it resolving to a sensible IP?

Is the route to the server sensible? (e.g. one can hit problems with proxy servers in a corporate
network, or ISPs returning a default result for unknown hosts).

The following commands can be useful:

* `host` is a DNS lookup utility. e.g. `host www.google.com`.
* `dig` stands for "domain information groper". e.g. `dig www.google.com`.
* `traceroute` prints the route that packets take to a network host. e.g. `traceroute www.google.com`.

## Service is listening

### Service responds
Try connecting to the service from the VM itself. For example, `curl http://localhost:8080` for a
web-service.

On dev/test VMs, don't be afraid to install the utilities you need such as `curl`, `telnet`, `nc`,
etc. Cloud VMs often have a very cut-down set of packages installed. For example, execute
`sudo apt-get update; sudo apt-get install -y curl` or `sudo yum install -y curl`.

### Listening on port
Check that the service is listening on the port, and on the correct NIC(s).

Execute `netstat -antp` (or on OS X `netstat -antp TCP`) to list the TCP ports in use (or use
`-anup` for UDP). You should expect to see the something like the output below for a service.

```
Proto Recv-Q Send-Q Local Address               Foreign Address             State       PID/Program name   
tcp        0      0 :::8080                     :::*                        LISTEN      8276/java           
```

In this case a Java process with pid 8276 is listening on port 8080. The local address `:::8080`
format means all NICs (in IPv6 address format). You may also see `0.0.0.0:8080` for IPv4 format.
If it says 127.0.0.1:8080 then your service will most likely not be reachable externally.

Use `ip addr show` (or the obsolete `ifconfig -a`) to see the network interfaces on your server.

For `netstat`, run with `sudo` to see the pid for all listed ports.

## Firewalls
On Linux, check if `iptables` is preventing the remote connection. On Windows, check the Windows Firewall.

If it is acceptable (e.g. it is not a server in production), try turning off the firewall temporarily,
and testing connectivity again. Remember to re-enable it afterwards! On CentOS, this is `sudo service
iptables stop`. On Ubuntu, use `sudo ufw disable`. On Windows, press the Windows key and type 'Windows
Firewall with Advanced Security' to open the firewall tools, then click 'Windows Firewall Properties'
and set the firewall state to 'Off' in the Domain, Public and Private profiles.

If you cannot temporarily turn off the firewall, then look carefully at the firewall settings. For
example, execute `sudo iptables -n --list` and `iptables -t nat -n --list`.

## Cloud firewalls
Some clouds offer a firewall service, where ports need to be explicitly listed to be reachable.

For example, [security groups for EC2-classic]
(http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-network-security.html#ec2-classic-security-groups)
have rules for the protocols and ports to be reachable from specific CIDRs.

Check these settings via the cloud provider's web-console (or API).

## Quick test of a listener port
It can be useful to start listening on a given port, and to then check if that port is reachable.
This is useful for testing basic connectivity when your service is not yet running, or to a
different port to compare behaviour, or to compare with another VM in the network.

The `nc` netcat tool is useful for this. For example, `nc -l 0.0.0.0 8080` will listen on port
TCP 8080 on all network interfaces. On another server, you can then run `echo hello from client
| nc <hostname> 8080`. If all works well, this will send "hello from client" over the TCP port 8080,
which will be written out by the `nc -l` process before exiting.

Similarly for UDP, you use `-lU`.

You may first have to install `nc`, e.g. with `sudo yum install -y nc` or `sudo apt-get install netcat`.

### Cloud load balancers
For some use-cases, it is good practice to use the load balancer service offered by the cloud provider
(e.g. [ELB in AWS](http://aws.amazon.com/elasticloadbalancing/) or the [Cloudstack Load Balancer]
(http://docs.cloudstack.apache.org/projects/cloudstack-installation/en/latest/network_setup.html#management-server-load-balancing))

The VMs can all be isolated within a private network, with access only through the load balancer service.

Debugging techniques here include ensuring connectivity from another jump server within the private
network, and careful checking of the load-balancer configuration from the Cloud Provider's web-console.

### DNAT
Use of DNAT is appropriate for some use-cases, where a particular port on a particular VM is to be
made available.

Debugging connectivity issues here is similar to the steps for a cloud load balancer. Ensure
connectivity from another jump server within the private network. Carefully check the NAT rules from
the Cloud Provider's web-console.

### Guest wifi
It is common for guest wifi to restrict access to only specific ports (e.g. 80 and 443, restricting
ssh over port 22 etc).

Normally your best bet is then to abandon the guest wifi (e.g. to tether to a mobile phone instead).

There are some unconventional workarounds such as [configuring sshd to listen on port 80 so you can
use an ssh tunnel](http://askubuntu.com/questions/107173/is-it-possible-to-ssh-through-port-80).
However, the firewall may well inspect traffic so sending non-http traffic over port 80 may still fail.

  
