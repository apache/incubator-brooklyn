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
package org.apache.brooklyn.camp.brooklyn.spi.creation.service;

import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynComponentTemplateResolver;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.resolve.entity.AbstractEntitySpecResolver;
import org.apache.brooklyn.core.resolve.entity.EntitySpecResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;

@SuppressWarnings("deprecation")
public class ServiceTypeResolverAdaptor extends AbstractEntitySpecResolver {
    private static final Logger log = LoggerFactory.getLogger(ServiceTypeResolverAdaptor.class);
    private ServiceTypeResolver serviceTypeResolver;
    private BrooklynComponentTemplateResolver resolver;

    public ServiceTypeResolverAdaptor(BrooklynComponentTemplateResolver resolver, ServiceTypeResolver serviceTypeResolver) {
        super(serviceTypeResolver.getTypePrefix());
        this.serviceTypeResolver = serviceTypeResolver;
        this.resolver = resolver;
    }

    @Override
    public boolean accepts(String type, BrooklynClassLoadingContext loader) {
        if (type.indexOf(':') != -1) {
            String prefix = Splitter.on(":").splitToList(type).get(0);
            return prefix.equals(serviceTypeResolver.getTypePrefix());
        } else {
            return false;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public EntitySpec<?> resolve(String type, BrooklynClassLoadingContext loader, Set<String> encounteredTypes) {
        // Assume this is interface! Only known implementation is DockerServiceTypeResolver.
        String brooklynType = serviceTypeResolver.getBrooklynType(type);
        Class<? extends Entity> javaType = loader.loadClass(brooklynType, Entity.class);
        if (!javaType.isInterface()) {
            log.warn("Using " + ServiceTypeResolver.class.getSimpleName() + " with a non-interface type - this usage is not supported. Use " + EntitySpecResolver.class.getSimpleName() + " instead.");
        }
        EntitySpec<?> spec = EntitySpec.create((Class)javaType);
        serviceTypeResolver.decorateSpec(resolver, spec);
        return spec;
    }

}
