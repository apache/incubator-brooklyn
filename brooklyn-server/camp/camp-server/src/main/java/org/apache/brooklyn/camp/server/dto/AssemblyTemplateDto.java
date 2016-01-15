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
package org.apache.brooklyn.camp.server.dto;

import java.util.ArrayList;
import java.util.List;

import org.apache.brooklyn.camp.server.rest.util.DtoFactory;
import org.apache.brooklyn.camp.spi.ApplicationComponentTemplate;
import org.apache.brooklyn.camp.spi.AssemblyTemplate;
import org.apache.brooklyn.camp.spi.Link;
import org.apache.brooklyn.camp.spi.PlatformComponentTemplate;

public class AssemblyTemplateDto extends ResourceDto {

    protected AssemblyTemplateDto() {}
    protected AssemblyTemplateDto(DtoFactory dtoFactory, AssemblyTemplate x) {
        super(dtoFactory, x);
        
        platformComponentTemplates = new ArrayList<LinkDto>();
        for (Link<PlatformComponentTemplate> t: x.getPlatformComponentTemplates().links()) {
            platformComponentTemplates.add(LinkDto.newInstance(dtoFactory, PlatformComponentTemplate.class, t));
        }
        
        applicationComponentTemplates = new ArrayList<LinkDto>();
        for (Link<ApplicationComponentTemplate> t: x.getApplicationComponentTemplates().links()) {
            applicationComponentTemplates.add(LinkDto.newInstance(dtoFactory, ApplicationComponentTemplate.class, t));
        }
    }
 
    private List<LinkDto> platformComponentTemplates;
    private List<LinkDto> applicationComponentTemplates;

    // TODO addl AssemblyTemplate fields
//  "parameterDefinitionUri": URI,
//  "pdpUri" : URI ?

    public List<LinkDto> getPlatformComponentTemplates() {
        return platformComponentTemplates;
    }
    
    public List<LinkDto> getApplicationComponentTemplates() {
        return applicationComponentTemplates;
    }
    
    // --- building ---

    public static AssemblyTemplateDto newInstance(DtoFactory dtoFactory, AssemblyTemplate x) {
        return new AssemblyTemplateDto(dtoFactory, x);
    }
    
}
