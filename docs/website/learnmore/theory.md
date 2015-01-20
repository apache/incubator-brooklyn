---
layout: website-normal
title: The Theory behind Brooklyn
children:
- { section: "Why Brooklyn?" }
- { section: Blueprints }
- { section: Benefits }
- { section: Standards }
---

<div class="jumobotron" markdown="1">

Brooklyn is a framework for modeling, monitoring, and managing applications
through autonomic blueprints.

</div>

## Why Brooklyn?

Building and deploying applications in the cloud computing era has changed many
things. Provision a bare server just-in-time, and use automated tools to install
an application. Use APIs to add the server to a load balancer. When load goes
up, provision another server; when load drops, kill a server to save money.

Many new tools have appeared that take advantage of this new era. However each
of them only solve part of the problem and don't consider the big picture. For
example, configuration management tools such as Chef can, in a single command,
provision a new cloud server then install and configure an application -- but
they require extra programming to reconfigure an load balancer whenever the pool
of web servers changes. Amazon Auto Scaling can provision new servers and update
load balancers, but it is dependent on CloudWatch -- this means either using
proxy metrics such as average response time, or writing more code to expose an
application's real metrics. A dedicated monitoring tool may be able to easily
monitor the key metrics with little effort, but its alerts will need to be
integrated it into the server provisioning process.

So all the tools are there to to create and manage a cloud-scale application
that can adapt to demand to meet user expectations without wasting money on
superfluous services - but you will need several such tools and it is up to you
to integrate them into your deployment plan. Some of these tools -- such as the
Amazon Web Services web of EC2, CloudWatch, AutoScaling and CloudFormation --
mean that you may suffer from lock-in. Related projects in OpenStack (Heat,
Ceilometer, Murano, Solum, etc) provide similar functionality but again for a
restricted target. The most common policies (such as minimising request latency)
may be easy, but less common policies such as follow-the-sun and follow-the-moon
may be up to you to implement. Your scaling policies may understand that
"high demand = add another server", but may not understand requirements such as
some clustered services requiring an odd number of instances to prevent voting
deadlocks.


## How Brooklyn Can Help

In this context the advantage of Brooklyn becomes apparent: a single tool is
able to manage provisioning and application deployment, monitor an application's
health and metrics, understand the dependencies between components (such as
knowing that adding a new web server means that the load balancer needs
reconfiguration) and apply complex policies to manage the application. The tool
provides a REST API and a GUI, and allows the autonomic blueprints to be treated
as an integral part of the application. With Brooklyn, these policies become
modular components which can be reused and easily added to blueprints.

Brooklyn is about deploying and managing applications: composing a full stack
for an application; deploying to cloud and non-cloud targets; using monitoring
tools to collect key health/performance metrics; responding to situations
such as a failing node; and adding or removing capacity to match demand.


## Blueprints

A Brooklyn blueprint defines an application, using a declarative YAML syntax
supporting JVM plugins. A basic blueprint might comprise a single process,
such as a web-application server running a WAR file or a SQL database and
its associated DDL scripts. More complex blueprints encompass combinations
of processes across multiple machines and services, such as a load-balancing
HTTP server or SDN controller fronting a cluster of J2EE application
servers, in turn connected to a resilient cluster of SQL database servers.
Even larger clustered application running in multiple regions can be
described, with features such as message buses with resilient brokers,
cacheing tiers of NoSQL key-value store servers, a high-availability
database cluster and multiple application components connected across these
layers.

One main benefit of these blueprints is that they are composable:
best-practice blueprints for one process or pattern (e.g. a Cassandra
cluster) can be incorporated in other blueprints (e.g. an application with a
Cassandra cluster as one component). Another major benefit is that the
blueprints can be treated as source code as part of an applications
codebase: tested, tracked, versioned, and hardened as an integral part of
the devops process. In some ways, Brooklyn is to run-time what Maven is to
build-time.


### Blueprints Turn into Deployments

Brooklyn knows about Chef, Salt, and similar tools, and APT and Yum and
plain old shell scripts, for deploying application components. Blueprints
are built from a mixture of both off-the-shelf packages such as Tomcat,
MySQL, Cassandra, and many more from our library; and components that are
bespoke to individual applications; together with policies that allow the
application to be self-managing.

