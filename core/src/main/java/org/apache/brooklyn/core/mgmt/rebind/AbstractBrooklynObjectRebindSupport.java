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
package org.apache.brooklyn.core.mgmt.rebind;

import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.mgmt.rebind.RebindContext;
import org.apache.brooklyn.api.mgmt.rebind.RebindSupport;
import org.apache.brooklyn.api.mgmt.rebind.mementos.Memento;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.EntityAdjunct;
import org.apache.brooklyn.api.relations.RelationshipType;
import org.apache.brooklyn.core.entity.EntityRelations;
import org.apache.brooklyn.core.mgmt.rebind.dto.MementosGenerators;
import org.apache.brooklyn.core.objs.AbstractBrooklynObject;
import org.apache.brooklyn.core.objs.AbstractEntityAdjunct.AdjunctTagSupport;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractBrooklynObjectRebindSupport<T extends Memento> implements RebindSupport<T> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractBrooklynObjectRebindSupport.class);
    
    private final AbstractBrooklynObject instance;
    
    public AbstractBrooklynObjectRebindSupport(AbstractBrooklynObject instance) {
        this.instance = instance;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public T getMemento() {
        T memento = (T) MementosGenerators.newBasicMemento(instance);
        if (LOG.isTraceEnabled()) LOG.trace("Created memento: {}", memento.toVerboseString());
        return memento;
    }

    @Override
    public void reconstruct(RebindContext rebindContext, T memento) {
        if (LOG.isTraceEnabled()) LOG.trace("Reconstructing: {}", memento.toVerboseString());

        instance.setDisplayName(memento.getDisplayName());
        //catalogItemId already set when creating the object
        addConfig(rebindContext, memento);
        addTags(rebindContext, memento);
        addRelations(rebindContext, memento);
        addCustoms(rebindContext, memento);
        
        doReconstruct(rebindContext, memento);
        if (!rebindContext.isReadOnly(instance))
            instance.rebind();
    }

    protected abstract void addConfig(RebindContext rebindContext, T memento);

    protected abstract void addCustoms(RebindContext rebindContext, T memento);
    
    @SuppressWarnings("rawtypes")
    protected void addTags(RebindContext rebindContext, T memento) {
        if (instance instanceof EntityAdjunct && Strings.isNonBlank(memento.getUniqueTag())) {
            ((AdjunctTagSupport)(instance.tags())).setUniqueTag(memento.getUniqueTag());
        }
        for (Object tag : memento.getTags()) {
            instance.tags().addTag(tag);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void addRelations(RebindContext rebindContext, T memento) {
        for (Map.Entry<String,Set<String>> rEntry : memento.getRelations().entrySet()) {
            RelationshipType<? extends BrooklynObject, ? extends BrooklynObject> r = EntityRelations.lookup(instance.getManagementContext(), rEntry.getKey());
            if (r==null) throw new IllegalStateException("Unsupported relationship -- "+rEntry.getKey() + " -- in "+memento);
            for (String itemId: rEntry.getValue()) {
                BrooklynObject item = rebindContext.lookup().lookup(null, itemId);
                if (item != null) {
                    instance.relations().add((RelationshipType)r, item);
                } else {
                    LOG.warn("Item not found; discarding item {} relation {} of entity {}({})",
                            new Object[] {itemId, r, memento.getType(), memento.getId()});
                    rebindContext.getExceptionHandler().onDanglingUntypedItemRef(itemId);
                }
            }
        }
    }

    @Override
    public void addPolicies(RebindContext rebindContext, T Memento) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addEnrichers(RebindContext rebindContext, T Memento) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addFeeds(RebindContext rebindContext, T Memento) {
        throw new UnsupportedOperationException();
    }

    /**
     * For overriding, to give custom reconstruct behaviour.
     * 
     * @deprecated since 0.7.0; should never need to override the RebindSupport types
     */
    @Deprecated
    protected void doReconstruct(RebindContext rebindContext, T memento) {
        // default is no-op
    }
}
