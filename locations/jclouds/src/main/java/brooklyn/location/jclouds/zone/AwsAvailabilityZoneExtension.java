package brooklyn.location.jclouds.zone;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Set;

import org.jclouds.aws.ec2.AWSEC2ApiMetadata;
import org.jclouds.aws.ec2.AWSEC2Client;
import org.jclouds.ec2.domain.AvailabilityZoneInfo;

import brooklyn.location.Location;
import brooklyn.location.cloud.AbstractAvailabilityZoneExtension;
import brooklyn.location.cloud.AvailabilityZoneExtension;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.management.ManagementContext;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class AwsAvailabilityZoneExtension extends AbstractAvailabilityZoneExtension implements AvailabilityZoneExtension {

    private final JcloudsLocation loc;
    
    public AwsAvailabilityZoneExtension(ManagementContext managementContext, JcloudsLocation loc) {
        super(managementContext);
        this.loc = checkNotNull(loc, "loc");
        checkArgument(loc.getProvider().equals("aws-ec2"), "provider not aws-ec2 (%s)", loc.getProvider());
    }

    @Override
    protected List<Location> doGetAllSubLocations() {
        List<Location> result = Lists.newArrayList();
        Set<AvailabilityZoneInfo> zones = getAvailabilityZones();
        for (AvailabilityZoneInfo zone : zones) {
            result.add(newSubLocation(loc, zone));
        }
        return result;
    }
    
    @Override
    protected boolean isNameMatch(Location loc, Predicate<? super String> namePredicate) {
        return namePredicate.apply(((JcloudsLocation)loc).getRegion());
    }
    
    protected Set<AvailabilityZoneInfo> getAvailabilityZones() {
        String regionName = loc.getRegion();
        AWSEC2Client ec2Client = loc.getComputeService().getContext().unwrap(AWSEC2ApiMetadata.CONTEXT_TOKEN).getApi();
        return ec2Client.getAvailabilityZoneAndRegionServices().describeAvailabilityZonesInRegion(regionName);
    }
    
    protected JcloudsLocation newSubLocation(Location parent, AvailabilityZoneInfo zone) {
        return loc.newSubLocation(ImmutableMap.of(JcloudsLocation.CLOUD_REGION_ID, zone.getZone()));
    }
}
