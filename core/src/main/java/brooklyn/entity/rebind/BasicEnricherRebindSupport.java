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

import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.mementos.EnricherMemento;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.FlagUtils;

public class BasicEnricherRebindSupport extends AbstractBrooklynObjectRebindSupport<EnricherMemento> {

    private final AbstractEnricher enricher;
    
    public BasicEnricherRebindSupport(AbstractEnricher enricher) {
        super(enricher);
        this.enricher = enricher;
    }
    
    @Override
    protected void addConfig(RebindContext rebindContext, EnricherMemento memento) {
        // TODO entity does config-lookup differently; the memento contains the config keys.
        // BasicEntityMemento.postDeserialize uses the injectTypeClass to call EntityTypes.getDefinedConfigKeys(clazz)
        // 
        // Note that the flags may have been set in the constructor; but some enrichers have no-arg constructors
        ConfigBag configBag = ConfigBag.newInstance(memento.getConfig());
        FlagUtils.setFieldsFromFlags(enricher, configBag);
        FlagUtils.setAllConfigKeys(enricher, configBag, false);
    }
    
    @Override
    protected void addCustoms(RebindContext rebindContext, EnricherMemento memento) {
        // no-op
    }
}
