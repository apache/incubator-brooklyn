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
package brooklyn.mementos;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.event.AttributeSensor;

/**
 * Represents the state of an entity, so that it can be reconstructed (e.g. after restarting brooklyn).
 * 
 * @see RebindSupport
 * 
 * @author aled
 */
public interface EntityMemento extends Memento, TreeNode {

    /** all dynamic effectors (ie differences between those registered on the entity type */ 
    public List<Effector<?>> getEffectors();

    public Map<ConfigKey<?>, Object> getConfig();

    /** true if the entity is top-level (parentless) and an application
     * (there may be parentless "orphaned" entities, for which this is false,
     * and "application" instances nested inside other apps, for which this is again)
     */
    public boolean isTopLevelApp();
    
    public Map<String, Object> getConfigUnmatched();
    
    public Map<AttributeSensor<?>, Object> getAttributes();

    /**
     * The ids of the member entities, if this is a Group; otherwise empty.
     * 
     * @see Group.getMembers()
     */
    public List<String> getMembers();
    
    /**
     * The ids of the locations for this entity.
     */
    public List<String> getLocations();

    /**
     * The ids of the policies of this entity.
     */
    public Collection<String> getPolicies();

    /**
     * The ids of the enrichers of this entity.
     */
    public Collection<String> getEnrichers();

    public Collection<Object> getTags();

}
