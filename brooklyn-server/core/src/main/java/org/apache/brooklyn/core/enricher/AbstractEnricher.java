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
package org.apache.brooklyn.core.enricher;

import static com.google.common.base.Preconditions.checkState;

import java.util.Map;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.mgmt.rebind.RebindSupport;
import org.apache.brooklyn.api.mgmt.rebind.mementos.EnricherMemento;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.api.sensor.EnricherType;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.mgmt.rebind.BasicEnricherRebindSupport;
import org.apache.brooklyn.core.objs.AbstractEntityAdjunct;
import org.apache.brooklyn.util.core.flags.TypeCoercions;

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
    
    @SuppressWarnings("unchecked")
    @Override
    public RelationSupportInternal<Enricher> relations() {
        return (RelationSupportInternal<Enricher>) super.relations();
    }
    
    @Override
    public EnricherType getEnricherType() {
        return enricherType.getSnapshot();
    }

    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        Boolean suppressDuplicates = getConfig(SUPPRESS_DUPLICATES);
        if (suppressDuplicates!=null) 
            this.suppressDuplicates = suppressDuplicates;
    }
    
    @Override
    protected void onChanged() {
        requestPersist();
    }

    @Override
    protected <T> void emit(Sensor<T> sensor, Object val) {
        checkState(entity != null, "entity must first be set");
        if (val == Entities.UNCHANGED) {
            return;
        }
        if (val == Entities.REMOVE) {
            ((EntityInternal)entity).removeAttribute((AttributeSensor<T>) sensor);
            return;
        }
        
        T newVal = TypeCoercions.coerce(val, sensor.getTypeToken());
        if (sensor instanceof AttributeSensor) {
            if (Boolean.TRUE.equals(suppressDuplicates)) {
                T oldValue = entity.getAttribute((AttributeSensor<T>)sensor);
                if (Objects.equal(oldValue, newVal))
                    return;
            }
            entity.sensors().set((AttributeSensor<T>)sensor, newVal);
        } else { 
            entity.sensors().emit(sensor, newVal);
        }
    }
    
}
