package io.brooklyn.camp.spi.instantiate;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;

/** instances of this class should have a public no-arg constructor so the class names can be serialized */
public interface AssemblyTemplateInstantiator {

    public Assembly instantiate(AssemblyTemplate template, CampPlatform platform);
    
}
