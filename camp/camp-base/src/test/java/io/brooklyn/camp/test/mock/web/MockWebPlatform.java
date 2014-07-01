package io.brooklyn.camp.test.mock.web;

import io.brooklyn.camp.BasicCampPlatform;
import io.brooklyn.camp.spi.ApplicationComponentTemplate;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.collection.BasicResourceLookup;
import io.brooklyn.camp.spi.pdp.Artifact;
import io.brooklyn.camp.spi.pdp.AssemblyTemplateConstructor;
import io.brooklyn.camp.spi.resolve.PdpMatcher;

public class MockWebPlatform {

    public static final ApplicationComponentTemplate WAR = 
            ApplicationComponentTemplate.builder()
                .name("io.camp.mock:WAR")
                .description("Mock WAR")
                .build();

    public static final PlatformComponentTemplate APPSERVER = 
            PlatformComponentTemplate.builder()
                .name("io.camp.mock:AppServer")
                .description("Mock Application Server")
                .build();

    public static final PlatformComponentTemplate DATABASE = 
            PlatformComponentTemplate.builder()
                .name("io.camp.mock:Database")
                .description("Mock Database")
                .build();

    public static final AssemblyTemplate ASSEMBLY1 =
            AssemblyTemplate.builder()
                .name("WebAppAssembly1")
                .description("Mock Web App Assembly Template")
                .applicationComponentTemplates(BasicResourceLookup.of(WAR))
                .instantiator(MockAssemblyTemplateInstantiator.class)
                .build();

    public static final PdpMatcher WAR_GETS_WAR_MATCHER = new PdpMatcher.ArtifactMatcher("com.java:WAR") {
        public boolean apply(Object art, AssemblyTemplateConstructor atc) {
            ApplicationComponentTemplate act = ApplicationComponentTemplate.builder()
                    .name( ((Artifact)art).getName() )
                    .description( ((Artifact)art).getDescription() )
                    .customAttribute("implementation", WAR.getName())
                    .customAttribute("artifactType", ((Artifact)art).getArtifactType())
                    .build();

            // TODO requirements, etc
            
            atc.add(act);
            
            return true;
        }
    };
    
    public static <T extends BasicCampPlatform> T populate(T platform) {
        platform.platformComponentTemplates().addAll(APPSERVER, DATABASE);
        platform.applicationComponentTemplates().add(WAR);
        platform.assemblyTemplates().add(ASSEMBLY1);
        
        platform.pdp().addMatcher(WAR_GETS_WAR_MATCHER);
        
        return platform;
    }

    public static BasicCampPlatform newPlatform() {
        return MockWebPlatform.populate(new BasicCampPlatform());
    }
    
}
