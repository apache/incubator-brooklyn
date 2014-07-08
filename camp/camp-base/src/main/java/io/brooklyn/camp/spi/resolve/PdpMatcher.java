/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
