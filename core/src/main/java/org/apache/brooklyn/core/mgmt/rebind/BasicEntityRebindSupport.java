/*
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
package org.apache.brooklyn.core.mgmt.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Map;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.rebind.RebindContext;
import org.apache.brooklyn.api.mgmt.rebind.mementos.EntityMemento;
import org.apache.brooklyn.api.objs.BrooklynObjectType;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.enricher.AbstractEnricher;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.feed.AbstractFeed;
import org.apache.brooklyn.core.mgmt.rebind.dto.MementosGenerators;
import org.apache.brooklyn.core.policy.AbstractPolicy;
import org.apache.brooklyn.entity.group.AbstractGroupImpl;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class BasicEntityRebindSupport extends AbstractBrooklynObjectRebindSupport<EntityMemento> {

    private static final Logger LOG = LoggerFactory.getLogger(BasicEntityRebindSupport.class);
    
    private final EntityLocal entity;
    
    public BasicEntityRebindSupport(AbstractEntity entity) {
        super(entity);
        this.entity = checkNotNull(entity, "entity");
    }
    
    // Can rely on super-type once the deprecated getMementoWithProperties is deleted
    @Override
    public EntityMemento getMemento() {
        return getMementoWithProperties(Collections.<String,Object>emptyMap());
    }

    /**
     * @deprecated since 0.7.0; use generic config/attributes rather than "custom fields", so use {@link #getMemento()}
     */
    @Deprecated
    protected EntityMemento getMementoWithProperties(Map<String,?> props) {
        EntityMemento memento = MementosGenerators.newEntityMementoBuilder(entity).customFields(props).build();
        if (LOG.isTraceEnabled()) LOG.trace("Creating memento for entity: {}", memento.toVerboseString());
        return memento;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void addCustoms(RebindContext rebindContext, EntityMemento memento) {
        for (Effector<?> eff: memento.getEffectors()) {
            ((EntityInternal)entity).getMutableEntityType().addEffector(eff);
        }
    
        for (Map.Entry<AttributeSensor<?>, Object> entry : memento.getAttributes().entrySet()) {
            try {
                AttributeSensor<?> key = entry.getKey();
                Object value = entry.getValue();
                @SuppressWarnings("unused") // just to ensure we can load the declared type? or maybe not needed
                Class<?> type = (key.getType() != null) ? key.getType() : rebindContext.loadClass(key.getTypeName());
                ((EntityInternal)entity).setAttributeWithoutPublishing((AttributeSensor<Object>)key, value);
            } catch (Exception e) {
                LOG.warn("Error adding custom sensor "+entry+" when rebinding "+entity+" (rethrowing): "+e);
                throw Exceptions.propagate(e);
            }
        }
        
        setParent(rebindContext, memento);
        addChildren(rebindContext, memento);
        addMembers(rebindContext, memento);
        addLocations(rebindContext, memento);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void addConfig(RebindContext rebindContext, EntityMemento memento) {
        ConfigKey<?> key = null;
        for (Map.Entry<ConfigKey<?>, Object> entry : memento.getConfig().entrySet()) {
            try {
                key = entry.getKey();
                Object value = entry.getValue();
                @SuppressWarnings("unused") // just to ensure we can load the declared type? or maybe not needed
                        // In what cases key.getType() will be null?
                Class<?> type = (key.getType() != null) ? key.getType() : rebindContext.loadClass(key.getTypeName());
                entity.config().set((ConfigKey<Object>)key, value);
            } catch (ClassNotFoundException|IllegalArgumentException e) {
                rebindContext.getExceptionHandler().onAddConfigFailed(memento, key, e);
            }
        }
        
        ((EntityInternal)entity).getConfigMap().addToLocalBag(memento.getConfigUnmatched());
        ((EntityInternal)entity).refreshInheritedConfig();
    }
    
    @Override
    public void addPolicies(RebindContext rebindContext, EntityMemento memento) {
        for (String policyId : memento.getPolicies()) {
            AbstractPolicy policy = (AbstractPolicy) rebindContext.lookup().lookupPolicy(policyId);
            if (policy != null) {
                try {
                    entity.policies().add(policy);
                } catch (Exception e) {
                    rebindContext.getExceptionHandler().onAddPolicyFailed(entity, policy, e);
                }
            } else {
                LOG.warn("Policy not found; discarding policy {} of entity {}({})",
                        new Object[] {policyId, memento.getType(), memento.getId()});
                rebindContext.getExceptionHandler().onDanglingPolicyRef(policyId);
            }
        }
    }
    
    @Override
    public void addEnrichers(RebindContext rebindContext, EntityMemento memento) {
        for (String enricherId : memento.getEnrichers()) {
            AbstractEnricher enricher = (AbstractEnricher) rebindContext.lookup().lookupEnricher(enricherId);
            if (enricher != null) {
                try {
                    entity.enrichers().add(enricher);
                } catch (Exception e) {
                    rebindContext.getExceptionHandler().onAddEnricherFailed(entity, enricher, e);
                }
            } else {
                LOG.warn("Enricher not found; discarding enricher {} of entity {}({})",
                        new Object[] {enricherId, memento.getType(), memento.getId()});
            }
        }
    }
    
    @Override
    public void addFeeds(RebindContext rebindContext, EntityMemento memento) {
        for (String feedId : memento.getFeeds()) {
            AbstractFeed feed = (AbstractFeed) rebindContext.lookup().lookupFeed(feedId);
            if (feed != null) {
                try {
                    ((EntityInternal)entity).feeds().addFeed(feed);
                } catch (Exception e) {
                    rebindContext.getExceptionHandler().onAddFeedFailed(entity, feed, e);
                }
                
                try {
                    if (!rebindContext.isReadOnly(feed)) {
                        feed.start();
                    }
                } catch (Exception e) {
                    rebindContext.getExceptionHandler().onRebindFailed(BrooklynObjectType.ENTITY, entity, e);
                }
            } else {
                LOG.warn("Feed not found; discarding feed {} of entity {}({})",
                        new Object[] {feedId, memento.getType(), memento.getId()});
            }
        }
    }
    
    protected void addMembers(RebindContext rebindContext, EntityMemento memento) {
        if (memento.getMembers().size() > 0) {
            if (entity instanceof AbstractGroupImpl) {
                for (String memberId : memento.getMembers()) {
                    Entity member = rebindContext.lookup().lookupEntity(memberId);
                    if (member != null) {
                        ((AbstractGroupImpl)entity).addMemberInternal(member);
                    } else {
                        LOG.warn("Entity not found; discarding member {} of group {}({})",
                                new Object[] {memberId, memento.getType(), memento.getId()});
                    }
                }
            } else {
                throw new UnsupportedOperationException("Entity with members should be a group: entity="+entity+"; type="+entity.getClass()+"; members="+memento.getMembers());
            }
        }
    }
    
    protected Entity proxy(Entity target) {
        return target instanceof AbstractEntity ? ((AbstractEntity)target).getProxyIfAvailable() : target;
    }
    
    protected void addChildren(RebindContext rebindContext, EntityMemento memento) {
        for (String childId : memento.getChildren()) {
            Entity child = rebindContext.lookup().lookupEntity(childId);
            if (child != null) {
                entity.addChild(proxy(child));
            } else {
                LOG.warn("Entity not found; discarding child {} of entity {}({})",
                        new Object[] {childId, memento.getType(), memento.getId()});
            }
        }
    }

    protected void setParent(RebindContext rebindContext, EntityMemento memento) {
        Entity parent = (memento.getParent() != null) ? rebindContext.lookup().lookupEntity(memento.getParent()) : null;
        if (parent != null) {
            entity.setParent(proxy(parent));
        } else if (memento.getParent() != null){
            LOG.warn("Entity not found; discarding parent {} of entity {}({}), so entity will be orphaned and unmanaged",
                    new Object[] {memento.getParent(), memento.getType(), memento.getId()});
        }
    }
    
    protected void addLocations(RebindContext rebindContext, EntityMemento memento) {
        for (String id : memento.getLocations()) {
            Location loc = rebindContext.lookup().lookupLocation(id);
            if (loc != null) {
                ((EntityInternal)entity).addLocations(ImmutableList.of(loc));
            } else {
                LOG.warn("Location not found; discarding location {} of entity {}({})",
                        new Object[] {id, memento.getType(), memento.getId()});
            }
        }
    }
}
