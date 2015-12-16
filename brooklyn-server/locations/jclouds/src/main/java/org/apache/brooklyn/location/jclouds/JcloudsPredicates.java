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
package org.apache.brooklyn.location.jclouds;

import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.domain.Location;

import com.google.common.base.Predicate;

public class JcloudsPredicates {

    public static Predicate<ComputeMetadata> nodeInLocation(String regionId, boolean matchNullLocations) {
        return new NodeInLocation(regionId, matchNullLocations);
    }

    /**
     * @deprecated since 0.9.0; direct access strongly discouraged; will be made protected in future release;
     *             use {@link JcloudsPredicates#nodeInLocation(String, boolean)}
     */
    public static class NodeInLocation implements Predicate<ComputeMetadata> {
        private final String regionId;
        private final boolean matchNullLocations;
        public NodeInLocation(String regionId, boolean matchNullLocations) {
            this.regionId = regionId;
            this.matchNullLocations = matchNullLocations;
        }
        @Override
        public boolean apply(ComputeMetadata input) {
            boolean exclude;
            Location nodeLocation = input.getLocation();
            if (nodeLocation==null) return matchNullLocations;
            
            exclude = true;
            while (nodeLocation!=null && exclude) {
                if (nodeLocation.getId().equals(regionId)) {
                    // matching location info found
                    exclude = false;
                }
                nodeLocation = nodeLocation.getParent();
            }
            return !exclude;
        }
    }
}
