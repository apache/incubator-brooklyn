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
package org.apache.brooklyn.rest.resources;

import io.brooklyn.camp.CampPlatform;

import javax.annotation.Nullable;
import javax.servlet.ServletContext;
import javax.ws.rs.core.Context;

import org.codehaus.jackson.map.ObjectMapper;

import brooklyn.config.BrooklynServerConfig;
import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.config.render.RendererHints;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.management.ManagementContextInjectable;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.rest.util.BrooklynRestResourceUtils;
import org.apache.brooklyn.rest.util.WebResourceUtils;
import org.apache.brooklyn.rest.util.json.BrooklynJacksonJsonProvider;

import brooklyn.util.guava.Maybe;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Duration;

public abstract class AbstractBrooklynRestResource implements ManagementContextInjectable {

    // can be injected by jersey when ManagementContext in not injected manually
    // (seems there is no way to make this optional so note it _must_ be injected;
    // most of the time that happens for free, but with test framework it doesn't,
    // so we have set up a NullServletContextProvider in our tests) 
    @Context ServletContext servletContext;
    
    private ManagementContext managementContext;
    private BrooklynRestResourceUtils brooklynRestResourceUtils;
    private ObjectMapper mapper;

    public ManagementContext mgmt() {
        return mgmtMaybe().get();
    }
    
    protected synchronized Maybe<ManagementContext> mgmtMaybe() {
        if (managementContext!=null) return Maybe.of(managementContext);
        managementContext = (ManagementContext) servletContext.getAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT);
        if (managementContext!=null) return Maybe.of(managementContext);
        
        return Maybe.absent("ManagementContext not available for Brooklyn Jersey Resource "+this);
    }
    
    public void injectManagementContext(ManagementContext managementContext) {
        if (this.managementContext!=null) {
            if (this.managementContext.equals(managementContext)) return;
            throw new IllegalStateException("ManagementContext cannot be changed: specified twice for Brooklyn Jersey Resource "+this);
        }
        this.managementContext = managementContext;
    }

    public synchronized BrooklynRestResourceUtils brooklyn() {
        if (brooklynRestResourceUtils!=null) return brooklynRestResourceUtils;
        brooklynRestResourceUtils = new BrooklynRestResourceUtils(mgmt());
        return brooklynRestResourceUtils;
    }
    
    protected ObjectMapper mapper() {
        if (mapper==null)
            mapper = BrooklynJacksonJsonProvider.findAnyObjectMapper(servletContext, managementContext);
        return mapper;
    }

    /** @deprecated since 0.7.0 use {@link #getValueForDisplay(Object, boolean, boolean, Boolean, EntityLocal, Duration)} */ @Deprecated
    protected Object getValueForDisplay(Object value, boolean preferJson, boolean isJerseyReturnValue) {
        return resolving(value).preferJson(preferJson).asJerseyOutermostReturnValue(isJerseyReturnValue).resolve();
    }

    protected RestValueResolver resolving(Object v) {
        return new RestValueResolver(v).mapper(mapper());
    }

    public static class RestValueResolver {
        final private Object valueToResolve;
        private @Nullable ObjectMapper mapper;
        private boolean preferJson;
        private boolean isJerseyReturnValue;
        private @Nullable Boolean raw; 
        private @Nullable Entity entity;
        private @Nullable Duration timeout;
        private @Nullable Object rendererHintSource;
        
        public static RestValueResolver resolving(Object v) { return new RestValueResolver(v); }
        
        private RestValueResolver(Object v) { valueToResolve = v; }
        
        public RestValueResolver mapper(ObjectMapper mapper) { this.mapper = mapper; return this; }
        
        /** whether JSON is the ultimate product; 
         * main effect here is to give null for null if true, else to give empty string 
         * <p>
         * conversion to JSON for complex types is done subsequently (often by the framework)
         * <p>
         * default is true */
        public RestValueResolver preferJson(boolean preferJson) { this.preferJson = preferJson; return this; }
        /** whether an outermost string must be wrapped in quotes, because a String return object is treated as
         * already JSON-encoded
         * <p>
         * default is false */
        public RestValueResolver asJerseyOutermostReturnValue(boolean asJerseyReturnJson) {
            isJerseyReturnValue = asJerseyReturnJson;
            return this;
        }
        public RestValueResolver raw(Boolean raw) { this.raw = raw; return this; }
        public RestValueResolver context(Entity entity) { this.entity = entity; return this; }
        public RestValueResolver timeout(Duration timeout) { this.timeout = timeout; return this; }
        public RestValueResolver renderAs(Object rendererHintSource) { this.rendererHintSource = rendererHintSource; return this; }

        public Object resolve() {
            Object valueResult = getImmediateValue(valueToResolve, entity);
            if (valueResult==UNRESOLVED) valueResult = valueToResolve;
            if (rendererHintSource!=null && Boolean.FALSE.equals(raw)) {
                valueResult = RendererHints.applyDisplayValueHintUnchecked(rendererHintSource, valueResult);
            }
            return WebResourceUtils.getValueForDisplay(mapper, valueResult, preferJson, isJerseyReturnValue);
        }
        
        private static Object UNRESOLVED = "UNRESOLVED".toCharArray();
        
        private static Object getImmediateValue(Object value, @Nullable Entity context) {
            return Tasks.resolving(value).as(Object.class).defaultValue(UNRESOLVED).timeout(Duration.ZERO).context(context).swallowExceptions().get();
        }

    }

    protected CampPlatform camp() {
        return BrooklynServerConfig.getCampPlatform(mgmt()).get();
    }
    
}
