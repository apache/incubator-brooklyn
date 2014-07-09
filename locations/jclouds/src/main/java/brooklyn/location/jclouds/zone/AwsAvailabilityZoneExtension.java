/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.location.jclouds.zone;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Set;

import org.jclouds.aws.ec2.AWSEC2Api;
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
        AWSEC2Api ec2Client = loc.getComputeService().getContext().unwrapApi(AWSEC2Api.class);
        return ec2Client.getAvailabilityZoneAndRegionApi().get().describeAvailabilityZonesInRegion(regionName);
    }
    
    protected JcloudsLocation newSubLocation(Location parent, AvailabilityZoneInfo zone) {
        return loc.newSubLocation(ImmutableMap.of(JcloudsLocation.CLOUD_REGION_ID, zone.getZone()));
    }
}
