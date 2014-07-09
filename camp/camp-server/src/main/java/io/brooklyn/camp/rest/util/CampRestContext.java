package io.brooklyn.camp.rest.util;

import javax.servlet.ServletContext;

import com.google.common.base.Preconditions;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.CampServer;

public class CampRestContext {

    private final ServletContext servletContext;
    private CampPlatform platform;
    private DtoFactory dto;
    
    public CampRestContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    public synchronized CampPlatform camp() {
        if (platform!=null) return platform;
        platform = (CampPlatform) servletContext.getAttribute(CampServer.CAMP_PLATFORM_ATTRIBUTE);
        return Preconditions.checkNotNull(platform, "CAMP platform instance not available from ServletContext");
    }

    public DtoFactory dto() {
        if (dto!=null) return dto;
        dto = (DtoFactory) servletContext.getAttribute(CampServer.DTO_FACTORY);
        return Preconditions.checkNotNull(dto, "CAMP DTO factory instance not available from ServletContext");
    }
    
}
