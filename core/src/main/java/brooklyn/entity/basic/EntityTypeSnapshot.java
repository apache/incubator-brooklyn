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
package brooklyn.entity.basic;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import brooklyn.basic.BrooklynTypeSnapshot;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.EntityType;
import brooklyn.entity.ParameterType;
import brooklyn.event.Sensor;
import brooklyn.util.guava.Maybe;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class EntityTypeSnapshot extends BrooklynTypeSnapshot implements EntityType {
    private static final long serialVersionUID = 4670930188951106009L;
    
    private final Map<String, Sensor<?>> sensors;
    private final Set<Effector<?>> effectors;
    private final Set<Sensor<?>> sensorsSet;

    EntityTypeSnapshot(String name, Map<String, ConfigKey<?>> configKeys, Map<String, Sensor<?>> sensors, Collection<Effector<?>> effectors) {
        super(name, configKeys);
        this.sensors = ImmutableMap.copyOf(sensors);
        this.effectors = ImmutableSet.copyOf(effectors);
        this.sensorsSet = ImmutableSet.copyOf(this.sensors.values());
    }

    @Override
    public Set<Sensor<?>> getSensors() {
        return sensorsSet;
    }
    
    @Override
    public Set<Effector<?>> getEffectors() {
        return effectors;
    }

    @Override
    public Maybe<Effector<?>> getEffectorByName(String name) {
        for (Effector<?> contender : effectors) {
            if (name.equals(contender.getName()))
                return Maybe.<Effector<?>>of(contender);
        }
        return Maybe.<Effector<?>>absent("No effector matching '"+name+"'");        
    }
    
    @Override
    public Effector<?> getEffector(String name, Class<?>... parameterTypes) {
        // TODO Could index for more efficient lookup (e.g. by name in a MultiMap, or using name+parameterTypes as a key)
        // TODO Looks for exact match; could go for what would be valid to call (i.e. if parameterType is sub-class of ParameterType.getParameterClass then ok)
        // TODO Could take into account ParameterType.getDefaultValue() for what can be omitted
        
        effectorLoop : for (Effector<?> contender : effectors) {
            if (name.equals(contender.getName())) {
                List<ParameterType<?>> contenderParameters = contender.getParameters();
                if (parameterTypes.length == contenderParameters.size()) {
                    for (int i = 0; i < parameterTypes.length; i++) {
                        if (parameterTypes[i] != contenderParameters.get(i).getParameterClass()) {
                            continue effectorLoop;
                        }
                    }
                    return contender;
                }
            }
        }
        throw new NoSuchElementException("No matching effector "+name+"("+Joiner.on(", ").join(parameterTypes)+") on entity "+getName());
    }

    @Override
    public Sensor<?> getSensor(String name) {
        return sensors.get(name);
    }

    @Override
    public boolean hasSensor(String name) {
        return sensors.containsKey(name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), sensors, effectors);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EntityTypeSnapshot)) return false;
        EntityTypeSnapshot o = (EntityTypeSnapshot) obj;
        
        return super.equals(obj) && Objects.equal(sensors, o.sensors) && Objects.equal(effectors, o.effectors);
    }
    
    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("sensors", sensors)
                .add("effectors", effectors);
    }
}
