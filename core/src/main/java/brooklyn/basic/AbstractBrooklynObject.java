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
package brooklyn.basic;

import java.util.Set;

import brooklyn.management.ManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.text.Identifiers;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public abstract class AbstractBrooklynObject implements BrooklynObjectInternal {

    @SetFromFlag(value="id")
    private String id = Identifiers.makeRandomId(8);
    
    private final Set<Object> tags = Sets.newLinkedHashSet();

    private volatile ManagementContext managementContext;

    public abstract void setDisplayName(String newName);

    /**
     * Called by framework (in new-style instances where spec was used) after configuring etc,
     * but before a reference to this instance is shared.
     * 
     * To preserve backwards compatibility for if the instance is constructed directly, one
     * can call the code below, but that means it will be called after references to this 
     * policy have been shared with other entities.
     * <pre>
     * {@code
     * if (isLegacyConstruction()) {
     *     init();
     * }
     * }
     * </pre>
     */
    public void init() {
        // no-op
    }
    
    /**
     * Called by framework on rebind (in new-style instances),
     * after configuring but before the instance is managed (or is attached to an entity, depending on its type), 
     * and before a reference to this policy is shared.
     * Note that {@link #init()} will not be called on rebind.
     */
    public void rebind() {
        // no-op
    }
    
    public void setManagementContext(ManagementContextInternal managementContext) {
        this.managementContext = managementContext;
    }
    
    public ManagementContext getManagementContext() {
        return managementContext;
    }

    protected void requestPersist() {
        // TODO Could add PolicyChangeListener, similar to EntityChangeListener; should we do that?
        if (getManagementContext() != null) {
            getManagementContext().getRebindManager().getChangeListener().onChanged(this);
        }
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    public TagSupport getTagSupport() {
        return new TagSupport() {
            @Override
            public Set<Object> getTags() {
                synchronized (tags) {
                    return ImmutableSet.copyOf(tags);
                }
            }
    
            @Override
            public boolean containsTag(Object tag) {
                synchronized (tags) {
                    return tags.contains(tag);
                }
            }
            
            @Override
            public boolean addTag(Object tag) {
                boolean result;
                synchronized (tags) {
                    result = tags.add(tag);
                }
                requestPersist();
                return result;
            }    
    
            @Override
            public boolean removeTag(Object tag) {
                boolean result;
                synchronized (tags) {
                    result = tags.remove(tag);
                }
                requestPersist();
                return result;
            }    
        };
    }
}
