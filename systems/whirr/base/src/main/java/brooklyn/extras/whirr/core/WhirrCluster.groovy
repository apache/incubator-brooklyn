package brooklyn.extras.whirr.core

import static com.google.common.collect.Iterables.getOnlyElement

import org.apache.commons.configuration.ConfigurationUtils;
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.whirr.Cluster
import org.apache.whirr.ClusterController
import org.apache.whirr.ClusterControllerFactory
import org.apache.whirr.ClusterSpec
import org.apache.whirr.ClusterSpec.Property;
import org.jclouds.compute.domain.TemplateBuilderSpec;
import org.jclouds.scriptbuilder.domain.OsFamily
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.trait.Startable
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.basic.jclouds.JcloudsLocation
import brooklyn.util.flags.SetFromFlag

/**
 * Generic entity that can be used to deploy clusters that are
 * managed by Apache Whirr.
 *
 */
public class WhirrCluster extends AbstractEntity implements Startable {

    public static final Logger log = LoggerFactory.getLogger(WhirrCluster.class);

    @SetFromFlag("recipe")
    public static final BasicConfigKey<String> RECIPE =
            [String, "whirr.recipe", "Apache Whirr cluster recipe"]

    public static final BasicAttributeSensor<String> CLUSTER_NAME =
            [String, "whirr.cluster.name", "Name of the Whirr cluster"]

    protected ClusterController _controller = null
    protected ClusterSpec clusterSpec = null
    protected Cluster cluster = null

    protected Location location = null

    /**
     * General entity initialisation
     */
    public WhirrCluster(Map flags = [:], Entity owner = null) {
        super(flags, owner)
    }

    /**
     * Apache Whirr can only start and manage a cluster in a single location
     *
     * @param locations
     */
    void start(Collection<? extends Location> locations) {
        location = getOnlyElement(locations)
        startInLocation(location)
    }

    /**
     * Start a cluster as specified in the recipe on localhost
     *
     * @param location corresponding to localhost
     */
    void startInLocation(LocalhostMachineProvisioningLocation location) {

        PropertiesConfiguration config = new PropertiesConfiguration()
        config.load(new StringReader(getConfig(RECIPE)))

        StringBuilder nodes = []
        nodes.with {
            append "nodes:\n"
            for (int i=0; i<10; i++) {
                String mid = (i==0?"":(""+(i+1)));
                append "    - id: localhost"+mid+"\n"
                append "      name: local machine "+mid+"\n"
                append "      hostname: 127.0.0.1\n"
                append "      os_arch: "+System.getProperty("os.arch")+"\n"
                append "      os_family: "+OsFamily.UNIX+"\n"
                append "      os_description: "+System.getProperty("os.name")+"\n"
                append "      os_version: "+System.getProperty("os.version")+"\n"
                append "      group: whirr\n"
                append "      tags:\n"
                append "          - local\n"
                append "      username: "+System.getProperty("user.name")+"\n" //NOTE: needs passwordless sudo!!!
                append "      credential_url: file://"+System.getProperty("user.home")+"/.ssh/id_rsa\n"
            }
        }

        //provide the BYON nodes to whirr
        config.setProperty("jclouds.byon.nodes", nodes.toString())
        config.setProperty(ClusterSpec.Property.LOCATION_ID.getConfigName(), "byon");

        clusterSpec = new ClusterSpec(config)

        clusterSpec.setServiceName("byon")
        clusterSpec.setProvider("byon")
        clusterSpec.setIdentity("notused")
        clusterSpec.setCredential("notused")

        log.info("Starting cluster with roles " + config.getProperty("whirr.instance-templates")
                + " in location " + location)

        startWithClusterSpec(clusterSpec,config);
    }

    /**
     * Start a cluster as specified in the recipe in a given location
     *
     * @param location jclouds location spec
     */
    void startInLocation(JcloudsLocation location) {
        PropertiesConfiguration config = new PropertiesConfiguration()
        config.load(new StringReader(getConfig(RECIPE)))

        customizeClusterSpecConfiguration(location, config);        

        clusterSpec = new ClusterSpec(config)
        clusterSpec.setProvider(location.getConf().provider)
        clusterSpec.setIdentity(location.getConf().identity)
        clusterSpec.setCredential(location.getConf().credential)
        clusterSpec.setPrivateKey((File)location.getPrivateKeyFile());
        clusterSpec.setPublicKey((File)location.getPublicKeyFile());
        // TODO: also add security groups when supported in the Whirr trunk

        startWithClusterSpec(clusterSpec, config);
    }

    protected void customizeClusterSpecConfiguration(JcloudsLocation location, PropertiesConfiguration config) {
        if (location.getConf().providerLocationId)
            config.setProperty(ClusterSpec.Property.LOCATION_ID.getConfigName(), location.getConf().providerLocationId);
    }
    
    synchronized ClusterController getController() {
        if (_controller==null) {
            _controller = new ClusterControllerFactory().create(clusterSpec?.getServiceName());
        }
        return _controller;
    }

    void startWithClusterSpec(ClusterSpec clusterSpec, PropertiesConfiguration config) {
        log.info("Starting cluster "+this+" with roles " + config.getProperty("whirr.instance-templates")
                + " in location " + location)
        if (log.isDebugEnabled()) log.debug("Cluster "+this+" using recipe:\n"+getConfig(RECIPE));
        
        cluster = controller.launchCluster(clusterSpec)

        for (Cluster.Instance instance : cluster.getInstances()) {
            log.info("Creating group for instance " + instance.id)
            def rolesGroup = new WhirrInstance(displayName: "Instance:" + instance.id, instance: instance, this);
            for (String role: instance.roles) {
                log.info("Creating entity for '" + role + "' on instance " + instance.id)
                rolesGroup.addOwnedChild(new WhirrRole(displayName: "Role:" + role, role: role, rolesGroup))
            }
            addGroup(rolesGroup)
        }

        setAttribute(CLUSTER_NAME, clusterSpec.getClusterName());
        setAttribute(SERVICE_UP, true);
    }

    void stop() {
        if (clusterSpec != null) {
            controller.destroyCluster(clusterSpec)
        }
        clusterSpec = null
        cluster = null
    }

    void restart() {
        // TODO better would be to restart the software instances, not the machines ?
        stop();
        start([location]);
    }
}
