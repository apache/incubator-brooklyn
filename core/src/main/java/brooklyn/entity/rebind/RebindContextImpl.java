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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

import com.google.common.collect.Maps;

public class RebindContextImpl implements RebindContext {

    private final Map<String, Entity> entities = Maps.newLinkedHashMap();
    private final Map<String, Location> locations = Maps.newLinkedHashMap();
    private final Map<String, Policy> policies = Maps.newLinkedHashMap();
    private final Map<String, Enricher> enrichers = Maps.newLinkedHashMap();
    private final ClassLoader classLoader;
    private final RebindExceptionHandler exceptionHandler;
    
    public RebindContextImpl(RebindExceptionHandler exceptionHandler, ClassLoader classLoader) {
        this.exceptionHandler = checkNotNull(exceptionHandler, "exceptionHandler");
        this.classLoader = checkNotNull(classLoader, "classLoader");
    }

    public void registerEntity(String id, Entity entity) {
        entities.put(id, entity);
    }
    
    public void registerLocation(String id, Location location) {
        locations.put(id, location);
    }
    
    public void registerPolicy(String id, Policy policy) {
        policies.put(id, policy);
    }
    
    public void registerEnricher(String id, Enricher enricher) {
        enrichers.put(id, enricher);
    }
    
    public void unregisterPolicy(Policy policy) {
        policies.remove(policy.getId());
    }

    public void unregisterEnricher(Enricher enricher) {
        enrichers.remove(enricher.getId());
    }

    @Override
    public Entity getEntity(String id) {
        return entities.get(id);
    }

    @Override
    public Location getLocation(String id) {
        return locations.get(id);
    }
    
    @Override
    public Policy getPolicy(String id) {
        return policies.get(id);
    }
    
    @Override
    public Enricher getEnricher(String id) {
        return enrichers.get(id);
    }
    
    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        return classLoader.loadClass(className);
    }

    @Override
    public RebindExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }
}
