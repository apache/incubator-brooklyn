package io.brooklyn.camp.dto;

import io.brooklyn.camp.rest.util.DtoFactory;
import io.brooklyn.camp.spi.ApplicationComponent;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.Link;
import io.brooklyn.camp.spi.PlatformComponent;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public class AssemblyDto extends ResourceDto {

    // defined as a constant so can be used in Swagger REST API annotations
    public static final String CLASS_NAME = "io.brooklyn.camp.dto.AssemblyDto";
    static { assert CLASS_NAME.equals(AssemblyDto.class.getCanonicalName()); }

    protected AssemblyDto() {}
    protected AssemblyDto(DtoFactory dtoFactory, Assembly x) {
        super(dtoFactory, x);
        
        platformComponents = new ArrayList<LinkDto>();
        for (Link<PlatformComponent> t: x.getPlatformComponents().links()) {
            platformComponents.add(LinkDto.newInstance(dtoFactory, PlatformComponent.class, t));
        }
        
        applicationComponents = new ArrayList<LinkDto>();
        for (Link<ApplicationComponent> t: x.getApplicationComponents().links()) {
            applicationComponents.add(LinkDto.newInstance(dtoFactory, ApplicationComponent.class, t));
        }
    }
 
    private List<LinkDto> platformComponents;
    private List<LinkDto> applicationComponents;

    // TODO addl AssemblyTemplate fields
//  "parameterDefinitionUri": URI,
//  "pdpUri" : URI ?

    @JsonInclude(Include.NON_EMPTY)
    public List<LinkDto> getPlatformComponents() {
        return platformComponents;
    }
    
    @JsonInclude(Include.NON_EMPTY)
    public List<LinkDto> getApplicationComponents() {
        return applicationComponents;
    }
    
    // --- building ---

    public static AssemblyDto newInstance(DtoFactory dtoFactory, Assembly x) {
        return new AssemblyDto(dtoFactory, x);
    }
    
}
