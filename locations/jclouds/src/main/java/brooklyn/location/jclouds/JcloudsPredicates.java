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
package brooklyn.location.jclouds;

import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.domain.Location;

import com.google.common.base.Predicate;

public class JcloudsPredicates {

    public static class NodeInLocation implements Predicate<ComputeMetadata> {
        private String regionId;
        private boolean matchNullLocations;
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
