package brooklyn.demo

import brooklyn.entity.basic.AbstractApplication
import java.util.Map

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.WebAppRunner
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.aws.AWSCredentialsFromEnv
import brooklyn.location.basic.aws.AwsLocation
import brooklyn.location.basic.aws.AwsLocationFactory
import brooklyn.management.internal.AbstractManagementContext

import com.google.common.base.Preconditions

/**
 * The application demonstrates the following:
 * 
 * <ul>
 * <li>Dynamic clusters of web application servers
 * <li>Multiple geographic locations
 * <li>Use of any geo-redirecting DNS provider to route users to their closest cluster of web servers
 * <li>Resizing the clusters to meet client demand
 * </ul>
 */
public class SpringTravel extends AbstractApplication {
    private static final Map MONTEREY_EAST_COORDS = [ 'latitude' : 41.10361, 'longitude' : -73.79583 ] // Hawthorne, NY
    private static final Map EDINBURGH_COORDS = [ 'latitude' : 55.94944, 'longitude' : -3.16028 ] // Edinburgh, Scotland
   
    final DynamicFabric fabric
    final DynamicGroup nginxEntities
    
    SpringTravel(Map props=[:]) {
        super(props)
        
        fabric = new DynamicFabric(
            [
                id : 'fabricID',
	            name : 'fabricName',
	            displayName : 'Fabric',
	            newEntity : { Map properties -> return new WebCluster(properties) }
            ],
            this)
        Preconditions.checkState fabric.displayName == "Fabric"
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
        AwsLocation awsEuWestLocation = newAwsEuWestLocation()
//        FixedListMachineProvisioningLocation montereyEastLocation = newMontereyEastLocation()
        MachineProvisioningLocation montereyEdinburghLocation = newMontereyEdinburghLocation()
        
        Application app = new SpringTravel(id: 'DemoID', name: 'DemoName', displayName: 'Demo')

        AbstractManagementContext context = app.getManagementContext()
        context.manage(app)
        WebAppRunner web = new WebAppRunner(context)
        web.start()
        
        app.start([awsUsEastLocation, awsEuWestLocation])
    }

    private static AwsLocation newAwsUsEastLocation() {
        final String REGION_NAME = "us-east-1" // "eu-west-1"
        final String IMAGE_ID = REGION_NAME+"/"+"ami-2342a94a" // "ami-d7bb90a3"
        final String IMAGE_OWNER = "411009282317"
        AwsLocationFactory factory = newAwsLocationFactory()
        AwsLocation result = factory.newLocation(REGION_NAME)
        
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

    private static AwsLocation newAwsEuWestLocation() {
        final String REGION_NAME = "eu-west-1"
        final String IMAGE_ID = REGION_NAME+"/"+"ami-89def4fd"
        final String IMAGE_OWNER = "411009282317"
        AwsLocationFactory factory = newAwsLocationFactory()
        AwsLocation result = factory.newLocation(REGION_NAME)
        
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

    private static AwsLocationFactory newAwsLocationFactory() {
        ClassLoader classLoader = SpringTravel.class.getClassLoader()
        
        File sshPrivateKey = new File(classLoader.getResource("jclouds/id_rsa.private").path)
        File sshPublicKey = new File(classLoader.getResource("jclouds/id_rsa.pub").path)
        
        AWSCredentialsFromEnv creds = new AWSCredentialsFromEnv();
        return new AwsLocationFactory([
                identity:creds.getAWSAccessKeyId(),
                credential:creds.getAWSSecretKey(),
                sshPrivateKey:sshPrivateKey,
                sshPublicKey:sshPublicKey])
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
