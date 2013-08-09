package brooklyn.extras.whirr.core;

import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;
import static com.google.common.collect.Iterables.getOnlyElement;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.whirr.Cluster;
import org.apache.whirr.ClusterController;
import org.apache.whirr.ClusterControllerFactory;
import org.apache.whirr.ClusterSpec;
import org.jclouds.scriptbuilder.domain.OsFamily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.LocationConfigUtils;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsLocationConfig;
import brooklyn.util.exceptions.Exceptions;

/**
 * Generic entity that can be used to deploy clusters that are
 * managed by Apache Whirr.
 *
 */
public class WhirrClusterImpl extends AbstractEntity implements WhirrCluster {

    public static final Logger log = LoggerFactory.getLogger(WhirrClusterImpl.class);

    protected ClusterController _controller = null;
    protected ClusterSpec clusterSpec = null;
    protected Cluster cluster = null;

    protected Location location = null;

    /**
     * General entity initialisation
     */
    public WhirrClusterImpl() {
    }
    public WhirrClusterImpl(Map flags) {
        super(flags);
    }
    public WhirrClusterImpl(Entity parent) {
        super(parent);
    }
    public WhirrClusterImpl(Map flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    public ClusterSpec getClusterSpec() {
        return clusterSpec;
    }
    
    @Override
    public Cluster getCluster() {
        return cluster;
    }
    
    /**
     * Apache Whirr can only start and manage a cluster in a single location
     *
     * @param locations
     */
    @Override
    public void start(Collection<? extends Location> locations) {
        location = getOnlyElement(locations);
        try {
            startInLocation(location);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    protected void startInLocation(Location location) throws Exception {
        if (location instanceof LocalhostMachineProvisioningLocation) {
            startInLocation((LocalhostMachineProvisioningLocation)location);
        } else if (location instanceof JcloudsLocation) {
            startInLocation((JcloudsLocation)location);
        } else if (location instanceof MachineLocation) {
            startInLocation((MachineLocation)location);
        } else {
            throw new IllegalArgumentException("Unsupported location "+location+", when starting "+this);
        }
    }

    /**
     * Start a cluster as specified in the recipe on localhost
     *
     * @param location corresponding to localhost
     * @throws ConfigurationException 
     */
    protected void startInLocation(LocalhostMachineProvisioningLocation location) throws Exception {

        PropertiesConfiguration config = new PropertiesConfiguration();
        config.load(new StringReader(getConfig(RECIPE)));

        StringBuilder nodes = new StringBuilder();
        nodes.append("nodes:\n");
        for (int i=0; i<10; i++) {
            String mid = (i==0?"":(""+(i+1)));
            nodes.append("    - id: localhost"+mid+"\n");
            nodes.append("      name: local machine "+mid+"\n");
            nodes.append("      hostname: 127.0.0.1\n");
            nodes.append("      os_arch: "+System.getProperty("os.arch")+"\n");
            nodes.append("      os_family: "+OsFamily.UNIX+"\n");
            nodes.append("      os_description: "+System.getProperty("os.name")+"\n");
            nodes.append("      os_version: "+System.getProperty("os.version")+"\n");
            nodes.append("      group: whirr\n");
            nodes.append("      tags:\n");
            nodes.append("          - local\n");
            nodes.append("      username: "+System.getProperty("user.name")+"\n"); //NOTE: needs passwordless sudo!!!
            nodes.append("      credential_url: file://"+System.getProperty("user.home")+"/.ssh/id_rsa\n");
        }

        //provide the BYON nodes to whirr
        config.setProperty("jclouds.byon.nodes", nodes.toString());
        config.setProperty(ClusterSpec.Property.LOCATION_ID.getConfigName(), "byon");

        clusterSpec = new ClusterSpec(config);

        clusterSpec.setServiceName("byon");
        clusterSpec.setProvider("byon");
        clusterSpec.setIdentity("notused");
        clusterSpec.setCredential("notused");

        log.info("Starting cluster with roles " + config.getProperty("whirr.instance-templates")
                + " in location " + location);

        startWithClusterSpec(clusterSpec,config);
    }

    /**
     * Start a cluster as specified in the recipe in a given location
     *
     * @param location jclouds location spec
     * @throws ConfigurationException 
     */
    protected void startInLocation(JcloudsLocation location) throws Exception {
        PropertiesConfiguration config = new PropertiesConfiguration();
        config.load(new StringReader(getConfig(RECIPE)));

        customizeClusterSpecConfiguration(location, config);        

        clusterSpec = new ClusterSpec(config);
        clusterSpec.setProvider(location.getProvider());
        clusterSpec.setIdentity(location.getIdentity());
        clusterSpec.setCredential(location.getCredential());
        // TODO inherit key data?
        clusterSpec.setPrivateKey(LocationConfigUtils.getPrivateKeyData(location.getConfigBag()));
        clusterSpec.setPublicKey(LocationConfigUtils.getPublicKeyData(location.getConfigBag()));
        // TODO: also add security groups when supported in the Whirr trunk

        startWithClusterSpec(clusterSpec, config);
    }

    protected void customizeClusterSpecConfiguration(JcloudsLocation location, PropertiesConfiguration config) {
        if (groovyTruth(location.getRegion()))
            config.setProperty(ClusterSpec.Property.LOCATION_ID.getConfigName(), location.getRegion());
        if (groovyTruth(location.getConfig(JcloudsLocationConfig.IMAGE_ID)))
            config.setProperty(ClusterSpec.Property.IMAGE_ID.getConfigName(), location.getConfig(JcloudsLocationConfig.IMAGE_ID));
    }
    
    @Override
    public synchronized ClusterController getController() {
        if (_controller==null) {
            String serviceName = (clusterSpec != null) ? clusterSpec.getServiceName() : null;
            _controller = new ClusterControllerFactory().create(serviceName);
        }
        return _controller;
    }

    void startWithClusterSpec(ClusterSpec clusterSpec, PropertiesConfiguration config) throws IOException, InterruptedException {
        log.info("Starting cluster "+this+" with roles " + config.getProperty("whirr.instance-templates")
                + " in location " + location);
        if (log.isDebugEnabled()) log.debug("Cluster "+this+" using recipe:\n"+getConfig(RECIPE));
        
        cluster = getController().launchCluster(clusterSpec);

        for (Cluster.Instance instance : cluster.getInstances()) {
            log.info("Creating group for instance " + instance.getId());
            WhirrInstance rolesGroup = 
                addChild(EntitySpec.create(WhirrInstance.class).
                    displayName("Instance:" + instance.getId()).
                    configure("instance", instance));

            for (String role: instance.getRoles()) {
                log.info("Creating entity for '" + role + "' on instance " + instance.getId());
                rolesGroup.addChild(EntitySpec.create(WhirrRole.class).
                        displayName("Role:" + role).
                        configure("role", role));
            }
            addGroup(rolesGroup);
        }

        setAttribute(CLUSTER_NAME, clusterSpec.getClusterName());
        setAttribute(SERVICE_UP, true);
    }

    public void stop() {
        if (clusterSpec != null) {
            try {
                getController().destroyCluster(clusterSpec);
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
        }
        clusterSpec = null;
        cluster = null;
    }

    public void restart() {
        // TODO better would be to restart the software instances, not the machines ?
        stop();
        start(getLocations());
    }
}
