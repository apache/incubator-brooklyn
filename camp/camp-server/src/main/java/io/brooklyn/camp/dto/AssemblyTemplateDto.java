package io.brooklyn.camp.dto;

import java.util.ArrayList;
import java.util.List;

import io.brooklyn.camp.rest.util.DtoFactory;
import io.brooklyn.camp.spi.ApplicationComponentTemplate;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.Link;
import io.brooklyn.camp.spi.PlatformComponentTemplate;

public class AssemblyTemplateDto extends ResourceDto {

    // defined as a constant so can be used in Swagger REST API annotations
    public static final String CLASS_NAME = "io.brooklyn.camp.dto.AssemblyTemplateDto";
    static { assert CLASS_NAME.equals(AssemblyTemplateDto.class.getCanonicalName()); }

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
