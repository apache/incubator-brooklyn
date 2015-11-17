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
package org.apache.brooklyn.core.resolve.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import javax.annotation.Nonnull;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.PropagatedRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class DelegatingEntitySpecResolver extends AbstractEntitySpecResolver {
    private static final String RESOLVER_PREFIX_CATALOG = "catalog:";

    private static final String RESOLVER_PREFIX_JAVA = "java:";

    private static final Logger log = LoggerFactory.getLogger(DelegatingEntitySpecResolver.class);

    private static final String RESOLVER_NAME = "brooklyn";

    private Collection<EntitySpecResolver> resolvers;

    public DelegatingEntitySpecResolver(@Nonnull List<EntitySpecResolver> resolvers) {
        super(RESOLVER_NAME);
        this.resolvers = resolvers;
    }

    protected static ImmutableList<EntitySpecResolver> getRegisteredResolvers() {
        return ImmutableList.copyOf(ServiceLoader.load(EntitySpecResolver.class));
    }

    @Override
    public boolean accepts(String type, BrooklynClassLoadingContext loader) {
        return accepts("", type, loader) ||
                accepts(RESOLVER_PREFIX_CATALOG, type, loader) ||
                accepts(RESOLVER_PREFIX_JAVA, type, loader);
    }

    private boolean accepts(String prefix, String type, BrooklynClassLoadingContext loader) {
        for (EntitySpecResolver resolver : resolvers) {
            String localType = getLocalType(type);
            if (resolver.accepts(prefix + localType, loader)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public EntitySpec<?> resolve(String type, BrooklynClassLoadingContext loader, Set<String> encounteredTypes) {
        String localType = getLocalType(type);

        EntitySpec<?> spec = resolve(resolvers, localType, loader, encounteredTypes);
        if (spec != null) {
            return spec;
        }
        spec = resolve(resolvers, RESOLVER_PREFIX_CATALOG + localType, loader, encounteredTypes);
        if (spec != null) {
            return spec;
        }
        return resolve(resolvers, RESOLVER_PREFIX_JAVA + localType, loader, encounteredTypes);
    }

    private EntitySpec<?> resolve(
            Collection<EntitySpecResolver> resolvers,
            String localType,
            BrooklynClassLoadingContext loader,
            Set<String> encounteredTypes) {
        Collection<String> resolversWhoDontSupport = new ArrayList<String>();
        Collection<Exception> otherProblemsFromResolvers = new ArrayList<Exception>();

        for (EntitySpecResolver resolver : resolvers) {
            if (resolver.accepts(localType, loader)) {
                try {
                    EntitySpec<?> spec = resolver.resolve(localType, loader, encounteredTypes);
                    if (spec != null) {
                        return spec;
                    } else {
                        resolversWhoDontSupport.add(resolver.getName() + " (returned null)");
                    }
                } catch (Exception e) {
                    otherProblemsFromResolvers.add(new PropagatedRuntimeException("Transformer for "+resolver.getName()+" gave an error creating this plan: ",
                            Exceptions.collapseText(e), e));
                }
            }
        }
        if (!otherProblemsFromResolvers.isEmpty()) {
            // at least one thought he could do it
            log.debug("Type " + localType + " could not be resolved; failure will be propagated (other transformers tried = "+resolversWhoDontSupport+"): "+otherProblemsFromResolvers);
            throw otherProblemsFromResolvers.size()==1 ? Exceptions.create(null, otherProblemsFromResolvers) :
                Exceptions.create("ServiceSpecResolvers all failed", otherProblemsFromResolvers);
        }
        return null;
    }

    @Override
    public String toString() {
        return this.getClass() + "[" + resolvers + "]";
    }

}
