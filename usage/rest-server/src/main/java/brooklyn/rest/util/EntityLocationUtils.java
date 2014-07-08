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
package brooklyn.rest.util;

import java.util.LinkedHashMap;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.management.ManagementContext;

public class EntityLocationUtils {

    protected final ManagementContext context;

    public EntityLocationUtils(ManagementContext ctx) {
        this.context = ctx;
    }
    
    /* Returns the number of entites at each location for which the geographic coordinates are known. */
    public Map<Location, Integer> countLeafEntitiesByLocatedLocations() {
        Map<Location, Integer> result = new LinkedHashMap<Location, Integer>();
        for (Entity e: context.getApplications()) {
            countLeafEntitiesByLocatedLocations(e, null, result);
        }
        return result;
    }

    protected void countLeafEntitiesByLocatedLocations(Entity target, Entity locatedParent, Map<Location, Integer> result) {
        if (isLocatedLocation(target))
            locatedParent = target;
        if (!target.getChildren().isEmpty()) {
            // non-leaf - inspect children
            for (Entity child: target.getChildren()) 
                countLeafEntitiesByLocatedLocations(child, locatedParent, result);
        } else {
            // leaf node - increment location count
            if (locatedParent!=null) {
                for (Location l: locatedParent.getLocations()) {
                    Location ll = getMostGeneralLocatedLocation(l);
                    if (ll!=null) {
                        Integer count = result.get(ll);
                        if (count==null) count = 1;
                        else count++;
                        result.put(ll, count);
                    }
                }
            }
        }
    }

    protected Location getMostGeneralLocatedLocation(Location l) {
        if (l==null) return null;
        if (!isLocatedLocation(l)) return null;
        Location ll = getMostGeneralLocatedLocation(l.getParent());
        if (ll!=null) return ll;
        return l;
    }

    protected boolean isLocatedLocation(Entity target) {
        for (Location l: target.getLocations())
            if (isLocatedLocation(l)) return true;
        return false;
    }
    protected boolean isLocatedLocation(Location l) {
        return l.getConfig(LocationConfigKeys.LATITUDE)!=null && l.getConfig(LocationConfigKeys.LONGITUDE)!=null;
    }

}
