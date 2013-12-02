package io.brooklyn.camp.brooklyn.spi.platform;

import brooklyn.management.ManagementContext;

public interface HasBrooklynManagementContext {

    public ManagementContext getBrooklynManagementContext();
    
}
