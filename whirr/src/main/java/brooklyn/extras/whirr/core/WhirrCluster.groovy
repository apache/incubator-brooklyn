package brooklyn.extras.whirr.core

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity
import org.apache.whirr.ClusterController
import org.apache.whirr.ClusterControllerFactory
import brooklyn.entity.trait.Startable
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import brooklyn.event.basic.BasicConfigKey
import brooklyn.util.flags.SetFromFlag
import brooklyn.location.Location
import brooklyn.location.basic.jclouds.JcloudsLocation
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.whirr.ClusterSpec
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import org.apache.whirr.Cluster
import brooklyn.entity.basic.AbstractGroup
import com.google.common.collect.Iterables
import static com.google.common.collect.Iterables.getOnlyElement
import brooklyn.entity.basic.DynamicGroup
import brooklyn.extras.whirr.core.WhirrRole

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

    private final ClusterController controller = null
    private ClusterSpec deployed = null

    /**
     * General entity initialisation
     */
    public WhirrCluster(Map flags = [:], Entity owner = null) {
        super(flags, owner)
        controller = new ClusterControllerFactory().create(null);
    }

    /**
     * Apache Whirr can only start and manage a cluster in a single location
     *
     * @param locations
     */
    void start(Collection<? extends Location> locations) {
        startInLocation(getOnlyElement(locations))
    }

    /**
     * Start a cluster as specified in the recipe in a given location
     *
     * @param location jclouds location spec
     */
    void startInLocation(JcloudsLocation location) {
        PropertiesConfiguration config = new PropertiesConfiguration()
        config.load(new StringReader(getConfig(RECIPE)))

        ClusterSpec clusterSpec = new ClusterSpec(config)

        clusterSpec.setProvider(location.getConf().provider)
        clusterSpec.setIdentity(location.getConf().identity)
        clusterSpec.setCredential(location.getConf().credential)
        clusterSpec.setLocationId(location.getConf().providerLocationId)

        log.info("Starting cluster with roles " + config.getProperty("whirr.instance-templates")
                + " in location " + location.getConf().providerLocationId)

        Cluster cluster = controller.launchCluster(clusterSpec)
        deployed = clusterSpec

        for (Cluster.Instance instance: cluster.getInstances()) {
            log.info("Creating group for instance " + instance.id)
            def rolesGroup = new AbstractGroup(displayName: "Instance:" + instance.id, this) {}
            for (String role: instance.roles) {
                log.info("Creating entity for '" + role + "' on instance " + instance.id)
                rolesGroup.addOwnedChild(new WhirrRole(displayName: "Role:" + role, role: role, rolesGroup))
            }
            addGroup(rolesGroup)
        }
    }

    void stop() {
        if (deployed != null) {
            controller.destroyCluster(deployed)
        }
        deployed = null
    }

    void restart() {
        // TODO: rebuild cluster by destroy all?
    }
}