Brooklyn is built for the cloud, and will take a blueprint and deploy it to
one of many supported clouds or even to multiple different clouds, or to
private infrastructure (bring-your-own-node), or to other platforms. It will
dynamically configure and connect all the different components of an
application, e.g. so load balancers know where the web servers are and the
web applications know where the database is.

Brooklyn collects key metrics to monitor the health of applications; for
example, by sending a request and measuring latency, or installing
monitoring tools and using those to read a server's management interface to
determine the request queue length. These metrics can be fed into policies,
which automatically take actions such as restarting a failed node, or
scaling out the web tier if user demand exceeds capacity. This allows an
application to be self-managing: to recover itself from simple failures, to
scale out when demand increases and meet capacity; then scale in as demand
drops and stop paying for spare capacity.

In short, Brooklyn blueprints allow the best practices for deploying and
managing complex software to be codified as part of the software development
process.



### <a id="benefits"></a> Agile and Flexible

Brooklyn is a product built from the ground up for application agility. This
includes portability across non-cloud, cloud, and PaaS targets; devops-style
infrastructure-as-code applied to applications; and real-time autonomic
management based on promise theory. Some introductions to these concepts,
associated tools, and open specifications may be useful.

Cloud computing at its core is about provisioning resources on-demand. The most
widely known aspect is IaaS (infrastructure-as-a-service) such as Amazon EC2,
Softlayer, Google Cloud Platform, Apache CloudStack, or OpenStack. By leveraging
the Apache jclouds project (and contributing heavily to it), the Brooklyn
project is able to work with a large number of such providers. Higher up the
stack, however, there is an increasingly diverse set of platform targets, from
PaaS (platform-as-a-service) such as Cloud Foundry and Apache Stratos, through
to myriad containers and runtime fabrics such as LXC/Docker, Apache Mesos,
Apache Karaf, Apache Hadoop, and Apache Usergrid and other backend-as-a-service
environments. Brooklyn is based on the premise that applications may need to run
in any or all of these, and the model must be flexible and open enough to
support this.

The buzzword-compliant trends of agile and devops have reinforced many important
lessons:

- The truth is in the code (not any ancillary documents)
- If it isn't tested then assume it isn't working
- Toolchain integration and APIs are key to a project's success
- Even more critical is empowering all stakeholders to a project
- Brooklyn's focus on blueprinting and modeling as code and APIs serves these
principles.

### Autonomic Computing

Another major influence on the design of Brooklyn are the ideas of autonomic
computing and promise theory. It is not necessary to have a thorough
understanding of these to use Brooklyn, but contributors tend to become versed
in these ideas quickly. Essentially, autonomics is based on the concept of
components looking after themselves where possible (self-healing,
self-optimizing, etc), and exposing a sensor (data outputs) and effector
(operations) API where they may need to controlled by another element. Promise
theory extends this approach by introducing the idea that communicating intent
(through promises) is a more reliable basis for complex cooperating systems than
obligation-based effectors. Tools such as CF Engine, Chef, Puppet, Ansible, and
Salt apply promise theory to files and processes on machines; Brooklyn can
leverage all of these tools, complementing it with an application-oriented
model.

### Standards

Finally we note some emerging standards in this area. OASIS CAMP 
(<a href="https://www.oasis-open.org/committees/tc_home.php?wg_abbrev=camp#technical">Cloud Application Management for Platforms</a>) 
and TOSCA 
(<a href="https://www.oasis-open.org/committees/tosca/">Topology and Orchestration Specification for Cloud Applications</a>) 
both define YAML application models similar to Brooklyn's. 
CAMP focuses on the REST API for interacting with such a
management layer, and TOSCA focuses on declarative support for more
sophisticated orchestration. Currently Brooklyn uses a YAML which complies with
CAMP's syntax and exposes many of the CAMP REST API endpoints. We would like to
support the hot-off-the-press TOSCA YAML and expand the CAMP REST API coverage.
