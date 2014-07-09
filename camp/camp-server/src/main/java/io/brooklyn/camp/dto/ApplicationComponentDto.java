package io.brooklyn.camp.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.brooklyn.camp.rest.util.DtoFactory;
import io.brooklyn.camp.spi.ApplicationComponent;
import io.brooklyn.camp.spi.Link;
import io.brooklyn.camp.spi.PlatformComponent;

public class ApplicationComponentDto extends ResourceDto {

    // defined as a constant so can be used in Swagger REST API annotations
    public static final String CLASS_NAME = "io.brooklyn.camp.dto.ApplicationComponentDto";
    static { assert CLASS_NAME.equals(ApplicationComponentDto.class.getCanonicalName()); }

    protected ApplicationComponentDto() {}
    protected ApplicationComponentDto(DtoFactory dtoFactory, ApplicationComponent x) {
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

    @JsonInclude(Include.NON_EMPTY)
    public List<LinkDto> getPlatformComponents() {
        return platformComponents;
    }
    
    @JsonInclude(Include.NON_EMPTY)
    public List<LinkDto> getApplicationComponents() {
        return applicationComponents;
    }
    
    // --- building ---

    public static ApplicationComponentDto newInstance(DtoFactory dtoFactory, ApplicationComponent x) {
        return new ApplicationComponentDto(dtoFactory, x);
    }
    
}
