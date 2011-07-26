package brooklyn.demo

import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.aws.AWSCredentialsFromEnv
import brooklyn.location.basic.aws.AwsLocation
import brooklyn.location.basic.aws.AwsLocationFactory
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.Location

public class Locations {
    public static final String LOCALHOST = "localhost"
    public static final Map LOCALHOST_COORDS = [
            id : LOCALHOST,
            displayName : "Localhost",
            streetAddress : "Appleton Tower, Edinburgh",
            'latitude' : 55.94944, 'longitude' : -3.16028,
            iso3166 : "GB-EDH" ]
    public static final String MONTEREY_EAST = "monterey-east"
    public static final Map MONTEREY_EAST_COORDS = [
            id : MONTEREY_EAST,
            displayName : "Hawthorne, NY", 
            streetAddress : "Hawthorne, NY",
            'latitude' : 41.10361, 'longitude' : -73.79583, 
            iso3166 : "US-NY" ]
    public static final String EDINBURGH = "edinburgh"
    public static final Map EDINBURGH_COORDS = [ 
            id : EDINBURGH,
            displayName : "HQ, Edinburgh", 
            streetAddress : "Appleton Tower, Edinburgh",
            'latitude' : 55.94944, 'longitude' : -3.16028, 
            iso3166 : "GB-EDH" ]
    public static final Map EC2_IMAGES = [
            "eu-west-1":"ami-89def4fd",
            "us-east-1":"ami-2342a94a",
            "us-west-1":"ami-25df8e60",
            "ap-southeast-1":"ami-21c2bd73",
            "ap-northeast-1":"ami-f0e842f1",
        ]
    public static final Collection AWS_REGIONS = EC2_IMAGES.keySet()

    private static final AwsLocationFactory AWS_FACTORY = newAwsLocationFactory()
   
    private Locations() { }

    private static final AwsLocationFactory newAwsLocationFactory() {
        File sshPrivateKey = new File("src/main/resources/jclouds/id_rsa.private")
        File sshPublicKey = new File("src/main/resources/jclouds/id_rsa.pub")

        AWSCredentialsFromEnv creds = new AWSCredentialsFromEnv();
        return new AwsLocationFactory([
                identity : creds.getAWSAccessKeyId(),
                credential : creds.getAWSSecretKey(),
                sshPrivateKey : sshPrivateKey,
                sshPublicKey : sshPublicKey
            ])
    }
    
    public static AwsLocation lookupAwsRegion(String regionName) {
        String imageId = regionName+"/"+EC2_IMAGES.get(regionName)
        AwsLocation region = AWS_FACTORY.newLocation(regionName)
        region.setTagMapping([
                (TomcatServer.class.getName()):[
                        imageId:imageId,
                        securityGroups:["brooklyn-all"]],
                (NginxController.class.getName()):[
                        imageId:imageId,
                        securityGroups:["brooklyn-all"]]])
        return region
    }

    public static LocalhostMachineProvisioningLocation newLocalhostLocation(int numberOfInstances) {
        return new LocalhostMachineProvisioningLocation(
            count: numberOfInstances,
            latitude : LOCALHOST_COORDS['latitude'],
            longitude : LOCALHOST_COORDS['longitude'],
            displayName : 'Localhost'
        )
    }

    public static FixedListMachineProvisioningLocation newMontereyEastLocation() {
        // The definition of the Monterey East location
        final Collection<SshMachineLocation> MONTEREY_EAST_PUBLIC_ADDRESSES = [
                '216.48.127.224', '216.48.127.225', // east1a/b
                '216.48.127.226', '216.48.127.227', // east2a/b
                '216.48.127.228', '216.48.127.229', // east3a/b
                '216.48.127.230', '216.48.127.231', // east4a/b
                '216.48.127.232', '216.48.127.233', // east5a/b
                '216.48.127.234', '216.48.127.235'  // east6a/b
            ].collect { new SshMachineLocation(address:InetAddress.getByName(it), userName:'cdm') }

        MachineProvisioningLocation<SshMachineLocation> result =
            new FixedListMachineProvisioningLocation<SshMachineLocation>(
                machines : MONTEREY_EAST_PUBLIC_ADDRESSES,
                latitude : MONTEREY_EAST_COORDS['latitude'],
                longitude : MONTEREY_EAST_COORDS['longitude'],
                displayName : 'Monterey East'
            )
        return result
    }

    public static FixedListMachineProvisioningLocation newMontereyEdinburghLocation() {
        // The definition of the Monterey Edinburgh location
        final Collection<SshMachineLocation> MONTEREY_EDINBURGH_MACHINES = [
                '192.168.144.241',
                '192.168.144.242',
                '192.168.144.243',
                '192.168.144.244',
                '192.168.144.245',
                '192.168.144.246'
            ].collect { new SshMachineLocation(address:InetAddress.getByName(it), userName:'cloudsoft') }

        MachineProvisioningLocation<SshMachineLocation> result =
            new FixedListMachineProvisioningLocation<SshMachineLocation>(
                machines : MONTEREY_EDINBURGH_MACHINES,
                latitude : EDINBURGH_COORDS['latitude'],
                longitude : EDINBURGH_COORDS['longitude'],
                displayName : 'Monterey Edinburgh'
            )
        return result
    }

    public static List<Location> getLocationsById(List<String> ids) {
        List<Location> locations = ids.collect { String location ->
            if (Locations.AWS_REGIONS.contains(location)) {
                Locations.lookupAwsRegion(location)
            } else if (Locations.LOCALHOST == location) {
                Locations.newLocalhostLocation(5)
            } else if (Locations.MONTEREY_EAST == location) {
                Locations.newMontereyEastLocation()
            } else if (Locations.EDINBURGH == location) {
                Locations.newMontereyEdinburghLocation()
            }
        }
        return locations
    }
}