package io.brooklyn.camp.test.mock.web;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockAssemblyTemplateInstantiator implements AssemblyTemplateInstantiator {

    private static final Logger log = LoggerFactory.getLogger(MockAssemblyTemplateInstantiator.class);
    
    public Assembly instantiate(AssemblyTemplate template, CampPlatform platform) {
        log.debug("Ignoring request to instantiate "+template);
        return null;
    }

}
