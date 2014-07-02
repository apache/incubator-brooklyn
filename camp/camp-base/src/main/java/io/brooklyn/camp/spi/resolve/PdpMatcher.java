package io.brooklyn.camp.spi.resolve;

import io.brooklyn.camp.spi.pdp.Artifact;
import io.brooklyn.camp.spi.pdp.AssemblyTemplateConstructor;
import io.brooklyn.camp.spi.pdp.Service;

/** Matchers build up the AssemblyTemplate by matching against items in the deployment plan */
public interface PdpMatcher {

    boolean accepts(Object deploymentPlanItem);
    boolean apply(Object deploymentPlanItem, AssemblyTemplateConstructor atc);

    public abstract class ArtifactMatcher implements PdpMatcher {
        private String artifactType;
        public ArtifactMatcher(String artifactType) {
            this.artifactType = artifactType;
        }
        public boolean accepts(Object art) {
            return (art instanceof Artifact) && this.artifactType.equals( ((Artifact)art).getArtifactType() );
        }
    }
    
    public abstract class ServiceMatcher implements PdpMatcher {
        private String serviceType;
        public ServiceMatcher(String serviceType) {
            this.serviceType = serviceType;
        }
        public boolean accepts(Object svc) {
            return (svc instanceof Service) && this.serviceType.equals( ((Service)svc).getServiceType() );
        }
    }

}
