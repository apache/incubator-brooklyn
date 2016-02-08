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
package org.apache.brooklyn.rest.util.json;

import javax.servlet.ServletContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.ManagementContextInjectable;
import org.apache.brooklyn.core.server.BrooklynServiceAttributes;
import org.apache.brooklyn.rest.util.OsgiCompat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

public class BrooklynJacksonJsonProvider extends JacksonJsonProvider implements ManagementContextInjectable {

    private static final Logger log = LoggerFactory.getLogger(BrooklynJacksonJsonProvider.class);

    public static final String BROOKLYN_REST_OBJECT_MAPPER = BrooklynServiceAttributes.BROOKLYN_REST_OBJECT_MAPPER;

    @Context protected ServletContext servletContext;

    protected ObjectMapper ourMapper;
    protected boolean notFound = false;

    private ManagementContext mgmt;

    public ObjectMapper locateMapper(Class<?> type, MediaType mediaType) {
        if (ourMapper != null)
            return ourMapper;

        findSharedMapper();

        if (ourMapper != null)
            return ourMapper;

        if (!notFound) {
            log.warn("Management context not available; using default ObjectMapper in "+this);
            notFound = true;
        }

        return super.locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);
    }

    protected synchronized ObjectMapper findSharedMapper() {
        if (ourMapper != null || notFound)
            return ourMapper;

        ourMapper = findSharedObjectMapper(servletContext, mgmt);
        if (ourMapper == null) return null;

        if (notFound) {
            notFound = false;
        }
        log.debug("Found mapper "+ourMapper+" for "+this+", creating custom Brooklyn mapper");

        return ourMapper;
    }

    /**
     * Finds a shared {@link ObjectMapper} or makes a new one, stored against the servlet context;
     * returns null if a shared instance cannot be created.
     */
    public static ObjectMapper findSharedObjectMapper(ServletContext servletContext, ManagementContext mgmt) {
        if (servletContext != null) {
            synchronized (servletContext) {
                ObjectMapper mapper = (ObjectMapper) servletContext.getAttribute(BROOKLYN_REST_OBJECT_MAPPER);
                if (mapper != null) return mapper;

                mapper = newPrivateObjectMapper(getManagementContext(servletContext));
                servletContext.setAttribute(BROOKLYN_REST_OBJECT_MAPPER, mapper);
                return mapper;
            }
        }
        if (mgmt != null) {
            synchronized (mgmt) {
                ConfigKey<ObjectMapper> key = ConfigKeys.newConfigKey(ObjectMapper.class, BROOKLYN_REST_OBJECT_MAPPER);
                ObjectMapper mapper = mgmt.getConfig().getConfig(key);
                if (mapper != null) return mapper;

                mapper = newPrivateObjectMapper(mgmt);
                log.debug("Storing new ObjectMapper against "+mgmt+" because no ServletContext available: "+mapper);
                ((BrooklynProperties)mgmt.getConfig()).put(key, mapper);
                return mapper;
            }
        }
        return null;
    }

    /**
     * Like {@link #findSharedObjectMapper(ServletContext, ManagementContext)} but will create a private
     * ObjectMapper if it can, from the servlet context and/or the management context, or else fail
     */
    public static ObjectMapper findAnyObjectMapper(ServletContext servletContext, ManagementContext mgmt) {
        ObjectMapper mapper = findSharedObjectMapper(servletContext, mgmt);
        if (mapper != null) return mapper;

        if (mgmt == null && servletContext != null) {
            mgmt = getManagementContext(servletContext);
        }
        return newPrivateObjectMapper(mgmt);
    }

    /**
     * @return A new Brooklyn-specific ObjectMapper.
     *   Normally {@link #findSharedObjectMapper(ServletContext, ManagementContext)} is preferred
     */
    public static ObjectMapper newPrivateObjectMapper(ManagementContext mgmt) {
        if (mgmt == null) {
            throw new IllegalStateException("No management context available for creating ObjectMapper");
        }

        ConfigurableSerializerProvider sp = new ConfigurableSerializerProvider();
        sp.setUnknownTypeSerializer(new ErrorAndToStringUnknownTypeSerializer());

        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializerProvider(sp);
        mapper.setVisibility(new PossiblyStrictPreferringFieldsVisibilityChecker());

        SimpleModule mapperModule = new SimpleModule("Brooklyn", new Version(0, 0, 0, "ignored"));

        new BidiSerialization.ManagementContextSerialization(mgmt).install(mapperModule);
        new BidiSerialization.EntitySerialization(mgmt).install(mapperModule);
        new BidiSerialization.LocationSerialization(mgmt).install(mapperModule);

        mapper.registerModule(new GuavaModule()).registerModule(mapperModule);

        return mapper;
    }

    public static ManagementContext getManagementContext(ServletContext servletContext) {
        return OsgiCompat.getManagementContext(servletContext);
    }

    @Override
    public void setManagementContext(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }
}
