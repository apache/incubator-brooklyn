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
package brooklyn.entity.rebind.transformer.impl;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.brooklyn.mementos.BrooklynMemento;
import org.apache.brooklyn.mementos.EntityMemento;
import org.apache.brooklyn.mementos.LocationMemento;

import brooklyn.entity.rebind.dto.BrooklynMementoImpl;
import brooklyn.entity.rebind.transformer.BrooklynMementoTransformer;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;

import com.google.common.annotations.Beta;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@Beta
public class DeleteOrphanedLocationsTransformer implements BrooklynMementoTransformer {

    // TODO Work in progress; untested code!
    
    public BrooklynMemento transform(BrooklynMemento input) throws Exception {
        Set<String> referencedLocationIds = findReferencedLocationIds(input);
        Set<String> unreferencedLocationIds = Sets.newLinkedHashSet();
        List<String> toCheck = Lists.newLinkedList(input.getLocationIds());
        
        while (!toCheck.isEmpty()) {
            String locationId = toCheck.remove(0);
            List<String> locationsInHierarchy = MutableList.<String>builder()
                    .add(locationId)
                    .addAll(findLocationAncestors(input, locationId))
                    .addAll(findLocationDescendents(input, locationId))
                    .build();
            
            if (containsAny(referencedLocationIds, locationsInHierarchy)) {
                // keep them all
            } else {
                unreferencedLocationIds.addAll(locationsInHierarchy);
            }
            toCheck.removeAll(locationsInHierarchy);
        }
        
        // TODO What about brooklyn version?
        return BrooklynMementoImpl.builder()
                .applicationIds(input.getApplicationIds())
                .topLevelLocationIds(MutableSet.<String>builder()
                        .addAll(input.getTopLevelLocationIds())
                        .removeAll(unreferencedLocationIds)
                        .build())
                .entities(input.getEntityMementos())
                .locations(MutableMap.<String, LocationMemento>builder()
                        .putAll(input.getLocationMementos())
                        .removeAll(unreferencedLocationIds)
                        .build())
                .policies(input.getPolicyMementos())
                .enrichers(input.getEnricherMementos())
                .catalogItems(input.getCatalogItemMementos())
                .build();
    }
    
    public boolean containsAny(Collection<?> container, Iterable<?> contenders) {
        for (Object contender : contenders) {
            if (container.contains(contender)) return true;
        }
        return false;
    }
    
    public Set<String> findReferencedLocationIds(BrooklynMemento input) {
        Set<String> result = Sets.newLinkedHashSet();
        
        for (EntityMemento entity : input.getEntityMementos().values()) {
            result.addAll(entity.getLocations());
        }
        return result;
    }
    
    public Set<String> findLocationAncestors(BrooklynMemento input, String locationId) {
        Set<String> result = Sets.newLinkedHashSet();
        
        String parentId = null;
        do {
            LocationMemento memento = input.getLocationMemento(locationId);
            parentId = memento.getParent();
            if (parentId != null) result.add(parentId);
        } while (parentId != null);

        return result;
    }
    
    public Set<String> findLocationDescendents(BrooklynMemento input, String locationId) {
        Set<String> result = Sets.newLinkedHashSet();
        List<String> tovisit = Lists.newLinkedList();
        
        tovisit.add(locationId);
        while (!tovisit.isEmpty()) {
            LocationMemento memento = input.getLocationMemento(tovisit.remove(0));
            List<String> children = memento.getChildren();
            result.addAll(children);
            tovisit.addAll(children);
        };

        return result;
    }
}
