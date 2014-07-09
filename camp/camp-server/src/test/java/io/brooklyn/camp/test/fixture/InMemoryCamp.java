package io.brooklyn.camp.test.fixture;

import io.brooklyn.camp.BasicCampPlatform;
import io.brooklyn.camp.CampServer;
import io.brooklyn.camp.test.mock.web.MockWebPlatform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InMemoryCamp {
    
    private static final Logger log = LoggerFactory.getLogger(InMemoryCamp.class);

    
    public static void main(String[] args) {
        
        // new platform with some mock types and some data structures
        
            // interface CampComponent
            // getComponentTemplate() -> operations, links, etc
        
            // platformView.getComponent(id) -> returns instance of domain-specific component type
        BasicCampPlatform p = new BasicCampPlatform();
        MockWebPlatform.populate(p);
        
        // new server
        CampServer s = new CampServer(p, "").start();
        
        log.info("Running at: "+s.getUriBase());
        // requests against server
        
    }
    
    
}
