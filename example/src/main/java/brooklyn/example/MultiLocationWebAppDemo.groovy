package brooklyn.example

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication

import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.location.Location
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.policy.Policy
import brooklyn.util.internal.EntityStartUtils
import com.google.common.base.Preconditions
import brooklyn.entity.basic.AbstractEntity

/**
 * The application demonstrates the following:
 * <ul><li>dynamic clusters of web application servers</li>
 * <li>multiple geographic locations</li>
 * <li>use of any anycast DNS provider to router users to the closest cluster of web servers</li>
 * <li>resizing the clusters to meet client demand</li></ul>
 */
public class MultiLocationWebAppDemo extends AbstractApplication implements Startable {

    /**
     * This group contains all the sub-groups and entities that go in to a single location.
     * These are:
     * <ul><li>a @{link DynamicCluster} of @{link JavaWebApp}s</li>
     * <li>a cluster controller</li>
     * <li>a @{link Policy} to resize the DynamicCluster</li></ul>
     */
    private static class WebClusterEntity extends AbstractEntity implements Startable {
        private static final String springTravelPath
        private static final String warName = "swf-booking-mvc.war"

        private DynamicCluster cluster
        private NginxController controller
        private Policy policy

        static {
            URL resource = SimpleTomcatApp.class.getClassLoader().getResource(warName)
            Preconditions.checkState resource != null, "Unable to locate resource $warName"
            springTravelPath = resource.getPath()
        }

        WebClusterEntity(Map props, Entity owner) {
            super(props, owner)

            cluster = new DynamicCluster(newEntity: { properties ->
                def server = new TomcatServer(properties)
                server.setConfig(JavaWebApp.WAR, springTravelPath)
                return server;
            }, this)
            cluster.setConfig(DynamicCluster.INITIAL_SIZE, 0)

            controller = new NginxController(
                owner: this,
                cluster: cluster,
                domain: 'localhost',
                port: 8000,
                portNumberSensor: JavaWebApp.HTTP_PORT
            )

            // FIXME: write this policy
//            policy = new WatermarkResizingPolicy()
//            policy.setConfig(WatermarkResizingPolicy.SENSOR, JavaWebApp.AVG_REQUESTS_PER_SECOND)
//            policy.setConfig(WatermarkResizingPolicy.LOW_WATER_MARK, 10)
//            policy.setConfig(WatermarkResizingPolicy.HIGH_WATER_MARK, 100)
        }

        // FIXME: why am I implementing these?
        void start(Collection<? extends Location> locations) {
            controller.start(locations)
            cluster.start(locations)
            // FIXME: register nginx' IP address with geo DNS
        }
        void stop() {
            controller.stop()
            cluster.stop()
        }
        void restart() {
            throw new UnsupportedOperationException()
        }
    }

    // The definition of the Monterey East location
    private static final Collection<SshMachineLocation> MONTEREY_EAST_PUBLIC_ADDRESSES = [
        '216.48.127.224', '216.48.127.225', // east1a/b
        '216.48.127.226', '216.48.127.227', // east2a/b
        '216.48.127.228', '216.48.127.229', // east3a/b
        '216.48.127.230', '216.48.127.231', // east4a/b
        '216.48.127.232', '216.48.127.233', // east5a/b
        '216.48.127.234', '216.48.127.235'  // east6a/b
    ].collect { new SshMachineLocation(address: InetAddress.getByName(it), userName: 'cdm') }
    private MachineProvisioningLocation<SshMachineLocation> montereyEastLocation =
        new FixedListMachineProvisioningLocation<SshMachineLocation>(machines: MONTEREY_EAST_PUBLIC_ADDRESSES)

    // The definition of the Amazon European region
    // FIXME: change this to actually be an AWS location
    private MachineProvisioningLocation<SshMachineLocation> amazonEuropeLocation =
        new LocalhostMachineProvisioningLocation()

    // The groups in each location
    private WebClusterEntity montereyEastGroup = new WebClusterEntity([:], this)
    private WebClusterEntity amazonEuropeGroup = new WebClusterEntity([:], this)

    public static void main(String[] args) {
        MultiLocationWebAppDemo app = new MultiLocationWebAppDemo()

        // FIXME: start the web management console here instead of manually starting the app
        app.start([])
        System.in.read()
        app.stop()
    }

    @Override
    public void start(Collection<Location> locs) {
        // FIXME: copy-and-paste of superclass method with customization
        // The only way(?) to specifically start each group in the expected location
        getManagementContext()
        EntityStartUtils.startEntity(montereyEastGroup, [montereyEastLocation])
        EntityStartUtils.startEntity(amazonEuropeGroup, [amazonEuropeLocation])
        // deployed = true ReadOnlyPropertyException: Cannot set readonly property
    }

    public void stop() {
        montereyEastGroup.stop()
        amazonEuropeGroup.stop()
    }

    public void restart() {
        throw new UnsupportedOperationException()
    }


}
