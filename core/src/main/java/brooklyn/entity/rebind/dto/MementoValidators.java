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
package brooklyn.entity.rebind.dto;

import java.util.Collection;
import java.util.Map;

import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.TreeNode;

public class MementoValidators {

    private MementoValidators() {}
    
    public static void validateMemento(BrooklynMemento memento) {
        // TODO Could also validate integrity of entityReferenceAttributes and entityReferenceConfig
        
        Collection<String> locationIds = memento.getLocationIds();
        
        // Ensure every entity's parent/children/locations exists
        validateParentChildRelations(memento.getLocationMementos());
        validateParentChildRelations(memento.getEntityMementos());
        
        for (String id : memento.getEntityIds()) {
            EntityMemento entityMemento = memento.getEntityMemento(id);
            for (String location : entityMemento.getLocations()) {
                if (!locationIds.contains(location)) {
                    throw new IllegalStateException("Location "+location+" missing, for entity "+entityMemento);
                }
            }
        }
    }
    
    private static void validateParentChildRelations(Map<String, ? extends TreeNode> nodes) {
        for (Map.Entry<String, ? extends TreeNode> entry : nodes.entrySet()) {
            TreeNode node = entry.getValue();
            if (node.getParent() != null && !nodes.containsKey(node.getParent())) {
                throw new IllegalStateException("Parent "+node.getParent()+" missing, for "+node);
            }
            for (String childId : node.getChildren()) {
                if (childId == null) {
                    throw new IllegalStateException("Null child, for "+node);
                }
                if (!nodes.containsKey(childId)) {
                    throw new IllegalStateException("Child "+childId+" missing, for "+node);
                }
            }
        }
    }
}
