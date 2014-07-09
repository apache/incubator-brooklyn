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

import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.rebind.dto.MementosGenerators;
import brooklyn.mementos.EnricherMemento;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.FlagUtils;

public class BasicEnricherRebindSupport implements RebindSupport<EnricherMemento> {

    private static final Logger LOG = LoggerFactory.getLogger(BasicEnricherRebindSupport.class);
    
    private final AbstractEnricher enricher;
    
    public BasicEnricherRebindSupport(AbstractEnricher enricher) {
        this.enricher = enricher;
    }
    
    @Override
    public EnricherMemento getMemento() {
        EnricherMemento memento = MementosGenerators.newEnricherMementoBuilder(enricher).build();
        if (LOG.isTraceEnabled()) LOG.trace("Creating memento for enricher: {}", memento.toVerboseString());
        return memento;
    }

    @Override
    public void reconstruct(RebindContext rebindContext, EnricherMemento memento) {
        if (LOG.isTraceEnabled()) LOG.trace("Reconstructing enricher: {}", memento.toVerboseString());

        enricher.setName(memento.getDisplayName());
        
        // TODO entity does config-lookup differently; the memento contains the config keys.
        // BasicEntityMemento.postDeserialize uses the injectTypeClass to call EntityTypes.getDefinedConfigKeys(clazz)
        // 
        // Note that the flags may have been set in the constructor; but some enrichers have no-arg constructors
        ConfigBag configBag = ConfigBag.newInstance(memento.getConfig());
        FlagUtils.setFieldsFromFlags(enricher, configBag);
        FlagUtils.setAllConfigKeys(enricher, configBag, false);
        
        doReconstruct(rebindContext, memento);
        ((AbstractEnricher)enricher).rebind();
    }

    @Override
    public void addPolicies(RebindContext rebindContext, EnricherMemento Memento) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addEnrichers(RebindContext rebindContext, EnricherMemento Memento) {
        throw new UnsupportedOperationException();
    }

    /**
     * For overriding, to give custom reconsruct behaviour.
     */
    protected void doReconstruct(RebindContext rebindContext, EnricherMemento memento) {
        // default is no-op
    }
}
