package io.brooklyn.camp.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.brooklyn.camp.rest.util.DtoFactory;
import io.brooklyn.camp.spi.ApplicationComponent;
import io.brooklyn.camp.spi.Link;
import io.brooklyn.camp.spi.PlatformComponent;

public class PlatformComponentDto extends ResourceDto {

    // defined as a constant so can be used in Swagger REST API annotations
    public static final String CLASS_NAME = "io.brooklyn.camp.dto.PlatformComponentDto";
    static { assert CLASS_NAME.equals(PlatformComponentDto.class.getCanonicalName()); }
 
    protected PlatformComponentDto() {}
    protected PlatformComponentDto(DtoFactory dtoFactory, PlatformComponent x) {
        super(dtoFactory, x);
        setExternalManagementUri(x.getExternalManagementUri());
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

    private String externalManagementUri;

    @JsonInclude(Include.NON_EMPTY)
    public List<LinkDto> getPlatformComponents() {
        return platformComponents;
    }
    
    @JsonInclude(Include.NON_EMPTY)
    public List<LinkDto> getApplicationComponents() {
        return applicationComponents;
    } 
    
    @JsonInclude(Include.NON_EMPTY)
    public String getExternalManagementUri() {
        return externalManagementUri;
    }
    private void setExternalManagementUri(String externalManagementUri) {
        this.externalManagementUri = externalManagementUri;
    }
    
    // --- building ---

    public static PlatformComponentDto newInstance(DtoFactory dtoFactory, PlatformComponent x) {
        return new PlatformComponentDto(dtoFactory, x);
    }
    
}
