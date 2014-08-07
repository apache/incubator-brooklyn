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
package brooklyn.enricher.basic;

import static com.google.common.base.Preconditions.checkState;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.rebind.BasicEnricherRebindSupport;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.mementos.EnricherMemento;
import brooklyn.policy.Enricher;
import brooklyn.policy.EnricherType;
import brooklyn.policy.basic.AbstractEntityAdjunct;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

/**
* Base {@link Enricher} implementation; all enrichers should extend this or its children
*/
public abstract class AbstractEnricher extends AbstractEntityAdjunct implements Enricher {

    public static final ConfigKey<Boolean> SUPPRESS_DUPLICATES = ConfigKeys.newBooleanConfigKey("enricher.suppressDuplicates",
        "Whether duplicate values published by this enricher should be suppressed");

    private final EnricherDynamicType enricherType;
    protected Boolean suppressDuplicates;

    public AbstractEnricher() {
        this(Maps.newLinkedHashMap());
    }
    
    public AbstractEnricher(Map<?,?> flags) {
        super(flags);
        
        enricherType = new EnricherDynamicType(this);
        
        if (isLegacyConstruction() && !isLegacyNoConstructionInit()) {
            init();
        }
    }

    @Override
    public RebindSupport<EnricherMemento> getRebindSupport() {
        return new BasicEnricherRebindSupport(this);
    }
    
    @Override
    public EnricherType getEnricherType() {
        return enricherType.getSnapshot();
    }

    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        this.suppressDuplicates = getConfig(SUPPRESS_DUPLICATES);
    }
    
    @Override
    protected void onChanged() {
        requestPersist();
    }

    @Override
    protected <T> void emit(Sensor<T> sensor, T val) {
        checkState(entity != null, "entity must first be set");
        
        if (sensor instanceof AttributeSensor) {
            if (Boolean.TRUE.equals(suppressDuplicates)) {
                T oldValue = entity.getAttribute((AttributeSensor<T>)sensor);
                if (Objects.equal(oldValue, val))
                    return;
            }
            entity.setAttribute((AttributeSensor<T>)sensor, val);
        } else { 
            entity.emit(sensor, val);
        }

    }
    
}
