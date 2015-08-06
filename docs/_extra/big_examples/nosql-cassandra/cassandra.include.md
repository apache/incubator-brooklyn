      
{% readj ../before-begin.include.md %}

## Simple Cassandra Cluster

Go to this particular example's directory:

{% highlight bash %}
% cd simple-nosql-cluster
{% endhighlight %}

The CLI needs to know where to find your compiled examples. You can set this up by exporting
the ``BROOKLYN_CLASSPATH`` environment variable in the following way:

{% highlight bash %}
% export BROOKLYN_CLASSPATH=$(pwd)/target/classes
{% endhighlight %}

The project ``simple-nosql-cluster`` includes several deployment descriptors
for deploying and managing Cassandra, under ``src/main/java``.

The simplest of these, ``SimpleCassandraCluster``, will start a Cassandra cluster. The code is:

{% highlight java %}
public class SimpleCassandraCluster extends AbstractApplication {
  public void init() {
    addChild(EntitySpec.create(CassandraCluster.class)
        .configure(CassandraCluster.INITIAL_SIZE, 1)
        .configure(CassandraCluster.CLUSTER_NAME, "Brooklyn"));
  }
}
{% endhighlight %}

To run that example on localhost (on *nix or Mac, assuming `ssh localhost` requires no password or passphrase):

{% highlight bash %}
% ${BROOKLYN_HOME}/bin/brooklyn launch --app brooklyn.demo.SimpleCassandraCluster \
  --location localhost
{% endhighlight %}

Then visit the Brooklyn console on ``localhost:8081``.
Note that the installation may take some time, because the default deployment downloads the software from
the official repos.  You can monitor start-up activity for each entity in the ``Activity`` pane in the management console,
and see more detail by tailing the log file (``tail -f brooklyn.log``).

This example runs successfully on a local machine because ``INITIAL_SIZE`` is configured to just one node
(a limitation of Cassandra is that every node must be on a different machine/VM).
If you want to run with more than one node in the cluster, you'll need to use a location 
that either points to multiple existing machines or to a cloud provider where you can 
provision new machines.

