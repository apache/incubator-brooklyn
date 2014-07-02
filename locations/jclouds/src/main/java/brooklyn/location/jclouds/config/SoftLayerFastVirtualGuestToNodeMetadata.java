/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.location.jclouds.config;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadata.Status;
import org.jclouds.compute.domain.NodeMetadataBuilder;
import org.jclouds.compute.functions.GroupNamingConvention;
import org.jclouds.softlayer.compute.functions.DatacenterToLocation;
import org.jclouds.softlayer.domain.VirtualGuest;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * @author Andrea Turli
 */
@Singleton
public class SoftLayerFastVirtualGuestToNodeMetadata implements
        Function<VirtualGuest, NodeMetadata> {

    public static final Map<VirtualGuest.State, Status> serverStateToNodeStatus = ImmutableMap
            .<VirtualGuest.State, Status> builder()
            .put(VirtualGuest.State.HALTED, Status.PENDING)
            .put(VirtualGuest.State.PAUSED, Status.SUSPENDED)
            .put(VirtualGuest.State.RUNNING, Status.RUNNING)
            .put(VirtualGuest.State.UNRECOGNIZED, Status.UNRECOGNIZED).build();

    private final GroupNamingConvention nodeNamingConvention;
    private final DatacenterToLocation datacenterConverter;

    @Inject
    SoftLayerFastVirtualGuestToNodeMetadata(
          DatacenterToLocation datacenterConverter,
          GroupNamingConvention.Factory namingConvention
        ) {
        this.datacenterConverter = datacenterConverter;
        this.nodeNamingConvention = namingConvention.createWithoutPrefix();
    }
    
    @Override
    public NodeMetadata apply(VirtualGuest from) {
        // convert the result object to a jclouds NodeMetadata
        NodeMetadataBuilder builder = new NodeMetadataBuilder();
        builder.ids(from.getId() + "");
        builder.name(from.getHostname());
        builder.hostname(from.getHostname());
        builder.group(nodeNamingConvention.groupInUniqueNameOrNull(from
                .getHostname()));
        builder.status(serverStateToNodeStatus.get(from.getPowerState()
                .getKeyName()));
        // These are null for 'bad' guest orders in the HALTED state.
        if (from.getPrimaryIpAddress() != null)
            builder.publicAddresses(ImmutableSet.<String> of(from
                    .getPrimaryIpAddress()));
        if (from.getPrimaryBackendIpAddress() != null)
            builder.privateAddresses(ImmutableSet.<String> of(from
                    .getPrimaryBackendIpAddress()));
        if (from.getDatacenter()!=null) {
            builder.location(
                // this is how it is done in org/jclouds/softlayer/compute/functions/VirtualGuestToNodeMetadata.java
                // but it requires two lookups (Account.ActivePackages then Product_Package.46) which take 30s or more:
//                FluentIterable.from(locations.get()).firstMatch(
//                    LocationPredicates.idEquals(from.getDatacenter().getId() + "")).orNull()
                // also note if the Supplier<Location> is injected (using the following in the constructor)
                // then it works for softlayer but it breaks OTHER clouds eg rackspace with a guice circular reference!
//                @Memoized Supplier<Set<? extends Location>> locations
                
                // this little lightweight snippet does it without any expensive lookups or guice errors :)
                datacenterConverter.apply(from.getDatacenter())
            );
        }
        return builder.build();
    }

}
