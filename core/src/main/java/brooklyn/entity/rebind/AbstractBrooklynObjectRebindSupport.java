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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.AbstractBrooklynObject;
import brooklyn.entity.rebind.dto.MementosGenerators;
import brooklyn.mementos.Memento;

public abstract class AbstractBrooklynObjectRebindSupport<T extends Memento> implements RebindSupport<T> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractBrooklynObjectRebindSupport.class);
    
    private final AbstractBrooklynObject instance;
    
    public AbstractBrooklynObjectRebindSupport(AbstractBrooklynObject instance) {
        this.instance = instance;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public T getMemento() {
        T memento = (T) MementosGenerators.newMemento(instance);
        if (LOG.isTraceEnabled()) LOG.trace("Created memento: {}", memento.toVerboseString());
        return memento;
    }

    @Override
    public void reconstruct(RebindContext rebindContext, T memento) {
        if (LOG.isTraceEnabled()) LOG.trace("Reconstructing: {}", memento.toVerboseString());

        instance.setDisplayName(memento.getDisplayName());
        addConfig(rebindContext, memento);
        addTags(rebindContext, memento);
        addCustoms(rebindContext, memento);
        
        doReconstruct(rebindContext, memento);
        instance.rebind();
    }

    protected abstract void addConfig(RebindContext rebindContext, T memento);

    protected abstract void addCustoms(RebindContext rebindContext, T memento);
    
    protected void addTags(RebindContext rebindContext, T memento) {
        for (Object tag : memento.getTags()) {
            instance.getTagSupport().addTag(tag);
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