With appropriate setup of credentials (as described [here]({{ site.path.guide }}/use/guide/management/index.html#startup-config)) 
this example can also be deployed to your favourite cloud. Let's pretend it's Amazon US East, as follows: 

{% highlight bash %}
% ${BROOKLYN_HOME}/bin/brooklyn launch --app brooklyn.demo.SimpleCassandraCluster \
  --location aws-ec2:us-east-1
{% endhighlight %}

If you want more nodes in your cluster, you can either modify the deployment descriptor (i.e. change the ``INITIAL_SIZE`` value),
or dynamically add more nodes by calling the ``resize`` effector through the web-console. 
To do the latter, select cluster entity in the tree on the left, then click on the "effectors" tab, and invoke ``resize`` 
with the desired number of nodes.


### Testing your Cluster

An easy way to test your cluster is to use the ``cassandra-stress`` command line tool.
For example, run:

{% highlight bash %}
# Substitute the id below for your VM
NODE_IDS=ec2-54-221-69-95.compute-1.amazonaws.com
/tmp/brooklyn-aled/installs/CassandraNode/1.2.9/apache-cassandra-1.2.9/tools/bin/cassandra-stress \
	--nodes ${NODE_IDS} \
    --replication-factor 1 \
    --progress-interval 1 \
    --num-keys 10000 \
    --operation INSERT
{% endhighlight %}

This command will fire 10000 inserts at the cluster, via the nodes specified in the comma-separated node list. 
If you change ``INSERT`` to ``READ``, it will read each of those 10000 values.


## High Availability Cassandra Cluster

Ready for something more interesting?  Try this:

{% highlight bash %}
% ${BROOKLYN_HOME}/bin/brooklyn launch --app brooklyn.demo.HighAvailabilityCassandraCluster \
  --location aws-ec2:us-east-1
{% endhighlight %}

This launches the class ``HighAvailabilityCassandraCluster``,
which launches a Cassandra cluster configured to replicate across availability zones.

To give some background for that statement, in 
[AWS](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-regions-availability-zones.html)
(and various other clouds), a region is a 
separate geographic area, consisting of multiple isolated locations known as availability zones.
To ensure high availability, the Cassandra cluster and thus the data should be spread across the 
availability zones. Cassandra should be configured to ensure there is at least one replica in
each availability zone. In 
[Cassandra terminology](http://www.datastax.com/docs/1.1/cluster_architecture/replication)
a region is normally mapped to a "datacenter" and an availability zone to a "rack".

To be properly highly available, we need some automated policies to restart failed servers 
and to replace unhealthy nodes. Brooklyn has these policies available out-of-the-box.
To wire them up, the essential code fragment looks like this:

{% highlight java %}
public class HighAvailabilityCassandraCluster extends AbstractApplication {
  public void init() {
    addChild(EntitySpec.create(CassandraCluster.class)
        .configure(CassandraCluster.CLUSTER_NAME, "Brooklyn")
        .configure(CassandraCluster.INITIAL_SIZE, 1)
        .configure(CassandraCluster.ENABLE_AVAILABILITY_ZONES, true)
        .configure(CassandraCluster.NUM_AVAILABILITY_ZONES, 3)
        .configure(CassandraCluster.ENDPOINT_SNITCH_NAME, "GossipingPropertyFileSnitch")
        .configure(CassandraCluster.MEMBER_SPEC, EntitySpec.create(CassandraNode.class)
            .policy(PolicySpec.create(ServiceFailureDetector.class))
            .policy(PolicySpec.create(ServiceRestarter.class)
                .configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, ServiceFailureDetector.ENTITY_FAILED)))
        .policy(PolicySpec.create(ServiceReplacer.class)
            .configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, ServiceRestarter.ENTITY_RESTART_FAILED)));
  }
}
{% endhighlight %}

This code is doing a lot and deserves some more detailed explanation:

* The ``MEMBER_SPEC`` describes the configuration of the Cassandra nodes to be created in the cluster.
  Assuming you're happy to use all the default thrift port etc, then the only configuration to add is
  a couple of policies.
* The ``ServiceFailureDetector`` policy watches the node's sensors, and generates
  an ``ENTITY_FAILED`` event if the node goes down.
* The ``ServiceRestarter`` policy responds to this failure-event
  by restarting the node. Its default configuration is that: if a node does not come back up, or if it 
  fails again within three minutes, then it will emit an ``ENTITY_RESTART_FAILED`` event.
* Finally, the ``SERVICE_REPLACER`` policy on the cluster responds to this event by replacing the
  entire VM. It sets up a new VM in the same location, and then tears down the faulty node.

> *Troubleshooting:*

> *In AWS, some availability zones can be constrained for particular instance sizes (see
  [this bug report](https://github.com/brooklyncentral/brooklyn/issues/973)
  If you get this error, the workaround is to specify explicitly the availability zones to use. 
  This requires an additional line of code such as:*

{% highlight java %}
  .configure(AVAILABILITY_ZONE_NAMES, ImmutableList.of("us-east-1b", "us-east-1c", "us-east-1e"))
{% endhighlight %}

> *However, this prevents the blueprint from being truly portable. We're looking at fixing this issue.*


## Wide Area Cassandra Cluster

For critical enterprise use-cases, you'll want to run your Cassandra cluster across multiple regions, 
or better yet across multiple cloud providers. This gives the highest level of availability for 
the service.

Try running:

{% highlight bash %}
% ${BROOKLYN_HOME}/bin/brooklyn launch --app brooklyn.demo.WideAreaCassandraCluster \
  --location "aws-ec2:us-east-1,aws-ec2:us-west-2"
{% endhighlight %}

This launches the class ``WideAreaCassandraCluster`` across two AWS regions.

Cassandra provides some great support for this with the 
[EC2MultiRegionSnitch](http://www.datastax.com/docs/1.1/cluster_architecture/replication)
The 
[snitch](http://www.datastax.com/docs/1.1/cluster_architecture/replication#snitches)
maps IPs to racks and data centers; it defines how the nodes are grouped together within the overall 
network topology. For wide-area deployments, it must also deal with when to use the private IPs 
(within a region) and the public IPs (between regions).
You'll need a more generic snitch if you're going to span different cloud providers.
Brooklyn has a custom MultiCloudSnitch that we're looking to contribute back to Cassandra.

The important piece of code in ``WideAreaCassandraCluster`` is:

{% highlight java %}
public class WideAreaCassandraCluster extends AbstractApplication {
  public void init() {
    addChild(EntitySpec.create(CassandraFabric.class)
        .configure(CassandraCluster.CLUSTER_NAME, "Brooklyn")
        .configure(CassandraCluster.INITIAL_SIZE, 2) // per location
        .configure(CassandraCluster.ENDPOINT_SNITCH_NAME, "brooklyn.entity.nosql.cassandra.customsnitch.MultiCloudSnitch")
        .configure(CassandraNode.CUSTOM_SNITCH_JAR_URL, "classpath://org/apache/brooklyn/entity/nosql/cassandra/cassandra-multicloud-snitch.jar"));
  }
}
{% endhighlight %}

The code below shows the wide-area example with the high-availability policies from the previous section also configured:

{% highlight java %}
public class WideAreaCassandraCluster extends AbstractApplication {
  public void init() {
    addChild(EntitySpec.create(CassandraFabric.class)
        .configure(CassandraCluster.CLUSTER_NAME, "Brooklyn")
        .configure(CassandraCluster.INITIAL_SIZE, 2) // per location
        .configure(CassandraCluster.ENDPOINT_SNITCH_NAME, "brooklyn.entity.nosql.cassandra.customsnitch.MultiCloudSnitch")
        .configure(CassandraNode.CUSTOM_SNITCH_JAR_URL, "classpath://org/apache/brooklyn/entity/nosql/cassandra/cassandra-multicloud-snitch.jar")
        .configure(CassandraFabric.MEMBER_SPEC, EntitySpec.create(CassandraCluster.class)
            .configure(CassandraCluster.MEMBER_SPEC, EntitySpec.create(CassandraNode.class)
                .policy(PolicySpec.create(ServiceFailureDetector.class))
                .policy(PolicySpec.create(ServiceRestarter.class)
                    .configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, ServiceFailureDetector.ENTITY_FAILED)))
            .policy(PolicySpec.create(ServiceReplacer.class)
                 .configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, ServiceRestarter.ENTITY_RESTART_FAILED))));
  }
}
{% endhighlight %}

To run Cassandra across multiple clouds, try running:

{% highlight bash %}
% ${BROOKLYN_HOME}/bin/brooklyn launch --app brooklyn.demo.WideAreaCassandraCluster \
  --location "aws-ec2:us-east-1,google-compute-engine,rackspace-cloudservers-uk"
{% endhighlight %}


### Testing your Wide-Area Cluster

You can again use the ``cassandra-stress`` command line tool to test the wide-area cluster.

Note that the replication strategy (such as 
[NetworkTopologyStrategy](http://www.datastax.com/docs/1.0/cluster_architecture/replication#networktopologystrategy)
is specified when creating a 
[keyspace](http://www.datastax.com/documentation/cassandra/1.2/webhelp/index.html#cassandra/configuration/configStorage_r.html).
The example below specifies a minimum of 1 replica in each datacenter.

To do updates against a node in a given availability zone:

{% highlight bash %}
NODE_IDS=<your node hostname>
/tmp/brooklyn-aled/installs/CassandraNode/1.2.9/apache-cassandra-1.2.9/tools/bin/cassandra-stress \
    --nodes ${NODE_IDS} \
    --replication-strategy NetworkTopologyStrategy \
    --strategy-properties=us-east-1:1,us-west-2:1 \
    --progress-interval 1 \
    --num-keys 10000 \
    --operation INSERT
{% endhighlight %}

To check that the same data is available from a different region, target the reads
against an appropriate node:

{% highlight bash %}
NODE_IDS=<your node hostname>
/tmp/brooklyn-aled/installs/CassandraNode/1.2.9/apache-cassandra-1.2.9/tools/bin/cassandra-stress \
    --nodes ${NODE_IDS} \
    --replication-strategy NetworkTopologyStrategy \
    --strategy-properties=us-east-1:1,us-west-2:1 \
    --progress-interval 1 \
    --num-keys 10000 \
    --operation READ
{% endhighlight %}

To really test this, you may want to simulate the failure of a region first.
You can kill the VMs or ``kill -9`` the processes. But remember that if Brooklyn policies are configured
they will by default restart the processes automatically! You can disable the Brooklyn policies through 
the brooklyn web-console (select the entity, go the policies tab, select the policy, and click "disable").


## Putting it all together: CumulusRDF

If you want to try this with a real example application using the Cassandra cluster, take a look at
[CumulusRDF](https://code.google.com/p/cumulusrdf). There is an example Brooklyn application at:

{% highlight bash %}
% ${BROOKLYN_HOME}/bin/brooklyn launch --app brooklyn.demo.CumulusRDFApplication \
  --location "aws-ec2:us-east-1"
{% endhighlight %}


## Contact us!

If you encounter any difficulties or have any comments, please [tell us]({{ site.path.guide }}/meta/contact.html) and we'll do our best to help.
