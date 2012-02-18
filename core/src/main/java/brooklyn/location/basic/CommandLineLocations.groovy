package brooklyn.location.basic

import java.util.Map

import org.jclouds.ec2.domain.InstanceType

import brooklyn.location.Location
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.jclouds.CredentialsFromEnv
import brooklyn.location.basic.jclouds.JcloudsLocation
import brooklyn.location.basic.jclouds.JcloudsLocationFactory


public class CommandLineLocations {
    
    public static final String LOCALHOST = "localhost"

    /** shortcut to allow these to be specified without specifying 'aws-ec2:' prefix */
    public static final Collection AWS_REGIONS = ["eu-west-1","us-east-1","us-west-1","ap-southeast-1","ap-northeast-1"]

    private CommandLineLocations() { }

    public static final JcloudsLocationFactory newJcloudsLocationFactory(String provider) {
        return new JcloudsLocationFactory(new CredentialsFromEnv(provider).asMap());
    }
    
    /** creates a location referring e.g. to  "aws-ec2:us-east-1" */
    public static final JcloudsLocation newJcloudsLocation(String provider) {
        String region = null;
        int split = provider.indexOf(':');
        if (split>=0) {
            region = provider.substring(split+1);
            provider = provider.substring(0, split);
        } else if (AWS_REGIONS.contains(provider)) {
            // treat amazon as a default
            region = provider;
            provider = "aws-ec2";
        }
        return newJcloudsLocation(provider, region);
    }
    public static final JcloudsLocation newJcloudsLocation(String provider, String region) {
        return newJcloudsLocationFactory(provider).newLocation(region)
    }
    
    public static final JcloudsLocationFactory newAwsLocationFactory() {
        return newJcloudsLocationFactory("aws-ec2");
    }
    public static final JcloudsLocation newAwsEuropeLocation() {
        return newAwsLocationFactory("aws-ec2").newLocation("eu-west-1")
    }
    public static final JcloudsLocation newAwsAmericaLocation() {
        return newAwsLocationFactory("aws-ec2").newLocation("us-west-1")
    }

    public static LocalhostMachineProvisioningLocation newLocalhostLocation(int numberOfInstances=0) {
        return new LocalhostMachineProvisioningLocation()
    }

    public static List<Location> getLocationsById(List<String> ids) {
        List<Location> locations = ids.collect { String location ->
            if (LOCALHOST == location) {
                newLocalhostLocation()
            } else {
                newJcloudsLocation(location);
            }
        }
        return locations
    }
    
}
