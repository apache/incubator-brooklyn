package brooklyn.camp.brooklyn.api;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;
import brooklyn.entity.proxying.EntitySpec;

public interface AssemblyTemplateSpecInstantiator extends AssemblyTemplateInstantiator {

    EntitySpec<?> createSpec(AssemblyTemplate template, CampPlatform platform);
    
}
