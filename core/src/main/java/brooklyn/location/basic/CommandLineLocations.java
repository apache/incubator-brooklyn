package brooklyn.location.basic;

import java.util.Collection;
import java.util.List;

import brooklyn.location.Location;
import brooklyn.location.basic.jclouds.CredentialsFromEnv;
import brooklyn.location.basic.jclouds.JcloudsLocation;
import brooklyn.location.basic.jclouds.JcloudsLocationFactory;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class CommandLineLocations {
    
    public static final String LOCALHOST = "localhost";

    /** shortcut to allow these to be specified without specifying 'aws-ec2:' prefix */
    public static final Collection<String> AWS_REGIONS = ImmutableList.of("eu-west-1","us-east-1","us-west-1","ap-southeast-1","ap-northeast-1");

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
        return newJcloudsLocationFactory(provider).newLocation(region);
    }
    
    public static final JcloudsLocationFactory newAwsLocationFactory() {
        return newJcloudsLocationFactory("aws-ec2");
    }
    public static final JcloudsLocation newAwsEuropeLocation() {
        return newAwsLocationFactory().newLocation("eu-west-1");
    }
    public static final JcloudsLocation newAwsAmericaLocation() {
        return newAwsLocationFactory().newLocation("us-west-1");
    }

    public static LocalhostMachineProvisioningLocation newLocalhostLocation() {
        return newLocalhostLocation(0);
    }
    
    public static LocalhostMachineProvisioningLocation newLocalhostLocation(int numberOfInstances) {
        return new LocalhostMachineProvisioningLocation();
    }

    /**
     * @deprecated will be deleted in 0.5.  use new LocationRegistry().getLocationsById(Collection<String>)
     */
    @Deprecated
   public static List<Location> getLocationsById(List<String> ids) {
        if (ids.size() == 1 && ((List)ids).get(0) instanceof List) {
            // TODO Horrible hack, dictated by LocationResolverTest.testLegacyCommandLineAcceptsListOLists
            ids = (List<String>) ((List)ids).get(0);
        }
        Iterable<Location> result = Iterables.transform(ids, new Function<String,Location>() {
                public Location apply(String id) {
                    if (LOCALHOST.equals(id)) {
                    return newLocalhostLocation();
                } else {
                    return newJcloudsLocation(id);
                }
            }});
        return ImmutableList.copyOf(result);
    }
}
