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
package brooklyn.entity.rebind;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Set;

import brooklyn.location.Location;

import com.google.common.collect.Sets;

public class TreeUtils {

    public static Collection<Location> findLocationsInHierarchy(Location root) {
        Set<Location> result = Sets.newLinkedHashSet();
        
        Deque<Location> tovisit = new ArrayDeque<Location>();
        tovisit.addFirst(root);
        
        while (tovisit.size() > 0) {
            Location current = tovisit.pop();
            result.add(current);
            for (Location child : current.getChildren()) {
                if (child != null) {
                    tovisit.push(child);
                }
            }
        }

        Location parentLocation = root.getParent();
        while (parentLocation != null) {
            result.add(parentLocation);
            parentLocation = parentLocation.getParent();
        }

        return result;
    }
}
