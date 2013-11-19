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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.jclouds.collect.Memoized;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadata.Status;
import org.jclouds.compute.domain.NodeMetadataBuilder;
import org.jclouds.compute.functions.GroupNamingConvention;
import org.jclouds.domain.Location;
import org.jclouds.softlayer.domain.VirtualGuest;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
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

    @Inject
    SoftLayerFastVirtualGuestToNodeMetadata(
            @Memoized Supplier<Set<? extends Location>> locations,
            GroupNamingConvention.Factory namingConvention) {
        this.nodeNamingConvention = checkNotNull(namingConvention,
                "namingConvention").createWithoutPrefix();
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
        return builder.build();
    }

}
