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
package io.brooklyn.camp.rest.util;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.dto.ApplicationComponentDto;
import io.brooklyn.camp.dto.ApplicationComponentTemplateDto;
import io.brooklyn.camp.dto.AssemblyDto;
import io.brooklyn.camp.dto.AssemblyTemplateDto;
import io.brooklyn.camp.dto.PlatformComponentDto;
import io.brooklyn.camp.dto.PlatformComponentTemplateDto;
import io.brooklyn.camp.dto.PlatformDto;
import io.brooklyn.camp.rest.resource.AbstractCampRestResource;
import io.brooklyn.camp.rest.resource.ApplicationComponentRestResource;
import io.brooklyn.camp.rest.resource.ApplicationComponentTemplateRestResource;
import io.brooklyn.camp.rest.resource.AssemblyRestResource;
import io.brooklyn.camp.rest.resource.AssemblyTemplateRestResource;
import io.brooklyn.camp.rest.resource.PlatformComponentRestResource;
import io.brooklyn.camp.rest.resource.PlatformComponentTemplateRestResource;
import io.brooklyn.camp.rest.resource.PlatformRestResource;
import io.brooklyn.camp.spi.AbstractResource;
import io.brooklyn.camp.spi.ApplicationComponent;
import io.brooklyn.camp.spi.ApplicationComponentTemplate;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponent;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;

import java.util.Map;

import javax.ws.rs.Path;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Urls;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;

public class DtoFactory {

    private CampPlatform platform;
    private String uriBase;
    
    private UriFactory uriFactory;

    public DtoFactory(CampPlatform campPlatform, String uriBase) {
        this.platform = campPlatform;
        this.uriBase = uriBase;
        
        uriFactory = new UriFactory();
        uriFactory.registerIdentifiableRestResource(PlatformRootSummary.class, PlatformRestResource.class);
        uriFactory.registerIdentifiableRestResource(AssemblyTemplate.class, AssemblyTemplateRestResource.class);
        uriFactory.registerIdentifiableRestResource(PlatformComponentTemplate.class, PlatformComponentTemplateRestResource.class);
        uriFactory.registerIdentifiableRestResource(ApplicationComponentTemplate.class, ApplicationComponentTemplateRestResource.class);
        uriFactory.registerIdentifiableRestResource(Assembly.class, AssemblyRestResource.class);
        uriFactory.registerIdentifiableRestResource(PlatformComponent.class, PlatformComponentRestResource.class);
        uriFactory.registerIdentifiableRestResource(ApplicationComponent.class, ApplicationComponentRestResource.class);
    }

    public CampPlatform getPlatform() {
        return platform;
    }

    public UriFactory getUriFactory() {
        return uriFactory;
    }

    public String uri(AbstractResource x) {
        return getUriFactory().uri(x);
    }
        
    public String uri(Class<? extends AbstractResource> targetType, String id) {
        return getUriFactory().uri(targetType, id);
    }

    public AssemblyTemplateDto adapt(AssemblyTemplate assemblyTemplate) {
        return AssemblyTemplateDto.newInstance(this, assemblyTemplate);
    }
    public PlatformComponentTemplateDto adapt(PlatformComponentTemplate platformComponentTemplate) {
        return PlatformComponentTemplateDto.newInstance(this, platformComponentTemplate);
    }
    public ApplicationComponentTemplateDto adapt(ApplicationComponentTemplate applicationComponentTemplate) {
        return ApplicationComponentTemplateDto.newInstance(this, applicationComponentTemplate);
    }

    public AssemblyDto adapt(Assembly assembly) {
        return AssemblyDto.newInstance(this, assembly);
    }
    public PlatformComponentDto adapt(PlatformComponent platformComponent) {
        return PlatformComponentDto.newInstance(this, platformComponent);
    }
    public ApplicationComponentDto adapt(ApplicationComponent applicationComponent) {
        return ApplicationComponentDto.newInstance(this, applicationComponent);
    }

    public PlatformDto adapt(PlatformRootSummary root) {
        return PlatformDto.newInstance(this, root);
    }

    public class UriFactory {
        /** registry of generating a URI given an object */
        Map<Class<?>,Function<Object,String>> registryResource = new MutableMap<Class<?>, Function<Object,String>>();
        /** registry of generating a URI given an ID */
        Map<Class<?>,Function<String,String>> registryId = new MutableMap<Class<?>, Function<String,String>>();

        /** registers a function which generates a URI given a type; note that this method cannot be used for links */
        @SuppressWarnings("unchecked")
        public synchronized <T> void registerResourceUriFunction(Class<T> type, Function<T,String> fnUri) {
            registryResource.put(type, (Function<Object, String>) fnUri);
        }

        /** registers a type to generate a URI which concatenates the given base with the
         * result of the given function to generate an ID against an object of the given type */
        public synchronized <T> void registerIdentityFunction(Class<T> type, final String resourceTypeUriBase, final Function<T,String> fnIdentity) {
            final Function<String,String> fnUriFromId = new Function<String,String>() {
                public String apply(String id) {
                    return Urls.mergePaths(resourceTypeUriBase, id);
                }
            };
            registryId.put(type, (Function<String, String>) fnUriFromId);
            registerResourceUriFunction(type, new Function<T,String>() {
                public String apply(T input) {
                    return fnUriFromId.apply(fnIdentity.apply(input));
                }
            });
        }

        /** registers a CAMP Resource type against a RestResource, generating the URI
         * by concatenating the @Path annotation on the RestResource with the ID of the CAMP resource */
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public synchronized <T extends AbstractResource> void registerIdentifiableRestResource(Class<T> type, Class<? extends AbstractCampRestResource> restResource) {
            registerIdentityFunction(type, 
                    uriOfRestResource(restResource),
                    (Function) CampRestGuavas.IDENTITY_OF_REST_RESOURCE);
        }
        
        public String uri(Class<? extends AbstractResource> targetType, String id) {
            return Preconditions.checkNotNull(registryId.get(targetType), 
                    "No REST ID converter registered for %s (id %s)", targetType, id)
                    .apply(id);
        }

        public String uri(AbstractResource x) {
            return Preconditions.checkNotNull(registryResource.get(x.getClass()), 
                    "No REST converter registered for %s (%s)", x.getClass(), x)
                    .apply(x);
        }
        
        public String uriOfRestResource(Class<?> restResourceClass) {
            return Urls.mergePaths(uriBase, 
                    Preconditions.checkNotNull(restResourceClass.getAnnotation(Path.class),
                            "No @Path on type %s", restResourceClass)
                    .value());
        }
            

    }

}
