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

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.mementos.BrooklynMementoPersister.LookupContext;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

public class RebindContextLookupContext implements LookupContext {
    
    private static final Logger LOG = LoggerFactory.getLogger(RebindContextLookupContext.class);
    
    @Nullable
    protected final ManagementContext managementContext;
    
    protected final RebindContext rebindContext;
    protected final RebindExceptionHandler exceptionHandler;
    
    public RebindContextLookupContext(RebindContext rebindContext, RebindExceptionHandler exceptionHandler) {
        this(null, rebindContext, exceptionHandler);
    }
    public RebindContextLookupContext(ManagementContext managementContext, RebindContext rebindContext, RebindExceptionHandler exceptionHandler) {
        this.managementContext = managementContext;
        this.rebindContext = rebindContext;
        this.exceptionHandler = exceptionHandler;
    }
    
    @Override public ManagementContext lookupManagementContext() {
        return managementContext;
    }
    
    @Override public Entity lookupEntity(String id) {
        Entity result = rebindContext.getEntity(id);
        if (result == null) {
            result = exceptionHandler.onDanglingEntityRef(id);
        }
        return result;
    }
    
    @Override public Location lookupLocation(String id) {
        Location result = rebindContext.getLocation(id);
        if (result == null) {
            result = exceptionHandler.onDanglingLocationRef(id);
        }
        return result;
    }
    
    @Override public Policy lookupPolicy(String id) {
        Policy result = rebindContext.getPolicy(id);
        if (result == null) {
            result = exceptionHandler.onDanglingPolicyRef(id);
        }
        return result;
    }
    
    @Override public Enricher lookupEnricher(String id) {
        Enricher result = rebindContext.getEnricher(id);
        if (result == null) {
            result = exceptionHandler.onDanglingEnricherRef(id);
        }
        return result;
    }
}