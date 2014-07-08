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
package io.brooklyn.camp.dto;

import io.brooklyn.camp.rest.resource.ApidocRestResource;
import io.brooklyn.camp.rest.util.DtoFactory;
import io.brooklyn.camp.spi.ApplicationComponent;
import io.brooklyn.camp.spi.ApplicationComponentTemplate;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.Link;
import io.brooklyn.camp.spi.PlatformComponent;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public class PlatformDto extends ResourceDto {

    // defined as a constant so can be used in Swagger REST API annotations
    public static final String CLASS_NAME = "io.brooklyn.camp.dto.PlatformDto";
    static { assert CLASS_NAME.equals(PlatformDto.class.getCanonicalName()); }

    protected PlatformDto() {}
    protected PlatformDto(DtoFactory dtoFactory, PlatformRootSummary x) {
        super(dtoFactory, x);
        platformComponentTemplates = new ArrayList<LinkDto>();
        for (Link<PlatformComponentTemplate> t: dtoFactory.getPlatform().platformComponentTemplates().links()) {
            platformComponentTemplates.add(LinkDto.newInstance(dtoFactory, PlatformComponentTemplate.class, t));
        }
        
        applicationComponentTemplates = new ArrayList<LinkDto>();
        for (Link<ApplicationComponentTemplate> t: dtoFactory.getPlatform().applicationComponentTemplates().links()) {
            applicationComponentTemplates.add(LinkDto.newInstance(dtoFactory, ApplicationComponentTemplate.class, t));
        }

        assemblyTemplates = new ArrayList<LinkDto>();
        for (Link<AssemblyTemplate> t: dtoFactory.getPlatform().assemblyTemplates().links()) {
            assemblyTemplates.add(LinkDto.newInstance(dtoFactory, AssemblyTemplate.class, t));
        }

        platformComponents = new ArrayList<LinkDto>();
        for (Link<PlatformComponent> t: dtoFactory.getPlatform().platformComponents().links()) {
            platformComponents.add(LinkDto.newInstance(dtoFactory, PlatformComponent.class, t));
        }
        
        applicationComponents = new ArrayList<LinkDto>();
        for (Link<ApplicationComponent> t: dtoFactory.getPlatform().applicationComponents().links()) {
            applicationComponents.add(LinkDto.newInstance(dtoFactory, ApplicationComponent.class, t));
        }

        assemblies = new ArrayList<LinkDto>();
        for (Link<Assembly> t: dtoFactory.getPlatform().assemblies().links()) {
            assemblies.add(LinkDto.newInstance(dtoFactory, Assembly.class, t));
        }

        // TODO set custom fields

        apidoc = LinkDto.newInstance(
                dtoFactory.getUriFactory().uriOfRestResource(ApidocRestResource.class),
                "API documentation");
    }

    // TODO add custom fields
    private List<LinkDto> assemblyTemplates;
    private List<LinkDto> platformComponentTemplates;
    private List<LinkDto> applicationComponentTemplates;
    private List<LinkDto> assemblies;
    private List<LinkDto> platformComponents;
    private List<LinkDto> applicationComponents;
    
    // non-CAMP, but useful
    private LinkDto apidoc;
    
    public List<LinkDto> getAssemblyTemplates() {
        return assemblyTemplates;
    }
    
    public List<LinkDto> getPlatformComponentTemplates() {
        return platformComponentTemplates;
    }
    
    public List<LinkDto> getApplicationComponentTemplates() {
        return applicationComponentTemplates;
    }
    
    public List<LinkDto> getAssemblies() {
        return assemblies;
    }
    
    @JsonInclude(Include.NON_EMPTY)
    public List<LinkDto> getPlatformComponents() {
        return platformComponents;
    }
    
    @JsonInclude(Include.NON_EMPTY)
    public List<LinkDto> getApplicationComponents() {
        return applicationComponents;
    }
    
    public LinkDto getApidoc() {
        return apidoc;
    }
    
    // --- building ---

    public static PlatformDto newInstance(DtoFactory dtoFactory, PlatformRootSummary x) {
        return new PlatformDto(dtoFactory, x);
    }

}
