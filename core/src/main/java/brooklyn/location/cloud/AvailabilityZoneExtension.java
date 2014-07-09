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
package brooklyn.location.cloud;

import java.util.List;

import brooklyn.entity.group.DynamicCluster;
import brooklyn.location.Location;
import brooklyn.location.basic.MultiLocation;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;

/**
 * For a location that has sub-zones within it (e.g. an AWS region has availability zones that can be
 * mapped as sub-locations), this extension interface allows those to be accessed and used.
 * For some well-known clouds, the availability zones are automatically set, although for others they may
 * have to be configured explicitly. The "multi:(locs,...)" location descriptor (cf {@link MultiLocation}) allows
 * this to be down at runtime.
 * <p>
 * Note that only entities which are explicitly aware of the {@link AvailabilityZoneExtension}
 * will use availability zone information. For example {@link DynamicCluster} 
 * <p>
 * Implementers are strongly encouraged to extend {@link AbstractAvailabilityZoneExtension}
 * which has useful behaviour, rather than attempt to implement this interface directly.
 * 
 * @since 0.6.0
 */
@Beta
public interface AvailabilityZoneExtension {

    List<Location> getAllSubLocations();

    List<Location> getSubLocations(int max);

    List<Location> getSubLocationsByName(Predicate<? super String> namePredicate, int max);

}
