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
package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import brooklyn.BrooklynVersion;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.EnricherMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.Memento;
import brooklyn.mementos.PolicyMemento;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class BrooklynMementoImpl implements BrooklynMemento, Serializable {

    private static final long serialVersionUID = -5848083830410137654L;
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        protected String brooklynVersion = BrooklynVersion.get();
        protected final List<String> applicationIds = Collections.synchronizedList(Lists.<String>newArrayList());
        protected final List<String> topLevelLocationIds = Collections.synchronizedList(Lists.<String>newArrayList());
        protected final Map<String, EntityMemento> entities = Maps.newConcurrentMap();
        protected final Map<String, LocationMemento> locations = Maps.newConcurrentMap();
        protected final Map<String, PolicyMemento> policies = Maps.newConcurrentMap();
        protected final Map<String, EnricherMemento> enrichers = Maps.newConcurrentMap();
        
        public Builder brooklynVersion(String val) {
            brooklynVersion = val; return this;
        }
        public Builder applicationId(String val) {
            applicationIds.add(val); return this;
        }
        public Builder applicationIds(List<String> vals) {
            applicationIds.addAll(vals); return this;
        }
        public Builder topLevelLocationIds(List<String> vals) {
            topLevelLocationIds.addAll(vals); return this;
        }
        public void memento(Memento memento) {
            if (memento instanceof EntityMemento) {
                entity((EntityMemento)memento);
            } else if (memento instanceof LocationMemento) {
                location((LocationMemento)memento);
            } else if (memento instanceof PolicyMemento) {
                policy((PolicyMemento)memento);
            } else if (memento instanceof EnricherMemento) {
                enricher((EnricherMemento)memento);
            } else {
                throw new IllegalStateException("Unexpected memento type :"+memento);
            }
        }
        public Builder entities(Map<String, EntityMemento> vals) {
            entities.putAll(vals); return this;
        }
        public Builder locations(Map<String, LocationMemento> vals) {
            locations.putAll(vals); return this;
        }
        public Builder policy(PolicyMemento val) {
            policies.put(val.getId(), val); return this;
        }
        public Builder enricher(EnricherMemento val) {
            enrichers.put(val.getId(), val); return this;
        }
        public Builder entity(EntityMemento val) {
            entities.put(val.getId(), val);
            if (val.isTopLevelApp()) {
                applicationId(val.getId());
            }
            return this;
        }
        public Builder location(LocationMemento val) {
            locations.put(val.getId(), val); return this;
        }
        public Builder policies(Map<String, PolicyMemento> vals) {
            policies.putAll(vals); return this;
        }
        public Builder enricheres(Map<String, EnricherMemento> vals) {
            enrichers.putAll(vals); return this;
        }
        public BrooklynMemento build() {
            return new BrooklynMementoImpl(this);
        }
    }

    @SuppressWarnings("unused")
    private String brooklynVersion;
    private List<String> applicationIds;
    private List<String> topLevelLocationIds;
    private Map<String, EntityMemento> entities;
    private Map<String, LocationMemento> locations;
    private Map<String, PolicyMemento> policies;
    private Map<String, EnricherMemento> enrichers;
    
    private BrooklynMementoImpl(Builder builder) {
        brooklynVersion = builder.brooklynVersion;
        applicationIds = builder.applicationIds;
        topLevelLocationIds = builder.topLevelLocationIds;
        entities = builder.entities;
        locations = builder.locations;
        policies = builder.policies;
        enrichers = builder.enrichers;
    }

    @Override
    public EntityMemento getEntityMemento(String id) {
        return entities.get(id);
    }

    @Override
    public LocationMemento getLocationMemento(String id) {
        return locations.get(id);
    }
    
    @Override
    public PolicyMemento getPolicyMemento(String id) {
        return policies.get(id);
    }
    
    @Override
    public EnricherMemento getEnricherMemento(String id) {
        return enrichers.get(id);
    }

    @Override
    public Collection<String> getApplicationIds() {
        return ImmutableList.copyOf(applicationIds);
    }

    @Override
    public Collection<String> getEntityIds() {
        return Collections.unmodifiableSet(entities.keySet());
    }
    
    @Override
    public Collection<String> getLocationIds() {
        return Collections.unmodifiableSet(locations.keySet());
    }
    
    @Override
    public Collection<String> getPolicyIds() {
        return Collections.unmodifiableSet(policies.keySet());
    }
    
    @Override
    public Collection<String> getEnricherIds() {
        return Collections.unmodifiableSet(enrichers.keySet());
    }
    
    @Override
    public Collection<String> getTopLevelLocationIds() {
        return Collections.unmodifiableList(topLevelLocationIds);
    }

    @Override
    public Map<String, EntityMemento> getEntityMementos() {
        return Collections.unmodifiableMap(entities);
    }
    @Override
    public Map<String, LocationMemento> getLocationMementos() {
        return Collections.unmodifiableMap(locations);
    }

    @Override
    public Map<String, PolicyMemento> getPolicyMementos() {
        return Collections.unmodifiableMap(policies);
    }

    @Override
    public Map<String, EnricherMemento> getEnricherMementos() {
        return Collections.unmodifiableMap(enrichers);
    }
}
