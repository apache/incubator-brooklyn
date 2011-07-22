package brooklyn.example

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.trait.Startable

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.basic.JavaApp
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.location.Location
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.aws.AWSCredentialsFromEnv
import brooklyn.location.basic.aws.AwsLocation
import brooklyn.policy.Policy
import brooklyn.policy.ResizerPolicy

import com.google.common.base.Preconditions
import brooklyn.management.internal.AbstractManagementContext
import brooklyn.launcher.WebAppRunner

/**
 * The application demonstrates the following:
 * <ul><li>dynamic clusters of web application servers</li>
 * <li>multiple geographic locations</li>
 * <li>use of any geo-redirecting DNS provider to route users to their closest cluster of web servers</li>
 * <li>resizing the clusters to meet client demand</li></ul>
 */
public class MultiLocationWebAppDemo extends AbstractApplication implements Startable {

    private static final Map AMAZON_US_WEST_COORDS = [ 'latitude' : 40.0, 'longitude' : -120.0 ] // Northern California (approx)
    private static final Map AMAZON_US_EAST_COORDS = [ 'latitude' : 38.0, 'longitude' : -76.0 ] // Northern Virginia (approx)
    private static final Map MONTEREY_EAST_COORDS = [ 'latitude' : 41.10361, 'longitude' : -73.79583 ] // Hawthorne, NY
    private static final Map AMAZON_EU_WEST_COORDS = [ 'latitude' : 53.34778, 'longitude' : -6.25972 ] // Dublin, Ireland
    private static final Map EDINBURGH_COORDS = [ 'latitude' : 55.94944, 'longitude' : -3.16028 ] // Edinburgh, Scotland
    
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
            URL resource = MultiLocationWebAppDemo.class.getClassLoader().getResource(warName)
            Preconditions.checkState resource != null, "Unable to locate resource $warName"
            springTravelPath = resource.getPath()
        }

        WebClusterEntity(Map props, Entity owner = null) {
            super(props, owner)
            def template = { Map properties ->
                    def server = new TomcatServer(properties)
                    server.setConfig(JavaApp.SUGGESTED_JMX_PORT, 32199)
                    server.setConfig(JavaWebApp.SUGGESTED_HTTP_PORT, 8080)
                    server.setConfig(TomcatServer.SUGGESTED_SHUTDOWN_PORT, 31880)
                    server.setConfig(JavaWebApp.WAR, springTravelPath)
                    return server;
                }
            cluster = new DynamicCluster(newEntity:template, this)
            cluster.setConfig(DynamicCluster.INITIAL_SIZE, 0)
        }

        // FIXME: why am I implementing these?
        void start(Collection<? extends Location> locations) {
            cluster.start(locations)
            cluster.resize(1)
            
            controller = new NginxController(
                owner: this,
                cluster: cluster,
                domain: 'cloudsoft.geopaas.org',
                port: 8000,
                portNumberSensor: JavaWebApp.HTTP_PORT
            )

            policy = new ResizerPolicy(JavaWebApp.AVG_REQUESTS_PER_SECOND)
            policy.setMinSize(1)
            policy.setMaxSize(5)
            policy.setMetricLowerBound(10)
            policy.setMetricUpperBound(100)
            policy.setEntity(cluster)

            controller.start(locations)
        }
        void stop() {
            controller.stop()
            cluster.stop()
        }
        void restart() {
            throw new UnsupportedOperationException()
        }
    }

    final DynamicFabric fabric
    final DynamicGroup nginxEntities
    
    MultiLocationWebAppDemo(Map props=[:]) {
        super(props)
        
        DynamicFabric fabric = new DynamicFabric(
            [id: 'fabricID',
            name: 'fabricName',
            displayName: 'Fabric',
            newEntity: { properties -> return new WebClusterEntity(properties) }],
            this)
        Preconditions.checkState fabric.getDisplayName() == "Fabric"
        nginxEntities = new DynamicGroup([:], this, { Entity e -> (e instanceof NginxController) })
        GeoscalingDnsService geoDns = new GeoscalingDnsService(
            config: [
                (GeoscalingDnsService.GEOSCALING_USERNAME): 'cloudsoft',
                (GeoscalingDnsService.GEOSCALING_PASSWORD): 'cl0uds0ft',
                (GeoscalingDnsService.GEOSCALING_PRIMARY_DOMAIN_NAME): 'geopaas.org',
                (GeoscalingDnsService.GEOSCALING_SMART_SUBDOMAIN_NAME): 'cloudsoft',
            ],
            this)
        nginxEntities.rescanEntities()
        geoDns.setGroup(nginxEntities)
    }
    
    @Override
    public void restart() {
        throw new UnsupportedOperationException()
    }
    
    public static void main(String[] args) {
        AwsLocation awsUsEastLocation = newAwsUsEastLocation()
//        FixedListMachineProvisioningLocation montereyEastLocation = newMontereyEastLocation()
        MachineProvisioningLocation montereyEdinburghLocation = newMontereyEdinburghLocation()
        
        MultiLocationWebAppDemo app = new MultiLocationWebAppDemo(id: 'DemoID', name: 'DemoName', displayName: 'Demo')

        AbstractManagementContext context = app.getManagementContext()
        context.manage(app)
        WebAppRunner web = new WebAppRunner(context)
        web.start()
        
        app.start([montereyEdinburghLocation])
    }

    private static AwsLocation newAwsUsEastLocation() {
        final String REGION_NAME = "us-east-1" // "eu-west-1"
        final String IMAGE_ID = REGION_NAME+"/"+"ami-2342a94a" // "ami-d7bb90a3"
        final String IMAGE_OWNER = "411009282317"
        
        ClassLoader classLoader = getClass().getClassLoader()
        
        File sshPrivateKey = new File(classLoader.getResource("jclouds/id_rsa.private").path)
        File sshPublicKey = new File(classLoader.getResource("jclouds/id_rsa.pub").path)
        
        AWSCredentialsFromEnv creds = new AWSCredentialsFromEnv();
        AwsLocation result = new AwsLocation([
            identity:creds.getAWSAccessKeyId(),
            credential:creds.getAWSSecretKey(),
            providerLocationId:REGION_NAME,
            sshPublicKey:sshPublicKey
            sshPrivateKey:sshPrivateKey
            latitude: AMAZON_US_EAST_COORDS['latitude'],
            longitude: AMAZON_US_EAST_COORDS['longitude']]
        )
        
        result.setTagMapping([
            (TomcatServer.class.getName()):[
                    imageId:IMAGE_ID,
                    securityGroups:["everything"]],
            (NginxController.class.getName()):[
                    imageId:IMAGE_ID,
                    securityGroups:["everything"]]
            ]) //, imageOwner:IMAGE_OWNER]])
        
        return result
    }

    private static FixedListMachineProvisioningLocation newMontereyEastLocation() {
        // The definition of the Monterey East location
        final Collection<SshMachineLocation> MONTEREY_EAST_PUBLIC_ADDRESSES = [
                '216.48.127.224', '216.48.127.225', // east1a/b
                '216.48.127.226', '216.48.127.227', // east2a/b
                '216.48.127.228', '216.48.127.229', // east3a/b
                '216.48.127.230', '216.48.127.231', // east4a/b
                '216.48.127.232', '216.48.127.233', // east5a/b
                '216.48.127.234', '216.48.127.235'  // east6a/b
                ].collect { new SshMachineLocation(address: InetAddress.getByName(it), userName: 'cdm') }

        MachineProvisioningLocation<SshMachineLocation> result =
            new FixedListMachineProvisioningLocation<SshMachineLocation>(
                machines: MONTEREY_EAST_PUBLIC_ADDRESSES,
                latitude: MONTEREY_EAST_COORDS['latitude'],
                longitude: MONTEREY_EAST_COORDS['longitude']
            )
        return result
    }

    private static FixedListMachineProvisioningLocation newMontereyEdinburghLocation() {
        // The definition of the Monterey Edinburgh location
        final Collection<SshMachineLocation> MONTEREY_EDINBURGH_MACHINES = [
            '192.168.144.241',
            '192.168.144.242',
            '192.168.144.243',
            '192.168.144.244',
            '192.168.144.245',
            '192.168.144.246'
        ].collect { new SshMachineLocation(address: InetAddress.getByName(it), userName: 'cloudsoft') }

        MachineProvisioningLocation<SshMachineLocation> result =
            new FixedListMachineProvisioningLocation<SshMachineLocation>(
                machines: MONTEREY_EDINBURGH_MACHINES,
                latitude: EDINBURGH_COORDS['latitude'],
                longitude: EDINBURGH_COORDS['longitude']
            )
        return result
    }
}
